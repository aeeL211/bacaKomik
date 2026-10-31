package aeeL.shngmWeb

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TAG = "MainScreen"

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
  showWelcomeDialog: Boolean,
  onDismissWelcome: () -> Unit,
  avatarChoice: AvatarChoiceState?,
  onUseOriginalAvatar: () -> Unit,
  onCropAvatarNormally: () -> Unit,
  onDismissAvatarChoice: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Full screen under the status bar and camera cutout (top is not padded).
  // Bottom (nav bar / keyboard) and sides (3-button nav in landscape) are padded.
  val webViewInsets = WindowInsets.systemBars
    .union(WindowInsets.ime)
    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

  // The host is created once and kept stable; AndroidView only attaches it to the hierarchy.
  val host = remember {
    PullToRefreshWebView(context).apply {
      val refreshHost = this
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
          refreshHost.isRefreshing = false
          return@setOnRefreshListener
        }
        viewModel.refresh()
      }

      onWebViewCreated(webView)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(DarkBackground),
  ) {
    AndroidView(
      factory = { host },
      update = { it.isRefreshing = uiState.isRefreshing },
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(webViewInsets),
    )

    LaunchedEffect(uiState.pageCommand?.id) {
      val command = uiState.pageCommand ?: return@LaunchedEffect
      val webView = host.webView

      try {
        val currentUrl = webView.url
        when {
          command.reloadIfSameOrigin && currentUrl != null && sameOrigin(currentUrl, command.url) ->
            webView.reload()
          currentUrl == null -> webView.loadUrl(command.url, viewModel.defaultHeaders)
          !command.reloadIfSameOrigin -> Unit
          else -> webView.loadUrl(command.url, viewModel.defaultHeaders)
        }
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Failed to apply page command for ${command.url}" }
      } finally {
        viewModel.onPageCommandHandled(command.id)
      }
    }

    val popup = popupWebView
    if (popup != null) {
      key(popup) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        ) {
          AndroidView(
            factory = { popup },
            modifier = Modifier
              .fillMaxSize()
              .windowInsetsPadding(webViewInsets),
          )
        }
      }
    }

    SplashOverlay(
      visible = uiState.isSplashVisible,
      progress = uiState.loadingProgress,
    )

    activeDialog?.let { dialog ->
      AppDialog(
        dialogType = dialog,
        onDismiss = onDismissDialog,
      )
    }

    avatarChoice?.let { choice ->
      AvatarChoiceDialog(
        state = choice,
        onUseOriginal = onUseOriginalAvatar,
        onCropNormally = onCropAvatarNormally,
        onDismiss = onDismissAvatarChoice,
      )
    }

    imageMenuUrl?.let { url ->
      ContextMenuBottomSheet(
        url = url,
        onDismissRequest = onDismissMenu,
        onOpenInPopup = onOpenPopup,
      )
    }

    if (showWelcomeDialog && !uiState.isSplashVisible) {
      WelcomeDialog(onDismiss = onDismissWelcome)
    }
  }
}

@Composable
private fun SplashOverlay(
  visible: Boolean,
  progress: Int,
) {
  AnimatedVisibility(
    visible = visible,
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

      if (progress > 0) {
        LinearProgressIndicator(
          progress = { progress / 100f },
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
}

private fun sameOrigin(first: String, second: String): Boolean {
  val a = first.toUri()
  val b = second.toUri()
  return a.scheme.equals(b.scheme, ignoreCase = true) &&
    a.host.equals(b.host, ignoreCase = true) &&
    a.port == b.port
}
