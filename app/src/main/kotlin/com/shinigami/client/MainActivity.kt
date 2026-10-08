package com.shinigami.client

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {

  val viewModel: MainViewModel by viewModels()
  val extension by lazy { Extension(cacheDir) }

  val imageDetectorJs: String by lazy {
    assets.open("js/image_detector.js").bufferedReader().use { it.readText() }
  }

  private var mainWebView: WebView? = null
  var popupWebView by mutableStateOf<WebView?>(null)

  var imageMenuUrl by mutableStateOf<String?>(null)
  var activeDialog by mutableStateOf<DialogType?>(null)

  private var uploadCallback: ValueCallback<Array<Uri>>? = null
  private var pendingChooser: WebChromeClient.FileChooserParams? = null

  private var lastBackPressedTime = 0L
  var touchX = 0
  var touchY = 0

  private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val callback = uploadCallback ?: return@registerForActivityResult
    uploadCallback = null
    pendingChooser = null

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

  private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
    val allGranted = permissions.all { it.value }

    if (!allGranted) {
      Toast.makeText(this, "Izin akses media diperlukan untuk mengunggah file.", Toast.LENGTH_SHORT).show()
      uploadCallback?.onReceiveValue(null)
      uploadCallback = null
      pendingChooser = null
    } else {
      pendingChooser?.let { params ->
        launchFileChooser(params)
      } ?: run {
        Toast.makeText(this, "Izin berhasil diberikan, silakan ulangi tindakan Anda.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    setupWindow()
    super.onCreate(savedInstanceState)

    setupBackHandler()

    setContent {
      AppTheme {
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(uiState.isSplashVisible) {
          val window = this@MainActivity.window
          val insetsController = WindowCompat.getInsetsController(window, window.decorView)

          insetsController.isAppearanceLightStatusBars = false
          insetsController.isAppearanceLightNavigationBars = false

          if (uiState.isSplashVisible) {
            window.statusBarColor = android.graphics.Color.parseColor("#18181B")
            window.navigationBarColor = android.graphics.Color.parseColor("#09090B")
          } else {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
          }
        }

        MainScreen(
          viewModel = viewModel,
          activity = this,
          extension = extension,
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
        )
      }
    }

    showWelcomeOnce()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    mainWebView?.saveState(outState)
  }

  private fun setupWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
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
  }

  fun detectImage() {
    val webView = mainWebView ?: return
    if (touchX <= 0 && touchY <= 0) return

    val javascriptCommand = String.format(imageDetectorJs, touchX, touchY)
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
    if (result.isNullOrBlank() || result == "null") return null
    var unescaped = result
    if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
      try {
        unescaped = org.json.JSONTokener(unescaped).nextValue() as? String ?: unescaped
      } catch (_: Exception) {}
    }
    return try {
      JSONObject(unescaped)
    } catch (_: Exception) {
      null
    }
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

  fun hasStoragePermission(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
      ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    else -> true
  }

  fun requestStoragePermission() {
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    permissionLauncher.launch(requiredPermissions)
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
    pendingChooser = null
  }

  fun openPopup(url: String) {
    val newWebView = NestedScrollWebView(this).apply {
      configureWebView(this)
      webViewClient = AppWebViewClient(this@MainActivity)
      webChromeClient = AppChromeClient(this@MainActivity)
    }

    popupWebView = newWebView
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

  fun dismissPopup() {
    popupWebView?.let { webView ->
      webView.stopLoading()
      webView.onPause()
      webView.loadUrl("about:blank")
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
      popupWebView = null
    }
  }

  private fun showWelcomeOnce() {
    val sharedPrefs = getSharedPreferences("Shinigami", MODE_PRIVATE)
    if (!sharedPrefs.getBoolean(PREF_WELCOME_SHOWN, false)) {
      Toast.makeText(this, "Selamat Datang! Login dengan akun Google untuk membuka fitur premium secara gratis.", Toast.LENGTH_LONG).show()
      sharedPrefs.edit().putBoolean(PREF_WELCOME_SHOWN, true).apply()
    }
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

    mainWebView?.let { webView ->
      webView.stopLoading()
      webView.onPause()
      webView.pauseTimers()
      webView.clearHistory()
      webView.clearCache(false)
      webView.clearFormData()
      webView.loadUrl("about:blank")
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
    mainWebView = null

    dismissPopup()

    super.onDestroy()
  }

  class AppWebViewClient(activity: MainActivity) : WebViewClient() {
    private val activityRef = WeakReference(activity)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
      Blocker.intercept(request)?.let { return it }

      return activityRef.get()?.extension?.intercept(request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val urlString = request.url.toString()
      val activity = activityRef.get() ?: return false

      if (isInternalNavigation(urlString)) return false

      return try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlString)))
        true
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Cannot launch external application for URL: $urlString" }
        false
      }
    }

    private fun isInternalNavigation(url: String): Boolean = url.contains("accounts.google.com") || url.contains("shinigami") || url.contains("shngm")

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      super.onPageStarted(view, url, favicon)
      val activity = activityRef.get() ?: return
      activity.viewModel.onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String) {
      val activity = activityRef.get() ?: return
      activity.viewModel.onPageFinished()
      ErudaConsole.inject(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
      if (request.isForMainFrame) {
        Logger.w(TAG) { "Main frame failed to load: ${error.description}" }
        activityRef.get()?.viewModel?.onPageFinished()
      }
    }
  }

  class AppChromeClient(activity: MainActivity) : WebChromeClient() {
    private val activityRef = WeakReference(activity)

    override fun onProgressChanged(view: WebView, newProgress: Int) {
      activityRef.get()?.viewModel?.onProgress(newProgress)
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

      if (!activity.hasStoragePermission()) {
        activity.pendingChooser = fileChooserParams
        activity.requestStoragePermission()
        return true
      }

      activity.launchFileChooser(fileChooserParams)
      return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
      val activity = activityRef.get() ?: return false
      val newWebView = NestedScrollWebView(activity).apply {
        activity.configureWebView(this)
        webViewClient = AppWebViewClient(activity)
        webChromeClient = this@AppChromeClient
      }

      activity.popupWebView = newWebView

      val transport = resultMsg?.obj as? WebView.WebViewTransport
      transport?.webView = newWebView
      resultMsg?.sendToTarget()

      return true
    }
  }

  companion object {
    private const val TAG = "MainActivity"
    private const val PREF_WELCOME_SHOWN = "welcome_dialog_displayed"
  }
}
