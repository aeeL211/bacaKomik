package com.shinigami.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestInterceptorTest {

    private fun createMockRequest(
        urlStr: String,
        methodStr: String = "GET",
        headersMap: Map<String, String> = emptyMap(),
    ): android.webkit.WebResourceRequest {
        val request = mock(android.webkit.WebResourceRequest::class.java)
        val uri = android.net.Uri.parse(urlStr)

        `when`(request.url).thenReturn(uri)
        `when`(request.method).thenReturn(methodStr)
        `when`(request.requestHeaders).thenReturn(headersMap)

        return request
    }

    @Test
    fun testInterceptBlockedRequest_adsUrl_returnsJsonResponse() {
        val request = createMockRequest("https://ads.shinigami.id/api/ads/123")
        val response = RequestInterceptor.interceptBlockedRequest(request)
        assertNotNull(response)
        assertEquals("application/json", response?.mimeType)
        assertEquals("utf-8", response?.encoding)
    }

    @Test
    fun testInterceptBlockedRequest_announcementList_returnsJsonResponse() {
        val request = createMockRequest("https://api.shngm.io/v1/announcement")
        val response = RequestInterceptor.interceptBlockedRequest(request)
        assertNotNull(response)
        assertEquals("application/json", response?.mimeType)
    }

    @Test
    fun testInterceptBlockedRequest_googletagmanager_returnsJavascript() {
        val request = createMockRequest("https://www.googletagmanager.com/gtm.js")
        val response = RequestInterceptor.interceptBlockedRequest(request)
        assertNotNull(response)
        assertEquals("application/javascript", response?.mimeType)
    }

    @Test
    fun testInterceptBlockedRequest_unblockedUrl_returnsNull() {
        val request = createMockRequest("https://shinigami.id/manga/test")
        val response = RequestInterceptor.interceptBlockedRequest(request)
        assertNull(response)
    }
}
