package aeeL.shngmWeb

import android.webkit.JsPromptResult
import android.webkit.JsResult
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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

/** Keeps the status bar hidden while a dialog window is visible. */
@Composable
internal fun HideStatusBarInDialog() {
  val view = LocalView.current
  DisposableEffect(view) {
    val window = (view.parent as? DialogWindowProvider)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    controller?.hide(WindowInsetsCompat.Type.statusBars())
    controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    onDispose { }
  }
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
    HideStatusBarInDialog()
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(AppScrim)
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        shape = RoundedCornerShape(28.dp),
        color = BaseCard,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, General700),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
        ) {
          Text(
            text = title,
            color = General100,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(16.dp))
          // Alert and confirm always show the message row; prompt hides an empty one.
          if (dialogType.message.isNotEmpty() || prompt == null) {
            Text(
              text = dialogType.message,
              color = General300,
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
                focusedBorderColor = Primary400,
                unfocusedBorderColor = General700,
                focusedTextColor = General100,
                unfocusedTextColor = General100,
                cursorColor = Primary300,
                focusedContainerColor = BaseDark,
                unfocusedContainerColor = BaseDark,
              ),
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            )
          }
          Spacer(modifier = Modifier.height(24.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            if (dialogType !is DialogType.Alert) {
              TextButton(onClick = onCancel) {
                Text(text = "Batal", color = General400)
              }
            }
            TextButton(onClick = onConfirm) {
              Text(text = "OK", color = Primary300)
            }
          }
        }
      }
    }
  }
}

/** One-time onboarding hint for the premium unlock flow. */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = BaseCard,
    titleContentColor = General100,
    textContentColor = General300,
    title = {
      Text(
        text = "Fitur Premium",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      HideStatusBarInDialog()
      Text(
        text = "Login dengan akun Google untuk membuka fitur premium secara gratis.",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )
    },
    confirmButton = {
      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        TextButton(onClick = onDismiss) {
          Text(text = "Mengerti", color = Primary300)
        }
      }
    },
  )
}
