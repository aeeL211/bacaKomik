package aeeL.shngmWeb

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Standard pull-to-refresh host for the WebView.
 *
 * SwipeRefreshLayout already knows how to intercept a downward drag, animate the spinner and
 * release it on fling. We only tell it when the WebView is at the top, so the WebView keeps its
 * normal scrolling behaviour everywhere else.
 */
class PullToRefreshWebView(context: Context) : SwipeRefreshLayout(context) {

  val webView: WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
    setBackgroundColor(BACKGROUND)
  }

  init {
    setBackgroundColor(BACKGROUND)
    addView(webView)

    setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
    setColorSchemeColors(ACCENT)
    setProgressBackgroundColorSchemeColor(BACKGROUND_TOP)

    val density = resources.displayMetrics.density
    val defaultTop = statusBarHeight()
    setProgressViewOffset(false, defaultTop, defaultTop + (64 * density).toInt())

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
      setProgressViewOffset(false, top, top + (64 * density).toInt())
      insets
    }
  }

  private fun statusBarHeight(): Int {
    val id = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
  }

  companion object {
    private val ACCENT = 0xFF5B2FC0.toInt()
    private val BACKGROUND = 0xFF121212.toInt()
    private val BACKGROUND_TOP = 0xFF18181B.toInt()
  }
}
