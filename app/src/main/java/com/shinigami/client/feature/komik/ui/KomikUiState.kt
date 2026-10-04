package com.shinigami.client.feature.komik.ui

import androidx.compose.runtime.Immutable

@Immutable
data class KomikUiState(
    val url: String? = null,
    val isLoading: Boolean = true,
    val loadingProgress: Int = 0,
    val isSplashVisible: Boolean = true,
    val shouldReload: Boolean = false,
)
