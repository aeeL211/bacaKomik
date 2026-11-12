package aeeL.shngmWeb

import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TAG = "MainScreen"

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
  val colorScheme = MaterialTheme.colorScheme

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
      .background(colorScheme.background),
  ) {
    AndroidView(
      factory = { host },
      update = { hostView ->
        hostView.isRefreshing = uiState.isRefreshing
        hostView.applyTheme(
          background = colorScheme.background.toArgb(),
          progressBackground = colorScheme.surfaceContainerHigh.toArgb(),
          accent = colorScheme.primary.toArgb(),
        )
        // Recomposition can happen after a back/forward or a URL change; keep native zoom in sync.
        WebViewZoom.apply(hostView.webView, hostView.webView.url)
      },
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(webViewInsets),
    )

    LaunchedEffect(uiState.pageCommand?.id) {
      val command = uiState.pageCommand ?: return@LaunchedEffect
      val webView = host.webView

      try {
        WebViewZoom.apply(webView, command.url)
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
            // The image viewer stays dark to avoid flashes around full-screen artwork.
            .background(BaseDark),
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
  val colors = MaterialTheme.colorScheme

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(280)) + scaleIn(
      animationSpec = tween(280),
      initialScale = 0.98f,
    ),
    exit = fadeOut(animationSpec = tween(450)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(colors.surfaceContainerLow, colors.background),
          ),
        ),
      contentAlignment = Alignment.Center,
    ) {
      val progressModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .align(Alignment.BottomCenter)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .height(4.dp)

      Image(
        painter = painterResource(id = R.drawable.logo),
        contentDescription = null,
        modifier = Modifier
          .fillMaxWidth(0.62f)
          .aspectRatio(1f),
        contentScale = ContentScale.Fit,
      )

      if (progress > 0) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = progressModifier,
          color = colors.primary,
          trackColor = colors.primary.copy(alpha = 0.18f),
          strokeCap = StrokeCap.Round,
        )
      } else {
        LinearProgressIndicator(
          modifier = progressModifier,
          color = colors.primary,
          trackColor = colors.primary.copy(alpha = 0.18f),
          strokeCap = StrokeCap.Round,
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
