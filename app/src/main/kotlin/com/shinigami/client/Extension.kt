package com.shinigami.client

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches the site's HTML pages itself (OkHttp, with a disk cache), switches on premium in the page data,
 * and hands the result to the WebView. Everything else is left to the WebView.
 */
class Extension(cacheDir: File) {

  /** Sent as Accept-Language. Set once the WebView exists. */
  @Volatile var language = "en-US,en;q=0.9"

  /** Same user agent as the WebView, so the site sees one client. */
  @Volatile var userAgent: String? = null

  private val cookies = CookieManager.getInstance()
  private val htmlCache = Cache(File(cacheDir, "html_http_cache"), 20L * 1024 * 1024)
  private val htmlClient = httpClient.newBuilder().cache(htmlCache).build()

  // Last cookie string seen per host. A change (login/logout) means cached HTML belongs to another account.
  private val lastCookies = ConcurrentHashMap<String, String>()

  /** Returns the patched page, or null to let the WebView load the request itself. */
  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    val host = request.url.host ?: return null
    if (ALLOWED_HOSTS.none { host == it || host.endsWith(".$it") }) return null
    if (request.requestHeaders["Accept"]?.contains("text/html") != true) return null
    if (request.method.uppercase() !in SAFE_METHODS) return null

    val url = request.url.toString()
    val startTime = System.currentTimeMillis()
    return try {
      val cookie = cookies.getCookie(url).orEmpty()
      evictOnCookieChange(host, cookie)
      fetchHtml(url, host, cookie, request)?.also {
        Logger.logNetwork(request.method, url, it.statusCode, System.currentTimeMillis() - startTime)
      }
    } catch (e: Exception) {
      Logger.e(TAG, e) { "Failed to process: $url" }
      null
    }
  }

  private fun evictOnCookieChange(host: String, cookie: String) {
    val previous = lastCookies.put(host, cookie)
    if (previous == null || previous == cookie) return
    try {
      htmlCache.evictAll()
      Logger.i(TAG) { "Cookie changed for $host, cache evicted" }
    } catch (e: IOException) {
      Logger.e(TAG, e) { "Failed to evict cache on cookie change" }
    }
  }

  private fun fetchHtml(url: String, host: String, cookie: String, request: WebResourceRequest): WebResourceResponse? {
    val builder = Request.Builder()
      .url(url)
      .method(request.method, null)
      .header("Accept-Language", language)

    userAgent?.let { builder.header("User-Agent", it) }
    if (cookie.isNotEmpty()) builder.header("Cookie", cookie)

    request.requestHeaders.forEach { (key, value) ->
      if (key.lowercase() !in SKIPPED_HEADERS) builder.header(key, value)
    }

    return htmlClient.newCall(builder.build()).execute().use { response ->
      if (!response.isSuccessful) return null

      // Save new cookies, and remember them so our own login/logout does not wipe the cache.
      val setCookies = response.headers("Set-Cookie")
      if (setCookies.isNotEmpty()) {
        setCookies.forEach { cookies.setCookie(url, it) }
        lastCookies[host] = cookies.getCookie(url).orEmpty()
      }

      val contentType = response.header("Content-Type") ?: return null
      if (!contentType.contains("html", ignoreCase = true)) return null

      val html = response.body.string().replace("is_premium:false", "is_premium:true")

      val headers = mutableMapOf("Access-Control-Allow-Origin" to "*")
      response.header("Cache-Control")?.let { headers["Cache-Control"] = it }

      WebResourceResponse(
        contentType.substringBefore(';').trim(),
        "UTF-8",
        response.code,
        "OK",
        headers,
        ByteArrayInputStream(html.toByteArray()),
      )
    }
  }

  companion object {
    private const val TAG = "Extension"
    private val ALLOWED_HOSTS = setOf("shinigami.asia", "shngm.io")
    private val SAFE_METHODS = setOf("GET", "HEAD")
    private val SKIPPED_HEADERS = setOf(
      "host",
      "content-length",
      "accept-encoding",
      "user-agent",
      "connection",
      "if-none-match",
      "if-modified-since",
      "if-range",
    )

    // Shared by the extension, the config fetch and image downloads. Other OkHttp settings are defaults.
    val httpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
    }
  }
}
