package aeeL.shngmWeb

import android.net.Uri
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val DarkBackground = Color(0xFF121212)
private val SplashGradientTop = Color(0xFF18181B)
private val SplashGradientBottom = Color(0xFF09090B)
private val SplashProgress = Color(0xFF5B2FC0)
private val SplashProgressTrack = Color(0x335B2FC0)

@Composable
fun MainScreen(
  viewModel: MainViewModel,
  activity: MainActivity,
  onWebViewCreated: (WebView) -> Unit,
  popupWebView: WebView?,
  imageMenuUrl: String?,
  onDismissMenu: () -> Unit,
  onOpenPopup: (String) -> Unit,
  activeDialog: DialogType?,
  onDismissDialog: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  // Full screen under the status bar and camera cutout (top is not padded).
  // Bottom (nav bar / keyboard) and sides (3-button nav in landscape) are padded.
  val webViewInsets = WindowInsets.systemBars
    .union(WindowInsets.ime)
    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(DarkBackground),
  ) {
    AndroidView(
      factory = { ctx ->
        PullToRefreshWebView(ctx).apply {
          val host = this
          val webView = this.webView
          activity.configureWebView(webView)

          CookieManager.getInstance().let { cookieManager ->
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
          }

          webView.webViewClient = MainActivity.AppWebViewClient(activity)
          webView.webChromeClient = MainActivity.AppChromeClient(activity)

          webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
              activity.touchX = event.x.toInt()
              activity.touchY = event.y.toInt()
            }
            false
          }

          webView.setOnLongClickListener {
            activity.detectImage()
            true
          }

          setOnRefreshListener {
            if (webView.canScrollVertically(-1)) {
              host.isRefreshing = false
              return@setOnRefreshListener
            }

            viewModel.refresh { freshUrl ->
              val currentUrl = webView.url
              if (currentUrl == null || !sameOrigin(currentUrl, freshUrl)) {
                webView.loadUrl(freshUrl, viewModel.defaultHeaders)
              } else {
                webView.reload()
              }
            }
          }

          onWebViewCreated(webView)
        }
      },
      update = { host ->
        val webView = host.webView
        host.isRefreshing = uiState.isRefreshing

        val url = uiState.url
        if (!host.hasLoadedInitialUrl && url != null && webView.url == null) {
          host.hasLoadedInitialUrl = true
          viewModel.onReloadHandled()
          webView.loadUrl(url, viewModel.defaultHeaders)
        } else if (uiState.shouldReload && webView.url != null) {
          viewModel.onReloadHandled()
          webView.reload()
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    if (popupWebView != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(DarkBackground),
      ) {
        AndroidView(
          factory = { popupWebView },
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(webViewInsets),
        )
      }
    }

    AnimatedVisibility(
      visible = uiState.isSplashVisible,
      exit = fadeOut(animationSpec = tween(durationMillis = 500)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(SplashGradientTop, SplashGradientBottom),
            ),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(id = R.drawable.logo),
          contentDescription = null,
          modifier = Modifier
            .fillMaxWidth(0.65f)
            .aspectRatio(1f),
          contentScale = ContentScale.Fit,
        )

        if (uiState.loadingProgress > 0) {
          LinearProgressIndicator(
            progress = { uiState.loadingProgress / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
              .windowInsetsPadding(WindowInsets.navigationBars)
              .height(3.dp),
            color = SplashProgress,
            trackColor = SplashProgressTrack,
            strokeCap = StrokeCap.Butt,
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
              .windowInsetsPadding(WindowInsets.navigationBars)
              .height(3.dp),
            color = SplashProgress,
            trackColor = SplashProgressTrack,
            strokeCap = StrokeCap.Butt,
          )
        }
      }
    }

    if (activeDialog != null) {
      AppDialog(
        dialogType = activeDialog,
        onDismiss = onDismissDialog,
      )
    }

    if (imageMenuUrl != null) {
      ContextMenuBottomSheet(
        url = imageMenuUrl,
        onDismissRequest = onDismissMenu,
        onOpenInPopup = onOpenPopup,
      )
    }
  }
}

private fun sameOrigin(first: String, second: String): Boolean {
  val a = Uri.parse(first)
  val b = Uri.parse(second)
  return a.scheme.equals(b.scheme, ignoreCase = true) &&
    a.host.equals(b.host, ignoreCase = true) &&
    a.port == b.port
}
