package com.shinigami.client

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

object Blocker {

  private const val ANNOUNCEMENT_ID = "8dfa0456-9c8a-4f0e-a1de-5153a26e13e5"
  private fun emptyInputStream() = ByteArrayInputStream(ByteArray(0))

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

  private val ANNOUNCEMENT_DETAIL_JSON = JSONObject()
    .put("retcode", 0)
    .put("message", "success")
    .put("data", ANNOUNCEMENT_ITEM)
    .toString()

  private val ANNOUNCEMENT_LIST_JSON = JSONObject()
    .put("retcode", 0)
    .put("message", "success")
    .put(
      "meta",
      JSONObject().put("page", 1).put("page_size", 10).put("total_page", 1).put("total_record", 1),
    )
    .put("data", JSONArray().put(ANNOUNCEMENT_ITEM))
    .toString()

  fun intercept(request: WebResourceRequest): WebResourceResponse? {
    val url = request.url
    val host = url.host.orEmpty()
    val path = url.path.orEmpty()

    return when {
      url.toString().contains("ads.shinigami") -> {
        val pageId = path.substringAfterLast("/")
        jsonResponse(request, """{"message":"success","meta":{"request_id":"","timestamp":0,"process_time":"0ms"},"data":[{"page_id":"$pageId","sections":[{"section_id":1,"section_num":1,"layout":{"rows":1,"columns":1},"type":"fixed","ads":[]}]}]}""")
      }
      host == "api.shngm.io" && path.startsWith("/v1/announcement") -> announcementResponse(request, path)
      host.contains("googletagmanager") -> {
        val mimeType = if (path.endsWith(".js")) "application/javascript" else "text/plain"
        WebResourceResponse(mimeType, "utf-8", emptyInputStream())
      }
      host.endsWith("novu.my") -> WebResourceResponse("text/plain", "utf-8", emptyInputStream())
      else -> null
    }
  }

  private fun announcementResponse(request: WebResourceRequest, path: String): WebResourceResponse = when {
    !path.contains("/detail/") -> jsonResponse(request, ANNOUNCEMENT_LIST_JSON)
    path.endsWith(ANNOUNCEMENT_ID) -> jsonResponse(request, ANNOUNCEMENT_DETAIL_JSON)
    else -> WebResourceResponse("text/plain", "utf-8", emptyInputStream())
  }

  private fun jsonResponse(request: WebResourceRequest, body: String): WebResourceResponse {
    val headers = mapOf(
      "Access-Control-Allow-Origin" to (request.requestHeaders["Origin"] ?: "*"),
      "Access-Control-Allow-Credentials" to "true",
      "Access-Control-Allow-Methods" to "GET, OPTIONS",
      "Access-Control-Allow-Headers" to (request.requestHeaders["Access-Control-Request-Headers"] ?: "*"),
    )

    return if (request.method == "OPTIONS") {
      WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers, emptyInputStream())
    } else {
      WebResourceResponse("application/json", "utf-8", 200, "OK", headers, ByteArrayInputStream(body.toByteArray()))
    }
  }
}
