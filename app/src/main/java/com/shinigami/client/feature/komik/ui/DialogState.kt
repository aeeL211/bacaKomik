package com.shinigami.client.feature.komik.ui

import androidx.compose.runtime.Immutable

@Immutable
sealed interface DialogState {
    @Immutable
    data class Info(
        val title: String,
        val message: String,
        val buttonText: String = "OK",
        val onDone: (() -> Unit)? = null,
    ) : DialogState

    @Immutable
    data class Confirm(
        val title: String,
        val message: String,
        val yesText: String = "OK",
        val noText: String = "Batal",
        val onYes: () -> Unit,
        val onNo: (() -> Unit)? = null,
    ) : DialogState

    @Immutable
    data class Prompt(
        val title: String,
        val message: String,
        val defaultInput: String = "",
        val onDone: (String) -> Unit,
        val onCancel: (() -> Unit)? = null,
    ) : DialogState
}
