package com.shinigami.client

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

private val DarkBackground = Color(0xFF121212)
private val SplashGradientTop = Color(0xFF18181B)
private val SplashGradientBottom = Color(0xFF09090B)
private val SplashProgress = Color(0xFF5B2FC0)
private val SplashProgressTrack = Color(0x335B2FC0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    activity: MainActivity,
    webExtension: WebExtension,
    mainWebViewState: WebView?,
    onMainWebViewCreated: (WebView) -> Unit,
    popupWebViewState: WebView?,
    onDismissPopup: () -> Unit,
    showContextMenuUrl: String?,
    onDismissContextMenu: () -> Unit,
    onOpenPopupWebView: (String) -> Unit,
    activeDialog: DialogType?,
    onDismissDialog: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Full screen under the status bar and camera cutout (top is not padded).
    // Bottom (nav bar / keyboard) and sides (3-button nav in landscape) are padded.
    // systemBars does not include the display cutout, so the waterdrop notch is covered.
    val webViewInsets = WindowInsets.systemBars
        .union(WindowInsets.ime)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    var canRefresh by remember { mutableStateOf(true) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    val isRefreshing = uiState.isRefreshing
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (canRefresh) {
                    viewModel.triggerManualRefresh {
                        val webView = currentWebView
                        if (webView != null) {
                            if (webView.url != null) {
                                webView.reload()
                            } else if (uiState.url != null) {
                                webView.loadUrl(uiState.url!!, viewModel.defaultHeaders)
                            }
                        }
                    }
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(webViewInsets),
        ) {
            AndroidView(
                factory = { ctx ->
                    NestedScrollWebView(ctx).apply {
                        activity.configureWebSettings(this)

                        webExtension.setLanguage(Locale.getDefault().toLanguageTag())
                        webExtension.setUserAgent(settings.userAgentString)

                        CookieManager.getInstance().let { cookieManager ->
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                        }

                        webViewClient = MainActivity.DefaultWebViewClient(activity)
                        webChromeClient = MainActivity.DefaultWebChromeClient(activity)

                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                activity.touchXCoordinate = event.x.toInt()
                                activity.touchYCoordinate = event.y.toInt()
                            }
                            false
                        }

                        setOnLongClickListener {
                            activity.detectImageElement()
                            true
                        }

                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            canRefresh = (scrollY == 0 || !canScrollVertically(-1))
                        }

                        currentWebView = this
                        onMainWebViewCreated(this)
                    }
                },
                update = { webView ->
                    currentWebView = webView
                    if (uiState.url != null && webView.url == null) {
                        webView.loadUrl(uiState.url!!, viewModel.defaultHeaders)
                    } else if (uiState.shouldReload) {
                        viewModel.onReloadHandled()
                        webView.reload()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            )
        }

        if (popupWebViewState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
            ) {
                AndroidView(
                    factory = { popupWebViewState },
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

        if (showContextMenuUrl != null) {
            ContextMenuBottomSheet(
                url = showContextMenuUrl,
                onDismissRequest = onDismissContextMenu,
                onOpenInPopup = onOpenPopupWebView,
            )
        }
    }
}
