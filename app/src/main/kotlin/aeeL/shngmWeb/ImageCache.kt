package aeeL.shngmWeb

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Disk-backed cache for every image the WebView and the preview sheet load.
 *
 * OkHttp owns the cache file, so eviction, validators and concurrent access are handled for us.
 * Comic CDNs usually send no-store, which is why a reload used to download every page again; the
 * network interceptor rewrites that header for images only. The cache lives in cacheDir, so it
 * survives app restarts but can still be cleared by the system when storage runs low.
 */
object ImageCache {

  private const val TAG = "ImageCache"
  private const val CACHE_SIZE = 256L * 1024 * 1024
  private const val MAX_AGE_SECONDS = 7L * 24 * 60 * 60
  private const val IMAGE_ACCEPT = "image/webp,image/apng,image/*,*/*;q=0.8"

  /** Kept in sync with RequestInterceptor so both clients look like the same browser. */
  @Volatile var language: String = Locale.getDefault().toLanguageTag()

  @Volatile var userAgent: String? = null

  @Volatile private var initialized = false
  private lateinit var imageClient: OkHttpClient

  fun init(context: Context) {
    if (initialized) return

    synchronized(this) {
      if (initialized) return

      val cache = Cache(File(context.cacheDir, "image_cache"), CACHE_SIZE)
      imageClient = Http.client.newBuilder()
        .cache(cache)
        .addNetworkInterceptor(cacheHeaders)
        .build()
      initialized = true
    }
  }

  val client: OkHttpClient
    get() {
      check(initialized) { "ImageCache.init() must be called first" }
      return imageClient
    }

  /** True for plain image GETs; everything else is left to the WebView. */
  fun shouldHandle(request: WebResourceRequest): Boolean {
    val url = request.url
    if (url.scheme != "http" && url.scheme != "https") return false
    if (!request.method.equals("GET", ignoreCase = true)) return false
    if (request.requestHeaders.keys.any { it.equals("Range", ignoreCase = true) }) return false

    val path = url.path.orEmpty().lowercase(Locale.ROOT)
    val looksLikeImage = IMAGE_EXTENSIONS.any { path.endsWith(it) }
    if (request.isForMainFrame) return looksLikeImage
    if (looksLikeImage) return true

    val accept = request.requestHeaders.entries
      .firstOrNull { it.key.equals("Accept", ignoreCase = true) }
      ?.value
      .orEmpty()
    return accept.contains("image/", ignoreCase = true)
  }

  /** Serves the image from disk, or downloads it once. Null means "let the WebView load it". */
  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    if (!shouldHandle(request)) return null

    val url = request.url.toString()
    val builder = requestBuilder(url)
    request.requestHeaders.forEach { (name, value) ->
      if (name.lowercase(Locale.ROOT) !in SKIPPED_HEADERS) builder.header(name, value)
    }

    val response = try {
      client.newCall(builder.build()).execute()
    } catch (e: IOException) {
      Logger.w(TAG) { "Image request failed, using cache for $url: ${e.message}" }
      try {
        client.newCall(
          builder.build().newBuilder()
            .cacheControl(CacheControl.FORCE_CACHE)
            .build(),
        ).execute()
      } catch (e: IOException) {
        Logger.w(TAG) { "No cached image for $url: ${e.message}" }
        return null
      }
    }

    val contentType = response.header("Content-Type").orEmpty()
    if (!response.isSuccessful || !contentType.startsWith("image/", ignoreCase = true)) {
      response.close()
      return null
    }

    val headers = buildMap {
      put("Content-Type", contentType)
      response.header("Cache-Control")?.let { put("Cache-Control", it) }
      response.header("ETag")?.let { put("ETag", it) }
      response.header("Access-Control-Allow-Origin")?.let { put("Access-Control-Allow-Origin", it) }
      response.header("Access-Control-Allow-Credentials")?.let { put("Access-Control-Allow-Credentials", it) }
    }

    // The WebView normally forwards these; we replaced the request, so do it ourselves.
    response.headers("Set-Cookie").forEach { cookie ->
      CookieManager.getInstance().setCookie(url, cookie)
    }

    // Closing this stream also closes the OkHttp response. The WebView owns it from here.
    return WebResourceResponse(
      contentType.substringBefore(';').trim(),
      null,
      response.code,
      response.message.ifEmpty { "OK" },
      headers,
      response.body.byteStream(),
    )
  }

  /** Reads an image through the same cache; used by the long-press preview. */
  fun load(url: String): ByteArray? = try {
    client.newCall(requestBuilder(url).build()).execute().use { response ->
      if (response.isSuccessful) response.body.bytes() else null
    }
  } catch (e: Exception) {
    Logger.w(TAG) { "Preview load failed for $url: ${e.message}" }
    null
  }

  private fun requestBuilder(url: String): Request.Builder {
    val builder = Request.Builder()
      .url(url)
      .get()
      .header("Accept", IMAGE_ACCEPT)
      .header("Accept-Language", language)

    userAgent?.let { builder.header("User-Agent", it) }
    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let {
      builder.header("Cookie", it)
    }

    return builder
  }

  private val cacheHeaders = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    val contentType = response.header("Content-Type").orEmpty()

    if (response.isSuccessful && contentType.startsWith("image/", ignoreCase = true)) {
      response.newBuilder()
        .removeHeader("Pragma")
        .removeHeader("Expires")
        .header("Cache-Control", "private, max-age=$MAX_AGE_SECONDS, immutable")
        .build()
    } else {
      response
    }
  }

  private val IMAGE_EXTENSIONS = setOf(
    ".jpg",
    ".jpeg",
    ".png",
    ".webp",
    ".gif",
    ".avif",
    ".bmp",
    ".svg",
  )

  private val SKIPPED_HEADERS = setOf(
    "host",
    "connection",
    "content-length",
    "accept",
    "accept-encoding",
    "accept-language",
    "user-agent",
    "cookie",
    "cache-control",
    "pragma",
    "range",
    "if-none-match",
    "if-modified-since",
    "if-range",
  )
}
