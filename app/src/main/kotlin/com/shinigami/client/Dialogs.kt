package com.shinigami.client

import android.webkit.JsPromptResult
import android.webkit.JsResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

val SurfaceDark = Color(0xFF121212)
val SurfaceContainerDark = Color(0xFF1E1E1E)
val OnSurface = Color(0xFFE6E1E5)
val OnSurfaceVariant = Color(0xFFCAC4D0)
val PrimaryAccent = Color(0xFFD0BCFF)
val Hint = Color(0xFF938F96)
val ImgPlaceholderTint = Color(0xFF605D62)

sealed interface DialogType {
    data class Alert(val message: String, val result: JsResult) : DialogType
    data class Confirm(val message: String, val result: JsResult) : DialogType
    data class Prompt(val message: String, val defaultValue: String, val result: JsPromptResult) : DialogType
}

@Composable
fun AppDialog(
    dialogType: DialogType,
    onDismiss: () -> Unit,
) {
    when (dialogType) {
        is DialogType.Alert -> {
            InfoDialog(
                title = "Informasi",
                message = dialogType.message,
                onDismiss = {
                    dialogType.result.confirm()
                    onDismiss()
                },
            )
        }
        is DialogType.Confirm -> {
            ConfirmDialog(
                title = "Konfirmasi",
                message = dialogType.message,
                onYes = {
                    dialogType.result.confirm()
                    onDismiss()
                },
                onNo = {
                    dialogType.result.cancel()
                    onDismiss()
                },
            )
        }
        is DialogType.Prompt -> {
            PromptDialog(
                title = "Input",
                message = dialogType.message,
                defaultInput = dialogType.defaultValue,
                onDone = { text ->
                    dialogType.result.confirm(text)
                    onDismiss()
                },
                onCancel = {
                    dialogType.result.cancel()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun InfoDialog(
    title: String,
    message: String,
    buttonText: String = "OK",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                color = OnSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = buttonText, color = PrimaryAccent)
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    yesText: String = "OK",
    noText: String = "Batal",
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNo,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = message,
                color = OnSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onYes) {
                Text(text = yesText, color = PrimaryAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onNo) {
                Text(text = noText, color = OnSurfaceVariant)
            }
        },
    )
}

@Composable
fun PromptDialog(
    title: String,
    message: String,
    defaultInput: String = "",
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var inputText by remember { mutableStateOf(defaultInput) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = OnSurfaceVariant,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                    ),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(inputText) }) {
                Text(text = "OK", color = PrimaryAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Batal", color = OnSurfaceVariant)
            }
        },
    )
}
