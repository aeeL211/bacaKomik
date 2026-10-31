package aeeL.shngmWeb

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class MainState(
  val url: String? = null,
  val isRefreshing: Boolean = false,
  val loadingProgress: Int = 0,
  val isSplashVisible: Boolean = true,
  val pageCommand: PageCommand? = null,
)

/** A one-shot instruction for the WebView host, acknowledged with [MainViewModel.onPageCommandHandled]. */
data class PageCommand(
  val id: Long,
  val url: String,
  val reloadIfSameOrigin: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val networkMonitor = NetworkMonitor(application)
  private val configRepository = ConfigRepository(
    application.getSharedPreferences("Shinigami", Context.MODE_PRIVATE),
  )

  private val _uiState = MutableStateFlow(MainState())
  val uiState: StateFlow<MainState> = _uiState.asStateFlow()

  val defaultHeaders: Map<String, String> = mapOf(
    "Accept-Language" to Locale.getDefault().toLanguageTag(),
  )

  private var isOnline = networkMonitor.isConnected()
  private var isPageLoaded = false
  private var splashJob: Job? = null
  private var refreshTimeoutJob: Job? = null
  private var configJob: Job? = null
  private var nextCommandId = 0L

  init {
    ImageCache.isOnline = isOnline
    loadConfig(emitCommand = true, reloadIfSameOrigin = false)
    observeNetwork()
    startSplashTimer(SPLASH_HANG_MS)
  }

  private fun loadConfig(emitCommand: Boolean, reloadIfSameOrigin: Boolean) {
    if (configJob?.isActive == true) return

    configJob = viewModelScope.launch {
      val url = configRepository.fetchUrl()
      val command = if (emitCommand) {
        PageCommand(nextCommandId++, url, reloadIfSameOrigin)
      } else {
        null
      }

      _uiState.update { state ->
        state.copy(
          url = url,
          pageCommand = command ?: state.pageCommand,
        )
      }
    }
  }

  private fun observeNetwork() {
    viewModelScope.launch {
      networkMonitor.networkStatus
        .catch { e -> Logger.w(TAG) { "Network monitor stopped: ${e.message}" } }
        .collect { isConnected ->
          val wasOnline = isOnline
          isOnline = isConnected
          ImageCache.isOnline = isConnected

          if (isConnected) {
            if (!wasOnline) {
              loadConfig(emitCommand = !isPageLoaded, reloadIfSameOrigin = true)
            }
          } else {
            _uiState.update { it.copy(isRefreshing = false) }
          }
        }
    }
  }

  fun onProgress(progress: Int) {
    val state = _uiState.value
    if (!state.isSplashVisible || state.loadingProgress == progress) return
    _uiState.update { it.copy(loadingProgress = progress) }
  }

  fun onPageStarted() {
    isPageLoaded = false
    if (_uiState.value.isSplashVisible) {
      _uiState.update { it.copy(loadingProgress = 0) }
    }
  }

  fun onPageFinished() {
    finishPageLoad(loaded = true)
  }

  fun onPageLoadFailed() {
    finishPageLoad(loaded = false)
  }

  private fun finishPageLoad(loaded: Boolean) {
    isPageLoaded = loaded
    refreshTimeoutJob?.cancel()
    refreshTimeoutJob = null
    _uiState.update { it.copy(isRefreshing = false) }

    if (_uiState.value.isSplashVisible) {
      startSplashTimer(SPLASH_DISMISS_MS)
    }
  }

  /**
   * Re-fetches the remote config, then asks the WebView host to reload or navigate.
   * The host acknowledges the command, so no callback is held across the ViewModel boundary.
   */
  fun refresh() {
    if (_uiState.value.isRefreshing) return

    _uiState.update { it.copy(isRefreshing = true) }
    refreshTimeoutJob?.cancel()
    refreshTimeoutJob = viewModelScope.launch {
      delay(REFRESH_TIMEOUT_MS)
      if (_uiState.value.isRefreshing) {
        _uiState.update { it.copy(isRefreshing = false) }
      }
    }

    viewModelScope.launch {
      val url = configRepository.fetchUrl()
      _uiState.update {
        it.copy(
          url = url,
          pageCommand = PageCommand(nextCommandId++, url, reloadIfSameOrigin = true),
        )
      }
    }
  }

  fun onPageCommandHandled(id: Long) {
    _uiState.update { state ->
      if (state.pageCommand?.id == id) state.copy(pageCommand = null) else state
    }
  }

  private fun startSplashTimer(delayMs: Long) {
    splashJob?.cancel()
    splashJob = viewModelScope.launch {
      delay(delayMs)
      dismissSplash()
    }
  }

  private fun dismissSplash() {
    splashJob?.cancel()
    splashJob = null
    _uiState.update { it.copy(isSplashVisible = false, isRefreshing = false) }
  }

  companion object {
    private const val TAG = "MainViewModel"
    private const val SPLASH_HANG_MS = 20_000L
    private const val SPLASH_DISMISS_MS = 3_000L
    private const val REFRESH_TIMEOUT_MS = 15_000L
  }
}
