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
val PlaceholderTint = Color(0xFF605D62)

sealed interface DialogType {
  val message: String
  val result: JsResult

  data class Alert(override val message: String, override val result: JsResult) : DialogType

  data class Confirm(override val message: String, override val result: JsResult) : DialogType

  data class Prompt(
    override val message: String,
    val defaultValue: String,
    override val result: JsPromptResult,
  ) : DialogType
}

/** Themed replacement for the WebView's JS alert / confirm / prompt dialogs. */
@Composable
fun AppDialog(
  dialogType: DialogType,
  onDismiss: () -> Unit,
) {
  val prompt = dialogType as? DialogType.Prompt
  var input by remember(dialogType) { mutableStateOf(prompt?.defaultValue.orEmpty()) }
  val focusRequester = remember { FocusRequester() }

  if (prompt != null) {
    LaunchedEffect(dialogType) { focusRequester.requestFocus() }
  }

  val title = when (dialogType) {
    is DialogType.Alert -> "Informasi"
    is DialogType.Confirm -> "Konfirmasi"
    is DialogType.Prompt -> "Input"
  }
  val onConfirm = {
    if (prompt != null) prompt.result.confirm(input) else dialogType.result.confirm()
    onDismiss()
  }
  val onCancel = {
    dialogType.result.cancel()
    onDismiss()
  }

  Dialog(
    // Dismissing an alert counts as pressing OK; confirm and prompt count it as cancel.
    onDismissRequest = if (dialogType is DialogType.Alert) onConfirm else onCancel,
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
          // Alert and confirm always show the message row; prompt hides an empty one.
          if (dialogType.message.isNotEmpty() || prompt == null) {
            Text(
              text = dialogType.message,
              color = OnSurfaceVariant,
              fontSize = 14.sp,
            )
            if (prompt != null) Spacer(modifier = Modifier.height(12.dp))
          }
          if (prompt != null) {
            OutlinedTextField(
              value = input,
              onValueChange = { input = it },
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
          Spacer(modifier = Modifier.height(24.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            if (dialogType !is DialogType.Alert) {
              TextButton(onClick = onCancel) {
                Text(text = "Batal", color = OnSurfaceVariant)
              }
            }
            TextButton(onClick = onConfirm) {
              Text(text = "OK", color = PrimaryAccent)
            }
          }
        }
      }
    }
  }
}
