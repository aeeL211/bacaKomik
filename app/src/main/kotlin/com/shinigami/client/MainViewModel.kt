package com.shinigami.client

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

  private var isOnline: Boolean = false
  private var isPageLoaded: Boolean = false
  private var hangJob: Job? = null
  private var dismissJob: Job? = null
  private var refreshJob: Job? = null
  private var refreshStartTime: Long = 0L

  init {
    observeNetwork()
  }

  private fun observeNetwork() {
    viewModelScope.launch {
      networkMonitor.networkStatus.collect { isConnected ->
        val wasConnected = isOnline
        isOnline = isConnected
        if (isConnected) {
          val currentUrl = _uiState.value.url
          if (currentUrl == null) {
            val remoteUrl = configRepository.fetchUrl()
            _uiState.update { currentState ->
              currentState.copy(url = remoteUrl)
            }
          } else if (!wasConnected && _uiState.value.isSplashVisible) {
            _uiState.update { currentState ->
              currentState.copy(shouldReload = true)
            }
          }

          if (_uiState.value.isSplashVisible) {
            if (isPageLoaded) {
              startDismissTimer()
            } else {
              startHangTimer()
            }
          }
        } else {
          cancelAllTimeouts()
        }
      }
    }
  }

  fun onReloadHandled() {
    _uiState.update { currentState ->
      currentState.copy(shouldReload = false)
    }
  }

  private fun cancelAllTimeouts() {
    hangJob?.cancel()
    hangJob = null
    dismissJob?.cancel()
    dismissJob = null
    refreshJob?.cancel()
    refreshJob = null
  }

  private fun startHangTimer() {
    if (!isOnline) return
    hangJob?.cancel()
    hangJob = viewModelScope.launch {
      delay(20000L)
      if (isOnline && _uiState.value.isSplashVisible) {
        dismissSplash()
      }
    }
  }

  private fun startDismissTimer() {
    if (!isOnline) return
    dismissJob?.cancel()
    dismissJob = viewModelScope.launch {
      delay(3000L)
      if (isOnline && _uiState.value.isSplashVisible) {
        dismissSplash()
      }
    }
  }

  fun onProgress(progress: Int) {
    _uiState.update { currentState ->
      currentState.copy(loadingProgress = progress)
    }
    if (progress == 100) {
      onPageFinished()
    }
  }

  fun onPageStarted() {
    isPageLoaded = false
  }

  fun refresh(onReload: () -> Unit) {
    refreshJob?.cancel()
    refreshStartTime = System.currentTimeMillis()
    _uiState.update { currentState ->
      currentState.copy(isRefreshing = true)
    }

    refreshJob = viewModelScope.launch {
      delay(10000L)
      if (_uiState.value.isRefreshing) {
        _uiState.update { currentState ->
          currentState.copy(isRefreshing = false)
        }
      }
    }

    onReload()
  }

  fun onPageFinished() {
    isPageLoaded = true
    hangJob?.cancel()
    hangJob = null

    val elapsedTime = System.currentTimeMillis() - refreshStartTime
    val minDuration = 600L
    val remainingDelay = if (elapsedTime < minDuration && _uiState.value.isRefreshing) {
      minDuration - elapsedTime
    } else {
      0L
    }

    if (_uiState.value.isSplashVisible && isOnline) {
      startDismissTimer()
    }

    if (remainingDelay > 0) {
      viewModelScope.launch {
        delay(remainingDelay)
        refreshJob?.cancel()
        refreshJob = null
        _uiState.update { currentState ->
          currentState.copy(isRefreshing = false)
        }
      }
    } else {
      refreshJob?.cancel()
      refreshJob = null
      _uiState.update { currentState ->
        currentState.copy(isRefreshing = false)
      }
    }
  }

  private fun dismissSplash() {
    cancelAllTimeouts()
    _uiState.update { currentState ->
      currentState.copy(
        isRefreshing = false,
        isSplashVisible = false,
      )
    }
  }
}
