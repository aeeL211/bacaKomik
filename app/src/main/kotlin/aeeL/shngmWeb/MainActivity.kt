package aeeL.shngmWeb

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale

class MainActivity : ComponentActivity() {

  val viewModel: MainViewModel by viewModels()
  private val interceptor by lazy { RequestInterceptor() }

  private val imageDetectorJs: String by lazy {
    assets.open("js/image_detector.js").bufferedReader().use { it.readText() }
  }

  private var mainWebView: WebView? = null
  var popupWebView by mutableStateOf<WebView?>(null)
    private set

  var imageMenuUrl by mutableStateOf<String?>(null)
  var activeDialog by mutableStateOf<DialogType?>(null)
  var showWelcomeDialog by mutableStateOf(false)
    private set

  private var uploadCallback: ValueCallback<Array<Uri>>? = null

  private var lastBackPressedTime = 0L
  var touchX = 0
  var touchY = 0

  private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val callback = uploadCallback ?: return@registerForActivityResult
    uploadCallback = null

    val resultUris = if (result.resultCode == RESULT_OK) {
      result.data?.data?.let { arrayOf(it) }
        ?: result.data?.clipData?.let { clipData ->
          Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
        }
    } else {
      null
    }

    callback.onReceiveValue(resultUris)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setupBackHandler()

    setContent {
      AppTheme {
        MainScreen(
          viewModel = viewModel,
          activity = this,
          onWebViewCreated = { webView ->
            mainWebView = webView
            savedInstanceState?.let { webView.restoreState(it) }
          },
          popupWebView = popupWebView,
          imageMenuUrl = imageMenuUrl,
          onDismissMenu = { imageMenuUrl = null },
          onOpenPopup = { url -> openPopup(url) },
          activeDialog = activeDialog,
          onDismissDialog = { activeDialog = null },
          showWelcomeDialog = showWelcomeDialog,
          onDismissWelcome = { dismissWelcomeDialog() },
        )
      }
    }

    showWelcomeOnce()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    mainWebView?.saveState(outState)
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun configureWebView(webView: WebView) {
    webView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      useWideViewPort = false
      loadWithOverviewMode = false
      setSupportZoom(false)
      builtInZoomControls = false
      displayZoomControls = false
      setSupportMultipleWindows(true)
      javaScriptCanOpenWindowsAutomatically = true
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      allowFileAccess = true
      allowContentAccess = true

      userAgentString = userAgentString.replace("; wv", "")
    }

    interceptor.language = Locale.getDefault().toLanguageTag()
    interceptor.userAgent = webView.settings.userAgentString
    ImageCache.language = interceptor.language
    ImageCache.userAgent = interceptor.userAgent
  }

  fun detectImage() {
    val webView = mainWebView ?: return
    if (touchX <= 0 && touchY <= 0) return

    val javascriptCommand = String.format(Locale.US, imageDetectorJs, touchX, touchY)
    webView.evaluateJavascript(javascriptCommand) { result ->
      val jsonObj = parseJsResult(result)
      val jsUrl = jsonObj?.optString("url")?.takeIf { it.isNotBlank() && it != "null" }

      if (jsUrl != null) {
        imageMenuUrl = jsUrl
        return@evaluateJavascript
      }

      val hitTestResult = webView.hitTestResult
      val hitUrl = if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE || hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
        hitTestResult.extra?.takeIf { it.isNotBlank() }
      } else {
        null
      }

      if (hitUrl != null) {
        imageMenuUrl = hitUrl
        return@evaluateJavascript
      }

      Logger.d(TAG) {
        val diagArray = jsonObj?.optJSONArray("diag")
        val topElementsStr = if (diagArray != null && diagArray.length() > 0) {
          (0 until diagArray.length()).mapNotNull { i ->
            val item = diagArray.optJSONObject(i) ?: return@mapNotNull null
            val tag = item.optString("tag", "unknown")
            val cls = item.optString("cls", "").let { if (it.isNotEmpty()) ".$it" else "" }
            val pe = item.optString("pe", "unknown")
            "$tag$cls(pe:$pe)"
          }.joinToString(", ")
        } else {
          "none"
        }

        val pageUrl = webView.url ?: "unknown"
        "No image detected on $pageUrl at ($touchX,$touchY) | Top elements: [$topElementsStr]".take(500)
      }
    }
  }

  private fun parseJsResult(result: String?): JSONObject? {
    val raw = result?.takeUnless { it.isBlank() || it == "null" } ?: return null
    val unescaped = if (raw.startsWith("\"") && raw.endsWith("\"")) {
      runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
    } else {
      raw
    }
    return runCatching { JSONObject(unescaped) }.getOrNull()
  }

  private fun setupBackHandler() {
    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          when {
            popupWebView != null -> dismissPopup()
            mainWebView?.canGoBack() == true -> mainWebView?.goBack()
            else -> confirmExit()
          }
        }
      },
    )
  }

  private fun confirmExit() {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastBackPressedTime < 2000L) {
      finish()
    } else {
      lastBackPressedTime = currentTime
      Toast.makeText(this, "Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
    }
  }

  fun launchFileChooser(params: WebChromeClient.FileChooserParams) {
    try {
      val fileIntent = params.createIntent().apply { addCategory(Intent.CATEGORY_OPENABLE) }
      if (fileIntent.resolveActivity(packageManager) != null) {
        filePickerLauncher.launch(fileIntent)
      } else {
        Toast.makeText(this, "Aplikasi manajer file tidak ditemukan di perangkat ini.", Toast.LENGTH_SHORT).show()
        clearUpload()
      }
    } catch (e: Exception) {
      Toast.makeText(this, "Gagal membuka jendela pemilihan file.", Toast.LENGTH_SHORT).show()
      clearUpload()
    }
  }

  fun clearUpload() {
    uploadCallback?.onReceiveValue(null)
    uploadCallback = null
  }

  fun openPopup(url: String) {
    val newWebView = createWebView(trackPageState = false)
    showPopup(newWebView)

    // Opening an image URL directly shows it at full pixel size (zoomed in), because the
    // WebView has wide-viewport off. Wrap it in a page that fits the image to the screen.
    val html = """
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <style>
        html, body { margin: 0; height: 100%; background: #121212; }
        body { display: flex; align-items: center; justify-content: center; }
        img { max-width: 100%; max-height: 100%; object-fit: contain; }
      </style>
      <img src="${TextUtils.htmlEncode(url)}">
    """.trimIndent()
    newWebView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
  }

  private fun createWebView(trackPageState: Boolean): WebView = WebView(this).apply {
    layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
    configureWebView(this)
    webViewClient = AppWebViewClient(this@MainActivity, trackPageState)
    webChromeClient = AppChromeClient(this@MainActivity, trackPageState)
  }

  private fun showPopup(webView: WebView) {
    dismissPopup()
    popupWebView = webView
  }

  fun dismissPopup() {
    val webView = popupWebView ?: return
    popupWebView = null
    webView.stopLoading()
    webView.onPause()
    webView.loadUrl("about:blank")
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.destroy()
  }

  private fun showWelcomeOnce() {
    val sharedPrefs = getSharedPreferences("Shinigami", MODE_PRIVATE)
    if (!sharedPrefs.getBoolean(PREF_WELCOME_SHOWN, false)) {
      showWelcomeDialog = true
    }
  }

  private fun dismissWelcomeDialog() {
    showWelcomeDialog = false
    getSharedPreferences("Shinigami", MODE_PRIVATE)
      .edit()
      .putBoolean(PREF_WELCOME_SHOWN, true)
      .apply()
  }

  override fun onPause() {
    super.onPause()
    mainWebView?.onPause()
    popupWebView?.onPause()
  }

  override fun onResume() {
    super.onResume()
    mainWebView?.onResume()
    popupWebView?.onResume()
  }

  override fun onDestroy() {
    clearUpload()
    runCatching { activeDialog?.result?.cancel() }
    activeDialog = null

    mainWebView?.let { webView ->
      webView.stopLoading()
      webView.loadUrl("about:blank")
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
    mainWebView = null

    dismissPopup()

    super.onDestroy()
  }

  class AppWebViewClient(
    activity: MainActivity,
    private val trackPageState: Boolean = true,
  ) : WebViewClient() {
    private val activityRef = WeakReference(activity)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
      val activity = activityRef.get() ?: return null
      return activity.interceptor.intercept(request) ?: ImageCache.intercept(request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      if (!request.isForMainFrame) return false

      val url = request.url
      val scheme = url.scheme.orEmpty()
      if (scheme.equals("about", ignoreCase = true) || scheme.equals("data", ignoreCase = true)) return false
      if (isInternalNavigation(url)) return false

      val activity = activityRef.get() ?: return false
      return try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, url))
        true
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Cannot launch external application for URL: $url" }
        false
      }
    }

    private fun isInternalNavigation(uri: Uri): Boolean {
      val scheme = uri.scheme ?: return false
      if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false

      val host = uri.host ?: return false
      return isComicHost(host) || host.equals("accounts.google.com", ignoreCase = true)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      super.onPageStarted(view, url, favicon)
      if (trackPageState) activityRef.get()?.viewModel?.onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String) {
      if (trackPageState) activityRef.get()?.viewModel?.onPageFinished()
      ErudaConsole.inject(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
      if (!request.isForMainFrame) return
      Logger.w(TAG) { "Main frame failed to load: ${error.description}" }
      if (trackPageState) activityRef.get()?.viewModel?.onPageLoadFailed()
    }

    override fun onReceivedHttpError(
      view: WebView,
      request: WebResourceRequest,
      errorResponse: WebResourceResponse,
    ) {
      if (!request.isForMainFrame) return
      Logger.w(TAG) { "Main frame HTTP ${errorResponse.statusCode} for ${request.url}" }
      if (trackPageState) activityRef.get()?.viewModel?.onPageLoadFailed()
    }
  }

  class AppChromeClient(
    activity: MainActivity,
    private val trackPageState: Boolean = true,
  ) : WebChromeClient() {
    private val activityRef = WeakReference(activity)

    override fun onProgressChanged(view: WebView, newProgress: Int) {
      if (trackPageState) activityRef.get()?.viewModel?.onProgress(newProgress)
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
      val activity = activityRef.get()
      if (result == null || activity == null) return false
      activity.activeDialog = DialogType.Alert(
        message = message ?: "",
        result = result,
      )
      return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
      val activity = activityRef.get()
      if (result == null || activity == null) return false
      activity.activeDialog = DialogType.Confirm(
        message = message ?: "",
        result = result,
      )
      return true
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
      val activity = activityRef.get()
      if (result == null || activity == null) return false
      activity.activeDialog = DialogType.Prompt(
        message = message ?: "",
        defaultValue = defaultValue ?: "",
        result = result,
      )
      return true
    }

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
      val activity = activityRef.get() ?: return false

      activity.uploadCallback?.onReceiveValue(null)
      activity.uploadCallback = filePathCallback
      activity.launchFileChooser(fileChooserParams)
      return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
      val activity = activityRef.get() ?: return false
      val newWebView = activity.createWebView(trackPageState = false)

      if (resultMsg == null) {
        newWebView.destroy()
        return false
      }

      val transport = resultMsg.obj as? WebView.WebViewTransport
      if (transport == null) {
        newWebView.destroy()
        return false
      }

      return try {
        activity.showPopup(newWebView)
        transport.webView = newWebView
        resultMsg.sendToTarget()
        true
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Cannot create popup window" }
        activity.dismissPopup()
        false
      }
    }
  }

  companion object {
    private const val TAG = "MainActivity"
    private const val PREF_WELCOME_SHOWN = "welcome_dialog_displayed"
  }
}
