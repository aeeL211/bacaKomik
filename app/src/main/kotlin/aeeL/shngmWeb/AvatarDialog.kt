package aeeL.shngmWeb

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class AvatarChoiceState(
  val uri: Uri,
  val fileName: String,
  val mimeType: String,
  val isBusy: Boolean = false,
  val status: String? = null,
  val error: String? = null,
)

/** Asks whether the picked avatar should skip the website's crop-to-JPG step. */
@Composable
fun AvatarChoiceDialog(
  state: AvatarChoiceState,
  onUseOriginal: () -> Unit,
  onCropNormally: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = { if (!state.isBusy) onDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    HideStatusBarInDialog()
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
          modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        ) {
          Text(
            text = "Foto Profil",
            color = OnSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Gunakan gambar asli tanpa crop? GIF dan WebP akan tetap animasi. " +
              "Jika memilih crop, website akan mengubah gambar menjadi JPG.",
            color = OnSurfaceVariant,
            fontSize = 14.sp,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Gambar asli diunggah ke catbox.moe (host publik).",
            color = Hint,
            fontSize = 12.sp,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "${state.fileName} (${state.mimeType})",
            color = Hint,
            fontSize = 12.sp,
          )

          if (state.isBusy || state.status != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (state.isBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = PrimaryAccent,
                )
                Spacer(modifier = Modifier.width(12.dp))
              }
              Text(
                text = state.status ?: "Memproses...",
                color = OnSurfaceVariant,
                fontSize = 14.sp,
              )
            }
          }

          if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = state.error,
              color = MaterialTheme.colorScheme.error,
              fontSize = 14.sp,
            )
          }

          Spacer(modifier = Modifier.height(24.dp))
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onUseOriginal,
              enabled = !state.isBusy,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Pakai asli (tanpa crop)")
            }
            OutlinedButton(
              onClick = onCropNormally,
              enabled = !state.isBusy,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Crop & JPG seperti biasa")
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onDismiss, enabled = !state.isBusy) {
              Text("Batal", color = OnSurfaceVariant)
            }
          }
        }
      }
    }
  }
}
