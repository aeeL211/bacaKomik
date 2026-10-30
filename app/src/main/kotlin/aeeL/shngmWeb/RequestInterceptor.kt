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
 * Handles every request that must not reach the network, or whose HTML body needs a patch.
 *
 * Synthetic responses are checked first because they are cheap and do not allocate a request.
 * Only main-frame HTML requests for the comic hosts are fetched with OkHttp.
 */
class RequestInterceptor {

  /** Sent as Accept-Language. Updated once the WebView exists. */
  @Volatile var language: String = Locale.getDefault().toLanguageTag()

  /** Same user agent as the WebView, so the site sees one client. */
  @Volatile var userAgent: String? = null

  private val cookies = CookieManager.getInstance()
  private val htmlClient = Http.client.newBuilder()
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

  /** Returns a response to use, or null to let the WebView load the request itself. */
  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    val uri = request.url
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val path = uri.path.orEmpty()

    syntheticResponse(request, host, path)?.let { return it }

    if (!shouldPatchHtml(request, host)) return null
    return fetchAndPatch(request)
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

  private fun shouldPatchHtml(request: WebResourceRequest, host: String): Boolean {
    if (!request.isForMainFrame) return false
    val method = request.method ?: return false
    if (!method.equals("GET", ignoreCase = true)) return false
    if (ALLOWED_HOSTS.none { host == it || host.endsWith(".$it") }) return false

    val accept = request.requestHeaders.header("Accept")
    return accept == null ||
      accept.contains("text/html", ignoreCase = true) ||
      accept.contains("*/*")
  }

  private fun fetchAndPatch(request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    val startedAt = System.currentTimeMillis()

    return try {
      val builder = Request.Builder()
        .url(url)
        .get()
        .header("Accept-Language", language)
        .header("Accept", request.requestHeaders.header("Accept") ?: DEFAULT_ACCEPT)

      userAgent?.let { builder.header("User-Agent", it) }
      cookies.getCookie(url)?.takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }

      request.requestHeaders.forEach { (name, value) ->
        if (name.lowercase(Locale.ROOT) !in SKIPPED_HEADERS) {
          builder.header(name, value)
        }
      }

      htmlClient.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) {
          Logger.w(TAG) { "HTTP ${response.code} for $url" }
          return null
        }

        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("html", ignoreCase = true)) {
          Logger.w(TAG) { "Not HTML (${contentType.ifEmpty { "unknown" }}) for $url" }
          return null
        }

        val finalUrl = response.request.url.toString()
        response.headers("Set-Cookie").forEach { cookies.setCookie(finalUrl, it) }

        val html = patchPremium(response.body.string())
        val headers = buildMap {
          put("Content-Type", contentType.substringBefore(';').trim().ifEmpty { "text/html" })
          response.header("Cache-Control")?.let { put("Cache-Control", it) }
          response.header("Content-Language")?.let { put("Content-Language", it) }
        }

        Logger.logNetwork("GET", url, response.code, System.currentTimeMillis() - startedAt)

        WebResourceResponse(
          headers["Content-Type"] ?: "text/html",
          "UTF-8",
          response.code,
          response.message.ifEmpty { "OK" },
          headers,
          ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
      }
    } catch (e: Exception) {
      Logger.e(TAG, e) { "Failed to patch $url" }
      null
    }
  }

  private fun patchPremium(html: String): String {
    if (!html.contains("is_premium")) return html
    return PREMIUM_FLAG.replace(html) { match -> match.groupValues[1] + "true" }
  }

  private fun jsonResponse(request: WebResourceRequest, body: ByteArray): WebResourceResponse {
    val headers = corsHeaders(request)
    return if (request.method.equals("OPTIONS", ignoreCase = true)) {
      WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers, emptyStream())
    } else {
      WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(body))
    }
  }

  private fun emptyResponse(mimeType: String): WebResourceResponse =
    WebResourceResponse(mimeType, "utf-8", 200, "OK", emptyMap(), emptyStream())

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

  private fun adResponse(pageId: String): ByteArray =
    """{"message":"success","meta":{"request_id":"","timestamp":0,"process_time":"0ms"},"data":[{"page_id":"$pageId","sections":[{"section_id":1,"section_num":1,"layout":{"rows":1,"columns":1},"type":"fixed","ads":[]}]}]}"""
      .toByteArray(Charsets.UTF_8)

  private fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

  companion object {
    private const val TAG = "RequestInterceptor"
    private const val ANNOUNCEMENT_ID = "8dfa0456-9c8a-4f0e-a1de-5153a26e13e5"
    private const val DEFAULT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    private val ALLOWED_HOSTS = setOf("shinigami.asia", "shngm.io", "shinigami.to", "shngm.id")

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

    // Handles "is_premium":false, is_premium:false, 'is_premium': false and is_premium=!1.
    private val PREMIUM_FLAG = Regex("""(?<key>["']?is_premium["']?\s*[:=]\s*)(?:false|!1)""")

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
}
