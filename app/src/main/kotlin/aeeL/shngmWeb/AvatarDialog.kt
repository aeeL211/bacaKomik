package aeeL.shngmWeb

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val AVATAR_PREVIEW_MAX_PX = 720

data class AvatarChoiceState(
  val uri: Uri,
  val fileName: String,
  val mimeType: String,
  val fileSizeBytes: Long? = null,
  val isBusy: Boolean = false,
  val status: String? = null,
  val error: String? = null,
)

/**
 * Asks whether the picked avatar should skip the website's crop-to-JPG step.
 *
 * GIF and WebP are shown with their real animation when the platform supports it (Android 9+),
 * so the choice is made against the image that will actually be installed.
 */
@Composable
fun AvatarChoiceDialog(
  state: AvatarChoiceState,
  onUseOriginal: () -> Unit,
  onCropNormally: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val animated = supportsAnimation(state.mimeType)
  val previewAnimates = animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
  val format = formatLabel(state.mimeType, state.fileName)
  val fileSize = state.fileSizeBytes
    ?.takeIf { it > 0 }
    ?.let { Formatter.formatShortFileSize(context, it) }

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
        .background(AppScrim)
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier = Modifier
          .widthIn(max = 420.dp)
          .fillMaxWidth()
          .wrapContentHeight(),
        shape = RoundedCornerShape(28.dp),
        color = BaseCard,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, General700),
      ) {
        Column(
          modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Primary500.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_photo_m3),
                contentDescription = null,
                tint = Primary300,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (animated) "Avatar GIF/WebP" else "Foto Profil",
                color = General100,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = when {
                  animated && !previewAnimates -> "Pratinjau statis, file tetap bergerak"
                  animated -> "Animasi tidak diubah"
                  else -> "Pratinjau sebelum dipasang"
                },
                color = General400,
                fontSize = 12.sp,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FormatBadge(format)
          }

          Spacer(modifier = Modifier.height(16.dp))

          AvatarPreviewCard(
            uri = state.uri,
            isBusy = state.isBusy,
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = state.fileName,
              color = General300,
              fontSize = 12.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            if (fileSize != null) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = fileSize,
                color = General400,
                fontSize = 11.sp,
              )
            }
          }

          if (state.isBusy || state.status != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (state.isBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = Primary300,
                )
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text(
                text = state.status ?: "Memproses...",
                color = General300,
                fontSize = 12.sp,
              )
            }
          }

          if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val errorShape = RoundedCornerShape(12.dp)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(errorShape)
                .background(Danger500.copy(alpha = 0.12f))
                .border(1.dp, Danger500.copy(alpha = 0.4f), errorShape)
                .padding(12.dp),
            ) {
              Text(
                text = state.error,
                color = Danger500,
                fontSize = 12.sp,
                lineHeight = 17.sp,
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          AvatarOption(
            iconRes = R.drawable.ic_play_circle_m3,
            title = if (animated) "Pertahankan animasi" else "Pakai gambar asli",
            description = if (animated) {
              "File asli ke catbox.moe (host publik). GIF/WebP tetap bergerak."
            } else {
              "File asli ke catbox.moe (host publik) tanpa crop."
            },
            highlighted = animated,
            enabled = !state.isBusy,
            onClick = onUseOriginal,
          )

          Spacer(modifier = Modifier.height(8.dp))

          AvatarOption(
            iconRes = R.drawable.ic_crop_m3,
            title = "Crop & JPG",
            description = "Ikuti cropper website. Hasilnya JPG statis.",
            highlighted = false,
            enabled = !state.isBusy,
            onClick = onCropNormally,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              onClick = onDismiss,
              enabled = !state.isBusy,
            ) {
              Text(
                text = "Batal",
                color = if (state.isBusy) General500 else General400,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FormatBadge(label: String) {
  val shape = RoundedCornerShape(999.dp)
  Box(
    modifier = Modifier
      .clip(shape)
      .background(BaseDark)
      .border(1.dp, General700, shape)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  ) {
    Text(
      text = label,
      color = Primary200,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.8.sp,
    )
  }
}

@Composable
private fun AvatarPreviewCard(
  uri: Uri,
  isBusy: Boolean,
) {
  val shape = RoundedCornerShape(18.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(180.dp)
      .clip(shape)
      .background(BaseDark)
      .border(1.dp, General700, shape),
    contentAlignment = Alignment.Center,
  ) {
    AvatarPreview(
      uri = uri,
      modifier = Modifier.fillMaxSize(),
    )

    if (isBusy) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BaseDark.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(30.dp),
          strokeWidth = 2.5.dp,
          color = Primary300,
        )
      }
    }
  }
}

@Composable
private fun AvatarPreview(
  uri: Uri,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var drawable by remember(uri) { mutableStateOf<Drawable?>(null) }
  var isLoading by remember(uri) { mutableStateOf(true) }

  LaunchedEffect(uri) {
    isLoading = true
    drawable = withContext(Dispatchers.IO) { loadPreviewDrawable(context, uri) }
    isLoading = false
  }

  val current = drawable
  DisposableEffect(current) {
    onDispose { stopAnimatedDrawable(current) }
  }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    when {
      current != null -> {
        AndroidView(
          factory = { viewContext ->
            ImageView(viewContext).apply {
              scaleType = ImageView.ScaleType.FIT_CENTER
              contentDescription = "Pratinjau avatar"
            }
          },
          update = { imageView ->
            imageView.setImageDrawable(current)
            startAnimatedDrawable(current)
          },
          modifier = Modifier.fillMaxSize(),
        )
      }

      isLoading -> {
        CircularProgressIndicator(
          modifier = Modifier.size(28.dp),
          strokeWidth = 2.5.dp,
          color = Primary300,
        )
      }

      else -> {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            painter = painterResource(id = R.drawable.ic_photo_m3),
            contentDescription = null,
            tint = General400,
            modifier = Modifier.size(36.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Pratinjau tidak tersedia",
            color = General400,
            fontSize = 12.sp,
          )
        }
      }
    }
  }
}

@Composable
private fun AvatarOption(
  iconRes: Int,
  title: String,
  description: String,
  highlighted: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  val background = if (highlighted) Primary500.copy(alpha = 0.10f) else BaseDark
  val borderColor = if (highlighted) Primary400.copy(alpha = 0.55f) else General700
  val iconBackground = if (highlighted) Primary500.copy(alpha = 0.18f) else General800
  val iconTint = if (highlighted) Primary300 else General300

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(background)
      .border(1.dp, borderColor, shape)
      .alpha(if (enabled) 1f else 0.55f)
      .clickable(
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
      )
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(iconBackground),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title,
          color = General100,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (highlighted) {
          Spacer(modifier = Modifier.width(8.dp))
          RecommendedBadge()
        }
      }
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = description,
        color = General400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      )
    }
  }
}

@Composable
private fun RecommendedBadge() {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(999.dp))
      .background(Primary500.copy(alpha = 0.20f))
      .padding(horizontal = 7.dp, vertical = 2.dp),
  ) {
    Text(
      text = "Disarankan",
      color = Primary200,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
    )
  }
}

private fun supportsAnimation(mimeType: String): Boolean = when (mimeType.lowercase(Locale.ROOT)) {
  "image/gif", "image/webp" -> true
  else -> false
}

private fun formatLabel(mimeType: String, fileName: String): String = when (mimeType.lowercase(Locale.ROOT)) {
  "image/gif" -> "GIF"
  "image/webp" -> "WEBP"
  "image/png" -> "PNG"
  "image/jpeg" -> "JPG"
  "image/avif" -> "AVIF"
  "image/heic", "image/heif" -> "HEIC"
  "image/bmp" -> "BMP"
  else -> fileName.substringAfterLast('.', "").uppercase(Locale.ROOT).ifBlank { "GAMBAR" }
}

private fun loadPreviewDrawable(context: Context, uri: Uri): Drawable? {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    runCatching { decodeAnimatedDrawable(context, uri) }.getOrNull()?.let { return it }
  }
  return runCatching { decodeSampledBitmap(context, uri) }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimatedDrawable(context: Context, uri: Uri): Drawable {
  val source = ImageDecoder.createSource(context.contentResolver, uri)
  return ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
    val width = info.size.width
    val height = info.size.height
    val longestSide = maxOf(width, height)
    if (longestSide > AVATAR_PREVIEW_MAX_PX) {
      val scale = AVATAR_PREVIEW_MAX_PX.toFloat() / longestSide
      decoder.setTargetSize(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
      )
    }
  }
}

private fun decodeSampledBitmap(context: Context, uri: Uri): Drawable? {
  val resolver = context.contentResolver
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

  val options = BitmapFactory.Options().apply {
    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, AVATAR_PREVIEW_MAX_PX)
  }
  val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
  return BitmapDrawable(context.resources, bitmap)
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
  var sampleSize = 1
  while (maxOf(width, height) / sampleSize > maxDimension) {
    sampleSize *= 2
  }
  return sampleSize
}

private fun startAnimatedDrawable(drawable: Drawable?) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    (drawable as? AnimatedImageDrawable)?.start()
  }
}

private fun stopAnimatedDrawable(drawable: Drawable?) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    (drawable as? AnimatedImageDrawable)?.stop()
  }
}
