package com.shinigami.client

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebExtension(cacheDir: File) {

  private val isDead = AtomicBoolean(false)

  private val htmlCache = Cache(File(cacheDir, "html_http_cache"), 20L * 1024 * 1024)

  private val htmlClient: OkHttpClient = httpClient.newBuilder()
    .cache(htmlCache)
    .build()

  private val cookieHashes = ConcurrentHashMap<String, String>()

  private val skippedHeaders = setOf(
    "host",
    "content-length",
    "accept-encoding",
    "user-agent",
    "connection",
    "if-none-match",
    "if-modified-since",
    "if-range",
  )
  private val allowedHosts = setOf("shinigami.asia", "shngm.io")

  @Volatile private var languageHeader = "en-US,en;q=0.9"

  @Volatile private var userAgentHeader: String? = null

  fun setLanguage(language: String) {
    languageHeader = language
    Logger.d(TAG) { "WebExtension Language set to: $language" }
  }

  fun setUserAgent(agent: String) {
    userAgentHeader = agent.replace("; wv", "")
    Logger.d(TAG) { "WebExtension User-Agent updated" }
  }

  fun shouldIntercept(request: WebResourceRequest): Boolean {
    if (isDead.get()) return false

    val host = request.url.host ?: return false
    if (!isAllowedHost(host)) return false

    val acceptHeader = request.requestHeaders["Accept"] ?: return false
    return acceptHeader.contains("text/html")
  }

  private fun isAllowedHost(host: String) = allowedHosts.any { host == it || host.endsWith(".$it") }

  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    if (isDead.get()) return null
    val urlString = request.url.toString()

    evictOnCookieChange(urlString)

    val startTime = System.currentTimeMillis()

    return try {
      fetchHtml(urlString, request)?.also { response ->
        Logger.logNetwork(request.method, urlString, response.statusCode, System.currentTimeMillis() - startTime)
      }
    } catch (e: Exception) {
      Logger.e(TAG, e) { "Interceptor failed to process: $urlString" }
      null
    }
  }

  private fun evictOnCookieChange(url: String) {
    val host = Uri.parse(url).host ?: return
    if (!isAllowedHost(host)) return
    val cookieString = CookieManager.getInstance().getCookie(url).orEmpty()
    val newHash = sha256(cookieString)
    val oldHash = cookieHashes[host]
    if (oldHash != null && oldHash != newHash) {
      try {
        htmlCache.evictAll()
        Logger.i(TAG) { "Cookie changed for $host, cache evicted" }
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Failed to evict cache on cookie change" }
      }
    }
    cookieHashes[host] = newHash
  }

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun fetchHtml(url: String, request: WebResourceRequest): WebResourceResponse? {
    val method = request.method
    if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) return null

    val requestBuilder = Request.Builder()
      .url(url)
      .method(method, null)
      .header("Accept-Language", languageHeader)

    userAgentHeader?.let { requestBuilder.header("User-Agent", it) }

    val cookieManager = CookieManager.getInstance()
    cookieManager.getCookie(url)?.let { requestBuilder.header("Cookie", it) }

    request.requestHeaders.forEach { (key, value) ->
      if (key.lowercase() !in skippedHeaders) {
        requestBuilder.header(key, value)
      }
    }

    return htmlClient.newCall(requestBuilder.build()).execute().use { response ->
      if (!response.isSuccessful) return null

      syncCookies(url, response.headers, cookieManager)

      val contentType = response.header("Content-Type") ?: return null
      if (!contentType.contains("html", ignoreCase = true)) return null

      val patchedContent = response.body.string().replace("is_premium:false", "is_premium:true")

      val responseHeaders = mutableMapOf("Access-Control-Allow-Origin" to "*")
      response.header("Cache-Control")?.let { responseHeaders["Cache-Control"] = it }

      Logger.i(TAG) { "Resource patched and served: $url" }

      WebResourceResponse(
        contentType.substringBefore(';').trim(),
        "UTF-8",
        response.code,
        "OK",
        responseHeaders,
        ByteArrayInputStream(patchedContent.toByteArray(StandardCharsets.UTF_8)),
      )
    }
  }

  private fun syncCookies(url: String, headers: Headers, cookieManager: CookieManager) {
    val cookies = headers.values("Set-Cookie")
    if (cookies.isNotEmpty()) {
      cookies.forEach { cookieStr ->
        cookieManager.setCookie(url, cookieStr)
      }
      Uri.parse(url).host?.let { host ->
        cookieHashes[host] = sha256(cookieManager.getCookie(url).orEmpty())
      }
    }
  }

  fun destroy() {
    if (isDead.getAndSet(true)) return
    Logger.i(TAG) { "WebExtension instance destroyed" }
  }

  companion object {
    private const val TAG = "WebExtension"

    val httpClient: OkHttpClient by lazy {
      OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .cache(null)
        .build()
    }
  }
}
