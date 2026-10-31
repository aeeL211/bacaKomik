package aeeL.shngmWeb

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val ContextMenuAccent = Color.White
private const val PREVIEW_MAX_PX = 1280

private val DOWNLOAD_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

private sealed interface PreviewState {
  data object Loading : PreviewState

  data class Success(val image: ImageBitmap) : PreviewState

  data object Error : PreviewState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuBottomSheet(
  url: String,
  onDismissRequest: () -> Unit,
  onOpenInPopup: (String) -> Unit,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  var preview by remember(url) { mutableStateOf<PreviewState>(PreviewState.Loading) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(url) {
    preview = PreviewState.Loading
    val bitmap = withContext(Dispatchers.IO) { loadPreviewBitmap(url) }
    preview = bitmap?.let { PreviewState.Success(it.asImageBitmap()) } ?: PreviewState.Error
  }

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    containerColor = SurfaceDark,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .width(36.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(ContextMenuAccent),
      )
    },
  ) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = bottomInset + 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerDark),
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .heightIn(max = 220.dp)
          .clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onOpenInPopup(url)
            onDismissRequest()
          },
      ) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          when (val state = preview) {
            PreviewState.Loading -> {
              val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
              val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                  animation = tween(800, easing = LinearEasing),
                  repeatMode = RepeatMode.Reverse,
                ),
                label = "shimmerAlpha",
              )
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(SurfaceContainerDark.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(32.dp),
                  color = ContextMenuAccent,
                  strokeWidth = 3.dp,
                )
              }
            }
            is PreviewState.Success -> {
              Image(
                bitmap = state.image,
                contentDescription = "Pratinjau gambar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
              )
            }
            PreviewState.Error -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
              ) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_photo_m3),
                  contentDescription = null,
                  tint = PlaceholderTint,
                  modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Gagal memuat pratinjau gambar",
                  color = Hint,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceContainerDark)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(
          text = url,
          color = OnSurfaceVariant,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ActionTile(
          iconRes = R.drawable.ic_download_m3,
          label = "Unduh",
          modifier = Modifier.weight(1f),
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            safeAction(context) { downloadImage(context, url) }
            onDismissRequest()
          },
        )

        ActionTile(
          iconRes = R.drawable.ic_share_m3,
          label = "Bagikan",
          modifier = Modifier.weight(1f),
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            safeAction(context) {
              val shareIntent = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, url)
                },
                "Bagikan tautan",
              )
              context.startActivity(shareIntent)
            }
            onDismissRequest()
          },
        )

        ActionTile(
          iconRes = R.drawable.ic_copy_m3,
          label = "Salin tautan",
          modifier = Modifier.weight(1f),
          onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            safeAction(context) {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
              Toast.makeText(context, "Tautan disalin", Toast.LENGTH_SHORT).show()
            }
            onDismissRequest()
          },
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      ActionRow(
        iconRes = R.drawable.ic_photo_m3,
        title = "Buka di popup",
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onOpenInPopup(url)
          onDismissRequest()
        },
      )

      ActionRow(
        iconRes = R.drawable.ic_open_m3,
        title = "Buka di browser",
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          safeAction(context) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          }
          onDismissRequest()
        },
      )
    }
  }
}

@Composable
private fun ActionTile(
  iconRes: Int,
  label: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Box(
    modifier = modifier
      .heightIn(min = 68.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceContainerDark)
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp, horizontal = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = ContextMenuAccent,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        color = OnSurface,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ActionRow(
  iconRes: Int,
  title: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp, horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(id = iconRes),
      contentDescription = null,
      tint = ContextMenuAccent,
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = title,
      color = OnSurface,
      fontSize = 14.sp,
    )
  }
}

private fun downloadImage(context: Context, url: String) {
  val extension = url.substringBefore('?')
    .substringAfterLast('.', "jpg")
    .lowercase(Locale.ROOT)
    .takeIf { it in DOWNLOAD_EXTENSIONS } ?: "jpg"

  val request = DownloadManager.Request(Uri.parse(url))
    .setTitle("Mengunduh Gambar")
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

  val fileName = "Shinigami/IMG_${System.currentTimeMillis()}.$extension"
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName)
  } else {
    // Public Pictures needs WRITE_EXTERNAL_STORAGE on API 24-28; app storage needs no permission.
    request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PICTURES, fileName)
  }

  val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  downloadManager.enqueue(request)
  Toast.makeText(context, "Proses unduh dimulai...", Toast.LENGTH_SHORT).show()
}

private fun loadPreviewBitmap(url: String): Bitmap? {
  val bytes = when {
    url.startsWith("data:image/", ignoreCase = true) -> decodeDataUrl(url)
    else -> ImageCache.openBytes(url)
  } ?: return null

  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

  val options = BitmapFactory.Options().apply {
    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, PREVIEW_MAX_PX)
  }
  return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun decodeDataUrl(url: String): ByteArray? {
  val comma = url.indexOf(',')
  if (comma < 0) return null

  val header = url.substring(0, comma)
  if (!header.contains(";base64", ignoreCase = true)) return null

  return runCatching { Base64.decode(url.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
  var sampleSize = 1
  while (maxOf(width, height) / sampleSize > maxDimension) {
    sampleSize *= 2
  }
  return sampleSize
}

private fun safeAction(context: Context, action: () -> Unit) {
  try {
    action()
  } catch (e: Exception) {
    Logger.e("ContextMenuSheet", e) { "Action execution failed" }
    Toast.makeText(context, "Gagal memproses aksi tersebut", Toast.LENGTH_SHORT).show()
  }
}
