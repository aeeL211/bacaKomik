package aeeL.shngmWeb

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Uploads an avatar without the website's cropper, so GIF and WebP keep their animation.
 *
 * The website's own upload endpoint always re-encodes to JPG. Catbox is used only as a public host
 * for the original file; the returned URL is stored through the profile API.
 */
internal object AvatarUploader {

  private const val CATBOX_URL = "https://catbox.moe/user/api.php"
  private const val PROFILE_URL = "https://api.shngm.io/v1/user/profile"
  private const val MAX_BYTES = 20 * 1024 * 1024
  private const val FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

  private val client by lazy {
    Http.client.newBuilder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  suspend fun uploadToCatbox(context: Context, uri: Uri, originalName: String?): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IOException("File tidak bisa dibaca.")
        if (bytes.isEmpty()) throw IOException("File kosong.")
        if (bytes.size > MAX_BYTES) throw IOException("Ukuran gambar maksimal 20 MB.")

        val mime = detectMime(bytes) ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (!mime.startsWith("image/")) throw IOException("File yang dipilih bukan gambar.")
        val fileName = buildFileName(originalName, mime)

        val body = MultipartBody.Builder()
          .setType(MultipartBody.FORM)
          .addFormDataPart("reqtype", "fileupload")
          .addFormDataPart("fileToUpload", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))
          .build()

        val request = Request.Builder()
          .url(CATBOX_URL)
          .post(body)
          .header("User-Agent", ImageCache.userAgent ?: FALLBACK_USER_AGENT)
          .build()

        client.newCall(request).execute().use { response ->
          val text = response.body.string().trim()
          if (!response.isSuccessful) throw IOException("Catbox HTTP ${response.code}: ${text.take(200)}")
          if (!text.startsWith("https://") && !text.startsWith("http://")) {
            throw IOException("Catbox menolak file: ${text.take(200)}")
          }
          text
        }
      }
    }

  suspend fun setAvatar(tokens: List<String>, imageUrl: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      if (tokens.isEmpty()) {
        return@withContext Result.failure(IOException("Sesi login tidak ditemukan. Login dulu di website."))
      }

      var lastError: Throwable? = null
      for (token in tokens.distinct()) {
        val result = setAvatarWithToken(token, imageUrl)
        if (result.isSuccess) return@withContext result

        val error = result.exceptionOrNull()
        lastError = error
        if (error !is AvatarApiException || error.code != 401) return@withContext result
      }
      Result.failure(lastError ?: IOException("Gagal menyimpan foto profil."))
    }

  private fun setAvatarWithToken(token: String, imageUrl: String): Result<Unit> = runCatching {
    val payload = JSONObject().put("avatar_image_url", imageUrl).toString()
    val request = Request.Builder()
      .url(PROFILE_URL)
      .put(payload.toRequestBody("application/json".toMediaTypeOrNull()))
      .header("Authorization", "Bearer $token")
      .header("Accept", "application/json")
      .header("User-Agent", ImageCache.userAgent ?: FALLBACK_USER_AGENT)
      .build()

    client.newCall(request).execute().use { response ->
      val body = response.body.string()
      if (response.code == 401) {
        throw AvatarApiException(401, "Sesi login kedaluwarsa. Login ulang.")
      }
      if (!response.isSuccessful) {
        throw AvatarApiException(response.code, "Gagal menyimpan foto profil (HTTP ${response.code}).")
      }
      val json = runCatching { JSONObject(body) }.getOrNull()
      if (json != null && json.has("success") && !json.optBoolean("success")) {
        throw AvatarApiException(
          response.code,
          json.optString("message").ifBlank { "Gagal menyimpan foto profil." },
        )
      }
    }
  }

  private fun buildFileName(originalName: String?, mime: String): String {
    val extension = extensionFor(mime)
    val base = originalName
      ?.trim()
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.takeIf { it.isNotEmpty() }
      ?: "avatar"
    if (extension == null) return base

    val lower = base.lowercase(Locale.ROOT)
    if (lower.endsWith(".$extension") || (extension == "jpg" && lower.endsWith(".jpeg"))) return base
    return "$base.$extension"
  }

  private fun extensionFor(mime: String): String? = when (mime.lowercase(Locale.ROOT)) {
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/avif" -> "avif"
    "image/heic", "image/heif" -> "heic"
    "image/bmp" -> "bmp"
    else -> null
  }

  private fun detectMime(bytes: ByteArray): String? {
    if (bytes.size >= 6) {
      val header = String(bytes, 0, 6, Charsets.US_ASCII)
      if (header == "GIF87a" || header == "GIF89a") return "image/gif"
    }
    if (bytes.size >= 12) {
      val riff = String(bytes, 0, 4, Charsets.US_ASCII)
      val webp = String(bytes, 8, 4, Charsets.US_ASCII)
      if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    }
    if (bytes.size >= 8 &&
      bytes[0] == 0x89.toByte() &&
      bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x4E.toByte() &&
      bytes[3] == 0x47.toByte()
    ) {
      return "image/png"
    }
    if (bytes.size >= 3 &&
      bytes[0] == 0xFF.toByte() &&
      bytes[1] == 0xD8.toByte() &&
      bytes[2] == 0xFF.toByte()
    ) {
      return "image/jpeg"
    }
    if (bytes.size >= 12) {
      val brand = String(bytes, 8, 4, Charsets.US_ASCII)
      if (brand == "avif" || brand == "avis") return "image/avif"
      if (brand == "heic" || brand == "heix" || brand == "hevc" || brand == "hevx" ||
        brand == "mif1" || brand == "msf1"
      ) {
        return "image/heic"
      }
    }
    return null
  }
}

internal class AvatarApiException(val code: Int, message: String) : IOException(message)
