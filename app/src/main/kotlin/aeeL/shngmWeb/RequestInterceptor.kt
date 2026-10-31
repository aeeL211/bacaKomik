package aeeL.shngmWeb

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Handles every request that must not reach the network, or whose body needs a patch.
 *
 * Synthetic responses are checked first because they are cheap and do not allocate a request.
 * Only GET responses that can carry the premium flag are fetched with OkHttp: main-frame HTML,
 * SvelteKit data payloads, and user API calls.
 */
class RequestInterceptor {

  /** Sent as Accept-Language. Updated once the WebView exists. */
  @Volatile var language: String = Locale.getDefault().toLanguageTag()

  /** Same user agent as the WebView, so the site sees one client. */
  @Volatile var userAgent: String? = null

  private val cookies = CookieManager.getInstance()
  private val patchClient = Http.client.newBuilder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

  /** Returns a response to use, or null to let the WebView load the request itself. */
  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    val uri = request.url
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val path = uri.path.orEmpty()

    syntheticResponse(request, host, path)?.let { return it }

    val kind = patchKind(request, host, path) ?: return null
    return fetchAndPatch(request, kind)
  }

  private fun syntheticResponse(
    request: WebResourceRequest,
    host: String,
    path: String,
  ): WebResourceResponse? = when {
    host.contains("ads.shinigami") ->
      jsonResponse(request, adResponse(path.substringAfterLast('/')))

    host == "api.shngm.io" && path.startsWith("/v1/announcement") ->
      announcementResponse(request, path)

    host.contains("googletagmanager") ->
      emptyResponse(if (path.endsWith(".js")) "application/javascript" else "text/plain")

    host.endsWith("novu.my") ->
      emptyResponse("text/plain")

    else -> null
  }

  private fun announcementResponse(request: WebResourceRequest, path: String): WebResourceResponse = when {
    !path.contains("/detail/") -> jsonResponse(request, ANNOUNCEMENT_LIST_BYTES)
    path.endsWith(ANNOUNCEMENT_ID) -> jsonResponse(request, ANNOUNCEMENT_DETAIL_BYTES)
    else -> emptyResponse("text/plain")
  }

  private fun patchKind(request: WebResourceRequest, host: String, path: String): PatchKind? {
    val method = request.method ?: return null
    if (!method.equals("GET", ignoreCase = true)) return null
    if (!isComicHost(host)) return null

    return when {
      path.endsWith(DATA_SUFFIX) -> PatchKind.JSON

      host == API_HOST && path.startsWith(USER_PATH) -> PatchKind.JSON

      request.isForMainFrame -> {
        val accept = request.requestHeaders.header("Accept")
        if (accept == null || accept.contains("text/html", ignoreCase = true)) PatchKind.HTML else null
      }

      else -> null
    }
  }

  private fun fetchAndPatch(request: WebResourceRequest, kind: PatchKind): WebResourceResponse? {
    val url = request.url.toString()
    val startedAt = System.currentTimeMillis()

    return try {
      val builder = Request.Builder()
        .url(url)
        .get()
        .header("Accept-Language", language)
        .header("Accept", request.requestHeaders.header("Accept") ?: kind.defaultAccept)

      userAgent?.let { builder.header("User-Agent", it) }
      cookies.getCookie(url)?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }

      request.requestHeaders.forEach { (name, value) ->
        if (name.lowercase(Locale.ROOT) !in SKIPPED_HEADERS) {
          builder.header(name, value)
        }
      }

      patchClient.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) {
          Logger.w(TAG) { "HTTP ${response.code} for $url" }
          return null
        }

        val contentType = response.header("Content-Type").orEmpty()
        if (!kind.matches(contentType)) {
          Logger.w(TAG) { "Unexpected content type (${contentType.ifEmpty { "unknown" }}) for $url" }
          return null
        }

        val declaredLength = response.body.contentLength()
        if (declaredLength > MAX_BODY_BYTES) {
          Logger.w(TAG) { "Body too large ($declaredLength bytes) for $url" }
          return null
        }

        val finalUrl = response.request.url.toString()
        response.headers("Set-Cookie").forEach { cookies.setCookie(finalUrl, it) }

        val body = response.body.bytes()
        if (body.size.toLong() > MAX_BODY_BYTES) {
          Logger.w(TAG) { "Body too large (${body.size} bytes) for $url" }
          return null
        }

        val patched = PremiumPatcher.patch(body)
        if (patched > 0) {
          Logger.d(TAG) { "Patched $patched is_premium flag(s) on $url" }
        }

        val mimeType = contentType.substringBefore(';').trim().ifEmpty { kind.defaultMime }
        val headers = buildMap {
          put("Content-Type", mimeType)
          response.header("Cache-Control")?.let { put("Cache-Control", it) }
          response.header("Content-Language")?.let { put("Content-Language", it) }
          if (kind == PatchKind.JSON) putAll(corsHeaders(request))
        }

        Logger.logNetwork("GET", url, response.code, System.currentTimeMillis() - startedAt)

        WebResourceResponse(
          mimeType,
          "UTF-8",
          response.code,
          response.message.ifEmpty { "OK" },
          headers,
          ByteArrayInputStream(body),
        )
      }
    } catch (e: Exception) {
      Logger.e(TAG, e) { "Failed to patch $url" }
      null
    }
  }

  private fun jsonResponse(request: WebResourceRequest, body: ByteArray): WebResourceResponse {
    val headers = corsHeaders(request)
    return if (request.method.equals("OPTIONS", ignoreCase = true)) {
      WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers, emptyStream())
    } else {
      WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(body))
    }
  }

  private fun emptyResponse(mimeType: String): WebResourceResponse = WebResourceResponse(mimeType, "utf-8", 200, "OK", emptyMap(), emptyStream())

  private fun corsHeaders(request: WebResourceRequest): Map<String, String> {
    val origin = request.requestHeaders.header("Origin")
    return buildMap {
      put("Access-Control-Allow-Origin", origin ?: "*")
      put("Access-Control-Allow-Methods", "GET, OPTIONS")
      put("Access-Control-Allow-Headers", request.requestHeaders.header("Access-Control-Request-Headers") ?: "*")
      put("Vary", "Origin")
      if (origin != null) put("Access-Control-Allow-Credentials", "true")
    }
  }

  private fun emptyStream() = ByteArrayInputStream(EMPTY_BYTES)

  private fun adResponse(pageId: String): ByteArray = """{"message":"success","meta":{"request_id":"","timestamp":0,"process_time":"0ms"},"data":[{"page_id":"$pageId","sections":[{"section_id":1,"section_num":1,"layout":{"rows":1,"columns":1},"type":"fixed","ads":[]}]}]}"""
    .toByteArray(Charsets.UTF_8)

  private fun Map<String, String>.header(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

  companion object {
    private const val TAG = "RequestInterceptor"
    private const val ANNOUNCEMENT_ID = "8dfa0456-9c8a-4f0e-a1de-5153a26e13e5"
    private const val API_HOST = "api.shngm.io"
    private const val USER_PATH = "/v1/user"
    private const val DATA_SUFFIX = "/__data.json"
    private const val MAX_BODY_BYTES = 8L * 1024 * 1024

    private val SKIPPED_HEADERS = setOf(
      "host",
      "connection",
      "content-length",
      "accept",
      "accept-encoding",
      "user-agent",
      "cookie",
      "if-none-match",
      "if-modified-since",
      "if-range",
      "range",
    )

    private val EMPTY_BYTES = ByteArray(0)

    private val ANNOUNCEMENT_CONTENT = """
      Web2APK adalah website yang dibungkus menjadi aplikasi Android, dibuat dan dikembangkan oleh AeeL.

      ## Fitur

      - **Pemblokiran iklan:** membaca lebih nyaman tanpa gangguan.
      - **Tampilan ponsel:** tampilan dan navigasi dioptimalkan untuk layar ponsel.
      - **Premium Extension:** sistem ekstensi bawaan aplikasi.

      ## Cara kerja ekstensi

      Ekstensi bekerja di dalam aplikasi. Respons web diubah langsung sebelum ditampilkan, sehingga fitur premium terbuka di sisi aplikasi. Ekstensi ini terinspirasi oleh nullRE.

      Ekstensi mengubah data akun yang dimuat setelah login, jadi tanpa login tidak ada yang dapat diubah. Perubahan hanya terjadi di aplikasi, tidak di server, sehingga fitur yang diverifikasi langsung oleh server tidak ikut terbuka.

      ## Persyaratan

      - Sudah login ke akun.
      - Koneksi internet aktif.
    """.trimIndent()

    private val ANNOUNCEMENT_ITEM = JSONObject()
      .put("announcement_id", ANNOUNCEMENT_ID)
      .put("title", "Web2APK by AeeL")
      .put("content", ANNOUNCEMENT_CONTENT)
      .put("thumbnail_image_url", "https://assets.shngm.id/thumbnail/image/72cea7ce-532f-4fea-b83f-80a41ecc340c.jpg")
      .put("publish_status", 1)
      .put("created_date", "2025-11-16T05:42:09Z")
      .put("created_at", "2025-11-16T05:42:09Z")
      .put("updated_at", "2026-01-09T00:27:11Z")

    private val ANNOUNCEMENT_DETAIL_BYTES = JSONObject()
      .put("retcode", 0)
      .put("message", "success")
      .put("data", ANNOUNCEMENT_ITEM)
      .toString()
      .toByteArray(Charsets.UTF_8)

    private val ANNOUNCEMENT_LIST_BYTES = JSONObject()
      .put("retcode", 0)
      .put("message", "success")
      .put(
        "meta",
        JSONObject().put("page", 1).put("page_size", 10).put("total_page", 1).put("total_record", 1),
      )
      .put("data", JSONArray().put(ANNOUNCEMENT_ITEM))
      .toString()
      .toByteArray(Charsets.UTF_8)
  }

  /** Response families that can carry the premium flag. */
  private enum class PatchKind(
    val defaultMime: String,
    val defaultAccept: String,
  ) {
    HTML("text/html", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"),
    JSON("application/json", "application/json, */*;q=0.8"),
    ;

    fun matches(contentType: String): Boolean = when (this) {
      HTML -> contentType.contains("html", ignoreCase = true)
      JSON -> contentType.contains("json", ignoreCase = true)
    }
  }
}
