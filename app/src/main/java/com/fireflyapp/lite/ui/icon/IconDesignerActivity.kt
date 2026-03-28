package com.fireflyapp.lite.ui.icon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.net.toUri
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppLanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconDesignerActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                IconDesignerRoute(onBack = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, IconDesignerActivity::class.java)
    }
}

@Composable
private fun IconDesignerRoute(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var imageLabel by rememberSaveable { mutableStateOf("") }
    var displayText by rememberSaveable { mutableStateOf("") }
    var backgroundHex by rememberSaveable { mutableStateOf(DEFAULT_ICON_BACKGROUND_HEX) }
    var cornerRadiusProgress by rememberSaveable { mutableStateOf(DEFAULT_CORNER_RADIUS_PROGRESS) }
    var contentSizeProgress by rememberSaveable { mutableStateOf(DEFAULT_CONTENT_SIZE_PROGRESS) }
    var textDialogVisible by rememberSaveable { mutableStateOf(false) }
    var colorDialogVisible by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }

    val imageUri = remember(imageUriString) { imageUriString?.takeIf { it.isNotBlank() }?.toUri() }
    val previewBitmap = rememberUriImageBitmap(imageUri)
    val previewBackground = parseOptionalColor(backgroundHex) ?: DEFAULT_ICON_BACKGROUND_COLOR

    val iconPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        grantReadPermission(context, uri)
        imageUriString = uri.toString()
        imageLabel = readableUriName(uri)
        displayText = ""
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri == null) {
            exporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                exportIconDesign(
                    context = context,
                    outputUri = uri,
                    background = previewBackground,
                    cornerRadiusProgress = cornerRadiusProgress,
                    contentSizeProgress = contentSizeProgress,
                    imageUri = imageUri,
                    previewBitmap = previewBitmap,
                    text = displayText
                )
            }
            exporting = false
            Toast.makeText(
                context,
                context.getString(if (result) R.string.icon_designer_export_success else R.string.icon_designer_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    IconDesignerScreen(
        previewBitmap = previewBitmap,
        backgroundColor = previewBackground,
        cornerRadiusProgress = cornerRadiusProgress,
        contentSizeProgress = contentSizeProgress,
        displayText = displayText,
        imageLabel = imageLabel.ifBlank { context.getString(R.string.icon_designer_not_set) },
        backgroundLabel = backgroundHex,
        exporting = exporting,
        onBack = onBack,
        onSave = {
            exporting = true
            exportLauncher.launch(DEFAULT_EXPORT_FILE_NAME)
        },
        onCornerRadiusChange = { cornerRadiusProgress = it },
        onContentSizeChange = { contentSizeProgress = it },
        onSelectIcon = { iconPickerLauncher.launch(arrayOf("image/*")) },
        onEditText = { textDialogVisible = true },
        onEditBackground = { colorDialogVisible = true }
    )

    if (textDialogVisible) {
        var draftText by remember(displayText) { mutableStateOf(displayText) }
        AlertDialog(
            onDismissRequest = { textDialogVisible = false },
            title = { Text(stringResource(R.string.icon_designer_set_text)) },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.icon_designer_text_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val nextText = draftText.trim()
                    displayText = nextText
                    if (nextText.isNotBlank()) {
                        imageUriString = null
                        imageLabel = ""
                    }
                    textDialogVisible = false
                }) {
                    Text(stringResource(R.string.icon_designer_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { textDialogVisible = false }) {
                    Text(stringResource(R.string.project_hub_cancel_action))
                }
            }
        )
    }

    if (colorDialogVisible) {
        ColorPickerDialog(
            title = stringResource(R.string.icon_designer_background_color),
            value = backgroundHex,
            onDismiss = { colorDialogVisible = false },
            onApply = {
                backgroundHex = it
                colorDialogVisible = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconDesignerScreen(
    previewBitmap: ImageBitmap?,
    backgroundColor: Color,
    cornerRadiusProgress: Float,
    contentSizeProgress: Float,
    displayText: String,
    imageLabel: String,
    backgroundLabel: String,
    exporting: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCornerRadiusChange: (Float) -> Unit,
    onContentSizeChange: (Float) -> Unit,
    onSelectIcon: () -> Unit,
    onEditText: () -> Unit,
    onEditBackground: () -> Unit
) {
    val cornerRadiusLabel = "${(cornerRadiusProgress * 100f).roundToInt()}%"
    val contentSizeLabel = "${(contentSizeProgress * 100f).roundToInt()}%"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.icon_designer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.icon_designer_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !exporting) {
                        Icon(Icons.Filled.Save, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.icon_designer_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        IconPreviewCard(
                            previewBitmap = previewBitmap,
                            backgroundColor = backgroundColor,
                            cornerRadiusProgress = cornerRadiusProgress,
                            contentSizeProgress = contentSizeProgress,
                            text = displayText
                        )
                        SliderSettingRow(
                            icon = Icons.Filled.RoundedCorner,
                            title = stringResource(R.string.icon_designer_corner_radius),
                            valueLabel = cornerRadiusLabel,
                            progress = cornerRadiusProgress,
                            onProgressChange = onCornerRadiusChange
                        )
                        SliderSettingRow(
                            icon = Icons.Filled.FormatSize,
                            title = stringResource(R.string.icon_designer_content_size),
                            valueLabel = contentSizeLabel,
                            progress = contentSizeProgress,
                            onProgressChange = onContentSizeChange
                        )
                    }
                }
            }

            item {
                ActionRow(
                    icon = Icons.Filled.Image,
                    title = stringResource(R.string.icon_designer_set_icon),
                    value = imageLabel,
                    onClick = onSelectIcon
                )
            }
            item {
                ActionRow(
                    icon = Icons.Filled.TextFields,
                    title = stringResource(R.string.icon_designer_set_text),
                    value = displayText.ifBlank { stringResource(R.string.icon_designer_not_set) },
                    onClick = onEditText
                )
            }
            item {
                ActionRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.icon_designer_background_color),
                    value = backgroundLabel,
                    swatchColor = backgroundColor,
                    onClick = onEditBackground
                )
            }
        }
    }
}

@Composable
private fun IconPreviewCard(
    previewBitmap: ImageBitmap?,
    backgroundColor: Color,
    cornerRadiusProgress: Float,
    contentSizeProgress: Float,
    text: String
) {
    val foreground = contentColorFor(backgroundColor)
    val hasImage = previewBitmap != null
    val hasText = text.isNotBlank()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(ICON_PREVIEW_BOX_FRACTION)
                .aspectRatio(1f)
        ) {
            val cornerRadiusPercent = iconCornerRadiusPercent(cornerRadiusProgress)
            val contentFraction = iconGraphicMaxSideFraction(contentSizeProgress)
            val innerPadding = maxWidth * ICON_INNER_PADDING_FRACTION
            val textSize = (maxWidth.value * iconTextSizeScale(contentSizeProgress)).sp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadiusPercent))
                    .background(backgroundColor)
                    .border(1.dp, foreground.copy(alpha = 0.14f), RoundedCornerShape(cornerRadiusPercent))
                    .padding(innerPadding)
            ) {
                if (hasImage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(contentFraction)
                    ) {
                        Image(
                            bitmap = checkNotNull(previewBitmap),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (hasText) {
                    Text(
                        text = text,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!hasImage && !hasText) {
            Text(
                text = stringResource(R.string.icon_designer_preview_placeholder),
                style = MaterialTheme.typography.titleMedium,
                color = foreground.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun SliderSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    valueLabel: String,
    progress: Float,
    onProgressChange: (Float) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f), shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value = progress, onValueChange = onProgressChange)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    swatchColor: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (swatchColor != null) {
                Surface(modifier = Modifier.size(24.dp), shape = RoundedCornerShape(8.dp), color = swatchColor) {}
                Spacer(modifier = Modifier.width(10.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
