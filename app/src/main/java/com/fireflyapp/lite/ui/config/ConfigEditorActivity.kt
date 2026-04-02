package com.fireflyapp.lite.ui.config

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireflyapp.lite.R
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.data.model.SSL_ERROR_HANDLING_IGNORE
import com.fireflyapp.lite.data.model.SSL_ERROR_HANDLING_STRICT
import com.fireflyapp.lite.data.model.TemplateType
import com.fireflyapp.lite.ui.template.RuntimeShellTemplateSpec
import com.fireflyapp.lite.ui.template.TemplateCatalog
import com.fireflyapp.lite.ui.template.TemplateIconCatalog
import com.fireflyapp.lite.ui.template.TemplateNavigationChromeStyle
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class DrawerMediaTarget {
    WALLPAPER,
    AVATAR
}

private const val MAX_EDIT_NAVIGATION_ITEMS = 5

class ConfigEditorActivity : ComponentActivity() {
    private val viewModel: ConfigEditorViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
        if (projectId.isBlank()) {
            finish()
            return
        }

        viewModel.loadProject(projectId)
        enableEdgeToEdge()
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var pendingDrawerMediaTarget by remember { mutableStateOf<DrawerMediaTarget?>(null) }
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null) {
                    viewModel.exportConfig(uri)
                }
            }
            val iconImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importProjectIcon(uri)
                }
            }
            val splashImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importProjectSplash(uri)
                }
            }
            val keystoreImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importProjectKeystore(uri)
                }
            }
            val drawerMediaCropLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val outputUri = UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
                if (result.resultCode == Activity.RESULT_OK && outputUri != null) {
                    when (pendingDrawerMediaTarget) {
                        DrawerMediaTarget.WALLPAPER -> viewModel.importDrawerWallpaper(outputUri)
                        DrawerMediaTarget.AVATAR -> viewModel.importDrawerAvatar(outputUri)
                        null -> Unit
                    }
                }
                pendingDrawerMediaTarget = null
            }
            val drawerWallpaperImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    grantReadPermission(uri)
                    pendingDrawerMediaTarget = DrawerMediaTarget.WALLPAPER
                    val cropIntent = createDrawerCropIntent(
                        sourceUri = uri,
                        target = DrawerMediaTarget.WALLPAPER,
                        wallpaperHeightDp = state.formState.drawerWallpaperHeightText.trim().toIntOrNull()
                            ?: DEFAULT_DRAWER_WALLPAPER_HEIGHT_DP,
                        drawerWidthDp = state.formState.drawerWidthText.trim().toIntOrNull()
                            ?: DEFAULT_DRAWER_WIDTH_DP,
                        hasSubtitle = state.formState.drawerHeaderSubtitle.isNotBlank(),
                        immersiveStatusBar = state.formState.immersiveStatusBar
                    )
                    drawerMediaCropLauncher.launch(cropIntent)
                }
            }
            val drawerAvatarImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    grantReadPermission(uri)
                    pendingDrawerMediaTarget = DrawerMediaTarget.AVATAR
                    val cropIntent = createDrawerCropIntent(
                        sourceUri = uri,
                        target = DrawerMediaTarget.AVATAR,
                        wallpaperHeightDp = state.formState.drawerWallpaperHeightText.trim().toIntOrNull()
                            ?: DEFAULT_DRAWER_WALLPAPER_HEIGHT_DP,
                        drawerWidthDp = state.formState.drawerWidthText.trim().toIntOrNull()
                            ?: DEFAULT_DRAWER_WIDTH_DP,
                        hasSubtitle = state.formState.drawerHeaderSubtitle.isNotBlank(),
                        immersiveStatusBar = state.formState.immersiveStatusBar
                    )
                    drawerMediaCropLauncher.launch(cropIntent)
                }
            }

            LaunchedEffect(state.userMessage) {
                val message = state.userMessage ?: return@LaunchedEffect
                Toast.makeText(this@ConfigEditorActivity, message, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }

            LaunchedEffect(state.appliedChangeCount) {
                if (state.appliedChangeCount > 0) {
                    setResult(Activity.RESULT_OK)
                }
            }

            MaterialTheme(colorScheme = lightColorScheme()) {
                ConfigEditorScreen(
                    state = state,
                    onBack = ::finish,
                    onSelectTab = viewModel::selectTab,
                    onSave = viewModel::saveSelectedTab,
                    onReset = viewModel::resetSelectedTab,
                    onExport = { exportLauncher.launch("${state.projectId ?: "project"}.fireflyproj.zip") },
                    onAppNameChanged = viewModel::updateAppName,
                    onDefaultUrlChanged = viewModel::updateDefaultUrl,
                    onTemplateSelected = viewModel::updateTemplate,
                    onUserAgentChanged = viewModel::updateUserAgent,
                    onBackActionSelected = viewModel::updateBackAction,
                    onNightModeChanged = viewModel::updateNightMode,
                    onLoadingOverlayChanged = viewModel::updateShowLoadingOverlay,
                    onShowPageProgressBarChanged = viewModel::updateShowPageProgressBar,
                    onErrorViewChanged = viewModel::updateShowErrorView,
                    onImmersiveStatusBarChanged = viewModel::updateImmersiveStatusBar,
                    onTopBarShowBackButtonChanged = viewModel::updateTopBarShowBackButton,
                    onTopBarShowHomeButtonChanged = viewModel::updateTopBarShowHomeButton,
                    onTopBarShowRefreshButtonChanged = viewModel::updateTopBarShowRefreshButton,
                    onTopBarHomeBehaviorChanged = viewModel::updateTopBarHomeBehavior,
                    onTopBarHomeScriptChanged = viewModel::updateTopBarHomeScript,
                    onTopBarRefreshBehaviorChanged = viewModel::updateTopBarRefreshBehavior,
                    onTopBarRefreshScriptChanged = viewModel::updateTopBarRefreshScript,
                    onTopBarFollowPageTitleChanged = viewModel::updateTopBarFollowPageTitle,
                    onTopBarTitleCenteredChanged = viewModel::updateTopBarTitleCentered,
                    onTopBarCornerRadiusChanged = viewModel::updateTopBarCornerRadiusText,
                    onTopBarShadowChanged = viewModel::updateTopBarShadowText,
                    onTopBarBackIconChanged = viewModel::updateTopBarBackIcon,
                    onTopBarHomeIconChanged = viewModel::updateTopBarHomeIcon,
                    onTopBarRefreshIconChanged = viewModel::updateTopBarRefreshIcon,
                    onBottomBarShowTextLabelsChanged = viewModel::updateBottomBarShowTextLabels,
                    onBottomBarCornerRadiusChanged = viewModel::updateBottomBarCornerRadiusText,
                    onBottomBarShadowChanged = viewModel::updateBottomBarShadowText,
                    onBottomBarBadgeColorChanged = viewModel::updateBottomBarBadgeColor,
                    onBottomBarBadgeTextColorChanged = viewModel::updateBottomBarBadgeTextColor,
                    onBottomBarBadgeGravityChanged = viewModel::updateBottomBarBadgeGravity,
                    onBottomBarBadgeMaxCharacterCountChanged = viewModel::updateBottomBarBadgeMaxCharacterCountText,
                    onBottomBarBadgeHorizontalOffsetChanged = viewModel::updateBottomBarBadgeHorizontalOffsetText,
                    onBottomBarBadgeVerticalOffsetChanged = viewModel::updateBottomBarBadgeVerticalOffsetText,
                    onBottomBarSelectedColorChanged = viewModel::updateBottomBarSelectedColor,
                    onDrawerHeaderTitleChanged = viewModel::updateDrawerHeaderTitle,
                    onDrawerHeaderSubtitleChanged = viewModel::updateDrawerHeaderSubtitle,
                    onDrawerWidthChanged = viewModel::updateDrawerWidthText,
                    onDrawerCornerRadiusChanged = viewModel::updateDrawerCornerRadiusText,
                    onDrawerHeaderBackgroundColorChanged = viewModel::updateDrawerHeaderBackgroundColor,
                    onDrawerWallpaperEnabledChanged = viewModel::updateDrawerWallpaperEnabled,
                    onImportDrawerWallpaper = {
                        drawerWallpaperImportLauncher.launch(arrayOf("image/*"))
                    },
                    onClearDrawerWallpaper = viewModel::clearDrawerWallpaper,
                    onDrawerWallpaperHeightChanged = viewModel::updateDrawerWallpaperHeightText,
                    onDrawerAvatarEnabledChanged = viewModel::updateDrawerAvatarEnabled,
                    onImportDrawerAvatar = {
                        drawerAvatarImportLauncher.launch(arrayOf("image/*"))
                    },
                    onClearDrawerAvatar = viewModel::clearDrawerAvatar,
                    onDrawerHeaderImageUrlChanged = viewModel::updateDrawerHeaderImageUrl,
                    onDrawerHeaderImageHeightChanged = viewModel::updateDrawerHeaderImageHeightText,
                    onDrawerHeaderImageScaleModeChanged = viewModel::updateDrawerHeaderImageScaleMode,
                    onDrawerHeaderImageOverlayPresetChanged = viewModel::updateDrawerHeaderImageOverlayPreset,
                    onDrawerHeaderImageOverlayColorChanged = viewModel::updateDrawerHeaderImageOverlayColor,
                    onDrawerMenuIconChanged = viewModel::updateDrawerMenuIcon,
                    onDefaultNavigationItemIdChanged = viewModel::updateDefaultNavigationItemId,
                    onEnableSwipeNavigationChanged = viewModel::updateEnableSwipeNavigation,
                    onNavigationBackBehaviorChanged = viewModel::updateNavigationBackBehavior,
                    onTopBarThemeColorChanged = viewModel::updateTopBarThemeColor,
                    onBottomBarThemeColorChanged = viewModel::updateBottomBarThemeColor,
                    onAllowExternalHostsChanged = viewModel::updateAllowExternalHosts,
                    onOpenOtherAppsModeChanged = viewModel::updateOpenOtherAppsMode,
                    onSslErrorHandlingChanged = viewModel::updateSslErrorHandling,
                    onAllowedHostsChanged = viewModel::updateAllowedHostsText,
                    onGlobalJsChanged = viewModel::updateGlobalJsText,
                    onGlobalCssChanged = viewModel::updateGlobalCssText,
                    onApplicationLabelChanged = viewModel::updateApplicationLabel,
                    onVersionNameChanged = viewModel::updateVersionName,
                    onVersionCodeChanged = viewModel::updateVersionCodeText,
                    onPackageNameChanged = viewModel::updatePackageName,
                    onOutputApkNameTemplateChanged = viewModel::updateOutputApkNameTemplate,
                    onSigningStorePasswordChanged = viewModel::updateSigningStorePassword,
                    onSigningKeyAliasChanged = viewModel::updateSigningKeyAlias,
                    onSigningKeyPasswordChanged = viewModel::updateSigningKeyPassword,
                    onSplashSkipEnabledChanged = viewModel::updateSplashSkipEnabled,
                    onSplashSkipSecondsChanged = viewModel::updateSplashSkipSecondsText,
                    onImportIcon = {
                        iconImportLauncher.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                    },
                    onClearIcon = viewModel::clearProjectIcon,
                    onImportSplash = {
                        splashImportLauncher.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                    },
                    onClearSplash = viewModel::clearProjectSplash,
                    onImportKeystore = {
                        keystoreImportLauncher.launch(arrayOf("*/*"))
                    },
                    onClearKeystore = viewModel::clearProjectKeystore,
                    onAddNavigationItem = viewModel::addNavigationItem,
                    onRemoveNavigationItem = viewModel::removeNavigationItem,
                    onNavigationIdChanged = viewModel::updateNavigationId,
                    onNavigationTitleChanged = viewModel::updateNavigationTitle,
                    onNavigationUrlChanged = viewModel::updateNavigationUrl,
                    onNavigationIconChanged = viewModel::updateNavigationIcon,
                    onNavigationSelectedIconChanged = viewModel::updateNavigationSelectedIcon,
                    onNavigationBadgeCountChanged = viewModel::updateNavigationBadgeCount,
                    onNavigationShowUnreadDotChanged = viewModel::updateNavigationShowUnreadDot,
                    onAddPageRule = viewModel::addPageRule,
                    onRemovePageRule = viewModel::removePageRule,
                    onPageRuleUrlEqualsChanged = viewModel::updatePageRuleUrlEquals,
                    onPageRuleUrlStartsWithChanged = viewModel::updatePageRuleUrlStartsWith,
                    onPageRuleUrlContainsChanged = viewModel::updatePageRuleUrlContains,
                    onPageRuleTitleChanged = viewModel::updatePageRuleTitle,
                    onPageRuleLoadingTextChanged = viewModel::updatePageRuleLoadingText,
                    onPageRuleErrorTitleChanged = viewModel::updatePageRuleErrorTitle,
                    onPageRuleErrorMessageChanged = viewModel::updatePageRuleErrorMessage,
                    onPageRuleRetryActionChanged = viewModel::updatePageRuleRetryAction,
                    onPageRuleRetryUrlChanged = viewModel::updatePageRuleRetryUrl,
                    onPageRuleInjectJsChanged = viewModel::updatePageRuleInjectJs,
                    onPageRuleInjectCssChanged = viewModel::updatePageRuleInjectCss,
                    onPageRuleShowTopBarChanged = viewModel::updatePageRuleShowTopBar,
                    onPageRuleShowBottomBarChanged = viewModel::updatePageRuleShowBottomBar,
                    onPageRuleShowDownloadOverlayChanged = viewModel::updatePageRuleShowDownloadOverlay,
                    onPageRuleSuppressFocusHighlightChanged = viewModel::updatePageRuleSuppressFocusHighlight,
                    onPageRuleOpenExternalChanged = viewModel::updatePageRuleOpenExternal,
                    onAddPageEvent = viewModel::addPageEvent,
                    onRemovePageEvent = viewModel::removePageEvent,
                    onPageEventIdChanged = viewModel::updatePageEventId,
                    onPageEventEnabledChanged = viewModel::updatePageEventEnabled,
                    onPageEventTriggerChanged = viewModel::updatePageEventTrigger,
                    onPageEventUrlEqualsChanged = viewModel::updatePageEventUrlEquals,
                    onPageEventUrlStartsWithChanged = viewModel::updatePageEventUrlStartsWith,
                    onPageEventUrlContainsChanged = viewModel::updatePageEventUrlContains,
                    onAddPageEventAction = viewModel::addPageEventAction,
                    onRemovePageEventAction = viewModel::removePageEventAction,
                    onPageEventActionTypeChanged = viewModel::updatePageEventActionType,
                    onPageEventActionValueChanged = viewModel::updatePageEventActionValue,
                    onPageEventActionUrlChanged = viewModel::updatePageEventActionUrl,
                    onPageEventActionScriptChanged = viewModel::updatePageEventActionScript,
                    onRawJsonChanged = viewModel::updateRawJson
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"
        private const val DRAWER_HEADER_HORIZONTAL_PADDING_DP = 22

        fun createIntent(context: Context, projectId: String): Intent {
            return Intent(context, ConfigEditorActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }

    private fun createDrawerCropIntent(
        sourceUri: Uri,
        target: DrawerMediaTarget,
        wallpaperHeightDp: Int,
        drawerWidthDp: Int,
        hasSubtitle: Boolean,
        immersiveStatusBar: Boolean
    ): Intent {
        val safeDrawerWidth = resolveDrawerPreviewWidthDp(drawerWidthDp.toString())
        val safeHeight = resolveDrawerHeaderHeightDp(
            wallpaperHeightDp = wallpaperHeightDp.coerceAtLeast(96),
            hasSubtitle = hasSubtitle,
            immersiveStatusBar = immersiveStatusBar
        )
        val destinationFile = File(
            cacheDir.resolve("crop").apply { mkdirs() },
            "crop_${target.name.lowercase()}_${System.currentTimeMillis()}.png"
        )
        val destinationUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            destinationFile
        )
        val crop = when (target) {
            DrawerMediaTarget.WALLPAPER -> {
                UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(safeDrawerWidth.toFloat(), safeHeight.toFloat())
                    .withMaxResultSize(1600, 1200)
            }

            DrawerMediaTarget.AVATAR -> {
                UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(720, 720)
                }
        }
        val options = UCrop.Options().apply {
            setStatusBarColor(ContextCompat.getColor(this@ConfigEditorActivity, R.color.firefly_surface))
            setToolbarColor(ContextCompat.getColor(this@ConfigEditorActivity, R.color.firefly_surface))
            setToolbarWidgetColor(ContextCompat.getColor(this@ConfigEditorActivity, R.color.firefly_on_surface))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@ConfigEditorActivity, R.color.firefly_primary))
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setToolbarTitle(
                if (target == DrawerMediaTarget.WALLPAPER) {
                    getString(R.string.config_editor_crop_wallpaper)
                } else {
                    getString(R.string.config_editor_crop_avatar)
                }
            )
        }
        return crop.withOptions(options).getIntent(this).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "drawer_crop_output", destinationUri)
        }
    }

    private fun grantReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}

@Composable
private fun ConfigEditorScreen(
    state: ConfigEditorUiState,
    onBack: () -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onAppNameChanged: (String) -> Unit,
    onDefaultUrlChanged: (String) -> Unit,
    onTemplateSelected: (TemplateType) -> Unit,
    onUserAgentChanged: (String) -> Unit,
    onBackActionSelected: (String) -> Unit,
    onNightModeChanged: (String) -> Unit,
    onLoadingOverlayChanged: (Boolean) -> Unit,
    onShowPageProgressBarChanged: (Boolean) -> Unit,
    onErrorViewChanged: (Boolean) -> Unit,
    onImmersiveStatusBarChanged: (Boolean) -> Unit,
    onTopBarShowBackButtonChanged: (Boolean) -> Unit,
    onTopBarShowHomeButtonChanged: (Boolean) -> Unit,
    onTopBarShowRefreshButtonChanged: (Boolean) -> Unit,
    onTopBarHomeBehaviorChanged: (String) -> Unit,
    onTopBarHomeScriptChanged: (String) -> Unit,
    onTopBarRefreshBehaviorChanged: (String) -> Unit,
    onTopBarRefreshScriptChanged: (String) -> Unit,
    onTopBarFollowPageTitleChanged: (Boolean) -> Unit,
    onTopBarTitleCenteredChanged: (Boolean) -> Unit,
    onTopBarCornerRadiusChanged: (String) -> Unit,
    onTopBarShadowChanged: (String) -> Unit,
    onTopBarBackIconChanged: (String) -> Unit,
    onTopBarHomeIconChanged: (String) -> Unit,
    onTopBarRefreshIconChanged: (String) -> Unit,
    onBottomBarShowTextLabelsChanged: (Boolean) -> Unit,
    onBottomBarCornerRadiusChanged: (String) -> Unit,
    onBottomBarShadowChanged: (String) -> Unit,
    onBottomBarBadgeColorChanged: (String) -> Unit,
    onBottomBarBadgeTextColorChanged: (String) -> Unit,
    onBottomBarBadgeGravityChanged: (String) -> Unit,
    onBottomBarBadgeMaxCharacterCountChanged: (String) -> Unit,
    onBottomBarBadgeHorizontalOffsetChanged: (String) -> Unit,
    onBottomBarBadgeVerticalOffsetChanged: (String) -> Unit,
    onBottomBarSelectedColorChanged: (String) -> Unit,
    onDrawerHeaderTitleChanged: (String) -> Unit,
    onDrawerHeaderSubtitleChanged: (String) -> Unit,
    onDrawerWidthChanged: (String) -> Unit,
    onDrawerCornerRadiusChanged: (String) -> Unit,
    onDrawerHeaderBackgroundColorChanged: (String) -> Unit,
    onDrawerWallpaperEnabledChanged: (Boolean) -> Unit,
    onImportDrawerWallpaper: () -> Unit,
    onClearDrawerWallpaper: () -> Unit,
    onDrawerWallpaperHeightChanged: (String) -> Unit,
    onDrawerAvatarEnabledChanged: (Boolean) -> Unit,
    onImportDrawerAvatar: () -> Unit,
    onClearDrawerAvatar: () -> Unit,
    onDrawerHeaderImageUrlChanged: (String) -> Unit,
    onDrawerHeaderImageHeightChanged: (String) -> Unit,
    onDrawerHeaderImageScaleModeChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayPresetChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayColorChanged: (String) -> Unit,
    onDrawerMenuIconChanged: (String) -> Unit,
    onDefaultNavigationItemIdChanged: (String) -> Unit,
    onEnableSwipeNavigationChanged: (Boolean) -> Unit,
    onNavigationBackBehaviorChanged: (String) -> Unit,
    onTopBarThemeColorChanged: (String) -> Unit,
    onBottomBarThemeColorChanged: (String) -> Unit,
    onAllowExternalHostsChanged: (Boolean) -> Unit,
    onOpenOtherAppsModeChanged: (String) -> Unit,
    onSslErrorHandlingChanged: (String) -> Unit,
    onAllowedHostsChanged: (String) -> Unit,
    onGlobalJsChanged: (String) -> Unit,
    onGlobalCssChanged: (String) -> Unit,
    onApplicationLabelChanged: (String) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onOutputApkNameTemplateChanged: (String) -> Unit,
    onSigningStorePasswordChanged: (String) -> Unit,
    onSigningKeyAliasChanged: (String) -> Unit,
    onSigningKeyPasswordChanged: (String) -> Unit,
    onSplashSkipEnabledChanged: (Boolean) -> Unit,
    onSplashSkipSecondsChanged: (String) -> Unit,
    onImportIcon: () -> Unit,
    onClearIcon: () -> Unit,
    onImportSplash: () -> Unit,
    onClearSplash: () -> Unit,
    onImportKeystore: () -> Unit,
    onClearKeystore: () -> Unit,
    onAddNavigationItem: () -> Unit,
    onRemoveNavigationItem: (Int) -> Unit,
    onNavigationIdChanged: (Int, String) -> Unit,
    onNavigationTitleChanged: (Int, String) -> Unit,
    onNavigationUrlChanged: (Int, String) -> Unit,
    onNavigationIconChanged: (Int, String) -> Unit,
    onNavigationSelectedIconChanged: (Int, String) -> Unit,
    onNavigationBadgeCountChanged: (Int, String) -> Unit,
    onNavigationShowUnreadDotChanged: (Int, Boolean) -> Unit,
    onAddPageRule: () -> Unit,
    onRemovePageRule: (Int) -> Unit,
    onPageRuleUrlEqualsChanged: (Int, String) -> Unit,
    onPageRuleUrlStartsWithChanged: (Int, String) -> Unit,
    onPageRuleUrlContainsChanged: (Int, String) -> Unit,
    onPageRuleTitleChanged: (Int, String) -> Unit,
    onPageRuleLoadingTextChanged: (Int, String) -> Unit,
    onPageRuleErrorTitleChanged: (Int, String) -> Unit,
    onPageRuleErrorMessageChanged: (Int, String) -> Unit,
    onPageRuleRetryActionChanged: (Int, String) -> Unit,
    onPageRuleRetryUrlChanged: (Int, String) -> Unit,
    onPageRuleInjectJsChanged: (Int, String) -> Unit,
    onPageRuleInjectCssChanged: (Int, String) -> Unit,
    onPageRuleShowTopBarChanged: (Int, RuleToggleState) -> Unit,
    onPageRuleShowBottomBarChanged: (Int, RuleToggleState) -> Unit,
    onPageRuleShowDownloadOverlayChanged: (Int, RuleToggleState) -> Unit,
    onPageRuleSuppressFocusHighlightChanged: (Int, RuleToggleState) -> Unit,
    onPageRuleOpenExternalChanged: (Int, RuleToggleState) -> Unit,
    onAddPageEvent: () -> Unit,
    onRemovePageEvent: (Int) -> Unit,
    onPageEventIdChanged: (Int, String) -> Unit,
    onPageEventEnabledChanged: (Int, Boolean) -> Unit,
    onPageEventTriggerChanged: (Int, String) -> Unit,
    onPageEventUrlEqualsChanged: (Int, String) -> Unit,
    onPageEventUrlStartsWithChanged: (Int, String) -> Unit,
    onPageEventUrlContainsChanged: (Int, String) -> Unit,
    onAddPageEventAction: (Int) -> Unit,
    onRemovePageEventAction: (Int, Int) -> Unit,
    onPageEventActionTypeChanged: (Int, Int, String) -> Unit,
    onPageEventActionValueChanged: (Int, Int, String) -> Unit,
    onPageEventActionUrlChanged: (Int, Int, String) -> Unit,
    onPageEventActionScriptChanged: (Int, Int, String) -> Unit,
    onRawJsonChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.formState.appName.ifBlank { stringResource(R.string.config_editor_project_fallback) },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                TextButton(onClick = onExport, enabled = !state.isSaving) {
                    Text(stringResource(R.string.config_export))
                }
                TextButton(onClick = onReset, enabled = !state.isSaving) {
                    Text(stringResource(R.string.config_reset))
                }
                TextButton(onClick = onSave, enabled = !state.isSaving) {
                    Text(stringResource(R.string.config_save))
                }
            }
            if (state.isLoading || state.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = state.selectedTab == EditorTab.BASIC,
                onClick = { onSelectTab(EditorTab.BASIC) },
                text = { Text(stringResource(R.string.config_editor_tab_basic)) }
            )
            Tab(
                selected = state.selectedTab == EditorTab.RULES,
                onClick = { onSelectTab(EditorTab.RULES) },
                text = { Text(stringResource(R.string.config_editor_tab_rules)) }
            )
            Tab(
                selected = state.selectedTab == EditorTab.EVENTS,
                onClick = { onSelectTab(EditorTab.EVENTS) },
                text = { Text(stringResource(R.string.config_editor_tab_events)) }
            )
            Tab(
                selected = state.selectedTab == EditorTab.BRANDING,
                onClick = { onSelectTab(EditorTab.BRANDING) },
                text = { Text(stringResource(R.string.config_editor_tab_branding)) }
            )
            Tab(
                selected = state.selectedTab == EditorTab.BUILD,
                onClick = { onSelectTab(EditorTab.BUILD) },
                text = { Text(stringResource(R.string.config_editor_tab_build)) }
            )
            Tab(
                selected = state.selectedTab == EditorTab.JSON,
                onClick = { onSelectTab(EditorTab.JSON) },
                text = { Text(stringResource(R.string.config_tab_json)) }
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        when (state.selectedTab) {
            EditorTab.BASIC -> ConfigFormContent(
                state = state.formState,
                projectId = state.projectId,
                onAppNameChanged = onAppNameChanged,
                onDefaultUrlChanged = onDefaultUrlChanged,
                onTemplateSelected = onTemplateSelected,
                onUserAgentChanged = onUserAgentChanged,
                onBackActionSelected = onBackActionSelected,
                onNightModeChanged = onNightModeChanged,
                onLoadingOverlayChanged = onLoadingOverlayChanged,
                onShowPageProgressBarChanged = onShowPageProgressBarChanged,
                onErrorViewChanged = onErrorViewChanged,
                onImmersiveStatusBarChanged = onImmersiveStatusBarChanged,
                onTopBarShowBackButtonChanged = onTopBarShowBackButtonChanged,
                onTopBarShowHomeButtonChanged = onTopBarShowHomeButtonChanged,
                onTopBarShowRefreshButtonChanged = onTopBarShowRefreshButtonChanged,
                onTopBarHomeBehaviorChanged = onTopBarHomeBehaviorChanged,
                onTopBarHomeScriptChanged = onTopBarHomeScriptChanged,
                onTopBarRefreshBehaviorChanged = onTopBarRefreshBehaviorChanged,
                onTopBarRefreshScriptChanged = onTopBarRefreshScriptChanged,
                onTopBarFollowPageTitleChanged = onTopBarFollowPageTitleChanged,
                onTopBarTitleCenteredChanged = onTopBarTitleCenteredChanged,
                onTopBarCornerRadiusChanged = onTopBarCornerRadiusChanged,
                onTopBarShadowChanged = onTopBarShadowChanged,
                onTopBarBackIconChanged = onTopBarBackIconChanged,
                onTopBarHomeIconChanged = onTopBarHomeIconChanged,
                onTopBarRefreshIconChanged = onTopBarRefreshIconChanged,
                onBottomBarShowTextLabelsChanged = onBottomBarShowTextLabelsChanged,
                onBottomBarCornerRadiusChanged = onBottomBarCornerRadiusChanged,
                onBottomBarShadowChanged = onBottomBarShadowChanged,
                onBottomBarBadgeColorChanged = onBottomBarBadgeColorChanged,
                onBottomBarBadgeTextColorChanged = onBottomBarBadgeTextColorChanged,
                onBottomBarBadgeGravityChanged = onBottomBarBadgeGravityChanged,
                onBottomBarBadgeMaxCharacterCountChanged = onBottomBarBadgeMaxCharacterCountChanged,
                onBottomBarBadgeHorizontalOffsetChanged = onBottomBarBadgeHorizontalOffsetChanged,
                onBottomBarBadgeVerticalOffsetChanged = onBottomBarBadgeVerticalOffsetChanged,
                onBottomBarSelectedColorChanged = onBottomBarSelectedColorChanged,
                onDrawerHeaderTitleChanged = onDrawerHeaderTitleChanged,
                onDrawerHeaderSubtitleChanged = onDrawerHeaderSubtitleChanged,
                onDrawerWidthChanged = onDrawerWidthChanged,
                onDrawerCornerRadiusChanged = onDrawerCornerRadiusChanged,
                onDrawerHeaderBackgroundColorChanged = onDrawerHeaderBackgroundColorChanged,
                onDrawerWallpaperEnabledChanged = onDrawerWallpaperEnabledChanged,
                onImportDrawerWallpaper = onImportDrawerWallpaper,
                onClearDrawerWallpaper = onClearDrawerWallpaper,
                onDrawerWallpaperHeightChanged = onDrawerWallpaperHeightChanged,
                onDrawerAvatarEnabledChanged = onDrawerAvatarEnabledChanged,
                onImportDrawerAvatar = onImportDrawerAvatar,
                onClearDrawerAvatar = onClearDrawerAvatar,
                onDrawerHeaderImageUrlChanged = onDrawerHeaderImageUrlChanged,
                onDrawerHeaderImageHeightChanged = onDrawerHeaderImageHeightChanged,
                onDrawerHeaderImageScaleModeChanged = onDrawerHeaderImageScaleModeChanged,
                onDrawerHeaderImageOverlayPresetChanged = onDrawerHeaderImageOverlayPresetChanged,
                onDrawerHeaderImageOverlayColorChanged = onDrawerHeaderImageOverlayColorChanged,
                onDrawerMenuIconChanged = onDrawerMenuIconChanged,
                onDefaultNavigationItemIdChanged = onDefaultNavigationItemIdChanged,
                onEnableSwipeNavigationChanged = onEnableSwipeNavigationChanged,
                onNavigationBackBehaviorChanged = onNavigationBackBehaviorChanged,
                onTopBarThemeColorChanged = onTopBarThemeColorChanged,
                onBottomBarThemeColorChanged = onBottomBarThemeColorChanged,
                onAllowExternalHostsChanged = onAllowExternalHostsChanged,
                onOpenOtherAppsModeChanged = onOpenOtherAppsModeChanged,
                onSslErrorHandlingChanged = onSslErrorHandlingChanged,
                onAllowedHostsChanged = onAllowedHostsChanged,
                onGlobalJsChanged = onGlobalJsChanged,
                onGlobalCssChanged = onGlobalCssChanged,
                onAddNavigationItem = onAddNavigationItem,
                onRemoveNavigationItem = onRemoveNavigationItem,
                onNavigationIdChanged = onNavigationIdChanged,
                onNavigationTitleChanged = onNavigationTitleChanged,
                onNavigationUrlChanged = onNavigationUrlChanged,
                onNavigationIconChanged = onNavigationIconChanged,
                onNavigationSelectedIconChanged = onNavigationSelectedIconChanged,
                onNavigationBadgeCountChanged = onNavigationBadgeCountChanged,
                onNavigationShowUnreadDotChanged = onNavigationShowUnreadDotChanged
            )

            EditorTab.RULES -> PageRulesContent(
                pageRules = state.formState.pageRules,
                onAddPageRule = onAddPageRule,
                onRemovePageRule = onRemovePageRule,
                onUrlEqualsChanged = onPageRuleUrlEqualsChanged,
                onUrlStartsWithChanged = onPageRuleUrlStartsWithChanged,
                onUrlContainsChanged = onPageRuleUrlContainsChanged,
                onTitleChanged = onPageRuleTitleChanged,
                onLoadingTextChanged = onPageRuleLoadingTextChanged,
                onErrorTitleChanged = onPageRuleErrorTitleChanged,
                onErrorMessageChanged = onPageRuleErrorMessageChanged,
                onRetryActionChanged = onPageRuleRetryActionChanged,
                onRetryUrlChanged = onPageRuleRetryUrlChanged,
                onInjectJsChanged = onPageRuleInjectJsChanged,
                onInjectCssChanged = onPageRuleInjectCssChanged,
                onShowTopBarChanged = onPageRuleShowTopBarChanged,
                onShowBottomBarChanged = onPageRuleShowBottomBarChanged,
                onShowDownloadOverlayChanged = onPageRuleShowDownloadOverlayChanged,
                onSuppressFocusHighlightChanged = onPageRuleSuppressFocusHighlightChanged,
                onOpenExternalChanged = onPageRuleOpenExternalChanged
            )

            EditorTab.EVENTS -> PageEventsContent(
                pageEvents = state.formState.pageEvents,
                onAddPageEvent = onAddPageEvent,
                onRemovePageEvent = onRemovePageEvent,
                onPageEventIdChanged = onPageEventIdChanged,
                onPageEventEnabledChanged = onPageEventEnabledChanged,
                onPageEventTriggerChanged = onPageEventTriggerChanged,
                onPageEventUrlEqualsChanged = onPageEventUrlEqualsChanged,
                onPageEventUrlStartsWithChanged = onPageEventUrlStartsWithChanged,
                onPageEventUrlContainsChanged = onPageEventUrlContainsChanged,
                onAddPageEventAction = onAddPageEventAction,
                onRemovePageEventAction = onRemovePageEventAction,
                onPageEventActionTypeChanged = onPageEventActionTypeChanged,
                onPageEventActionValueChanged = onPageEventActionValueChanged,
                onPageEventActionUrlChanged = onPageEventActionUrlChanged,
                onPageEventActionScriptChanged = onPageEventActionScriptChanged
            )

            EditorTab.BRANDING -> BrandingContent(
                state = state.formState,
                onImportIcon = onImportIcon,
                onClearIcon = onClearIcon,
                onImportSplash = onImportSplash,
                onClearSplash = onClearSplash,
                onSplashSkipEnabledChanged = onSplashSkipEnabledChanged,
                onSplashSkipSecondsChanged = onSplashSkipSecondsChanged,
                projectId = state.projectId
            )

            EditorTab.BUILD -> BuildConfigContent(
                state = state.formState,
                onApplicationLabelChanged = onApplicationLabelChanged,
                onVersionNameChanged = onVersionNameChanged,
                onVersionCodeChanged = onVersionCodeChanged,
                onPackageNameChanged = onPackageNameChanged,
                onOutputApkNameTemplateChanged = onOutputApkNameTemplateChanged,
                onSigningStorePasswordChanged = onSigningStorePasswordChanged,
                onSigningKeyAliasChanged = onSigningKeyAliasChanged,
                onSigningKeyPasswordChanged = onSigningKeyPasswordChanged,
                onImportKeystore = onImportKeystore,
                onClearKeystore = onClearKeystore
            )

            EditorTab.JSON -> ConfigJsonContent(
                rawJson = state.rawJson,
                onRawJsonChanged = onRawJsonChanged
            )
        }
    }
}

@Composable
private fun BrandingContent(
    state: ConfigEditorFormState,
    onImportIcon: () -> Unit,
    onClearIcon: () -> Unit,
    onImportSplash: () -> Unit,
    onClearSplash: () -> Unit,
    onSplashSkipEnabledChanged: (Boolean) -> Unit,
    onSplashSkipSecondsChanged: (String) -> Unit,
    projectId: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
        SectionCard(title = stringResource(R.string.config_editor_section_app_icon)) {
            Text(
                text = stringResource(R.string.config_editor_app_icon_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.iconMode == "custom" && state.iconPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ProjectAssetPreview(
                    projectId = projectId,
                    relativePath = state.iconPath,
                    contentScale = ContentScale.Crop,
                    previewWidth = 76.dp,
                    previewHeight = 76.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.config_editor_current_icon, state.iconPath),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onImportIcon) {
                    Text(stringResource(R.string.config_editor_import_icon))
                }
                OutlinedButton(
                    onClick = onClearIcon,
                    enabled = state.iconMode == "custom" && state.iconPath.isNotBlank()
                ) {
                    Text(stringResource(R.string.config_editor_clear))
                }
            }
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_editor_section_splash_screen)) {
            Text(
                text = stringResource(R.string.config_editor_splash_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.splashMode == "custom" && state.splashPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ProjectAssetPreview(
                    projectId = projectId,
                    relativePath = state.splashPath,
                    contentScale = ContentScale.Crop,
                    previewWidth = 132.dp,
                    previewHeight = 220.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.config_editor_current_splash_image, state.splashPath),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
            ToggleRow(
                label = stringResource(R.string.config_editor_enable_skip_button),
                checked = state.splashSkipEnabled,
                onCheckedChange = onSplashSkipEnabledChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_skip_after_seconds),
                value = state.splashSkipSecondsText,
                onValueChange = onSplashSkipSecondsChanged,
                keyboardType = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onImportSplash) {
                    Text(stringResource(R.string.config_editor_import_splash))
                }
                OutlinedButton(
                    onClick = onClearSplash,
                    enabled = state.splashMode == "custom" && state.splashPath.isNotBlank()
                ) {
                    Text(stringResource(R.string.config_editor_clear))
                }
            }
        }
        }
    }
}

@Composable
private fun ProjectAssetPreview(
    projectId: String?,
    relativePath: String,
    contentScale: ContentScale,
    previewWidth: androidx.compose.ui.unit.Dp,
    previewHeight: androidx.compose.ui.unit.Dp,
    fillMaxWidth: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium
) {
    val context = LocalContext.current
    val previewBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        projectId,
        relativePath
    ) {
        value = withContext(Dispatchers.IO) {
            val path = relativePath.trim()
            val id = projectId.orEmpty().trim()
            if (path.isBlank() || id.isBlank()) {
                null
            } else {
                val imageFile = context.filesDir.resolve("projects").resolve(id).resolve(path)
                if (!imageFile.exists() || !imageFile.isFile) {
                    null
                } else {
                    BitmapFactory.decodeFile(imageFile.absolutePath)?.asImageBitmap()
                }
            }
        }
    }

    Surface(
        tonalElevation = 1.dp,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.width(previewWidth))
                .height(previewHeight),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = contentScale
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigFormContent(
    state: ConfigEditorFormState,
    projectId: String?,
    onAppNameChanged: (String) -> Unit,
    onDefaultUrlChanged: (String) -> Unit,
    onTemplateSelected: (TemplateType) -> Unit,
    onUserAgentChanged: (String) -> Unit,
    onBackActionSelected: (String) -> Unit,
    onNightModeChanged: (String) -> Unit,
    onLoadingOverlayChanged: (Boolean) -> Unit,
    onShowPageProgressBarChanged: (Boolean) -> Unit,
    onErrorViewChanged: (Boolean) -> Unit,
    onImmersiveStatusBarChanged: (Boolean) -> Unit,
    onTopBarShowBackButtonChanged: (Boolean) -> Unit,
    onTopBarShowHomeButtonChanged: (Boolean) -> Unit,
    onTopBarShowRefreshButtonChanged: (Boolean) -> Unit,
    onTopBarHomeBehaviorChanged: (String) -> Unit,
    onTopBarHomeScriptChanged: (String) -> Unit,
    onTopBarRefreshBehaviorChanged: (String) -> Unit,
    onTopBarRefreshScriptChanged: (String) -> Unit,
    onTopBarFollowPageTitleChanged: (Boolean) -> Unit,
    onTopBarTitleCenteredChanged: (Boolean) -> Unit,
    onTopBarCornerRadiusChanged: (String) -> Unit,
    onTopBarShadowChanged: (String) -> Unit,
    onTopBarBackIconChanged: (String) -> Unit,
    onTopBarHomeIconChanged: (String) -> Unit,
    onTopBarRefreshIconChanged: (String) -> Unit,
    onBottomBarShowTextLabelsChanged: (Boolean) -> Unit,
    onBottomBarCornerRadiusChanged: (String) -> Unit,
    onBottomBarShadowChanged: (String) -> Unit,
    onBottomBarBadgeColorChanged: (String) -> Unit,
    onBottomBarBadgeTextColorChanged: (String) -> Unit,
    onBottomBarBadgeGravityChanged: (String) -> Unit,
    onBottomBarBadgeMaxCharacterCountChanged: (String) -> Unit,
    onBottomBarBadgeHorizontalOffsetChanged: (String) -> Unit,
    onBottomBarBadgeVerticalOffsetChanged: (String) -> Unit,
    onBottomBarSelectedColorChanged: (String) -> Unit,
    onDrawerHeaderTitleChanged: (String) -> Unit,
    onDrawerHeaderSubtitleChanged: (String) -> Unit,
    onDrawerWidthChanged: (String) -> Unit,
    onDrawerCornerRadiusChanged: (String) -> Unit,
    onDrawerHeaderBackgroundColorChanged: (String) -> Unit,
    onDrawerWallpaperEnabledChanged: (Boolean) -> Unit,
    onImportDrawerWallpaper: () -> Unit,
    onClearDrawerWallpaper: () -> Unit,
    onDrawerWallpaperHeightChanged: (String) -> Unit,
    onDrawerAvatarEnabledChanged: (Boolean) -> Unit,
    onImportDrawerAvatar: () -> Unit,
    onClearDrawerAvatar: () -> Unit,
    onDrawerHeaderImageUrlChanged: (String) -> Unit,
    onDrawerHeaderImageHeightChanged: (String) -> Unit,
    onDrawerHeaderImageScaleModeChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayPresetChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayColorChanged: (String) -> Unit,
    onDrawerMenuIconChanged: (String) -> Unit,
    onDefaultNavigationItemIdChanged: (String) -> Unit,
    onEnableSwipeNavigationChanged: (Boolean) -> Unit,
    onNavigationBackBehaviorChanged: (String) -> Unit,
    onTopBarThemeColorChanged: (String) -> Unit,
    onBottomBarThemeColorChanged: (String) -> Unit,
    onAllowExternalHostsChanged: (Boolean) -> Unit,
    onOpenOtherAppsModeChanged: (String) -> Unit,
    onSslErrorHandlingChanged: (String) -> Unit,
    onAllowedHostsChanged: (String) -> Unit,
    onGlobalJsChanged: (String) -> Unit,
    onGlobalCssChanged: (String) -> Unit,
    onAddNavigationItem: () -> Unit,
    onRemoveNavigationItem: (Int) -> Unit,
    onNavigationIdChanged: (Int, String) -> Unit,
    onNavigationTitleChanged: (Int, String) -> Unit,
    onNavigationUrlChanged: (Int, String) -> Unit,
    onNavigationIconChanged: (Int, String) -> Unit,
    onNavigationSelectedIconChanged: (Int, String) -> Unit,
    onNavigationBadgeCountChanged: (Int, String) -> Unit,
    onNavigationShowUnreadDotChanged: (Int, Boolean) -> Unit
) {
    val selectedTemplateSpec = TemplateCatalog.specFor(state.templateType)
    val context = LocalContext.current
    var selectedNavigationIndex by remember { mutableStateOf(0) }

    LaunchedEffect(state.navigationItems.size) {
        selectedNavigationIndex = selectedNavigationIndex.coerceIn(
            minimumValue = 0,
            maximumValue = (state.navigationItems.lastIndex).coerceAtLeast(0)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
        SectionCard(title = stringResource(R.string.config_section_app)) {
            LabeledTextField(
                label = stringResource(R.string.config_editor_project_name),
                value = state.appName,
                onValueChange = onAppNameChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_field_default_url),
                value = state.defaultUrl,
                onValueChange = onDefaultUrlChanged,
                keyboardType = KeyboardType.Uri
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.config_editor_template), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateCatalog.specs.forEach { spec ->
                    FilterChip(
                        selected = state.templateType == spec.type,
                        onClick = { onTemplateSelected(spec.type) },
                        label = { Text(formatTemplateTypeLabel(context, spec.type)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = templateDescription(context, selectedTemplateSpec.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        item {
            TemplatePreviewCard(
                state = state,
                selectedTemplateSpec = selectedTemplateSpec
            )
        }

        item {
        SectionCard(title = stringResource(R.string.config_section_navigation)) {
            val context = LocalContext.current
            val canAddNavigationItem = state.navigationItems.size < MAX_EDIT_NAVIGATION_ITEMS
            if (selectedTemplateSpec.supportsNavigationItems) {
                Text(
                    text = stringResource(R.string.config_editor_common_icon_names),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.navigationItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.navigationItems.forEachIndexed { index, _ ->
                            FilterChip(
                                selected = index == selectedNavigationIndex,
                                onClick = { selectedNavigationIndex = index },
                                label = {
                                    Text(
                                        state.navigationItems[index].title
                                            .trim()
                                            .ifBlank { stringResource(R.string.config_editor_item_fallback, index + 1) }
                                    )
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                selectedNavigationIndex = state.navigationItems.size
                                onAddNavigationItem()
                            },
                            enabled = canAddNavigationItem
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.config_add_navigation_item))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!canAddNavigationItem) {
                        Text(
                            text = stringResource(
                                R.string.config_editor_navigation_limit_reached,
                                MAX_EDIT_NAVIGATION_ITEMS
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    val selectedItem = state.navigationItems.getOrNull(selectedNavigationIndex)
                    if (selectedItem != null) {
                        NavigationItemEditor(
                            index = selectedNavigationIndex,
                            item = selectedItem,
                            onRemove = {
                                onRemoveNavigationItem(selectedNavigationIndex)
                                val nextSize = (state.navigationItems.size - 1).coerceAtLeast(0)
                                selectedNavigationIndex = selectedNavigationIndex.coerceAtMost(
                                    (nextSize - 1).coerceAtLeast(0)
                                )
                            },
                            onIdChanged = { onNavigationIdChanged(selectedNavigationIndex, it) },
                            onTitleChanged = { onNavigationTitleChanged(selectedNavigationIndex, it) },
                            onUrlChanged = { onNavigationUrlChanged(selectedNavigationIndex, it) },
                            onIconChanged = { onNavigationIconChanged(selectedNavigationIndex, it) },
                            onSelectedIconChanged = { onNavigationSelectedIconChanged(selectedNavigationIndex, it) },
                            onBadgeCountChanged = { onNavigationBadgeCountChanged(selectedNavigationIndex, it) },
                            onShowUnreadDotChanged = { onNavigationShowUnreadDotChanged(selectedNavigationIndex, it) }
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            selectedNavigationIndex = state.navigationItems.size
                            onAddNavigationItem()
                        },
                        enabled = canAddNavigationItem
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.config_add_navigation_item))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.config_editor_no_navigation_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_default_navigation_item_id),
                    value = state.defaultNavigationItemId,
                    onValueChange = onDefaultNavigationItemIdChanged
                )
                Spacer(modifier = Modifier.height(12.dp))
                ToggleRow(
                    label = stringResource(R.string.config_editor_enable_swipe_navigation),
                    checked = state.enableSwipeNavigation,
                    onCheckedChange = onEnableSwipeNavigationChanged
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledDropdownField(
                    label = stringResource(R.string.config_editor_navigation_back_behavior),
                    value = state.navigationBackBehavior,
                    options = listOf("web_history", "reset_on_navigation"),
                    optionLabel = { formatNavigationBackBehaviorLabel(context, it) },
                    onValueChange = onNavigationBackBehaviorChanged
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_editor_navigation_behavior_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.config_editor_shell_no_navigation_items,
                        formatTemplateTypeLabel(context, selectedTemplateSpec.type)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }

        if (!selectedTemplateSpec.supportsTopBar && !selectedTemplateSpec.supportsBottomBar) {
            item {
                SectionCard(title = stringResource(R.string.config_editor_section_shell)) {
                    Text(
                        text = stringResource(
                            R.string.config_editor_shell_no_bar_controls,
                            formatTemplateTypeLabel(context, selectedTemplateSpec.type)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (selectedTemplateSpec.supportsTopBar) {
            item {
                TopBarShellSection(
                    supportsTopBarBackButton = selectedTemplateSpec.supportsTopBarBackButton,
                    state = state,
                    onTopBarShowBackButtonChanged = onTopBarShowBackButtonChanged,
                    onTopBarShowHomeButtonChanged = onTopBarShowHomeButtonChanged,
                    onTopBarShowRefreshButtonChanged = onTopBarShowRefreshButtonChanged,
                    onTopBarHomeBehaviorChanged = onTopBarHomeBehaviorChanged,
                    onTopBarHomeScriptChanged = onTopBarHomeScriptChanged,
                    onTopBarRefreshBehaviorChanged = onTopBarRefreshBehaviorChanged,
                    onTopBarRefreshScriptChanged = onTopBarRefreshScriptChanged,
                    onTopBarFollowPageTitleChanged = onTopBarFollowPageTitleChanged,
                    onTopBarTitleCenteredChanged = onTopBarTitleCenteredChanged,
                    onTopBarCornerRadiusChanged = onTopBarCornerRadiusChanged,
                    onTopBarShadowChanged = onTopBarShadowChanged,
                    onTopBarBackIconChanged = onTopBarBackIconChanged,
                    onTopBarHomeIconChanged = onTopBarHomeIconChanged,
                    onTopBarRefreshIconChanged = onTopBarRefreshIconChanged,
                    onTopBarThemeColorChanged = onTopBarThemeColorChanged
                )
            }
        }

        if (state.templateType == TemplateType.SIDE_DRAWER) {
            item {
                DrawerShellSection(
                    projectId = projectId,
                    state = state,
                    onDrawerHeaderTitleChanged = onDrawerHeaderTitleChanged,
                    onDrawerHeaderSubtitleChanged = onDrawerHeaderSubtitleChanged,
                    onDrawerWidthChanged = onDrawerWidthChanged,
                    onDrawerCornerRadiusChanged = onDrawerCornerRadiusChanged,
                    onDrawerHeaderBackgroundColorChanged = onDrawerHeaderBackgroundColorChanged,
                    onDrawerWallpaperEnabledChanged = onDrawerWallpaperEnabledChanged,
                    onImportDrawerWallpaper = onImportDrawerWallpaper,
                    onClearDrawerWallpaper = onClearDrawerWallpaper,
                    onDrawerWallpaperHeightChanged = onDrawerWallpaperHeightChanged,
                    onDrawerAvatarEnabledChanged = onDrawerAvatarEnabledChanged,
                    onImportDrawerAvatar = onImportDrawerAvatar,
                    onClearDrawerAvatar = onClearDrawerAvatar,
                    onDrawerHeaderImageUrlChanged = onDrawerHeaderImageUrlChanged,
                    onDrawerHeaderImageHeightChanged = onDrawerHeaderImageHeightChanged,
                    onDrawerHeaderImageScaleModeChanged = onDrawerHeaderImageScaleModeChanged,
                    onDrawerHeaderImageOverlayPresetChanged = onDrawerHeaderImageOverlayPresetChanged,
                    onDrawerHeaderImageOverlayColorChanged = onDrawerHeaderImageOverlayColorChanged,
                    onDrawerMenuIconChanged = onDrawerMenuIconChanged
                )
            }
        }

        if (selectedTemplateSpec.supportsBottomBar) {
            item {
                BottomBarShellSection(
                    state = state,
                    navigationChromeStyle = selectedTemplateSpec.navigationChromeStyle
                        ?: TemplateNavigationChromeStyle.BOTTOM_BAR,
                    onBottomBarShowTextLabelsChanged = onBottomBarShowTextLabelsChanged,
                    onBottomBarCornerRadiusChanged = onBottomBarCornerRadiusChanged,
                    onBottomBarShadowChanged = onBottomBarShadowChanged,
                    onBottomBarBadgeColorChanged = onBottomBarBadgeColorChanged,
                    onBottomBarBadgeTextColorChanged = onBottomBarBadgeTextColorChanged,
                    onBottomBarBadgeGravityChanged = onBottomBarBadgeGravityChanged,
                    onBottomBarBadgeMaxCharacterCountChanged = onBottomBarBadgeMaxCharacterCountChanged,
                    onBottomBarBadgeHorizontalOffsetChanged = onBottomBarBadgeHorizontalOffsetChanged,
                    onBottomBarBadgeVerticalOffsetChanged = onBottomBarBadgeVerticalOffsetChanged,
                    onBottomBarSelectedColorChanged = onBottomBarSelectedColorChanged,
                    onBottomBarThemeColorChanged = onBottomBarThemeColorChanged
                )
            }
        }

        item {
        SectionCard(title = stringResource(R.string.config_section_browser)) {
            val context = LocalContext.current
            var userAgentPreset by remember(state.userAgent) {
                mutableStateOf(inferUserAgentPreset(state.userAgent))
            }
            LabeledDropdownField(
                label = stringResource(R.string.config_field_user_agent),
                value = userAgentPreset,
                options = USER_AGENT_PRESET_OPTIONS,
                optionLabel = { formatUserAgentPresetLabel(context, it) },
                onValueChange = { preset ->
                    userAgentPreset = preset
                    onUserAgentChanged(
                        when (preset) {
                            USER_AGENT_PRESET_DEFAULT -> ""
                            USER_AGENT_PRESET_SAFARI_IPHONE -> USER_AGENT_SAFARI_IPHONE
                            USER_AGENT_PRESET_SAFARI_MAC -> USER_AGENT_SAFARI_MAC
                            USER_AGENT_PRESET_CHROME_PC -> USER_AGENT_CHROME_PC
                            USER_AGENT_PRESET_CUSTOM -> state.userAgent
                            else -> state.userAgent
                        }
                    )
                }
            )
            if (userAgentPreset == USER_AGENT_PRESET_CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_custom_user_agent),
                    value = state.userAgent,
                    onValueChange = onUserAgentChanged
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.config_editor_back_action), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.backAction == "go_back_or_exit",
                    onClick = { onBackActionSelected("go_back_or_exit") },
                    label = { Text(stringResource(R.string.config_back_action_exit)) }
                )
                FilterChip(
                    selected = state.backAction == "go_back_or_home",
                    onClick = { onBackActionSelected("go_back_or_home") },
                    label = { Text(stringResource(R.string.config_back_action_home)) }
                )
                FilterChip(
                    selected = state.backAction == "disabled",
                    onClick = { onBackActionSelected("disabled") },
                    label = { Text(stringResource(R.string.config_back_action_disabled)) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LabeledDropdownField(
                label = stringResource(R.string.config_editor_night_mode),
                value = state.nightMode,
                options = listOf("off", "on", "follow_theme"),
                optionLabel = { formatNightModeLabel(context, it) },
                onValueChange = onNightModeChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            ToggleRow(
                label = stringResource(R.string.config_toggle_loading_overlay),
                checked = state.showLoadingOverlay,
                onCheckedChange = onLoadingOverlayChanged
            )
            ToggleRow(
                label = stringResource(R.string.config_editor_show_page_progress_bar),
                checked = state.showPageProgressBar,
                onCheckedChange = onShowPageProgressBarChanged
            )
            ToggleRow(
                label = stringResource(R.string.config_toggle_error_view),
                checked = state.showErrorView,
                onCheckedChange = onErrorViewChanged
            )
            ToggleRow(
                label = stringResource(R.string.config_editor_immersive_status_bar),
                checked = state.immersiveStatusBar,
                onCheckedChange = onImmersiveStatusBarChanged
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_section_security)) {
            val context = LocalContext.current
            ToggleRow(
                label = stringResource(R.string.config_toggle_allow_external_hosts),
                checked = state.allowExternalHosts,
                onCheckedChange = onAllowExternalHostsChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledDropdownField(
                label = stringResource(R.string.config_editor_open_other_apps),
                value = state.openOtherAppsMode,
                options = listOf("ask", "allow", "block"),
                optionLabel = { formatOpenOtherAppsModeLabel(context, it) },
                onValueChange = onOpenOtherAppsModeChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledDropdownField(
                label = stringResource(R.string.config_editor_ssl_error_handling),
                value = state.sslErrorHandling,
                options = listOf(SSL_ERROR_HANDLING_STRICT, SSL_ERROR_HANDLING_IGNORE),
                optionLabel = { formatSslErrorHandlingLabel(context, it) },
                onValueChange = onSslErrorHandlingChanged
            )
            if (state.sslErrorHandling == SSL_ERROR_HANDLING_IGNORE) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_editor_ssl_error_handling_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = state.allowedHostsText,
                onValueChange = onAllowedHostsChanged,
                label = { Text(stringResource(R.string.config_field_allowed_hosts)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.config_editor_host_rules_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_section_inject)) {
            OutlinedTextField(
                value = state.globalJsText,
                onValueChange = onGlobalJsChanged,
                label = { Text(stringResource(R.string.config_field_global_js)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.globalCssText,
                onValueChange = onGlobalCssChanged,
                label = { Text(stringResource(R.string.config_field_global_css)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        }
    }
}



@Composable
private fun TopBarShellSection(
    supportsTopBarBackButton: Boolean,
    state: ConfigEditorFormState,
    onTopBarShowBackButtonChanged: (Boolean) -> Unit,
    onTopBarShowHomeButtonChanged: (Boolean) -> Unit,
    onTopBarShowRefreshButtonChanged: (Boolean) -> Unit,
    onTopBarHomeBehaviorChanged: (String) -> Unit,
    onTopBarHomeScriptChanged: (String) -> Unit,
    onTopBarRefreshBehaviorChanged: (String) -> Unit,
    onTopBarRefreshScriptChanged: (String) -> Unit,
    onTopBarFollowPageTitleChanged: (Boolean) -> Unit,
    onTopBarTitleCenteredChanged: (Boolean) -> Unit,
    onTopBarCornerRadiusChanged: (String) -> Unit,
    onTopBarShadowChanged: (String) -> Unit,
    onTopBarBackIconChanged: (String) -> Unit,
    onTopBarHomeIconChanged: (String) -> Unit,
    onTopBarRefreshIconChanged: (String) -> Unit,
    onTopBarThemeColorChanged: (String) -> Unit
) {
    SectionCard(title = stringResource(R.string.config_editor_section_top_bar)) {
        val context = LocalContext.current
        ToggleRow(
            label = stringResource(R.string.config_editor_top_bar_follow_page_title),
            checked = state.topBarFollowPageTitle,
            onCheckedChange = onTopBarFollowPageTitleChanged
        )
        ToggleRow(
            label = stringResource(R.string.config_editor_top_bar_title_centered),
            checked = state.topBarTitleCentered,
            onCheckedChange = onTopBarTitleCenteredChanged
        )
        ToggleRow(
            label = stringResource(R.string.config_editor_top_bar_show_refresh_button),
            checked = state.topBarShowRefreshButton,
            onCheckedChange = onTopBarShowRefreshButtonChanged
        )
        if (supportsTopBarBackButton) {
            ToggleRow(
                label = stringResource(R.string.config_editor_top_bar_show_back_button),
                checked = state.topBarShowBackButton,
                onCheckedChange = onTopBarShowBackButtonChanged
            )
        }
        ToggleRow(
            label = stringResource(R.string.config_editor_top_bar_show_home_button),
            checked = state.topBarShowHomeButton,
            onCheckedChange = onTopBarShowHomeButtonChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledDropdownField(
            label = stringResource(R.string.config_editor_home_button_behavior),
            value = state.topBarHomeBehavior,
            options = listOf("default_home", "default_navigation_item", "run_js"),
            optionLabel = { formatTopBarHomeBehaviorLabel(context, it) },
            onValueChange = onTopBarHomeBehaviorChanged
        )
        if (state.topBarHomeBehavior == "run_js") {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.topBarHomeScriptText,
                onValueChange = onTopBarHomeScriptChanged,
                label = { Text(stringResource(R.string.config_editor_home_button_javascript)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledDropdownField(
            label = stringResource(R.string.config_editor_refresh_button_behavior),
            value = state.topBarRefreshBehavior,
            options = listOf("reload", "reload_ignore_cache", "run_js"),
            optionLabel = { formatTopBarRefreshBehaviorLabel(context, it) },
            onValueChange = onTopBarRefreshBehaviorChanged
        )
        if (state.topBarRefreshBehavior == "run_js") {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.topBarRefreshScriptText,
                onValueChange = onTopBarRefreshScriptChanged,
                label = { Text(stringResource(R.string.config_editor_refresh_button_javascript)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_top_bar_corner_radius),
            value = state.topBarCornerRadiusText,
            onValueChange = onTopBarCornerRadiusChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_top_bar_shadow),
            value = state.topBarShadowText,
            onValueChange = onTopBarShadowChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        IconPickerField(
            label = stringResource(R.string.config_editor_top_bar_back_icon),
            value = state.topBarBackIcon,
            onValueChange = onTopBarBackIconChanged,
            preferredIds = listOf("back", "close", "home", "menu")
        )
        Spacer(modifier = Modifier.height(12.dp))
        IconPickerField(
            label = stringResource(R.string.config_editor_top_bar_home_icon),
            value = state.topBarHomeIcon,
            onValueChange = onTopBarHomeIconChanged,
            preferredIds = listOf("home", "search", "menu", "profile")
        )
        Spacer(modifier = Modifier.height(12.dp))
        IconPickerField(
            label = stringResource(R.string.config_editor_top_bar_refresh_icon),
            value = state.topBarRefreshIcon,
            onValueChange = onTopBarRefreshIconChanged,
            preferredIds = listOf("refresh", "search", "settings")
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(R.string.config_editor_top_bar_theme_color),
            value = state.topBarThemeColor,
            onValueChange = onTopBarThemeColorChanged
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_editor_optional_hex_example_teal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_editor_action_icon_names),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DrawerShellSection(
    projectId: String?,
    state: ConfigEditorFormState,
    onDrawerHeaderTitleChanged: (String) -> Unit,
    onDrawerHeaderSubtitleChanged: (String) -> Unit,
    onDrawerWidthChanged: (String) -> Unit,
    onDrawerCornerRadiusChanged: (String) -> Unit,
    onDrawerHeaderBackgroundColorChanged: (String) -> Unit,
    onDrawerWallpaperEnabledChanged: (Boolean) -> Unit,
    onImportDrawerWallpaper: () -> Unit,
    onClearDrawerWallpaper: () -> Unit,
    onDrawerWallpaperHeightChanged: (String) -> Unit,
    onDrawerAvatarEnabledChanged: (Boolean) -> Unit,
    onImportDrawerAvatar: () -> Unit,
    onClearDrawerAvatar: () -> Unit,
    onDrawerHeaderImageUrlChanged: (String) -> Unit,
    onDrawerHeaderImageHeightChanged: (String) -> Unit,
    onDrawerHeaderImageScaleModeChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayPresetChanged: (String) -> Unit,
    onDrawerHeaderImageOverlayColorChanged: (String) -> Unit,
    onDrawerMenuIconChanged: (String) -> Unit
) {
    SectionCard(title = stringResource(R.string.config_editor_section_drawer)) {
        LabeledTextField(
            label = stringResource(R.string.config_editor_drawer_header_title),
            value = state.drawerHeaderTitle,
            onValueChange = onDrawerHeaderTitleChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_drawer_header_subtitle),
            value = state.drawerHeaderSubtitle,
            onValueChange = onDrawerHeaderSubtitleChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_drawer_width),
            value = state.drawerWidthText,
            onValueChange = onDrawerWidthChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_drawer_corner_radius),
            value = state.drawerCornerRadiusText,
            onValueChange = onDrawerCornerRadiusChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_editor_drawer_corner_radius_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(R.string.config_editor_drawer_header_background_color),
            value = state.drawerHeaderBackgroundColor,
            onValueChange = onDrawerHeaderBackgroundColorChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        ToggleRow(
            label = stringResource(R.string.config_editor_enable_drawer_wallpaper),
            checked = state.drawerWallpaperEnabled,
            onCheckedChange = onDrawerWallpaperEnabledChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        DrawerHeaderPreview(
            projectId = projectId,
            state = state
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onImportDrawerWallpaper) {
                Text(stringResource(R.string.config_editor_choose_wallpaper))
            }
            OutlinedButton(
                onClick = onClearDrawerWallpaper,
                enabled = state.drawerWallpaperPath.isNotBlank()
            ) {
                Text(stringResource(R.string.config_editor_clear_wallpaper))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_wallpaper_height),
            value = state.drawerWallpaperHeightText,
            onValueChange = onDrawerWallpaperHeightChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        ToggleRow(
            label = stringResource(R.string.config_editor_enable_drawer_avatar),
            checked = state.drawerAvatarEnabled,
            onCheckedChange = onDrawerAvatarEnabledChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onImportDrawerAvatar) {
                Text(stringResource(R.string.config_editor_choose_avatar))
            }
            OutlinedButton(
                onClick = onClearDrawerAvatar,
                enabled = state.drawerAvatarPath.isNotBlank()
            ) {
                Text(stringResource(R.string.config_editor_clear_avatar))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_editor_wallpaper_avatar_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        IconPickerField(
            label = stringResource(R.string.config_editor_drawer_menu_icon),
            value = state.drawerMenuIcon,
            onValueChange = onDrawerMenuIconChanged,
            preferredIds = listOf("menu", "back", "home", "profile")
        )
    }
}

@Composable
private fun BottomBarShellSection(
    state: ConfigEditorFormState,
    navigationChromeStyle: TemplateNavigationChromeStyle,
    onBottomBarShowTextLabelsChanged: (Boolean) -> Unit,
    onBottomBarCornerRadiusChanged: (String) -> Unit,
    onBottomBarShadowChanged: (String) -> Unit,
    onBottomBarBadgeColorChanged: (String) -> Unit,
    onBottomBarBadgeTextColorChanged: (String) -> Unit,
    onBottomBarBadgeGravityChanged: (String) -> Unit,
    onBottomBarBadgeMaxCharacterCountChanged: (String) -> Unit,
    onBottomBarBadgeHorizontalOffsetChanged: (String) -> Unit,
    onBottomBarBadgeVerticalOffsetChanged: (String) -> Unit,
    onBottomBarSelectedColorChanged: (String) -> Unit,
    onBottomBarThemeColorChanged: (String) -> Unit
) {
    val isTopTabs = navigationChromeStyle == TemplateNavigationChromeStyle.TOP_TABS
    SectionCard(
        title = stringResource(
            if (isTopTabs) R.string.config_editor_section_tabs else R.string.config_editor_section_bottom_bar
        )
    ) {
        val context = LocalContext.current
        if (!isTopTabs) {
            ToggleRow(
                label = stringResource(R.string.config_editor_bottom_bar_show_text_labels),
                checked = state.bottomBarShowTextLabels,
                onCheckedChange = onBottomBarShowTextLabelsChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        LabeledTextField(
            label = stringResource(
                if (isTopTabs) R.string.config_editor_tabs_corner_radius
                else R.string.config_editor_bottom_bar_corner_radius
            ),
            value = state.bottomBarCornerRadiusText,
            onValueChange = onBottomBarCornerRadiusChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(
                if (isTopTabs) R.string.config_editor_tabs_shadow
                else R.string.config_editor_bottom_bar_shadow
            ),
            value = state.bottomBarShadowText,
            onValueChange = onBottomBarShadowChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(R.string.config_editor_bottom_bar_badge_color),
            value = state.bottomBarBadgeColor,
            onValueChange = onBottomBarBadgeColorChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(R.string.config_editor_bottom_bar_badge_text_color),
            value = state.bottomBarBadgeTextColor,
            onValueChange = onBottomBarBadgeTextColorChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledDropdownField(
            label = stringResource(R.string.config_editor_badge_gravity),
            value = state.bottomBarBadgeGravity,
            options = listOf("top_end", "top_start", "bottom_end", "bottom_start"),
            optionLabel = { formatBadgeGravityLabel(context, it) },
            onValueChange = onBottomBarBadgeGravityChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_badge_max_characters),
            value = state.bottomBarBadgeMaxCharacterCountText,
            onValueChange = onBottomBarBadgeMaxCharacterCountChanged,
            keyboardType = KeyboardType.Number
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_badge_horizontal_offset),
            value = state.bottomBarBadgeHorizontalOffsetText,
            onValueChange = onBottomBarBadgeHorizontalOffsetChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        LabeledTextField(
            label = stringResource(R.string.config_editor_badge_vertical_offset),
            value = state.bottomBarBadgeVerticalOffsetText,
            onValueChange = onBottomBarBadgeVerticalOffsetChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(
                if (isTopTabs) R.string.config_editor_tabs_selected_color
                else R.string.config_editor_bottom_bar_selected_color
            ),
            value = state.bottomBarSelectedColor,
            onValueChange = onBottomBarSelectedColorChanged
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorPickerField(
            label = stringResource(
                if (isTopTabs) R.string.config_editor_tabs_theme_color
                else R.string.config_editor_bottom_bar_theme_color
            ),
            value = state.bottomBarThemeColor,
            onValueChange = onBottomBarThemeColorChanged
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isTopTabs) R.string.config_editor_tabs_desc
                else R.string.config_editor_bottom_bar_desc
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
private fun BuildConfigContent(
    state: ConfigEditorFormState,
    onApplicationLabelChanged: (String) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onOutputApkNameTemplateChanged: (String) -> Unit,
    onSigningStorePasswordChanged: (String) -> Unit,
    onSigningKeyAliasChanged: (String) -> Unit,
    onSigningKeyPasswordChanged: (String) -> Unit,
    onImportKeystore: () -> Unit,
    onClearKeystore: () -> Unit
) {
    val context = LocalContext.current
    val outputApkPreview = remember(
        context,
        state.applicationLabel,
        state.versionName,
        state.versionCodeText,
        state.packageName,
        state.appName,
        state.outputApkNameTemplate
    ) {
        resolveOutputApkPreviewName(context, state)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
        SectionCard(title = stringResource(R.string.config_editor_section_app_identity)) {
            Text(
                text = stringResource(R.string.config_editor_app_identity_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_application_name),
                value = state.applicationLabel,
                onValueChange = onApplicationLabelChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_version_name),
                value = state.versionName,
                onValueChange = onVersionNameChanged
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_version_code),
                value = state.versionCodeText,
                onValueChange = onVersionCodeChanged,
                keyboardType = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_package_name),
                value = state.packageName,
                onValueChange = onPackageNameChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.config_editor_package_name_blank_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.config_editor_package_name_rules_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_editor_section_pack_output)) {
            Text(
                text = stringResource(R.string.config_editor_pack_output_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledTextField(
                label = stringResource(R.string.config_editor_apk_file_name_template),
                value = state.outputApkNameTemplate,
                onValueChange = onOutputApkNameTemplateChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.config_editor_apk_template_blank_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.config_editor_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = outputApkPreview,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_editor_section_signing)) {
            Text(
                text = stringResource(R.string.config_editor_signing_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (state.signingMode == "custom" && state.signingKeystorePath.isNotBlank()) {
                Text(
                    text = stringResource(R.string.config_editor_current_keystore, state.signingKeystorePath),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    text = stringResource(R.string.config_editor_current_signer_default),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onImportKeystore) {
                    Text(stringResource(R.string.config_editor_import_keystore))
                }
                if (state.signingMode == "custom" && state.signingKeystorePath.isNotBlank()) {
                    OutlinedButton(onClick = onClearKeystore) {
                        Text(stringResource(R.string.config_editor_use_default_signer))
                    }
                }
            }
            if (state.signingMode == "custom" && state.signingKeystorePath.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_store_password),
                    value = state.signingStorePassword,
                    onValueChange = onSigningStorePasswordChanged,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_key_alias),
                    value = state.signingKeyAlias,
                    onValueChange = onSigningKeyAliasChanged
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_key_password),
                    value = state.signingKeyPassword,
                    onValueChange = onSigningKeyPasswordChanged,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_editor_key_password_blank_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageRulesContent(
    pageRules: List<PageRuleForm>,
    onAddPageRule: () -> Unit,
    onRemovePageRule: (Int) -> Unit,
    onUrlEqualsChanged: (Int, String) -> Unit,
    onUrlStartsWithChanged: (Int, String) -> Unit,
    onUrlContainsChanged: (Int, String) -> Unit,
    onTitleChanged: (Int, String) -> Unit,
    onLoadingTextChanged: (Int, String) -> Unit,
    onErrorTitleChanged: (Int, String) -> Unit,
    onErrorMessageChanged: (Int, String) -> Unit,
    onRetryActionChanged: (Int, String) -> Unit,
    onRetryUrlChanged: (Int, String) -> Unit,
    onInjectJsChanged: (Int, String) -> Unit,
    onInjectCssChanged: (Int, String) -> Unit,
    onShowTopBarChanged: (Int, RuleToggleState) -> Unit,
    onShowBottomBarChanged: (Int, RuleToggleState) -> Unit,
    onShowDownloadOverlayChanged: (Int, RuleToggleState) -> Unit,
    onSuppressFocusHighlightChanged: (Int, RuleToggleState) -> Unit,
    onOpenExternalChanged: (Int, RuleToggleState) -> Unit
) {
    var selectedPageRuleIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(pageRules.size) {
        selectedPageRuleIndex = selectedPageRuleIndex.coerceIn(
            minimumValue = 0,
            maximumValue = pageRules.lastIndex.coerceAtLeast(0)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
        SectionCard(title = stringResource(R.string.config_editor_section_page_rules)) {
            Text(
                text = stringResource(R.string.config_editor_page_rules_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_editor_section_rule_selector)) {
            if (pageRules.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pageRules.forEachIndexed { index, rule ->
                        FilterChip(
                            selected = index == selectedPageRuleIndex,
                            onClick = { selectedPageRuleIndex = index },
                            label = { Text(resolvePageRuleChipLabel(context, rule, index)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    text = stringResource(R.string.config_editor_no_page_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onAddPageRule) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.config_editor_add_page_rule))
            }
        }
        }

        pageRules.getOrNull(selectedPageRuleIndex)?.let { rule ->
            item {
            SectionCard(title = stringResource(R.string.config_editor_rule_title, selectedPageRuleIndex + 1)) {
                PageRuleEditor(
                    rule = rule,
                    onRemove = {
                        onRemovePageRule(selectedPageRuleIndex)
                        val nextSize = (pageRules.size - 1).coerceAtLeast(0)
                        selectedPageRuleIndex = selectedPageRuleIndex.coerceAtMost(
                            (nextSize - 1).coerceAtLeast(0)
                        )
                    },
                    onUrlEqualsChanged = { onUrlEqualsChanged(selectedPageRuleIndex, it) },
                    onUrlStartsWithChanged = { onUrlStartsWithChanged(selectedPageRuleIndex, it) },
                    onUrlContainsChanged = { onUrlContainsChanged(selectedPageRuleIndex, it) },
                    onTitleChanged = { onTitleChanged(selectedPageRuleIndex, it) },
                    onLoadingTextChanged = { onLoadingTextChanged(selectedPageRuleIndex, it) },
                    onErrorTitleChanged = { onErrorTitleChanged(selectedPageRuleIndex, it) },
                    onErrorMessageChanged = { onErrorMessageChanged(selectedPageRuleIndex, it) },
                    onRetryActionChanged = { onRetryActionChanged(selectedPageRuleIndex, it) },
                    onRetryUrlChanged = { onRetryUrlChanged(selectedPageRuleIndex, it) },
                    onInjectJsChanged = { onInjectJsChanged(selectedPageRuleIndex, it) },
                    onInjectCssChanged = { onInjectCssChanged(selectedPageRuleIndex, it) },
                    onShowTopBarChanged = { onShowTopBarChanged(selectedPageRuleIndex, it) },
                    onShowBottomBarChanged = { onShowBottomBarChanged(selectedPageRuleIndex, it) },
                    onShowDownloadOverlayChanged = { onShowDownloadOverlayChanged(selectedPageRuleIndex, it) },
                    onSuppressFocusHighlightChanged = { onSuppressFocusHighlightChanged(selectedPageRuleIndex, it) },
                    onOpenExternalChanged = { onOpenExternalChanged(selectedPageRuleIndex, it) }
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageEventsContent(
    pageEvents: List<PageEventForm>,
    onAddPageEvent: () -> Unit,
    onRemovePageEvent: (Int) -> Unit,
    onPageEventIdChanged: (Int, String) -> Unit,
    onPageEventEnabledChanged: (Int, Boolean) -> Unit,
    onPageEventTriggerChanged: (Int, String) -> Unit,
    onPageEventUrlEqualsChanged: (Int, String) -> Unit,
    onPageEventUrlStartsWithChanged: (Int, String) -> Unit,
    onPageEventUrlContainsChanged: (Int, String) -> Unit,
    onAddPageEventAction: (Int) -> Unit,
    onRemovePageEventAction: (Int, Int) -> Unit,
    onPageEventActionTypeChanged: (Int, Int, String) -> Unit,
    onPageEventActionValueChanged: (Int, Int, String) -> Unit,
    onPageEventActionUrlChanged: (Int, Int, String) -> Unit,
    onPageEventActionScriptChanged: (Int, Int, String) -> Unit
) {
    var selectedEventIndex by remember { mutableStateOf(0) }
    var selectedActionIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(pageEvents.size) {
        selectedEventIndex = selectedEventIndex.coerceIn(0, pageEvents.lastIndex.coerceAtLeast(0))
    }
    LaunchedEffect(pageEvents.getOrNull(selectedEventIndex)?.actions?.size ?: 0) {
        val selectedEvent = pageEvents.getOrNull(selectedEventIndex)
        selectedActionIndex = selectedActionIndex.coerceIn(
            0,
            (selectedEvent?.actions?.lastIndex ?: -1).coerceAtLeast(0)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
        SectionCard(title = stringResource(R.string.config_editor_section_page_events)) {
            Text(
                text = stringResource(R.string.config_editor_page_events_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.config_editor_page_events_targets_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        item {
        SectionCard(title = stringResource(R.string.config_editor_section_event_selector)) {
            if (pageEvents.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pageEvents.forEachIndexed { index, event ->
                        FilterChip(
                            selected = index == selectedEventIndex,
                            onClick = { selectedEventIndex = index },
                            label = { Text(resolvePageEventChipLabel(context, event, index)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    text = stringResource(R.string.config_editor_no_page_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onAddPageEvent) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.config_editor_add_page_event))
            }
        }
        }

        pageEvents.getOrNull(selectedEventIndex)?.let { event ->
            item {
            SectionCard(title = stringResource(R.string.config_editor_event_title, selectedEventIndex + 1)) {
                ToggleRow(
                    label = stringResource(R.string.config_editor_enabled),
                    checked = event.enabled,
                    onCheckedChange = { onPageEventEnabledChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_event_id),
                    value = event.id,
                    onValueChange = { onPageEventIdChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledDropdownField(
                    label = stringResource(R.string.config_editor_trigger),
                    value = event.trigger,
                    options = pageEventTriggers,
                    optionLabel = { formatPageEventTriggerLabel(context, it) },
                    onValueChange = { onPageEventTriggerChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_url_equals),
                    value = event.urlEquals,
                    onValueChange = { onPageEventUrlEqualsChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_url_starts_with),
                    value = event.urlStartsWith,
                    onValueChange = { onPageEventUrlStartsWithChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledTextField(
                    label = stringResource(R.string.config_editor_url_contains),
                    value = event.urlContains,
                    onValueChange = { onPageEventUrlContainsChanged(selectedEventIndex, it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_editor_event_match_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.config_editor_actions),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        onRemovePageEvent(selectedEventIndex)
                        val nextSize = (pageEvents.size - 1).coerceAtLeast(0)
                        selectedEventIndex = selectedEventIndex.coerceAtMost((nextSize - 1).coerceAtLeast(0))
                        selectedActionIndex = 0
                    }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.config_editor_remove_event))
                    }
                }
                if (event.actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        event.actions.forEachIndexed { actionIndex, action ->
                            FilterChip(
                                selected = actionIndex == selectedActionIndex,
                                onClick = { selectedActionIndex = actionIndex },
                                label = { Text(resolvePageEventActionChipLabel(context, action, actionIndex)) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    onAddPageEventAction(selectedEventIndex)
                    selectedActionIndex = event.actions.size
                }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.config_editor_add_action))
                }
                event.actions.getOrNull(selectedActionIndex)?.let { action ->
                    Spacer(modifier = Modifier.height(12.dp))
                    PageEventActionEditor(
                        action = action,
                        actionIndex = selectedActionIndex,
                        onRemove = {
                            onRemovePageEventAction(selectedEventIndex, selectedActionIndex)
                            val nextSize = (event.actions.size - 1).coerceAtLeast(0)
                            selectedActionIndex = selectedActionIndex.coerceAtMost((nextSize - 1).coerceAtLeast(0))
                        },
                        onTypeChanged = { onPageEventActionTypeChanged(selectedEventIndex, selectedActionIndex, it) },
                        onValueChanged = { onPageEventActionValueChanged(selectedEventIndex, selectedActionIndex, it) },
                        onUrlChanged = { onPageEventActionUrlChanged(selectedEventIndex, selectedActionIndex, it) },
                        onScriptChanged = { onPageEventActionScriptChanged(selectedEventIndex, selectedActionIndex, it) }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun PageRuleEditor(
    rule: PageRuleForm,
    onRemove: () -> Unit,
    onUrlEqualsChanged: (String) -> Unit,
    onUrlStartsWithChanged: (String) -> Unit,
    onUrlContainsChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingTextChanged: (String) -> Unit,
    onErrorTitleChanged: (String) -> Unit,
    onErrorMessageChanged: (String) -> Unit,
    onRetryActionChanged: (String) -> Unit,
    onRetryUrlChanged: (String) -> Unit,
    onInjectJsChanged: (String) -> Unit,
    onInjectCssChanged: (String) -> Unit,
    onShowTopBarChanged: (RuleToggleState) -> Unit,
    onShowBottomBarChanged: (RuleToggleState) -> Unit,
    onShowDownloadOverlayChanged: (RuleToggleState) -> Unit,
    onSuppressFocusHighlightChanged: (RuleToggleState) -> Unit,
    onOpenExternalChanged: (RuleToggleState) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.config_editor_match),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.config_editor_remove_rule)
                )
            }
        }
        Text(
            text = stringResource(R.string.config_editor_rule_match_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LabeledTextField(label = stringResource(R.string.config_editor_url_equals), value = rule.urlEquals, onValueChange = onUrlEqualsChanged)
        LabeledTextField(label = stringResource(R.string.config_editor_url_starts_with), value = rule.urlStartsWith, onValueChange = onUrlStartsWithChanged)
        LabeledTextField(label = stringResource(R.string.config_editor_url_contains), value = rule.urlContains, onValueChange = onUrlContainsChanged)

        HorizontalDivider()
        Text(text = stringResource(R.string.config_editor_behavior), style = MaterialTheme.typography.titleMedium)
        NullableToggleSelector(
            label = stringResource(R.string.config_template_top_bar),
            state = rule.showTopBar,
            enabledLabel = stringResource(R.string.config_editor_show),
            disabledLabel = stringResource(R.string.config_editor_hide),
            onStateChanged = onShowTopBarChanged
        )
        NullableToggleSelector(
            label = stringResource(R.string.config_template_bottom_bar),
            state = rule.showBottomBar,
            enabledLabel = stringResource(R.string.config_editor_show),
            disabledLabel = stringResource(R.string.config_editor_hide),
            onStateChanged = onShowBottomBarChanged
        )
        NullableToggleSelector(
            label = stringResource(R.string.config_editor_download_overlay),
            state = rule.showDownloadOverlay,
            enabledLabel = stringResource(R.string.config_editor_show),
            disabledLabel = stringResource(R.string.config_editor_hide),
            onStateChanged = onShowDownloadOverlayChanged
        )
        NullableToggleSelector(
            label = stringResource(R.string.config_editor_focus_highlight),
            state = rule.suppressFocusHighlight,
            enabledLabel = stringResource(R.string.config_editor_suppress),
            disabledLabel = stringResource(R.string.config_editor_keep),
            onStateChanged = onSuppressFocusHighlightChanged
        )
        NullableToggleSelector(
            label = stringResource(R.string.config_editor_open_mode),
            state = rule.openExternal,
            enabledLabel = stringResource(R.string.config_editor_external),
            disabledLabel = stringResource(R.string.config_editor_in_app),
            onStateChanged = onOpenExternalChanged
        )

        HorizontalDivider()
        Text(text = stringResource(R.string.config_editor_content), style = MaterialTheme.typography.titleMedium)
        LabeledTextField(label = stringResource(R.string.config_editor_title_override), value = rule.title, onValueChange = onTitleChanged)
        LabeledTextField(label = stringResource(R.string.config_editor_loading_text), value = rule.loadingText, onValueChange = onLoadingTextChanged)
        LabeledTextField(label = stringResource(R.string.config_editor_error_title), value = rule.errorTitle, onValueChange = onErrorTitleChanged)
        OutlinedTextField(
            value = rule.errorMessage,
            onValueChange = onErrorMessageChanged,
            label = { Text(stringResource(R.string.config_editor_error_message)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        HorizontalDivider()
        Text(text = stringResource(R.string.config_editor_retry_action), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = rule.errorRetryAction.isBlank(),
                onClick = { onRetryActionChanged("") },
                label = { Text(stringResource(R.string.config_editor_inherit)) }
            )
            FilterChip(
                selected = rule.errorRetryAction == "reload",
                onClick = { onRetryActionChanged("reload") },
                label = { Text(stringResource(R.string.config_editor_refresh_button_behavior_reload)) }
            )
            FilterChip(
                selected = rule.errorRetryAction == "go_home",
                onClick = { onRetryActionChanged("go_home") },
                label = { Text(stringResource(R.string.config_editor_go_home)) }
            )
            FilterChip(
                selected = rule.errorRetryAction == "load_url",
                onClick = { onRetryActionChanged("load_url") },
                label = { Text(stringResource(R.string.config_editor_load_url)) }
            )
        }
        LabeledTextField(
            label = stringResource(R.string.config_editor_retry_url),
            value = rule.errorRetryUrl,
            onValueChange = onRetryUrlChanged,
            keyboardType = KeyboardType.Uri
        )

        HorizontalDivider()
        Text(text = stringResource(R.string.config_editor_rule_inject), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = rule.injectJsText,
            onValueChange = onInjectJsChanged,
            label = { Text(stringResource(R.string.config_editor_rule_js_blocks)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = rule.injectCssText,
            onValueChange = onInjectCssChanged,
            label = { Text(stringResource(R.string.config_editor_rule_css_blocks)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun PageEventActionEditor(
    action: PageEventActionForm,
    actionIndex: Int,
    onRemove: () -> Unit,
    onTypeChanged: (String) -> Unit,
    onValueChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onScriptChanged: (String) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.config_editor_selected_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.config_editor_remove_action)
                )
            }
        }
        LabeledDropdownField(
            label = stringResource(R.string.config_editor_action_type),
            value = action.type,
            options = pageEventActionTypes,
            optionLabel = { formatPageEventActionTypeLabel(context, it) },
            onValueChange = onTypeChanged
        )
        when (action.type) {
            "toast", "copy_to_clipboard" -> {
                LabeledTextField(
                    label = stringResource(R.string.config_editor_text_value),
                    value = action.value,
                    onValueChange = onValueChanged
                )
            }

            "load_url", "open_external" -> {
                LabeledTextField(
                    label = stringResource(R.string.config_editor_target_url),
                    value = action.url.ifBlank { action.value },
                    onValueChange = onUrlChanged,
                    keyboardType = KeyboardType.Uri
                )
            }

            "run_js" -> {
                OutlinedTextField(
                    value = action.script.ifBlank { action.value },
                    onValueChange = onScriptChanged,
                    label = { Text(stringResource(R.string.config_editor_custom_javascript)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.config_editor_action_no_extra_fields),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = stringResource(R.string.config_editor_action_template_variables),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigJsonContent(
    rawJson: String,
    onRawJsonChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = rawJson,
            onValueChange = onRawJsonChanged,
            label = { Text(stringResource(R.string.config_field_raw_json)) },
            modifier = Modifier.fillMaxSize(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun TemplatePreviewCard(
    state: ConfigEditorFormState,
    selectedTemplateSpec: RuntimeShellTemplateSpec
) {
    val context = LocalContext.current
    val topBarColor = parsePreviewColor(
        state.topBarThemeColor,
        MaterialTheme.colorScheme.primary
    )
    val bottomBarColor = parsePreviewColor(
        state.bottomBarThemeColor,
        MaterialTheme.colorScheme.surfaceVariant
    )
    val bottomBarSelectedColor = parsePreviewColor(
        state.bottomBarSelectedColor,
        contentColorFor(bottomBarColor)
    )
    val selectedNavigationId = state.defaultNavigationItemId.takeIf { it.isNotBlank() }
        ?: state.navigationItems.firstOrNull()?.id.orEmpty()
    val navigationItems = state.navigationItems.take(4).ifEmpty {
        listOf(
            NavigationItemForm(id = "home", title = context.getString(R.string.config_editor_preview_nav_home), url = "", icon = "home"),
            NavigationItemForm(id = "feed", title = context.getString(R.string.config_editor_preview_nav_feed), url = "", icon = "news"),
            NavigationItemForm(id = "me", title = context.getString(R.string.config_editor_preview_nav_me), url = "", icon = "profile")
        )
    }

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.config_editor_template_preview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF0F172A)
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFFF8FAFC)
                    ) {
                        when (selectedTemplateSpec.type) {
                            TemplateType.BROWSER -> MiniBrowserPreview(state)
                            TemplateType.IMMERSIVE_SINGLE_PAGE -> MiniImmersivePreview(state)
                            TemplateType.TOP_BAR -> MiniTopBarPreview(state, topBarColor)
                            TemplateType.BOTTOM_BAR -> MiniBottomBarPreview(
                                state = state,
                                bottomBarColor = bottomBarColor,
                                bottomBarSelectedColor = bottomBarSelectedColor,
                                navigationItems = navigationItems,
                                selectedNavigationId = selectedNavigationId
                            )
                            TemplateType.SIDE_DRAWER -> MiniSideDrawerPreview(
                                state = state,
                                topBarColor = topBarColor,
                                navigationItems = navigationItems,
                                selectedNavigationId = selectedNavigationId
                            )
                            TemplateType.TOP_BAR_TABS -> MiniTopTabsPreview(
                                state = state,
                                topBarColor = topBarColor,
                                tabsColor = bottomBarColor,
                                tabsSelectedColor = bottomBarSelectedColor,
                                navigationItems = navigationItems,
                                selectedNavigationId = selectedNavigationId
                            )
                            TemplateType.TOP_BAR_BOTTOM_TABS -> MiniTopBarBottomTabsPreview(
                                state = state,
                                topBarColor = topBarColor,
                                bottomBarColor = bottomBarColor,
                                bottomBarSelectedColor = bottomBarSelectedColor,
                                navigationItems = navigationItems,
                                selectedNavigationId = selectedNavigationId
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewPill(formatTemplateTypeLabel(context, selectedTemplateSpec.type))
                if (selectedTemplateSpec.supportsTopBar) PreviewPill(stringResource(R.string.config_editor_preview_top_bar))
                when (selectedTemplateSpec.navigationChromeStyle) {
                    TemplateNavigationChromeStyle.TOP_TABS -> {
                        PreviewPill(stringResource(R.string.config_editor_preview_tabs))
                    }

                    TemplateNavigationChromeStyle.BOTTOM_BAR -> {
                        if (selectedTemplateSpec.supportsBottomBar) {
                            PreviewPill(stringResource(R.string.config_editor_preview_bottom_bar))
                        }
                    }

                    null -> {
                        if (selectedTemplateSpec.supportsBottomBar) {
                            PreviewPill(stringResource(R.string.config_editor_preview_bottom_bar))
                        }
                    }
                }
                if (selectedTemplateSpec.type == TemplateType.SIDE_DRAWER) PreviewPill(stringResource(R.string.config_editor_preview_drawer))
                if (selectedTemplateSpec.supportsNavigationItems) {
                    PreviewPill(stringResource(R.string.config_editor_preview_nav_items, navigationItems.size))
                }
                if (selectedTemplateSpec.type == TemplateType.IMMERSIVE_SINGLE_PAGE) {
                    PreviewPill(stringResource(R.string.config_editor_preview_immersive))
                }
            }
            Text(
                text = stringResource(R.string.config_editor_preview_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniBrowserPreview(state: ConfigEditorFormState) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(immersive = false)
        MiniWebCanvas(
            modifier = Modifier.weight(1f),
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_web_project) },
            accent = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MiniImmersivePreview(state: ConfigEditorFormState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
    ) {
        MiniWebCanvas(
            modifier = Modifier.fillMaxSize(),
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_immersive_page) },
            accent = Color(0xFF38BDF8),
            chromeLess = true
        )
        PreviewPill(
            text = stringResource(R.string.config_editor_preview_immersive),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }
}

@Composable
private fun MiniTopBarPreview(
    state: ConfigEditorFormState,
    topBarColor: Color
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(
            immersive = state.immersiveStatusBar,
            background = topBarColor
        )
        MiniTopBar(
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_top_bar) },
            showBack = state.topBarShowBackButton,
            showHome = state.topBarShowHomeButton,
            showRefresh = state.topBarShowRefreshButton,
            centered = state.topBarTitleCentered,
            background = topBarColor,
            backIconName = state.topBarBackIcon,
            homeIconName = state.topBarHomeIcon,
            refreshIconName = state.topBarRefreshIcon
        )
        MiniWebCanvas(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.config_editor_preview_title_current_page),
            accent = topBarColor
        )
    }
}

@Composable
private fun MiniBottomBarPreview(
    state: ConfigEditorFormState,
    bottomBarColor: Color,
    bottomBarSelectedColor: Color,
    navigationItems: List<NavigationItemForm>,
    selectedNavigationId: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(immersive = false)
        MiniWebCanvas(
            modifier = Modifier.weight(1f),
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_bottom_bar) },
            accent = bottomBarColor
        )
        MiniBottomBar(
            background = bottomBarColor,
            selectedColor = bottomBarSelectedColor,
            items = navigationItems,
            selectedNavigationId = selectedNavigationId,
            showLabels = state.bottomBarShowTextLabels
        )
    }
}

@Composable
private fun MiniTopBarBottomTabsPreview(
    state: ConfigEditorFormState,
    topBarColor: Color,
    bottomBarColor: Color,
    bottomBarSelectedColor: Color,
    navigationItems: List<NavigationItemForm>,
    selectedNavigationId: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(
            immersive = state.immersiveStatusBar,
            background = topBarColor
        )
        MiniTopBar(
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_hybrid_shell) },
            showBack = state.topBarShowBackButton,
            showHome = state.topBarShowHomeButton,
            showRefresh = state.topBarShowRefreshButton,
            centered = state.topBarTitleCentered,
            background = topBarColor,
            backIconName = state.topBarBackIcon,
            homeIconName = state.topBarHomeIcon,
            refreshIconName = state.topBarRefreshIcon
        )
        MiniWebCanvas(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.config_editor_preview_title_selected_tab_page),
            accent = topBarColor
        )
        MiniBottomBar(
            background = bottomBarColor,
            selectedColor = bottomBarSelectedColor,
            items = navigationItems,
            selectedNavigationId = selectedNavigationId,
            showLabels = state.bottomBarShowTextLabels
        )
    }
}

@Composable
private fun MiniTopTabsPreview(
    state: ConfigEditorFormState,
    topBarColor: Color,
    tabsColor: Color,
    tabsSelectedColor: Color,
    navigationItems: List<NavigationItemForm>,
    selectedNavigationId: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(
            immersive = state.immersiveStatusBar,
            background = topBarColor
        )
        MiniTopBar(
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_top_tabs_shell) },
            showBack = state.topBarShowBackButton,
            showHome = state.topBarShowHomeButton,
            showRefresh = state.topBarShowRefreshButton,
            centered = state.topBarTitleCentered,
            background = topBarColor,
            backIconName = state.topBarBackIcon,
            homeIconName = state.topBarHomeIcon,
            refreshIconName = state.topBarRefreshIcon
        )
        MiniTabsBar(
            background = tabsColor,
            selectedColor = tabsSelectedColor,
            items = navigationItems,
            selectedNavigationId = selectedNavigationId
        )
        MiniWebCanvas(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.config_editor_preview_title_selected_tab_page),
            accent = tabsSelectedColor,
            showAccentPanel = false,
            showPrimaryAction = false
        )
    }
}

@Composable
private fun MiniTabsBar(
    background: Color,
    selectedColor: Color,
    items: List<NavigationItemForm>,
    selectedNavigationId: String
) {
    val inactive = resolvePreviewBottomBarUnselectedColor(background)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.take(4).forEach { item ->
            val selected = item.id == selectedNavigationId
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        selectedColor.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    }
                ) {
                    Text(
                        text = item.title.ifBlank { item.id }.take(10),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) selectedColor else inactive,
                        maxLines = 1
                    )
                }
                when {
                    item.badgeCount.isNotBlank() -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp)
                                .background(Color(0xFFE11D48), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.badgeCount.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    item.showUnreadDot -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .background(Color(0xFFE11D48), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSideDrawerPreview(
    state: ConfigEditorFormState,
    topBarColor: Color,
    navigationItems: List<NavigationItemForm>,
    selectedNavigationId: String
) {
    val resolvedDrawerWidthDp = resolveDrawerPreviewWidthDp(state.drawerWidthText)
    val drawerCornerRadius = resolveDrawerCornerRadiusDp(state.drawerCornerRadiusText)
    val drawerPreviewWidth = ((resolvedDrawerWidthDp / 320f) * 132f)
        .coerceIn(108f, 172f)
        .dp
    val drawerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = drawerCornerRadius.dp,
        bottomEnd = drawerCornerRadius.dp,
        bottomStart = 0.dp
    )
    Column(modifier = Modifier.fillMaxSize()) {
        MiniStatusStrip(
            immersive = state.immersiveStatusBar,
            background = topBarColor
        )
        MiniTopBar(
            title = state.appName.ifBlank { stringResource(R.string.config_editor_preview_title_drawer_shell) },
            showBack = false,
            showHome = state.topBarShowHomeButton,
            showRefresh = state.topBarShowRefreshButton,
            centered = state.topBarTitleCentered,
            background = topBarColor,
            leadingIconName = state.drawerMenuIcon,
            homeIconName = state.topBarHomeIcon,
            refreshIconName = state.topBarRefreshIcon
        )
        Box(modifier = Modifier.weight(1f)) {
            MiniWebCanvas(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.config_editor_preview_title_content_page),
                accent = topBarColor
            )
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerPreviewWidth)
                    .clip(drawerShape)
                    .align(Alignment.CenterStart),
                color = topBarColor.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shape = drawerShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.drawerHeaderTitle.ifBlank { state.appName.ifBlank { stringResource(R.string.config_editor_preview_drawer_title) } },
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColorFor(topBarColor)
                    )
                    if (state.drawerHeaderSubtitle.isNotBlank()) {
                        Text(
                            text = state.drawerHeaderSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColorFor(topBarColor).copy(alpha = 0.76f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    navigationItems.take(4).forEach { item ->
                        val selected = item.id == selectedNavigationId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                contentColorFor(topBarColor).copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiniNavigationGlyph(
                                    iconName = if (selected) item.selectedIcon.ifBlank { item.icon } else item.icon,
                                    selected = selected,
                                    activeColor = contentColorFor(topBarColor),
                                    inactiveColor = contentColorFor(topBarColor).copy(alpha = 0.72f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title.ifBlank { item.id },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColorFor(topBarColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatusStrip(
    immersive: Boolean,
    background: Color = Color(0xFF111827)
) {
    if (immersive) return
    val foreground = contentColorFor(background)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "9:41",
            style = MaterialTheme.typography.labelSmall,
            color = foreground
        )
        Spacer(modifier = Modifier.weight(1f))
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(width = 10.dp, height = 4.dp)
                    .background(foreground.copy(alpha = 0.85f), RoundedCornerShape(99.dp))
            )
        }
    }
}

@Composable
private fun MiniTopBar(
    title: String,
    showBack: Boolean,
    showHome: Boolean,
    showRefresh: Boolean,
    centered: Boolean,
    background: Color,
    leadingIconName: String? = null,
    backIconName: String = "back",
    homeIconName: String = "home",
    refreshIconName: String = "refresh"
) {
    val foreground = contentColorFor(background)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIconName != null) {
                MiniActionGlyph(iconName = leadingIconName, tint = foreground, fallbackLabel = "M")
            } else if (showBack) {
                MiniActionGlyph(iconName = backIconName, tint = foreground, fallbackLabel = "<")
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }
            if (centered) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = title,
                modifier = if (centered) Modifier else Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (showHome) {
                MiniActionGlyph(iconName = homeIconName, tint = foreground, fallbackLabel = "H")
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (showRefresh) {
                MiniActionGlyph(iconName = refreshIconName, tint = foreground, fallbackLabel = "R")
            } else {
                Spacer(modifier = Modifier.width(18.dp))
            }
        }
    }
}

@Composable
private fun MiniBottomBar(
    background: Color,
    selectedColor: Color,
    items: List<NavigationItemForm>,
    selectedNavigationId: String,
    showLabels: Boolean
) {
    val foreground = selectedColor
    val inactive = resolvePreviewBottomBarUnselectedColor(background)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.take(4).forEach { item ->
            val selected = item.id == selectedNavigationId
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    MiniNavigationGlyph(
                        iconName = if (selected) item.selectedIcon.ifBlank { item.icon } else item.icon,
                        selected = selected,
                        activeColor = foreground,
                        inactiveColor = inactive
                    )
                    when {
                        item.badgeCount.isNotBlank() -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .background(Color(0xFFE11D48), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.badgeCount.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        item.showUnreadDot -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(Color(0xFFE11D48), CircleShape)
                            )
                        }
                    }
                }
                if (showLabels) {
                    Text(
                        text = item.title.ifBlank { item.id }.take(8),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) foreground else inactive
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniWebCanvas(
    modifier: Modifier = Modifier,
    title: String,
    accent: Color,
    chromeLess: Boolean = false,
    showAccentPanel: Boolean = true,
    showPrimaryAction: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (chromeLess) Color(0xFFE2E8F0) else Color(0xFFF8FAFC))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (chromeLess) stringResource(R.string.config_editor_preview_content_fullscreen) else stringResource(R.string.config_editor_preview_content_shell),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }
        if (showAccentPanel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            )
        }
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.72f else 1f)
                    .height(if (index == 0) 16.dp else 12.dp)
                    .background(Color(0xFFDCE6F0), RoundedCornerShape(99.dp))
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showPrimaryAction) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f)
            ) {
                Text(
                    text = stringResource(R.string.config_editor_preview_primary_action),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
private fun MiniActionGlyph(
    iconName: String,
    tint: Color,
    fallbackLabel: String
) {
    val drawableRes = TemplateIconCatalog.find(iconName)?.drawableRes
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(tint.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Text(
                text = fallbackLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
private fun MiniNavigationGlyph(
    iconName: String,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    val tint = if (selected) activeColor else inactiveColor
    val drawableRes = TemplateIconCatalog.find(iconName)?.drawableRes
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = iconName.trim().take(1).uppercase().ifBlank { "*" },
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
private fun PreviewPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun parsePreviewColor(value: String, fallback: Color): Color {
    val candidate = value.trim()
    if (candidate.isBlank()) {
        return fallback
    }
    return runCatching { Color(android.graphics.Color.parseColor(candidate)) }
        .getOrDefault(fallback)
}

private fun inferUserAgentPreset(userAgent: String): String {
    return when (userAgent.trim()) {
        "" -> USER_AGENT_PRESET_DEFAULT
        USER_AGENT_SAFARI_IPHONE -> USER_AGENT_PRESET_SAFARI_IPHONE
        USER_AGENT_SAFARI_MAC -> USER_AGENT_PRESET_SAFARI_MAC
        USER_AGENT_CHROME_PC -> USER_AGENT_PRESET_CHROME_PC
        else -> USER_AGENT_PRESET_CUSTOM
    }
}

@Composable
private fun DrawerHeaderPreview(
    projectId: String?,
    state: ConfigEditorFormState
) {
    val drawerWidth = resolveDrawerPreviewWidthDp(state.drawerWidthText)
    val drawerCornerRadius = resolveDrawerCornerRadiusDp(state.drawerCornerRadiusText)
    val statusBarInset = resolveDrawerPreviewStatusBarInsetDp(state.immersiveStatusBar)
    val wallpaperHeight = state.drawerWallpaperHeightText.trim().toIntOrNull()
        ?.coerceIn(MIN_DRAWER_WALLPAPER_HEIGHT_DP, MAX_DRAWER_WALLPAPER_HEIGHT_DP)
        ?: DEFAULT_DRAWER_WALLPAPER_HEIGHT_DP
    val previewHeight = resolveDrawerHeaderHeightDp(
        wallpaperHeightDp = wallpaperHeight,
        hasSubtitle = state.drawerHeaderSubtitle.isNotBlank(),
        immersiveStatusBar = state.immersiveStatusBar
    )
    val backgroundColor = parseOptionalColor(state.drawerHeaderBackgroundColor)
        ?: Color.White
    val drawerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = drawerCornerRadius.dp,
        bottomEnd = drawerCornerRadius.dp,
        bottomStart = 0.dp
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 1.dp,
            shape = drawerShape,
            modifier = Modifier.width(drawerWidth.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight.dp)
                    .clip(drawerShape)
                    .background(backgroundColor)
            ) {
                if (state.drawerWallpaperEnabled && state.drawerWallpaperPath.isNotBlank()) {
                    ProjectAssetPreview(
                        projectId = projectId,
                        relativePath = state.drawerWallpaperPath,
                        contentScale = ContentScale.Crop,
                        previewWidth = drawerWidth.dp,
                        previewHeight = previewHeight.dp,
                        fillMaxWidth = true,
                        shape = drawerShape
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = DRAWER_PREVIEW_HORIZONTAL_PADDING_DP.dp,
                            top = (DRAWER_PREVIEW_TOP_PADDING_DP + statusBarInset).dp,
                            end = DRAWER_PREVIEW_HORIZONTAL_PADDING_DP.dp,
                            bottom = DRAWER_PREVIEW_BOTTOM_PADDING_DP.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wallpaperHeight.dp)
                    ) {
                        if (state.drawerAvatarEnabled && state.drawerAvatarPath.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = DRAWER_PREVIEW_AVATAR_BOTTOM_MARGIN_DP.dp)
                                    .size(DRAWER_PREVIEW_AVATAR_SIZE_DP.dp)
                                    .background(Color.White, CircleShape)
                                    .padding(2.dp)
                            ) {
                                ProjectAssetPreview(
                                    projectId = projectId,
                                    relativePath = state.drawerAvatarPath,
                                    contentScale = ContentScale.Crop,
                                    previewWidth = DRAWER_PREVIEW_AVATAR_IMAGE_SIZE_DP.dp,
                                    previewHeight = DRAWER_PREVIEW_AVATAR_IMAGE_SIZE_DP.dp,
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(DRAWER_PREVIEW_WALLPAPER_GAP_DP.dp))
                    Text(
                        text = state.drawerHeaderTitle.ifBlank { state.appName.ifBlank { stringResource(R.string.config_editor_preview_drawer_header_title) } },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF111827)
                    )
                    if (state.drawerHeaderSubtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.drawerHeaderSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xB3111827)
                        )
                    }
                }
            }
        }
    }
}

private fun resolvePreviewBottomBarUnselectedColor(background: Color): Color {
    return if (background.luminance() > 0.28f) {
        Color(0xFF4B5563)
    } else {
        Color.White.copy(alpha = 0.7f)
    }
}

private fun resolveDrawerPreviewWidthDp(value: String): Int {
    return value.trim().toIntOrNull()
        ?.takeIf { it > 0 }
        ?.coerceIn(MIN_DRAWER_WIDTH_DP, MAX_DRAWER_WIDTH_DP)
        ?: DEFAULT_DRAWER_WIDTH_DP
}

private fun resolveDrawerCornerRadiusDp(value: String): Int {
    return value.trim().toIntOrNull()
        ?.coerceIn(0, MAX_DRAWER_CORNER_RADIUS_DP)
        ?: DEFAULT_DRAWER_CORNER_RADIUS_DP
}

private fun resolveDrawerHeaderHeightDp(
    wallpaperHeightDp: Int,
    hasSubtitle: Boolean,
    immersiveStatusBar: Boolean
): Int {
    val baseChromeHeight = if (hasSubtitle) {
        DRAWER_PREVIEW_BASE_HEIGHT_WITH_SUBTITLE_DP
    } else {
        DRAWER_PREVIEW_BASE_HEIGHT_NO_SUBTITLE_DP
    }
    return (
        wallpaperHeightDp.coerceIn(MIN_DRAWER_WALLPAPER_HEIGHT_DP, MAX_DRAWER_WALLPAPER_HEIGHT_DP) +
            baseChromeHeight +
            resolveDrawerPreviewStatusBarInsetDp(immersiveStatusBar)
        )
        .coerceIn(MIN_DRAWER_HEADER_HEIGHT_DP, MAX_DRAWER_HEADER_HEIGHT_DP)
}

private fun resolveDrawerPreviewStatusBarInsetDp(immersiveStatusBar: Boolean): Int {
    return if (immersiveStatusBar) 0 else DRAWER_PREVIEW_STATUS_BAR_INSET_DP
}

private fun formatUserAgentPresetLabel(context: Context, value: String): String {
    return when (value) {
        USER_AGENT_PRESET_DEFAULT -> context.getString(R.string.config_editor_option_default)
        USER_AGENT_PRESET_SAFARI_IPHONE -> context.getString(R.string.config_editor_user_agent_safari_iphone)
        USER_AGENT_PRESET_SAFARI_MAC -> context.getString(R.string.config_editor_user_agent_safari_mac)
        USER_AGENT_PRESET_CHROME_PC -> context.getString(R.string.config_editor_user_agent_chrome_pc)
        USER_AGENT_PRESET_CUSTOM -> context.getString(R.string.config_editor_option_custom)
        else -> value
    }
}

private fun formatNightModeLabel(context: Context, value: String): String {
    return when (value) {
        "off" -> context.getString(R.string.config_editor_option_off)
        "on" -> context.getString(R.string.config_editor_option_on)
        "follow_theme" -> context.getString(R.string.config_editor_night_mode_follow_theme)
        else -> value
    }
}

private fun formatOpenOtherAppsModeLabel(context: Context, value: String): String {
    return when (value) {
        "ask" -> context.getString(R.string.config_editor_option_ask)
        "allow" -> context.getString(R.string.config_editor_option_allow)
        "block" -> context.getString(R.string.config_editor_option_block)
        else -> value
    }
}

private fun formatSslErrorHandlingLabel(context: Context, value: String): String {
    return when (value) {
        SSL_ERROR_HANDLING_STRICT -> context.getString(R.string.config_editor_ssl_error_handling_strict)
        SSL_ERROR_HANDLING_IGNORE -> context.getString(R.string.config_editor_ssl_error_handling_ignore)
        else -> value
    }
}

private fun formatNavigationBackBehaviorLabel(context: Context, value: String): String {
    return when (value) {
        "web_history" -> context.getString(R.string.config_editor_navigation_back_behavior_web_history)
        "reset_on_navigation" -> context.getString(R.string.config_editor_navigation_back_behavior_reset)
        else -> value
    }
}

private fun formatTopBarHomeBehaviorLabel(context: Context, value: String): String {
    return when (value) {
        "default_home" -> context.getString(R.string.config_editor_home_button_behavior_default_home)
        "default_navigation_item" -> context.getString(R.string.config_editor_home_button_behavior_default_navigation_item)
        "run_js" -> context.getString(R.string.config_editor_action_run_js)
        else -> value
    }
}

private fun formatTopBarRefreshBehaviorLabel(context: Context, value: String): String {
    return when (value) {
        "reload" -> context.getString(R.string.config_editor_refresh_button_behavior_reload)
        "reload_ignore_cache" -> context.getString(R.string.config_editor_refresh_button_behavior_reload_ignore_cache)
        "run_js" -> context.getString(R.string.config_editor_action_run_js)
        else -> value
    }
}

private fun formatBadgeGravityLabel(context: Context, value: String): String {
    return when (value) {
        "top_end" -> context.getString(R.string.config_editor_badge_gravity_top_end)
        "top_start" -> context.getString(R.string.config_editor_badge_gravity_top_start)
        "bottom_end" -> context.getString(R.string.config_editor_badge_gravity_bottom_end)
        "bottom_start" -> context.getString(R.string.config_editor_badge_gravity_bottom_start)
        else -> value
    }
}

private fun formatPageEventTriggerLabel(context: Context, value: String): String {
    return when (value) {
        "page_started" -> context.getString(R.string.config_editor_trigger_page_started)
        "page_finished" -> context.getString(R.string.config_editor_trigger_page_finished)
        "page_title_changed" -> context.getString(R.string.config_editor_trigger_page_title_changed)
        "page_left" -> context.getString(R.string.config_editor_trigger_page_left)
        "spa_url_changed" -> context.getString(R.string.config_editor_trigger_spa_url_changed)
        else -> value
    }
}

private fun formatPageEventActionTypeLabel(context: Context, value: String): String {
    return when (value) {
        "toast" -> context.getString(R.string.config_editor_action_toast)
        "load_url" -> context.getString(R.string.config_editor_action_load_url)
        "open_external" -> context.getString(R.string.config_editor_action_open_external)
        "reload" -> context.getString(R.string.config_editor_action_reload)
        "reload_ignore_cache" -> context.getString(R.string.config_editor_action_reload_ignore_cache)
        "go_back" -> context.getString(R.string.config_editor_action_go_back)
        "copy_to_clipboard" -> context.getString(R.string.config_editor_action_copy_to_clipboard)
        "run_js" -> context.getString(R.string.config_editor_action_run_js)
        else -> value
    }
}

private fun formatTemplateTypeLabel(context: Context, templateType: TemplateType): String {
    return when (templateType) {
        TemplateType.BROWSER -> context.getString(R.string.project_hub_template_browser)
        TemplateType.IMMERSIVE_SINGLE_PAGE -> context.getString(R.string.project_hub_template_immersive)
        TemplateType.SIDE_DRAWER -> context.getString(R.string.project_hub_template_side_drawer)
        TemplateType.TOP_BAR_TABS -> context.getString(R.string.project_hub_template_top_tabs)
        TemplateType.TOP_BAR_BOTTOM_TABS -> context.getString(R.string.project_hub_template_top_bottom_bar)
        TemplateType.TOP_BAR -> context.getString(R.string.project_hub_template_top_bar)
        TemplateType.BOTTOM_BAR -> context.getString(R.string.project_hub_template_bottom_bar)
    }
}

private fun templateDescription(context: Context, templateType: TemplateType): String {
    return when (templateType) {
        TemplateType.BROWSER -> context.getString(R.string.config_editor_template_desc_browser)
        TemplateType.IMMERSIVE_SINGLE_PAGE -> context.getString(R.string.config_editor_template_desc_immersive)
        TemplateType.SIDE_DRAWER -> context.getString(R.string.config_editor_template_desc_side_drawer)
        TemplateType.TOP_BAR_TABS -> context.getString(R.string.config_editor_template_desc_top_tabs)
        TemplateType.TOP_BAR_BOTTOM_TABS -> context.getString(R.string.config_editor_template_desc_top_bottom_bar)
        TemplateType.TOP_BAR -> context.getString(R.string.config_editor_template_desc_top_bar)
        TemplateType.BOTTOM_BAR -> context.getString(R.string.config_editor_template_desc_bottom_bar)
    }
}

private fun parseOptionalColor(value: String): Color? {
    val candidate = value.trim()
    if (candidate.isBlank()) {
        return null
    }
    return runCatching { Color(android.graphics.Color.parseColor(candidate)) }
        .getOrNull()
}

private fun contentColorFor(background: Color): Color {
    val luminance = (0.299f * background.red) + (0.587f * background.green) + (0.114f * background.blue)
    return if (luminance > 0.5f) Color(0xFF0F172A) else Color.White
}

private val DEFAULT_COLOR_PANEL_COLOR = Color(0xFF3B82F6)

private const val USER_AGENT_PRESET_DEFAULT = "default"
private const val USER_AGENT_PRESET_SAFARI_IPHONE = "safari_iphone"
private const val USER_AGENT_PRESET_SAFARI_MAC = "safari_mac"
private const val USER_AGENT_PRESET_CHROME_PC = "chrome_pc"
private const val USER_AGENT_PRESET_CUSTOM = "custom"
private const val DEFAULT_DRAWER_WIDTH_DP = 320
private const val MIN_DRAWER_WIDTH_DP = 240
private const val MAX_DRAWER_WIDTH_DP = 420
private const val DEFAULT_DRAWER_CORNER_RADIUS_DP = 0
private const val MAX_DRAWER_CORNER_RADIUS_DP = 40
private const val DEFAULT_DRAWER_WALLPAPER_HEIGHT_DP = 132
private const val MIN_DRAWER_WALLPAPER_HEIGHT_DP = 96
private const val MAX_DRAWER_WALLPAPER_HEIGHT_DP = 220
private const val MIN_DRAWER_HEADER_HEIGHT_DP = 184
private const val MAX_DRAWER_HEADER_HEIGHT_DP = 340
private const val DRAWER_PREVIEW_HORIZONTAL_PADDING_DP = 22
private const val DRAWER_PREVIEW_TOP_PADDING_DP = 24
private const val DRAWER_PREVIEW_BOTTOM_PADDING_DP = 22
private const val DRAWER_PREVIEW_WALLPAPER_GAP_DP = 18
private const val DRAWER_PREVIEW_AVATAR_SIZE_DP = 72
private const val DRAWER_PREVIEW_AVATAR_IMAGE_SIZE_DP = 68
private const val DRAWER_PREVIEW_AVATAR_BOTTOM_MARGIN_DP = 14
private const val DRAWER_PREVIEW_BASE_HEIGHT_NO_SUBTITLE_DP = 92
private const val DRAWER_PREVIEW_BASE_HEIGHT_WITH_SUBTITLE_DP = 120
private const val DRAWER_PREVIEW_STATUS_BAR_INSET_DP = 24
private val USER_AGENT_PRESET_OPTIONS = listOf(
    USER_AGENT_PRESET_DEFAULT,
    USER_AGENT_PRESET_SAFARI_IPHONE,
    USER_AGENT_PRESET_SAFARI_MAC,
    USER_AGENT_PRESET_CHROME_PC,
    USER_AGENT_PRESET_CUSTOM
)
private const val USER_AGENT_SAFARI_IPHONE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
private const val USER_AGENT_SAFARI_MAC =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
private const val USER_AGENT_CHROME_PC =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun resolveOutputApkPreviewName(context: Context, state: ConfigEditorFormState): String {
    val packageName = state.packageName.trim().ifBlank { context.getString(R.string.config_editor_output_package_fallback) }
    val template = state.outputApkNameTemplate.trim()
    val rawName = if (template.isBlank()) {
        "${state.applicationLabel.ifBlank { state.appName.ifBlank { context.getString(R.string.config_editor_output_app_name_fallback) } }}_${state.versionName}"
    } else {
        template
            .replace("{projectName}", state.appName)
            .replace("{appName}", state.applicationLabel)
            .replace("{versionName}", state.versionName)
            .replace("{versionCode}", state.versionCodeText)
            .replace("{packageName}", packageName)
    }
    val sanitized = rawName
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
        .ifBlank { context.getString(R.string.config_editor_output_file_fallback) }
    return if (sanitized.lowercase().endsWith(".apk")) sanitized else "$sanitized.apk"
}


@Composable
private fun NavigationItemEditor(
    index: Int,
    item: NavigationItemForm,
    onRemove: () -> Unit,
    onIdChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onIconChanged: (String) -> Unit,
    onSelectedIconChanged: (String) -> Unit,
    onBadgeCountChanged: (String) -> Unit,
    onShowUnreadDotChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.config_editor_navigation_item_title, index + 1),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.config_remove_navigation_item)
                )
            }
        }
        LabeledTextField(label = stringResource(R.string.config_field_nav_id), value = item.id, onValueChange = onIdChanged)
        LabeledTextField(label = stringResource(R.string.config_field_nav_title), value = item.title, onValueChange = onTitleChanged)
        LabeledTextField(
            label = stringResource(R.string.config_field_nav_url),
            value = item.url,
            onValueChange = onUrlChanged,
            keyboardType = KeyboardType.Uri
        )
        IconPickerField(
            label = stringResource(R.string.config_field_nav_icon),
            value = item.icon,
            onValueChange = onIconChanged
        )
        IconPickerField(
            label = stringResource(R.string.config_editor_selected_icon),
            value = item.selectedIcon,
            onValueChange = onSelectedIconChanged
        )
        LabeledTextField(
            label = stringResource(R.string.config_editor_badge_count),
            value = item.badgeCount,
            onValueChange = onBadgeCountChanged,
            keyboardType = KeyboardType.Number
        )
        ToggleRow(
            label = stringResource(R.string.config_editor_show_unread_dot),
            checked = item.showUnreadDot,
            onCheckedChange = onShowUnreadDotChanged
        )
    }
}

private fun resolvePageRuleChipLabel(context: Context, rule: PageRuleForm, index: Int): String {
    return when {
        rule.urlEquals.isNotBlank() -> rule.urlEquals.trim()
        rule.urlStartsWith.isNotBlank() -> rule.urlStartsWith.trim()
        rule.urlContains.isNotBlank() -> rule.urlContains.trim()
        else -> context.getString(R.string.config_editor_rule_chip_fallback, index + 1)
    }
}

private fun resolvePageEventChipLabel(context: Context, event: PageEventForm, index: Int): String {
    return event.id.trim()
        .ifBlank { event.urlEquals.trim() }
        .ifBlank { event.urlStartsWith.trim() }
        .ifBlank { event.urlContains.trim() }
        .ifBlank { context.getString(R.string.config_editor_event_chip_fallback, index + 1) }
}

private fun resolvePageEventActionChipLabel(context: Context, action: PageEventActionForm, index: Int): String {
    return action.type.trim()
        .takeIf { it.isNotBlank() }
        ?.let { formatPageEventActionTypeLabel(context, it) }
        ?: context.getString(R.string.config_editor_action_chip_fallback, index + 1)
}

private val pageEventTriggers = listOf(
    "page_started",
    "page_finished",
    "page_title_changed",
    "page_left",
    "spa_url_changed"
)

private val pageEventActionTypes = listOf(
    "toast",
    "load_url",
    "open_external",
    "reload",
    "reload_ignore_cache",
    "go_back",
    "copy_to_clipboard",
    "run_js"
)

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation
    )
}

@Composable
private fun LabeledDropdownField(
    label: String,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String = { it },
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val previewColor = parseOptionalColor(value)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge
            )
            Surface(
                modifier = Modifier
                    .size(width = 42.dp, height = 28.dp)
                    .clickable { dialogOpen = true },
                shape = RoundedCornerShape(10.dp),
                color = previewColor ?: MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                if (previewColor == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.config_editor_auto),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.config_editor_hex_value)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { dialogOpen = true }) {
                    Text(stringResource(R.string.config_editor_pick))
                }
            }
        )
        Text(
            text = stringResource(R.string.config_editor_color_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (dialogOpen) {
        var draftValue by remember(value) { mutableStateOf(value) }
        val draftColor = parseOptionalColor(draftValue)
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = draftColor ?: MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = draftValue.trim().ifBlank { stringResource(R.string.config_editor_auto_default) },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (draftColor != null) {
                                    contentColorFor(draftColor)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    ColorPanel(
                        initialColor = draftColor,
                        onColorChange = { selectedColor ->
                            draftValue = formatRgbHex(selectedColor)
                        }
                    )
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        label = { Text(stringResource(R.string.config_editor_hex_value)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (draftValue.isNotBlank() && draftColor == null) {
                        Text(
                            text = stringResource(R.string.config_editor_invalid_hex_color),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(draftValue.trim())
                    dialogOpen = false
                }) {
                    Text(stringResource(R.string.config_editor_apply))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onValueChange("")
                        dialogOpen = false
                    }) {
                        Text(stringResource(R.string.config_editor_clear))
                    }
                    TextButton(onClick = { dialogOpen = false }) {
                        Text(stringResource(R.string.project_hub_cancel_action))
                    }
                }
            }
        )
    }
}

@Composable
private fun ColorPanel(
    initialColor: Color?,
    onColorChange: (Color) -> Unit
) {
    var hsvState by remember {
        mutableStateOf(colorToHsvState(initialColor ?: DEFAULT_COLOR_PANEL_COLOR))
    }

    LaunchedEffect(initialColor) {
        if (initialColor != null) {
            hsvState = colorToHsvState(initialColor)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.config_editor_color_panel),
            style = MaterialTheme.typography.labelLarge
        )
        SaturationValuePanel(
            hsvState = hsvState,
            onHsvChange = { updated ->
                hsvState = updated
                onColorChange(hsvStateToColor(updated))
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
                    val saturation = (offset.x / width).coerceIn(0f, 1f)
                    val value = (1f - (offset.y / height)).coerceIn(0f, 1f)
                    onHsvChange(
                        hsvState.copy(
                            saturation = saturation,
                            value = value
                        )
                    )
                }
                detectTapGestures(onTap = ::update)
            }
            .pointerInput(hsvState.hue) {
                fun update(offset: Offset) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val saturation = (offset.x / width).coerceIn(0f, 1f)
                    val value = (1f - (offset.y / height)).coerceIn(0f, 1f)
                    onHsvChange(
                        hsvState.copy(
                            saturation = saturation,
                            value = value
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
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, hueColor)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black)
            )
        )
        val markerX = hsvState.saturation * size.width
        val markerY = (1f - hsvState.value) * size.height
        drawCircle(
            color = markerStroke,
            radius = 11.dp.toPx(),
            center = Offset(markerX, markerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
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
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(markerX, size.height / 2f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = 10.dp.toPx(),
            center = Offset(markerX, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

private data class HsvState(
    val hue: Float,
    val saturation: Float,
    val value: Float
)

private fun colorToHsvState(color: Color): HsvState {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return HsvState(
        hue = hsv[0].coerceIn(0f, 360f),
        saturation = hsv[1].coerceIn(0f, 1f),
        value = hsv[2].coerceIn(0f, 1f)
    )
}

private fun hsvStateToColor(state: HsvState): Color {
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

private fun formatRgbHex(color: Color): String {
    return String.format(
        Locale.US,
        "#%06X",
        0xFFFFFF and color.toArgb()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    preferredIds: List<String> = emptyList()
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedSpec = TemplateIconCatalog.find(value)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge
            )
            Surface(
                modifier = Modifier
                    .size(width = 42.dp, height = 28.dp)
                    .clickable { dialogOpen = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedSpec != null) {
                        Icon(
                            painter = painterResource(id = selectedSpec.drawableRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = value.trim().take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.config_editor_icon_id)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { dialogOpen = true }) {
                    Text(stringResource(R.string.config_editor_pick))
                }
            }
        )
        Text(
            text = selectedSpec?.let { context.getString(R.string.config_editor_selected_icon_summary, it.label, it.id) }
                ?: stringResource(R.string.config_editor_icon_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (dialogOpen) {
        var draftValue by remember(value) { mutableStateOf(value) }
        var searchQuery by remember { mutableStateOf("") }
        val draftSpec = TemplateIconCatalog.find(draftValue)
        val allSpecs = TemplateIconCatalog.allSpecs
        val normalizedQuery = searchQuery.trim().lowercase()
        val filteredSpecs = allSpecs.filter { spec ->
            normalizedQuery.isBlank() ||
                spec.id.contains(normalizedQuery) ||
                spec.label.lowercase().contains(normalizedQuery) ||
                spec.category.lowercase().contains(normalizedQuery) ||
                spec.aliases.any { it.contains(normalizedQuery) }
        }

        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (draftSpec != null) {
                                        Icon(
                                            painter = painterResource(id = draftSpec.drawableRes),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = draftValue.trim().take(1).uppercase().ifBlank { "?" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Column {
                                Text(
                                    text = draftSpec?.label ?: stringResource(R.string.config_editor_custom_icon_id),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = draftValue.trim().ifBlank { stringResource(R.string.config_editor_no_icon_selected) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.config_editor_search_icons)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    IconCatalogGrid(
                        specs = filteredSpecs,
                        selectedIconId = draftValue,
                        onIconSelected = { draftValue = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(draftValue.trim())
                    dialogOpen = false
                }) {
                    Text(stringResource(R.string.config_editor_apply))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onValueChange("")
                        dialogOpen = false
                    }) {
                        Text(stringResource(R.string.config_editor_clear))
                    }
                    TextButton(onClick = { dialogOpen = false }) {
                        Text(stringResource(R.string.project_hub_cancel_action))
                    }
                }
            }
        )
    }
}

@Composable
private fun IconCatalogGrid(
    specs: List<com.fireflyapp.lite.ui.template.TemplateIconSpec>,
    selectedIconId: String,
    onIconSelected: (String) -> Unit
) {
    val rows = specs.chunked(4)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { spec ->
                    val selected = selectedIconId.equals(spec.id, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable { onIconSelected(spec.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        tonalElevation = if (selected) 2.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = spec.drawableRes),
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = spec.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2
                            )
                        }
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NullableToggleSelector(
    label: String,
    state: RuleToggleState,
    enabledLabel: String,
    disabledLabel: String,
    onStateChanged: (RuleToggleState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state == RuleToggleState.INHERIT,
                onClick = { onStateChanged(RuleToggleState.INHERIT) },
                label = { Text(stringResource(R.string.config_editor_inherit)) }
            )
            FilterChip(
                selected = state == RuleToggleState.ENABLED,
                onClick = { onStateChanged(RuleToggleState.ENABLED) },
                label = { Text(enabledLabel) }
            )
            FilterChip(
                selected = state == RuleToggleState.DISABLED,
                onClick = { onStateChanged(RuleToggleState.DISABLED) },
                label = { Text(disabledLabel) }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
