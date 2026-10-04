package com.shinigami.client

import android.webkit.JsPromptResult
import android.webkit.JsResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = title,
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(text = buttonText, color = PrimaryAccent)
                        }
                    }
                }
            }
        }
    }
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
    Dialog(
        onDismissRequest = onNo,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = title,
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onNo) {
                            Text(text = noText, color = OnSurfaceVariant)
                        }
                        TextButton(onClick = onYes) {
                            Text(text = yesText, color = PrimaryAccent)
                        }
                    }
                }
            }
        }
    }
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

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceDark,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = title,
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text(text = "Batal", color = OnSurfaceVariant)
                        }
                        TextButton(onClick = { onDone(inputText) }) {
                            Text(text = "OK", color = PrimaryAccent)
                        }
                    }
                }
            }
        }
    }
}
