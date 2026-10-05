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
    val isLoading: Boolean = true,
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

    private var isConnectedToNetwork: Boolean = false
    private var isPageFinishedLoading: Boolean = false
    private var hangTimeoutJob: Job? = null
    private var delayDismissJob: Job? = null
    private var refreshTimeoutJob: Job? = null
    private var refreshStartTime: Long = 0L

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            networkMonitor.networkStatus.collect { isConnected ->
                val wasConnected = isConnectedToNetwork
                isConnectedToNetwork = isConnected
                if (isConnected) {
                    val currentUrl = _uiState.value.url
                    if (currentUrl == null) {
                        val remoteUrl = configRepository.getUrl()
                        _uiState.update { currentState ->
                            currentState.copy(url = remoteUrl)
                        }
                    } else if (!wasConnected && _uiState.value.isSplashVisible) {
                        _uiState.update { currentState ->
                            currentState.copy(shouldReload = true)
                        }
                    }

                    if (_uiState.value.isSplashVisible) {
                        if (isPageFinishedLoading) {
                            startDelayDismissTimer()
                        } else {
                            startHangTimeoutTimer()
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
        hangTimeoutJob?.cancel()
        hangTimeoutJob = null
        delayDismissJob?.cancel()
        delayDismissJob = null
        refreshTimeoutJob?.cancel()
        refreshTimeoutJob = null
    }

    private fun startHangTimeoutTimer() {
        if (!isConnectedToNetwork) return
        hangTimeoutJob?.cancel()
        hangTimeoutJob = viewModelScope.launch {
            delay(20000L)
            if (isConnectedToNetwork && _uiState.value.isSplashVisible) {
                dismissSplashInternal()
            }
        }
    }

    private fun startDelayDismissTimer() {
        if (!isConnectedToNetwork) return
        delayDismissJob?.cancel()
        delayDismissJob = viewModelScope.launch {
            delay(3000L)
            if (isConnectedToNetwork && _uiState.value.isSplashVisible) {
                dismissSplashInternal()
            }
        }
    }

    fun updateLoadingProgress(progress: Int) {
        _uiState.update { currentState ->
            currentState.copy(loadingProgress = progress)
        }
        if (progress == 100) {
            onPageFinished()
        }
    }

    fun onPageStarted() {
        isPageFinishedLoading = false
        _uiState.update { currentState ->
            currentState.copy(isLoading = true)
        }
    }

    fun triggerManualRefresh(onReload: () -> Unit) {
        refreshTimeoutJob?.cancel()
        refreshStartTime = System.currentTimeMillis()
        _uiState.update { currentState ->
            currentState.copy(isRefreshing = true, isLoading = true)
        }

        refreshTimeoutJob = viewModelScope.launch {
            delay(10000L)
            if (_uiState.value.isRefreshing) {
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, isRefreshing = false)
                }
            }
        }

        onReload()
    }

    fun onPageFinished() {
        isPageFinishedLoading = true
        hangTimeoutJob?.cancel()
        hangTimeoutJob = null

        val elapsedTime = System.currentTimeMillis() - refreshStartTime
        val minDuration = 600L
        val remainingDelay = if (elapsedTime < minDuration && _uiState.value.isRefreshing) {
            minDuration - elapsedTime
        } else {
            0L
        }

        if (_uiState.value.isSplashVisible && isConnectedToNetwork) {
            startDelayDismissTimer()
        }

        if (remainingDelay > 0) {
            viewModelScope.launch {
                delay(remainingDelay)
                refreshTimeoutJob?.cancel()
                refreshTimeoutJob = null
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, isRefreshing = false)
                }
            }
        } else {
            refreshTimeoutJob?.cancel()
            refreshTimeoutJob = null
            _uiState.update { currentState ->
                currentState.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    private fun dismissSplashInternal() {
        cancelAllTimeouts()
        _uiState.update { currentState ->
            currentState.copy(
                isLoading = false,
                isRefreshing = false,
                isSplashVisible = false,
            )
        }
    }
}
