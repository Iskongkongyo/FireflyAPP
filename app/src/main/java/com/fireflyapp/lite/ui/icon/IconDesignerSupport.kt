package com.fireflyapp.lite.ui.icon

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fireflyapp.lite.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

@Composable
fun ColorPickerDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var draftValue by remember(value) { mutableStateOf(value) }
    val draftColor = parseOptionalColor(draftValue) ?: DEFAULT_ICON_BACKGROUND_COLOR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = draftColor
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = draftValue,
                            color = contentColorFor(draftColor),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                ColorPanel(
                    initialColor = draftColor,
                    onColorChange = { draftValue = formatRgbHex(it) }
                )
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = { draftValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.icon_designer_hex_value)) }
                )
                if (parseOptionalColor(draftValue) == null) {
                    Text(
                        text = stringResource(R.string.icon_designer_invalid_color),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draftValue.trim()) }, enabled = parseOptionalColor(draftValue) != null) {
                Text(stringResource(R.string.icon_designer_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.project_hub_cancel_action))
            }
        }
    )
}

@Composable
private fun ColorPanel(
    initialColor: Color,
    onColorChange: (Color) -> Unit
) {
    var hsvState by remember { mutableStateOf(colorToHsvState(initialColor)) }

    LaunchedEffect(initialColor) {
        hsvState = colorToHsvState(initialColor)
    }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        SaturationValuePanel(
            hsvState = hsvState,
            onHsvChange = {
                hsvState = it
                onColorChange(hsvStateToColor(it))
            }
        )
        HueStrip(
            hue = hsvState.hue,
            onHueChange = { hue ->
                val updated = hsvState.copy(hue = hue)
                hsvState = updated
                onColorChange(hsvStateToColor(updated))
            }
        )
    }
}

@Composable
private fun SaturationValuePanel(
    hsvState: HsvState,
    onHsvChange: (HsvState) -> Unit
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsvState.hue, 1f, 1f)))
    val markerStroke = if (hsvState.value > 0.55f) Color.Black.copy(alpha = 0.78f) else Color.White
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .pointerInput(hsvState.hue) {
                fun update(offset: Offset) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onHsvChange(
                        hsvState.copy(
                            saturation = (offset.x / width).coerceIn(0f, 1f),
                            value = (1f - (offset.y / height)).coerceIn(0f, 1f)
                        )
                    )
                }
                detectTapGestures(onTap = ::update)
            }
            .pointerInput(hsvState.hue) {
                fun update(offset: Offset) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onHsvChange(
                        hsvState.copy(
                            saturation = (offset.x / width).coerceIn(0f, 1f),
                            value = (1f - (offset.y / height)).coerceIn(0f, 1f)
                        )
                    )
                }
                detectDragGestures(
                    onDragStart = ::update,
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    }
                )
            }
    ) {
        drawRect(Color.White)
        drawRect(brush = Brush.horizontalGradient(colors = listOf(Color.White, hueColor)))
        drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))
        drawCircle(
            color = markerStroke,
            radius = 11.dp.toPx(),
            center = Offset(hsvState.saturation * size.width, (1f - hsvState.value) * size.height),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
private fun HueStrip(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    val rainbow = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000)
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .pointerInput(Unit) {
                fun update(offset: Offset) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onHueChange(((offset.x / width).coerceIn(0f, 1f)) * 360f)
                }
                detectTapGestures(onTap = ::update)
            }
            .pointerInput(Unit) {
                fun update(offset: Offset) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onHueChange(((offset.x / width).coerceIn(0f, 1f)) * 360f)
                }
                detectDragGestures(
                    onDragStart = ::update,
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    }
                )
            }
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(rainbow),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        )
        val markerX = (hue.coerceIn(0f, 360f) / 360f) * size.width
        drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(markerX, size.height / 2f))
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = 10.dp.toPx(),
            center = Offset(markerX, size.height / 2f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun rememberUriImageBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, uri, context) {
        value = withContext(Dispatchers.IO) {
            val safeUri = uri ?: return@withContext null
            runCatching {
                context.contentResolver.openInputStream(safeUri)?.use(BitmapFactory::decodeStream)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return imageBitmap
}

fun exportIconDesign(
    context: Context,
    outputUri: Uri,
    background: Color,
    cornerRadiusProgress: Float,
    contentSizeProgress: Float,
    imageUri: Uri?,
    previewBitmap: ImageBitmap?,
    text: String
): Boolean {
    return runCatching {
        val output = Bitmap.createBitmap(EXPORT_SIZE_PX, EXPORT_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(output)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.toArgb() }
        val cornerRadiusPx = EXPORT_SIZE_PX * iconCornerRadiusFraction(cornerRadiusProgress)
        canvas.drawRoundRect(
            RectF(0f, 0f, EXPORT_SIZE_PX.toFloat(), EXPORT_SIZE_PX.toFloat()),
            cornerRadiusPx,
            cornerRadiusPx,
            backgroundPaint
        )

        val hasText = text.isNotBlank()
        val iconBitmap = if (hasText) {
            null
        } else {
            previewBitmap?.asAndroidBitmap()
                ?: imageUri?.let { uri -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }
        }
        val imageMaxSideFraction = iconGraphicMaxSideFraction(contentSizeProgress)
        val innerPaddingPx = EXPORT_SIZE_PX * ICON_INNER_PADDING_FRACTION

        iconBitmap?.let { bitmap ->
            val contentAreaSize = EXPORT_SIZE_PX - (innerPaddingPx * 2f)
            val maxSide = contentAreaSize * imageMaxSideFraction
            val scale = min(maxSide / bitmap.width.coerceAtLeast(1), maxSide / bitmap.height.coerceAtLeast(1))
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (EXPORT_SIZE_PX - drawWidth) / 2f
            val top = (EXPORT_SIZE_PX - drawHeight) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), Paint(Paint.ANTI_ALIAS_FLAG))
        }

        if (hasText) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = contentColorFor(background).toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = EXPORT_SIZE_PX * iconTextSizeScale(contentSizeProgress)
            }
            val displayText = TextUtils.ellipsize(
                text.trim(),
                textPaint,
                (EXPORT_SIZE_PX - (innerPaddingPx * 2f)) * 0.92f,
                TextUtils.TruncateAt.END
            ).toString()
            val baseline = (EXPORT_SIZE_PX / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(displayText, EXPORT_SIZE_PX / 2f, baseline, textPaint)
        }

        context.contentResolver.openOutputStream(outputUri, "w")?.use { outputStream ->
            output.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        } ?: return false

        true
    }.getOrElse { false }
}

fun grantReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun readableUriName(uri: Uri): String {
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotBlank() }
        ?: uri.toString()
}

fun parseOptionalColor(value: String): Color? {
    val candidate = value.trim()
    if (candidate.isBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(candidate)) }.getOrNull()
}

fun contentColorFor(background: Color): Color {
    val luminance = (0.299f * background.red) + (0.587f * background.green) + (0.114f * background.blue)
    return if (luminance > 0.5f) Color(0xFF0F172A) else Color.White
}

data class HsvState(
    val hue: Float,
    val saturation: Float,
    val value: Float
)

fun colorToHsvState(color: Color): HsvState {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return HsvState(hue = hsv[0].coerceIn(0f, 360f), saturation = hsv[1].coerceIn(0f, 1f), value = hsv[2].coerceIn(0f, 1f))
}

fun hsvStateToColor(state: HsvState): Color {
    return Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(
                state.hue.coerceIn(0f, 360f),
                state.saturation.coerceIn(0f, 1f),
                state.value.coerceIn(0f, 1f)
            )
        )
    )
}

fun formatRgbHex(color: Color): String {
    return String.format(Locale.US, "#%06X", 0xFFFFFF and color.toArgb())
}

fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + ((stop - start) * fraction.coerceIn(0f, 1f))
}

fun iconGraphicMaxSideFraction(contentSizeProgress: Float): Float {
    return lerpFloat(0.24f, 1.0f, contentSizeProgress)
}

fun iconCornerRadiusFraction(cornerRadiusProgress: Float): Float {
    return lerpFloat(0f, 0.5f, cornerRadiusProgress)
}

fun iconCornerRadiusPercent(cornerRadiusProgress: Float): Int {
    return (iconCornerRadiusFraction(cornerRadiusProgress) * 100f).toInt().coerceIn(0, 50)
}

fun iconTextSizeScale(contentSizeProgress: Float): Float {
    return lerpFloat(0.14f, 0.24f, contentSizeProgress)
}

const val DEFAULT_EXPORT_FILE_NAME = "firefly-icon.png"
const val EXPORT_SIZE_PX = 1024
const val DEFAULT_CORNER_RADIUS_PROGRESS = 0.34f
const val DEFAULT_CONTENT_SIZE_PROGRESS = 0.52f
const val DEFAULT_ICON_BACKGROUND_HEX = "#9CCC65"
val DEFAULT_ICON_BACKGROUND_COLOR = Color(0xFF9CCC65)
const val ICON_PREVIEW_BOX_FRACTION = 0.56f
const val ICON_INNER_PADDING_FRACTION = 0.08f
