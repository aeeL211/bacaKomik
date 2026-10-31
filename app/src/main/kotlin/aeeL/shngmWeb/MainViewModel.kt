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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class MainState(
  val url: String? = null,
  val isRefreshing: Boolean = false,
  val loadingProgress: Int = 0,
  val isSplashVisible: Boolean = true,
  val shouldReload: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val networkMonitor = NetworkMonitor(application)
  private val configRepository = ConfigRepository(
    application.getSharedPreferences("Shinigami", Context.MODE_PRIVATE),
  )

  private val _uiState = MutableStateFlow(MainState())
  val uiState: StateFlow<MainState> = _uiState.asStateFlow()

  val defaultHeaders: Map<String, String> = mapOf("Accept-Language" to Locale.getDefault().language)

  private var isOnline = networkMonitor.isConnected()
  private var isPageLoaded = false
  private var splashJob: Job? = null
  private var refreshTimeoutJob: Job? = null
  private var configJob: Job? = null

  init {
    ImageCache.isOnline = isOnline
    loadConfig()
    observeNetwork()
    startSplashTimer(SPLASH_HANG_MS)
  }

  private fun loadConfig() {
    if (configJob?.isActive == true) return
    configJob = viewModelScope.launch {
      val url = configRepository.fetchUrl()
      _uiState.update { it.copy(url = url) }
    }
  }

  private fun observeNetwork() {
    viewModelScope.launch {
      networkMonitor.networkStatus.collect { isConnected ->
        val wasOnline = isOnline
        isOnline = isConnected
        ImageCache.isOnline = isConnected

        if (isConnected) {
          if (!wasOnline) {
            loadConfig()
            if (_uiState.value.isSplashVisible && !isPageLoaded && _uiState.value.url != null) {
              _uiState.update { it.copy(shouldReload = true) }
            }
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
  }

  fun onPageFinished() {
    isPageLoaded = true
    refreshTimeoutJob?.cancel()
    refreshTimeoutJob = null
    _uiState.update { it.copy(isRefreshing = false) }

    if (_uiState.value.isSplashVisible) {
      startSplashTimer(SPLASH_DISMISS_MS)
    }
  }

  /**
   * Re-fetches the remote config before reloading, so an edited url.txt takes effect on the
   * next pull instead of waiting for the next app launch.
   */
  fun refresh(onReady: (String) -> Unit) {
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
      _uiState.update { it.copy(url = url) }
      try {
        onReady(url)
      } catch (e: Exception) {
        Logger.e(TAG, e) { "Refresh callback failed" }
      }
    }
  }

  fun onReloadHandled() {
    _uiState.update { it.copy(shouldReload = false) }
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
