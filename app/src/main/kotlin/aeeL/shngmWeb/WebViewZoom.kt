package aeeL.shngmWeb

import android.webkit.WebView
import androidx.core.net.toUri

/**
 * Applies the product zoom rule: zoom is enabled only on chapter pages.
 *
 * The website is an SPA, so a full page load is not the only way the URL changes. Native settings
 * are refreshed on every main-frame navigation signal, while the viewport patch is re-applied
 * after a document is available (page finished or a same-document history update). The patch is
 * idempotent, so applying it again is cheaper and safer than caching it across document swaps.
 */
internal object WebViewZoom {

  private const val CHAPTER = "chapter"

  /** The requirement is intentionally URL-based because mirrors use different path layouts. */
  fun isChapterUrl(url: String?): Boolean =
    !url.isNullOrBlank() && url.contains(CHAPTER, ignoreCase = true)

  /** Updates native zoom support without touching the current document. */
  fun apply(webView: WebView, url: String? = webView.url) {
    val enabled = isChapterUrl(url)
    webView.settings.apply {
      setSupportZoom(enabled)
      builtInZoomControls = enabled
      displayZoomControls = false
    }
  }

  /** Applies native settings and, when possible, makes the viewport agree with them. */
  fun sync(webView: WebView, url: String? = webView.url) {
    apply(webView, url)

    val host = runCatching { url?.toUri()?.host }.getOrNull()
    if (host == null || !isComicHost(host)) return

    val enabled = isChapterUrl(url)
    webView.evaluateJavascript(viewportScript(maxScale = if (enabled) 5 else 1, scalable = enabled), null)
  }

  private fun viewportScript(maxScale: Int, scalable: Boolean): String = """
    (function() {
      var meta = document.querySelector('meta[name="viewport"]');
      if (!meta) {
        meta = document.createElement('meta');
        meta.setAttribute('name', 'viewport');
        (document.head || document.documentElement).appendChild(meta);
      }
      var content = meta.getAttribute('content') || 'width=device-width, initial-scale=1';
      var parts = content.split(',').map(function (part) {
        return part.trim();
      }).filter(function (part) {
        return part && !/^(user-scalable|maximum-scale|minimum-scale)\s*=/i.test(part);
      });
      parts.push('maximum-scale=$maxScale');
      parts.push('user-scalable=${if (scalable) "yes" else "no"}');
      meta.setAttribute('content', parts.join(', '));
    })();
  """.trimIndent()
}
