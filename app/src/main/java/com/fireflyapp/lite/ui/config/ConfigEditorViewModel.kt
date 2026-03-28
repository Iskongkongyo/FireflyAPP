package com.fireflyapp.lite.ui.config

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fireflyapp.lite.R
import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.AppInfo
import com.fireflyapp.lite.data.model.BrowserConfig
import com.fireflyapp.lite.data.model.InjectConfig
import com.fireflyapp.lite.data.model.MatchRule
import com.fireflyapp.lite.data.model.NavigationConfig
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.data.model.PageEventAction
import com.fireflyapp.lite.data.model.PageEventRule
import com.fireflyapp.lite.data.model.PageOverride
import com.fireflyapp.lite.data.model.PageRule
import com.fireflyapp.lite.data.model.ProjectManifest
import com.fireflyapp.lite.data.model.SecurityConfig
import com.fireflyapp.lite.data.model.ShellConfig
import com.fireflyapp.lite.data.model.TemplateType
import com.fireflyapp.lite.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfigEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ConfigEditorUiState())
    val uiState: StateFlow<ConfigEditorUiState> = _uiState.asStateFlow()
    private var currentProjectId: String? = null

    fun loadProject(projectId: String) {
        if (projectId == currentProjectId && _uiState.value.rawJson.isNotBlank()) {
            return
        }
        currentProjectId = projectId
        _uiState.value = _uiState.value.copy(projectId = projectId)
        loadConfig(projectId)
    }

    fun selectTab(tab: EditorTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun updateAppName(value: String) = updateForm { copy(appName = value) }
    fun updateDefaultUrl(value: String) = updateForm { copy(defaultUrl = value) }
    fun updateTemplate(value: TemplateType) = updateForm { copy(templateType = value) }
    fun updateUserAgent(value: String) = updateForm { copy(userAgent = value) }
    fun updateBackAction(value: String) = updateForm { copy(backAction = value) }
    fun updateNightMode(value: String) = updateForm { copy(nightMode = value) }
    fun updateShowLoadingOverlay(value: Boolean) = updateForm { copy(showLoadingOverlay = value) }
    fun updateShowPageProgressBar(value: Boolean) = updateForm { copy(showPageProgressBar = value) }
    fun updateShowErrorView(value: Boolean) = updateForm { copy(showErrorView = value) }
    fun updateImmersiveStatusBar(value: Boolean) = updateForm { copy(immersiveStatusBar = value) }
    fun updateTopBarShowBackButton(value: Boolean) = updateForm { copy(topBarShowBackButton = value) }
    fun updateTopBarShowHomeButton(value: Boolean) = updateForm { copy(topBarShowHomeButton = value) }
    fun updateTopBarShowRefreshButton(value: Boolean) = updateForm { copy(topBarShowRefreshButton = value) }
    fun updateTopBarHomeBehavior(value: String) = updateForm { copy(topBarHomeBehavior = value) }
    fun updateTopBarRefreshBehavior(value: String) = updateForm { copy(topBarRefreshBehavior = value) }
    fun updateTopBarFollowPageTitle(value: Boolean) = updateForm { copy(topBarFollowPageTitle = value) }
    fun updateTopBarTitleCentered(value: Boolean) = updateForm { copy(topBarTitleCentered = value) }
    fun updateTopBarCornerRadiusText(value: String) = updateForm { copy(topBarCornerRadiusText = value) }
    fun updateTopBarShadowText(value: String) = updateForm { copy(topBarShadowText = value) }
    fun updateTopBarBackIcon(value: String) = updateForm { copy(topBarBackIcon = value) }
    fun updateTopBarHomeIcon(value: String) = updateForm { copy(topBarHomeIcon = value) }
    fun updateTopBarRefreshIcon(value: String) = updateForm { copy(topBarRefreshIcon = value) }
    fun updateBottomBarShowTextLabels(value: Boolean) = updateForm { copy(bottomBarShowTextLabels = value) }
    fun updateBottomBarCornerRadiusText(value: String) = updateForm { copy(bottomBarCornerRadiusText = value) }
    fun updateBottomBarShadowText(value: String) = updateForm { copy(bottomBarShadowText = value) }
    fun updateBottomBarBadgeColor(value: String) = updateForm { copy(bottomBarBadgeColor = value) }
    fun updateBottomBarBadgeTextColor(value: String) = updateForm { copy(bottomBarBadgeTextColor = value) }
    fun updateBottomBarBadgeGravity(value: String) = updateForm { copy(bottomBarBadgeGravity = value) }
    fun updateBottomBarBadgeMaxCharacterCountText(value: String) = updateForm { copy(bottomBarBadgeMaxCharacterCountText = value) }
    fun updateBottomBarBadgeHorizontalOffsetText(value: String) = updateForm { copy(bottomBarBadgeHorizontalOffsetText = value) }
    fun updateBottomBarBadgeVerticalOffsetText(value: String) = updateForm { copy(bottomBarBadgeVerticalOffsetText = value) }
    fun updateBottomBarSelectedColor(value: String) = updateForm { copy(bottomBarSelectedColor = value) }
    fun updateDrawerHeaderTitle(value: String) = updateForm { copy(drawerHeaderTitle = value) }
    fun updateDrawerHeaderSubtitle(value: String) = updateForm { copy(drawerHeaderSubtitle = value) }
    fun updateDrawerWidthText(value: String) = updateForm { copy(drawerWidthText = value) }
    fun updateDrawerCornerRadiusText(value: String) = updateForm { copy(drawerCornerRadiusText = value) }
    fun updateDrawerHeaderBackgroundColor(value: String) = updateForm { copy(drawerHeaderBackgroundColor = value) }
    fun updateDrawerWallpaperEnabled(value: Boolean) = updateForm { copy(drawerWallpaperEnabled = value) }
    fun updateDrawerWallpaperHeightText(value: String) = updateForm { copy(drawerWallpaperHeightText = value) }
    fun updateDrawerAvatarEnabled(value: Boolean) = updateForm { copy(drawerAvatarEnabled = value) }
    fun updateDrawerHeaderImageUrl(value: String) = updateForm { copy(drawerHeaderImageUrl = value) }
    fun updateDrawerHeaderImageHeightText(value: String) = updateForm { copy(drawerHeaderImageHeightText = value) }
    fun updateDrawerHeaderImageScaleMode(value: String) = updateForm { copy(drawerHeaderImageScaleMode = value) }
    fun updateDrawerHeaderImageOverlayPreset(value: String) = updateForm { copy(drawerHeaderImageOverlayPreset = value) }
    fun updateDrawerHeaderImageOverlayColor(value: String) = updateForm { copy(drawerHeaderImageOverlayColor = value) }
    fun updateDrawerMenuIcon(value: String) = updateForm { copy(drawerMenuIcon = value) }
    fun updateDefaultNavigationItemId(value: String) = updateForm { copy(defaultNavigationItemId = value) }
    fun updateEnableSwipeNavigation(value: Boolean) = updateForm { copy(enableSwipeNavigation = value) }
    fun updateNavigationBackBehavior(value: String) = updateForm { copy(navigationBackBehavior = value) }
    fun updateTopBarThemeColor(value: String) = updateForm { copy(topBarThemeColor = value) }
    fun updateBottomBarThemeColor(value: String) = updateForm { copy(bottomBarThemeColor = value) }
    fun updateAllowExternalHosts(value: Boolean) = updateForm { copy(allowExternalHosts = value) }
    fun updateOpenOtherAppsMode(value: String) = updateForm { copy(openOtherAppsMode = value) }
    fun updateAllowedHostsText(value: String) = updateForm { copy(allowedHostsText = value) }
    fun updateGlobalJsText(value: String) = updateForm { copy(globalJsText = value) }
    fun updateGlobalCssText(value: String) = updateForm { copy(globalCssText = value) }
    fun updateApplicationLabel(value: String) = updateForm { copy(applicationLabel = value) }
    fun updateVersionName(value: String) = updateForm { copy(versionName = value) }
    fun updateVersionCodeText(value: String) = updateForm { copy(versionCodeText = value) }
    fun updatePackageName(value: String) = updateForm { copy(packageName = value) }
    fun updateOutputApkNameTemplate(value: String) = updateForm { copy(outputApkNameTemplate = value) }
    fun updateSigningStorePassword(value: String) = updateForm { copy(signingStorePassword = value) }
    fun updateSigningKeyAlias(value: String) = updateForm { copy(signingKeyAlias = value) }
    fun updateSigningKeyPassword(value: String) = updateForm { copy(signingKeyPassword = value) }
    fun updateSplashSkipEnabled(value: Boolean) = updateForm { copy(splashSkipEnabled = value) }
    fun updateSplashSkipSecondsText(value: String) = updateForm { copy(splashSkipSecondsText = value) }

    fun updateRawJson(value: String) {
        _uiState.value = _uiState.value.copy(rawJson = value)
    }

    fun addNavigationItem() {
        updateForm {
            copy(
                navigationItems = navigationItems + NavigationItemForm(
                    id = "nav_${navigationItems.size + 1}",
                    title = "",
                    url = "",
                    icon = "home"
                )
            )
        }
    }

    fun removeNavigationItem(index: Int) {
        updateForm {
            copy(
                navigationItems = navigationItems.filterIndexed { currentIndex, _ ->
                    currentIndex != index
                }
            )
        }
    }

    fun updateNavigationId(index: Int, value: String) = updateNavigationItem(index) { copy(id = value) }
    fun updateNavigationTitle(index: Int, value: String) = updateNavigationItem(index) { copy(title = value) }
    fun updateNavigationUrl(index: Int, value: String) = updateNavigationItem(index) { copy(url = value) }
    fun updateNavigationIcon(index: Int, value: String) = updateNavigationItem(index) { copy(icon = value) }
    fun updateNavigationSelectedIcon(index: Int, value: String) = updateNavigationItem(index) { copy(selectedIcon = value) }
    fun updateNavigationBadgeCount(index: Int, value: String) = updateNavigationItem(index) { copy(badgeCount = value) }
    fun updateNavigationShowUnreadDot(index: Int, value: Boolean) = updateNavigationItem(index) { copy(showUnreadDot = value) }

    fun addPageRule() {
        updateForm {
            copy(pageRules = pageRules + PageRuleForm())
        }
    }

    fun removePageRule(index: Int) {
        updateForm {
            copy(
                pageRules = pageRules.filterIndexed { currentIndex, _ ->
                    currentIndex != index
                }
            )
        }
    }

    fun updatePageRuleUrlEquals(index: Int, value: String) = updatePageRule(index) { copy(urlEquals = value) }
    fun updatePageRuleUrlStartsWith(index: Int, value: String) = updatePageRule(index) { copy(urlStartsWith = value) }
    fun updatePageRuleUrlContains(index: Int, value: String) = updatePageRule(index) { copy(urlContains = value) }
    fun updatePageRuleTitle(index: Int, value: String) = updatePageRule(index) { copy(title = value) }
    fun updatePageRuleLoadingText(index: Int, value: String) = updatePageRule(index) { copy(loadingText = value) }
    fun updatePageRuleErrorTitle(index: Int, value: String) = updatePageRule(index) { copy(errorTitle = value) }
    fun updatePageRuleErrorMessage(index: Int, value: String) = updatePageRule(index) { copy(errorMessage = value) }
    fun updatePageRuleRetryAction(index: Int, value: String) = updatePageRule(index) { copy(errorRetryAction = value) }
    fun updatePageRuleRetryUrl(index: Int, value: String) = updatePageRule(index) { copy(errorRetryUrl = value) }
    fun updatePageRuleInjectJs(index: Int, value: String) = updatePageRule(index) { copy(injectJsText = value) }
    fun updatePageRuleInjectCss(index: Int, value: String) = updatePageRule(index) { copy(injectCssText = value) }
    fun updatePageRuleShowTopBar(index: Int, value: RuleToggleState) = updatePageRule(index) { copy(showTopBar = value) }
    fun updatePageRuleShowBottomBar(index: Int, value: RuleToggleState) = updatePageRule(index) { copy(showBottomBar = value) }
    fun updatePageRuleShowDownloadOverlay(index: Int, value: RuleToggleState) = updatePageRule(index) { copy(showDownloadOverlay = value) }
    fun updatePageRuleSuppressFocusHighlight(index: Int, value: RuleToggleState) = updatePageRule(index) { copy(suppressFocusHighlight = value) }
    fun updatePageRuleOpenExternal(index: Int, value: RuleToggleState) = updatePageRule(index) { copy(openExternal = value) }

    fun addPageEvent() {
        updateForm {
            copy(
                pageEvents = pageEvents + PageEventForm(
                    id = "event_${pageEvents.size + 1}",
                    actions = listOf(PageEventActionForm())
                )
            )
        }
    }

    fun removePageEvent(index: Int) {
        updateForm {
            copy(
                pageEvents = pageEvents.filterIndexed { currentIndex, _ -> currentIndex != index }
            )
        }
    }

    fun updatePageEventId(index: Int, value: String) = updatePageEvent(index) { copy(id = value) }
    fun updatePageEventEnabled(index: Int, value: Boolean) = updatePageEvent(index) { copy(enabled = value) }
    fun updatePageEventTrigger(index: Int, value: String) = updatePageEvent(index) { copy(trigger = value) }
    fun updatePageEventUrlEquals(index: Int, value: String) = updatePageEvent(index) { copy(urlEquals = value) }
    fun updatePageEventUrlStartsWith(index: Int, value: String) = updatePageEvent(index) { copy(urlStartsWith = value) }
    fun updatePageEventUrlContains(index: Int, value: String) = updatePageEvent(index) { copy(urlContains = value) }

    fun addPageEventAction(eventIndex: Int) {
        updatePageEvent(eventIndex) {
            copy(actions = actions + PageEventActionForm())
        }
    }

    fun removePageEventAction(eventIndex: Int, actionIndex: Int) {
        updatePageEvent(eventIndex) {
            copy(actions = actions.filterIndexed { currentIndex, _ -> currentIndex != actionIndex })
        }
    }

    fun updatePageEventActionType(eventIndex: Int, actionIndex: Int, value: String) =
        updatePageEventAction(eventIndex, actionIndex) { copy(type = value) }

    fun updatePageEventActionValue(eventIndex: Int, actionIndex: Int, value: String) =
        updatePageEventAction(eventIndex, actionIndex) { copy(value = value) }

    fun updatePageEventActionUrl(eventIndex: Int, actionIndex: Int, value: String) =
        updatePageEventAction(eventIndex, actionIndex) { copy(url = value) }

    fun updatePageEventActionScript(eventIndex: Int, actionIndex: Int, value: String) =
        updatePageEventAction(eventIndex, actionIndex) { copy(script = value) }

    fun saveSelectedTab() {
        when (_uiState.value.selectedTab) {
            EditorTab.BASIC,
            EditorTab.RULES,
            EditorTab.EVENTS -> saveVisualConfig()
            EditorTab.BRANDING -> saveProjectManifest()
            EditorTab.BUILD -> saveProjectManifest()
            EditorTab.JSON -> saveRawConfig()
        }
    }

    fun resetSelectedTab() {
        when (_uiState.value.selectedTab) {
            EditorTab.BRANDING -> resetProjectBranding()
            EditorTab.BUILD -> resetProjectBuildMetadata()
            else -> resetConfig()
        }
    }

    private fun saveVisualConfig() {
        val projectId = currentProjectId ?: return
        val currentState = _uiState.value
        val mergedConfig = currentState.sourceConfig.merge(currentState.formState.toConfigSections())
        persistConfig(projectId, mergedConfig)
    }

    fun saveRawConfig() {
        val projectId = currentProjectId ?: return
        val rawJson = _uiState.value.rawJson
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            repository.saveRawConfig(projectId, rawJson)
                .onSuccess { savedConfig ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = savedConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_configuration_saved),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_save_configuration, throwable)
                    )
                }
        }
    }

    private fun saveProjectManifest() {
        val projectId = currentProjectId ?: return
        val currentState = _uiState.value
        val projectManifest = currentState.formState.toProjectManifest(
            existingManifest = currentState.sourceProjectManifest,
            projectId = projectId
        )
        val successMessage = when (currentState.selectedTab) {
            EditorTab.BRANDING -> appString(R.string.config_editor_message_branding_saved)
            EditorTab.BUILD -> appString(R.string.config_editor_message_build_metadata_saved)
            else -> appString(R.string.config_editor_message_project_metadata_saved)
        }
        val failurePrefixResId = when (currentState.selectedTab) {
            EditorTab.BRANDING -> R.string.config_editor_error_save_branding
            EditorTab.BUILD -> R.string.config_editor_error_save_build_metadata
            else -> R.string.config_editor_error_save_project_metadata
        }
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            repository.saveProjectManifest(projectId, projectManifest)
                .onSuccess { savedProjectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = currentState.sourceConfig,
                        projectManifest = savedProjectManifest,
                        message = successMessage,
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(failurePrefixResId, throwable)
                    )
                }
        }
    }

    private fun resetConfig() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            repository.resetProjectConfig(projectId)
                .onSuccess { config ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = config,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_configuration_reset),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_load_configuration, throwable)
                    )
                }
        }
    }

    private fun resetProjectBuildMetadata() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            repository.resetProjectBuildMetadata(projectId)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_build_metadata_reset),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_reset_build_metadata, throwable)
                    )
                }
        }
    }

    fun importProjectIcon(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.importProjectIcon(projectId, uri)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_icon_imported),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_import_icon, throwable)
                    )
                }
        }
    }

    fun clearProjectIcon() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.clearProjectIcon(projectId)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_icon_cleared),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_clear_icon, throwable)
                    )
                }
        }
    }

    fun importProjectSplash(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.importProjectSplash(projectId, uri)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_splash_imported),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_import_splash, throwable)
                    )
                }
        }
    }

    fun clearProjectSplash() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.clearProjectSplash(projectId)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_splash_cleared),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_clear_splash, throwable)
                    )
                }
        }
    }

    fun importDrawerWallpaper(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.importDrawerWallpaper(projectId, uri)
                .onSuccess { config ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = config,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_drawer_wallpaper_imported),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_import_drawer_wallpaper, throwable)
                    )
                }
        }
    }

    fun clearDrawerWallpaper() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.clearDrawerWallpaper(projectId)
                .onSuccess { config ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = config,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_drawer_wallpaper_cleared),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_clear_drawer_wallpaper, throwable)
                    )
                }
        }
    }

    fun importDrawerAvatar(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.importDrawerAvatar(projectId, uri)
                .onSuccess { config ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = config,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_drawer_avatar_imported),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_import_drawer_avatar, throwable)
                    )
                }
        }
    }

    fun clearDrawerAvatar() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.clearDrawerAvatar(projectId)
                .onSuccess { config ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = config,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_drawer_avatar_cleared),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_clear_drawer_avatar, throwable)
                    )
                }
        }
    }

    fun importProjectKeystore(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.importProjectKeystore(projectId, uri)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_keystore_imported),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_import_keystore, throwable)
                    )
                }
        }
    }

    fun clearProjectKeystore() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.clearProjectKeystore(projectId)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_keystore_cleared),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_clear_keystore, throwable)
                    )
                }
        }
    }

    private fun resetProjectBranding() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, userMessage = null)
        viewModelScope.launch {
            repository.resetProjectBranding(projectId)
                .onSuccess { projectManifest ->
                    applyLoadedConfig(
                        projectId = projectId,
                        config = _uiState.value.sourceConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_branding_reset),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_reset_branding, throwable)
                    )
                }
        }
    }

    fun exportConfig(uri: Uri) {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        val rawConfig = buildExportRawConfig()
        val projectManifest = buildExportProjectManifest()
        viewModelScope.launch {
            repository.exportProjectPackage(projectId, uri, rawConfig, projectManifest)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = appString(R.string.config_editor_message_configuration_exported)
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_export_configuration, throwable)
                    )
                }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    private fun loadConfig(projectId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, userMessage = null)
        viewModelScope.launch {
            repository.loadConfig(projectId)
                .onSuccess { config ->
                    repository.loadProjectManifest(projectId)
                        .onSuccess { projectManifest ->
                            applyLoadedConfig(
                                projectId = projectId,
                                config = config,
                                projectManifest = projectManifest,
                                message = null,
                                changed = false
                            )
                        }
                        .onFailure { throwable ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isSaving = false,
                                userMessage = errorMessage(R.string.config_editor_error_load_build_metadata, throwable)
                            )
                        }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_load_configuration, throwable)
                    )
                }
        }
    }

    private fun persistConfig(projectId: String, config: AppConfig) {
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            repository.saveConfig(projectId, config)
                .onSuccess { savedConfig ->
                    val projectManifest = repository.loadProjectManifest(projectId)
                        .getOrDefault(_uiState.value.sourceProjectManifest)
                    applyLoadedConfig(
                        projectId = projectId,
                        config = savedConfig,
                        projectManifest = projectManifest,
                        message = appString(R.string.config_editor_message_configuration_saved),
                        changed = true
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        userMessage = errorMessage(R.string.config_editor_error_save_configuration, throwable)
                    )
                }
        }
    }

    private fun applyLoadedConfig(
        projectId: String,
        config: AppConfig,
        projectManifest: ProjectManifest,
        message: String?,
        changed: Boolean
    ) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSaving = false,
            projectId = projectId,
            sourceConfig = config,
            sourceProjectManifest = projectManifest,
            formState = ConfigEditorFormState.fromConfig(config, projectManifest),
            rawJson = repository.stringify(config),
            userMessage = message,
            appliedChangeCount = if (changed) _uiState.value.appliedChangeCount + 1 else _uiState.value.appliedChangeCount
        )
    }

    private fun appString(@StringRes resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun errorMessage(@StringRes prefixResId: Int, throwable: Throwable): String {
        val detail = throwable.message?.takeIf { it.isNotBlank() }
            ?: appString(R.string.common_unknown_error)
        return appString(
            R.string.common_error_with_reason,
            appString(prefixResId),
            detail
        )
    }

    private fun buildExportRawConfig(): String {
        val currentState = _uiState.value
        return if (currentState.selectedTab == EditorTab.JSON) {
            currentState.rawJson
        } else {
            val config = currentState.sourceConfig.merge(currentState.formState.toConfigSections())
            repository.stringify(config)
        }
    }

    private fun buildExportProjectManifest(): ProjectManifest {
        val currentState = _uiState.value
        val projectId = currentState.projectId.orEmpty()
        val manifest = currentState.formState.toProjectManifest(
            existingManifest = currentState.sourceProjectManifest,
            projectId = projectId
        )
        return manifest.copy(
            projectName = currentState.formState.appName.trim().ifBlank { manifest.projectName }
        )
    }

    private fun updateForm(transform: ConfigEditorFormState.() -> ConfigEditorFormState) {
        _uiState.value = _uiState.value.copy(formState = _uiState.value.formState.transform())
    }

    private fun updateNavigationItem(
        index: Int,
        transform: NavigationItemForm.() -> NavigationItemForm
    ) {
        updateForm {
            copy(
                navigationItems = navigationItems.mapIndexed { currentIndex, item ->
                    if (currentIndex == index) item.transform() else item
                }
            )
        }
    }

    private fun updatePageRule(
        index: Int,
        transform: PageRuleForm.() -> PageRuleForm
    ) {
        updateForm {
            copy(
                pageRules = pageRules.mapIndexed { currentIndex, item ->
                    if (currentIndex == index) item.transform() else item
                }
            )
        }
    }

    private fun updatePageEvent(
        index: Int,
        transform: PageEventForm.() -> PageEventForm
    ) {
        updateForm {
            copy(
                pageEvents = pageEvents.mapIndexed { currentIndex, item ->
                    if (currentIndex == index) item.transform() else item
                }
            )
        }
    }

    private fun updatePageEventAction(
        eventIndex: Int,
        actionIndex: Int,
        transform: PageEventActionForm.() -> PageEventActionForm
    ) {
        updatePageEvent(eventIndex) {
            copy(
                actions = actions.mapIndexed { currentIndex, item ->
                    if (currentIndex == actionIndex) item.transform() else item
                }
            )
        }
    }
}

data class ConfigEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val projectId: String? = null,
    val selectedTab: EditorTab = EditorTab.BASIC,
    val sourceConfig: AppConfig = AppConfig(),
    val sourceProjectManifest: ProjectManifest = ProjectManifest(),
    val formState: ConfigEditorFormState = ConfigEditorFormState(),
    val rawJson: String = "",
    val userMessage: String? = null,
    val appliedChangeCount: Int = 0
)

enum class EditorTab {
    BASIC,
    RULES,
    EVENTS,
    BRANDING,
    BUILD,
    JSON
}

data class ConfigEditorFormState(
    val appName: String = "",
    val defaultUrl: String = "",
    val templateType: TemplateType = TemplateType.TOP_BAR,
    val userAgent: String = "",
    val showLoadingOverlay: Boolean = true,
    val showPageProgressBar: Boolean = true,
    val showErrorView: Boolean = true,
    val immersiveStatusBar: Boolean = false,
    val topBarShowBackButton: Boolean = true,
    val topBarShowHomeButton: Boolean = false,
    val topBarShowRefreshButton: Boolean = true,
    val topBarHomeBehavior: String = "default_home",
    val topBarRefreshBehavior: String = "reload",
    val topBarFollowPageTitle: Boolean = true,
    val topBarTitleCentered: Boolean = true,
    val topBarCornerRadiusText: String = "0",
    val topBarShadowText: String = "0",
    val topBarBackIcon: String = "back",
    val topBarHomeIcon: String = "home",
    val topBarRefreshIcon: String = "refresh",
    val bottomBarShowTextLabels: Boolean = true,
    val bottomBarCornerRadiusText: String = "0",
    val bottomBarShadowText: String = "0",
    val bottomBarBadgeColor: String = "",
    val bottomBarBadgeTextColor: String = "",
    val bottomBarBadgeGravity: String = "top_end",
    val bottomBarBadgeMaxCharacterCountText: String = "2",
    val bottomBarBadgeHorizontalOffsetText: String = "0",
    val bottomBarBadgeVerticalOffsetText: String = "0",
    val bottomBarSelectedColor: String = "",
    val drawerHeaderTitle: String = "",
    val drawerHeaderSubtitle: String = "",
    val drawerWidthText: String = "",
    val drawerCornerRadiusText: String = "0",
    val drawerHeaderBackgroundColor: String = "",
    val drawerWallpaperEnabled: Boolean = false,
    val drawerWallpaperPath: String = "",
    val drawerWallpaperHeightText: String = "132",
    val drawerAvatarEnabled: Boolean = false,
    val drawerAvatarPath: String = "",
    val drawerHeaderImageUrl: String = "",
    val drawerHeaderImageHeightText: String = "120",
    val drawerHeaderImageScaleMode: String = "crop",
    val drawerHeaderImageOverlayPreset: String = "custom",
    val drawerHeaderImageOverlayColor: String = "",
    val drawerMenuIcon: String = "menu",
    val defaultNavigationItemId: String = "",
    val enableSwipeNavigation: Boolean = false,
    val navigationBackBehavior: String = "web_history",
    val topBarThemeColor: String = "",
    val bottomBarThemeColor: String = "",
    val backAction: String = "go_back_or_exit",
    val nightMode: String = "off",
    val allowExternalHosts: Boolean = true,
    val openOtherAppsMode: String = "ask",
    val allowedHostsText: String = "",
    val globalJsText: String = "",
    val globalCssText: String = "",
    val applicationLabel: String = "",
    val versionName: String = "1.0.0",
    val versionCodeText: String = "1",
    val packageName: String = "",
    val outputApkNameTemplate: String = "",
    val signingMode: String = "default",
    val signingKeystorePath: String = "",
    val signingStorePassword: String = "",
    val signingKeyAlias: String = "",
    val signingKeyPassword: String = "",
    val iconMode: String = "default",
    val iconPath: String = "",
    val splashMode: String = "default",
    val splashPath: String = "",
    val splashSkipEnabled: Boolean = true,
    val splashSkipSecondsText: String = "3",
    val navigationItems: List<NavigationItemForm> = emptyList(),
    val pageRules: List<PageRuleForm> = emptyList(),
    val pageEvents: List<PageEventForm> = emptyList()
) {
    fun toConfigSections(): ConfigSections {
        return ConfigSections(
            app = AppInfo(
                name = appName,
                template = templateType,
                defaultUrl = defaultUrl
            ),
            browser = BrowserConfig(
                userAgent = userAgent,
                showLoadingOverlay = showLoadingOverlay,
                showPageProgressBar = showPageProgressBar,
                showErrorView = showErrorView,
                backAction = backAction,
                immersiveStatusBar = immersiveStatusBar,
                nightMode = nightMode
            ),
            shell = ShellConfig(
                topBarShowBackButton = topBarShowBackButton,
                topBarShowHomeButton = topBarShowHomeButton,
                topBarShowRefreshButton = topBarShowRefreshButton,
                topBarHomeBehavior = topBarHomeBehavior,
                topBarRefreshBehavior = topBarRefreshBehavior,
                topBarFollowPageTitle = topBarFollowPageTitle,
                topBarTitleCentered = topBarTitleCentered,
                topBarCornerRadiusDp = topBarCornerRadiusText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                topBarShadowDp = topBarShadowText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                topBarBackIcon = topBarBackIcon,
                topBarHomeIcon = topBarHomeIcon,
                topBarRefreshIcon = topBarRefreshIcon,
                bottomBarShowTextLabels = bottomBarShowTextLabels,
                bottomBarCornerRadiusDp = bottomBarCornerRadiusText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                bottomBarShadowDp = bottomBarShadowText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                bottomBarBadgeColor = bottomBarBadgeColor,
                bottomBarBadgeTextColor = bottomBarBadgeTextColor,
                bottomBarBadgeGravity = bottomBarBadgeGravity,
                bottomBarBadgeMaxCharacterCount = bottomBarBadgeMaxCharacterCountText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 2,
                bottomBarBadgeHorizontalOffsetDp = bottomBarBadgeHorizontalOffsetText.trim().toIntOrNull() ?: 0,
                bottomBarBadgeVerticalOffsetDp = bottomBarBadgeVerticalOffsetText.trim().toIntOrNull() ?: 0,
                bottomBarSelectedColor = bottomBarSelectedColor,
                drawerHeaderTitle = drawerHeaderTitle,
                drawerHeaderSubtitle = drawerHeaderSubtitle,
                drawerWidthDp = drawerWidthText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                drawerCornerRadiusDp = drawerCornerRadiusText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                drawerHeaderBackgroundColor = drawerHeaderBackgroundColor,
                drawerWallpaperEnabled = drawerWallpaperEnabled,
                drawerWallpaperPath = drawerWallpaperPath,
                drawerWallpaperHeightDp = drawerWallpaperHeightText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 132,
                drawerAvatarEnabled = drawerAvatarEnabled,
                drawerAvatarPath = drawerAvatarPath,
                drawerHeaderImageUrl = drawerHeaderImageUrl,
                drawerHeaderImageHeightDp = drawerHeaderImageHeightText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 120,
                drawerHeaderImageScaleMode = drawerHeaderImageScaleMode,
                drawerHeaderImageOverlayPreset = drawerHeaderImageOverlayPreset,
                drawerHeaderImageOverlayColor = drawerHeaderImageOverlayColor,
                drawerMenuIcon = drawerMenuIcon,
                defaultNavigationItemId = defaultNavigationItemId,
                enableSwipeNavigation = enableSwipeNavigation,
                navigationBackBehavior = navigationBackBehavior,
                topBarThemeColor = topBarThemeColor,
                bottomBarThemeColor = bottomBarThemeColor
            ),
            navigation = NavigationConfig(
                items = navigationItems.map { item ->
                    NavigationItem(
                        id = item.id,
                        title = item.title,
                        url = item.url,
                        icon = item.icon,
                        selectedIcon = item.selectedIcon,
                        badgeCount = item.badgeCount,
                        showUnreadDot = item.showUnreadDot
                    )
                }
            ),
            security = SecurityConfig(
                allowedHosts = allowedHostsText.lines().map { it.trim() }.filter { it.isNotBlank() },
                allowExternalHosts = allowExternalHosts,
                openOtherAppsMode = openOtherAppsMode
            ),
            inject = InjectConfig(
                globalJs = splitBlocks(globalJsText),
                globalCss = splitBlocks(globalCssText)
            ),
            pageRules = pageRules.map { it.toPageRule() },
            pageEvents = pageEvents.map { it.toPageEventRule() }
        )
    }

    fun toProjectManifest(existingManifest: ProjectManifest, projectId: String): ProjectManifest {
        val parsedVersionCode = versionCodeText.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val parsedSplashSkipSeconds = splashSkipSecondsText.trim().toIntOrNull()?.coerceIn(1, 15) ?: 3
        return existingManifest.copy(
            projectId = projectId,
            appIdentity = existingManifest.appIdentity.copy(
                applicationLabel = applicationLabel.trim(),
                versionName = versionName.trim(),
                versionCode = parsedVersionCode,
                packageName = packageName.trim()
            ),
            signing = existingManifest.signing.copy(
                mode = signingMode,
                keystorePath = signingKeystorePath,
                storePassword = signingStorePassword,
                keyAlias = signingKeyAlias.trim(),
                keyPassword = signingKeyPassword
            ),
            packaging = existingManifest.packaging.copy(
                outputApkNameTemplate = outputApkNameTemplate.trim()
            ),
            branding = existingManifest.branding.copy(
                iconMode = iconMode,
                iconPath = iconPath,
                splashMode = splashMode,
                splashPath = splashPath,
                splashSkipEnabled = splashSkipEnabled,
                splashSkipSeconds = parsedSplashSkipSeconds
            )
        )
    }

    companion object {
        fun fromConfig(config: AppConfig, projectManifest: ProjectManifest): ConfigEditorFormState {
            return ConfigEditorFormState(
                appName = config.app.name,
                defaultUrl = config.app.defaultUrl,
                templateType = config.app.template,
                userAgent = config.browser.userAgent,
                showLoadingOverlay = config.browser.showLoadingOverlay,
                showPageProgressBar = config.browser.showPageProgressBar,
                showErrorView = config.browser.showErrorView,
                immersiveStatusBar = config.browser.immersiveStatusBar,
                topBarShowBackButton = config.shell.topBarShowBackButton,
                topBarShowHomeButton = config.shell.topBarShowHomeButton,
                topBarShowRefreshButton = config.shell.topBarShowRefreshButton,
                topBarHomeBehavior = config.shell.topBarHomeBehavior,
                topBarRefreshBehavior = config.shell.topBarRefreshBehavior,
                topBarFollowPageTitle = config.shell.topBarFollowPageTitle,
                topBarTitleCentered = config.shell.topBarTitleCentered,
                topBarCornerRadiusText = config.shell.topBarCornerRadiusDp.toString(),
                topBarShadowText = config.shell.topBarShadowDp.toString(),
                topBarBackIcon = config.shell.topBarBackIcon,
                topBarHomeIcon = config.shell.topBarHomeIcon,
                topBarRefreshIcon = config.shell.topBarRefreshIcon,
                bottomBarShowTextLabels = config.shell.bottomBarShowTextLabels,
                bottomBarCornerRadiusText = config.shell.bottomBarCornerRadiusDp.toString(),
                bottomBarShadowText = config.shell.bottomBarShadowDp.toString(),
                bottomBarBadgeColor = config.shell.bottomBarBadgeColor,
                bottomBarBadgeTextColor = config.shell.bottomBarBadgeTextColor,
                bottomBarBadgeGravity = config.shell.bottomBarBadgeGravity,
                bottomBarBadgeMaxCharacterCountText = config.shell.bottomBarBadgeMaxCharacterCount.toString(),
                bottomBarBadgeHorizontalOffsetText = config.shell.bottomBarBadgeHorizontalOffsetDp.toString(),
                bottomBarBadgeVerticalOffsetText = config.shell.bottomBarBadgeVerticalOffsetDp.toString(),
                bottomBarSelectedColor = config.shell.bottomBarSelectedColor,
                drawerHeaderTitle = config.shell.drawerHeaderTitle,
                drawerHeaderSubtitle = config.shell.drawerHeaderSubtitle,
                drawerWidthText = config.shell.drawerWidthDp.takeIf { it > 0 }?.toString().orEmpty(),
                drawerCornerRadiusText = config.shell.drawerCornerRadiusDp.toString(),
                drawerHeaderBackgroundColor = config.shell.drawerHeaderBackgroundColor,
                drawerWallpaperEnabled = config.shell.drawerWallpaperEnabled,
                drawerWallpaperPath = config.shell.drawerWallpaperPath,
                drawerWallpaperHeightText = config.shell.drawerWallpaperHeightDp.toString(),
                drawerAvatarEnabled = config.shell.drawerAvatarEnabled,
                drawerAvatarPath = config.shell.drawerAvatarPath,
                drawerHeaderImageUrl = config.shell.drawerHeaderImageUrl,
                drawerHeaderImageHeightText = config.shell.drawerHeaderImageHeightDp.toString(),
                drawerHeaderImageScaleMode = config.shell.drawerHeaderImageScaleMode,
                drawerHeaderImageOverlayPreset = config.shell.drawerHeaderImageOverlayPreset,
                drawerHeaderImageOverlayColor = config.shell.drawerHeaderImageOverlayColor,
                drawerMenuIcon = config.shell.drawerMenuIcon,
                defaultNavigationItemId = config.shell.defaultNavigationItemId,
                enableSwipeNavigation = config.shell.enableSwipeNavigation,
                navigationBackBehavior = config.shell.navigationBackBehavior,
                topBarThemeColor = config.shell.topBarThemeColor,
                bottomBarThemeColor = config.shell.bottomBarThemeColor,
                backAction = config.browser.backAction,
                nightMode = config.browser.nightMode,
                allowExternalHosts = config.security.allowExternalHosts,
                openOtherAppsMode = config.security.openOtherAppsMode,
                allowedHostsText = config.security.allowedHosts.joinToString("\n"),
                globalJsText = config.inject.globalJs.joinToString(BLOCK_SEPARATOR),
                globalCssText = config.inject.globalCss.joinToString(BLOCK_SEPARATOR),
                applicationLabel = projectManifest.appIdentity.applicationLabel,
                versionName = projectManifest.appIdentity.versionName,
                versionCodeText = projectManifest.appIdentity.versionCode.toString(),
                packageName = projectManifest.appIdentity.packageName,
                outputApkNameTemplate = projectManifest.packaging.outputApkNameTemplate,
                signingMode = projectManifest.signing.mode,
                signingKeystorePath = projectManifest.signing.keystorePath,
                signingStorePassword = projectManifest.signing.storePassword,
                signingKeyAlias = projectManifest.signing.keyAlias,
                signingKeyPassword = projectManifest.signing.keyPassword,
                iconMode = projectManifest.branding.iconMode,
                iconPath = projectManifest.branding.iconPath,
                splashMode = projectManifest.branding.splashMode,
                splashPath = projectManifest.branding.splashPath,
                splashSkipEnabled = projectManifest.branding.splashSkipEnabled,
                splashSkipSecondsText = projectManifest.branding.splashSkipSeconds.toString(),
                navigationItems = config.navigation.items.map { item ->
                    NavigationItemForm(
                        id = item.id,
                        title = item.title,
                        url = item.url,
                        icon = item.icon,
                        selectedIcon = item.selectedIcon,
                        badgeCount = item.badgeCount,
                        showUnreadDot = item.showUnreadDot
                    )
                },
                pageRules = config.pageRules.map(PageRuleForm::fromPageRule),
                pageEvents = config.pageEvents.map(PageEventForm::fromPageEventRule)
            )
        }
    }
}

data class NavigationItemForm(
    val id: String,
    val title: String,
    val url: String,
    val icon: String,
    val selectedIcon: String = "",
    val badgeCount: String = "",
    val showUnreadDot: Boolean = false
)

data class PageRuleForm(
    val urlEquals: String = "",
    val urlStartsWith: String = "",
    val urlContains: String = "",
    val title: String = "",
    val loadingText: String = "",
    val errorTitle: String = "",
    val errorMessage: String = "",
    val errorRetryAction: String = "",
    val errorRetryUrl: String = "",
    val showTopBar: RuleToggleState = RuleToggleState.INHERIT,
    val showBottomBar: RuleToggleState = RuleToggleState.INHERIT,
    val showDownloadOverlay: RuleToggleState = RuleToggleState.INHERIT,
    val suppressFocusHighlight: RuleToggleState = RuleToggleState.INHERIT,
    val openExternal: RuleToggleState = RuleToggleState.INHERIT,
    val injectJsText: String = "",
    val injectCssText: String = ""
) {
    fun toPageRule(): PageRule {
        return PageRule(
            match = MatchRule(
                urlEquals = urlEquals.trim().takeIf { it.isNotBlank() },
                urlStartsWith = urlStartsWith.trim().takeIf { it.isNotBlank() },
                urlContains = urlContains.trim().takeIf { it.isNotBlank() }
            ),
            overrides = PageOverride(
                showTopBar = showTopBar.toNullableBoolean(),
                showBottomBar = showBottomBar.toNullableBoolean(),
                showDownloadOverlay = showDownloadOverlay.toNullableBoolean(),
                suppressFocusHighlight = suppressFocusHighlight.toNullableBoolean(),
                title = title.trim().takeIf { it.isNotBlank() },
                openExternal = openExternal.toNullableBoolean(),
                loadingText = loadingText.trim().takeIf { it.isNotBlank() },
                errorTitle = errorTitle.trim().takeIf { it.isNotBlank() },
                errorMessage = errorMessage.trim().takeIf { it.isNotBlank() },
                errorRetryAction = errorRetryAction.trim().takeIf { it.isNotBlank() },
                errorRetryUrl = errorRetryUrl.trim().takeIf { it.isNotBlank() },
                injectJs = splitBlocks(injectJsText),
                injectCss = splitBlocks(injectCssText)
            )
        )
    }

    companion object {
        fun fromPageRule(rule: PageRule): PageRuleForm {
            return PageRuleForm(
                urlEquals = rule.match.urlEquals.orEmpty(),
                urlStartsWith = rule.match.urlStartsWith.orEmpty(),
                urlContains = rule.match.urlContains.orEmpty(),
                title = rule.overrides.title.orEmpty(),
                loadingText = rule.overrides.loadingText.orEmpty(),
                errorTitle = rule.overrides.errorTitle.orEmpty(),
                errorMessage = rule.overrides.errorMessage.orEmpty(),
                errorRetryAction = rule.overrides.errorRetryAction.orEmpty(),
                errorRetryUrl = rule.overrides.errorRetryUrl.orEmpty(),
                showTopBar = RuleToggleState.fromNullableBoolean(rule.overrides.showTopBar),
                showBottomBar = RuleToggleState.fromNullableBoolean(rule.overrides.showBottomBar),
                showDownloadOverlay = RuleToggleState.fromNullableBoolean(rule.overrides.showDownloadOverlay),
                suppressFocusHighlight = RuleToggleState.fromNullableBoolean(rule.overrides.suppressFocusHighlight),
                openExternal = RuleToggleState.fromNullableBoolean(rule.overrides.openExternal),
                injectJsText = rule.overrides.injectJs.joinToString(BLOCK_SEPARATOR),
                injectCssText = rule.overrides.injectCss.joinToString(BLOCK_SEPARATOR)
            )
        }
    }
}

data class PageEventForm(
    val id: String = "",
    val enabled: Boolean = true,
    val trigger: String = "page_finished",
    val urlEquals: String = "",
    val urlStartsWith: String = "",
    val urlContains: String = "",
    val actions: List<PageEventActionForm> = listOf(PageEventActionForm())
) {
    fun toPageEventRule(): PageEventRule {
        return PageEventRule(
            id = id.trim(),
            enabled = enabled,
            trigger = trigger.trim(),
            match = MatchRule(
                urlEquals = urlEquals.trim().takeIf { it.isNotBlank() },
                urlStartsWith = urlStartsWith.trim().takeIf { it.isNotBlank() },
                urlContains = urlContains.trim().takeIf { it.isNotBlank() }
            ),
            actions = actions.map { it.toPageEventAction() }
        )
    }

    companion object {
        fun fromPageEventRule(rule: PageEventRule): PageEventForm {
            return PageEventForm(
                id = rule.id,
                enabled = rule.enabled,
                trigger = rule.trigger,
                urlEquals = rule.match.urlEquals.orEmpty(),
                urlStartsWith = rule.match.urlStartsWith.orEmpty(),
                urlContains = rule.match.urlContains.orEmpty(),
                actions = rule.actions.map(PageEventActionForm::fromPageEventAction).ifEmpty {
                    listOf(PageEventActionForm())
                }
            )
        }
    }
}

data class PageEventActionForm(
    val type: String = "toast",
    val value: String = "",
    val url: String = "",
    val script: String = ""
) {
    fun toPageEventAction(): PageEventAction {
        return PageEventAction(
            type = type.trim(),
            value = value,
            url = url,
            script = script
        )
    }

    companion object {
        fun fromPageEventAction(action: PageEventAction): PageEventActionForm {
            return PageEventActionForm(
                type = action.type,
                value = action.value,
                url = action.url,
                script = action.script
            )
        }
    }
}

enum class RuleToggleState {
    INHERIT,
    ENABLED,
    DISABLED;

    fun toNullableBoolean(): Boolean? {
        return when (this) {
            INHERIT -> null
            ENABLED -> true
            DISABLED -> false
        }
    }

    companion object {
        fun fromNullableBoolean(value: Boolean?): RuleToggleState {
            return when (value) {
                true -> ENABLED
                false -> DISABLED
                null -> INHERIT
            }
        }
    }
}

data class ConfigSections(
    val app: AppInfo,
    val browser: BrowserConfig,
    val shell: ShellConfig,
    val navigation: NavigationConfig,
    val security: SecurityConfig,
    val inject: InjectConfig,
    val pageRules: List<PageRule>,
    val pageEvents: List<PageEventRule>
)

private fun AppConfig.merge(sections: ConfigSections): AppConfig {
    return copy(
        app = app.copy(
            name = sections.app.name,
            template = sections.app.template,
            defaultUrl = sections.app.defaultUrl
        ),
        browser = browser.copy(
            userAgent = sections.browser.userAgent,
            showLoadingOverlay = sections.browser.showLoadingOverlay,
            showPageProgressBar = sections.browser.showPageProgressBar,
            showErrorView = sections.browser.showErrorView,
            backAction = sections.browser.backAction,
            immersiveStatusBar = sections.browser.immersiveStatusBar,
            nightMode = sections.browser.nightMode
        ),
        shell = shell.copy(
            topBarShowBackButton = sections.shell.topBarShowBackButton,
            topBarShowHomeButton = sections.shell.topBarShowHomeButton,
            topBarShowRefreshButton = sections.shell.topBarShowRefreshButton,
            topBarHomeBehavior = sections.shell.topBarHomeBehavior,
            topBarRefreshBehavior = sections.shell.topBarRefreshBehavior,
            topBarFollowPageTitle = sections.shell.topBarFollowPageTitle,
            topBarTitleCentered = sections.shell.topBarTitleCentered,
            topBarCornerRadiusDp = sections.shell.topBarCornerRadiusDp,
            topBarShadowDp = sections.shell.topBarShadowDp,
            topBarBackIcon = sections.shell.topBarBackIcon,
            topBarHomeIcon = sections.shell.topBarHomeIcon,
            topBarRefreshIcon = sections.shell.topBarRefreshIcon,
            bottomBarShowTextLabels = sections.shell.bottomBarShowTextLabels,
            bottomBarCornerRadiusDp = sections.shell.bottomBarCornerRadiusDp,
            bottomBarShadowDp = sections.shell.bottomBarShadowDp,
            bottomBarBadgeColor = sections.shell.bottomBarBadgeColor,
            bottomBarBadgeTextColor = sections.shell.bottomBarBadgeTextColor,
            bottomBarBadgeGravity = sections.shell.bottomBarBadgeGravity,
            bottomBarBadgeMaxCharacterCount = sections.shell.bottomBarBadgeMaxCharacterCount,
            bottomBarBadgeHorizontalOffsetDp = sections.shell.bottomBarBadgeHorizontalOffsetDp,
            bottomBarBadgeVerticalOffsetDp = sections.shell.bottomBarBadgeVerticalOffsetDp,
            bottomBarSelectedColor = sections.shell.bottomBarSelectedColor,
            drawerHeaderTitle = sections.shell.drawerHeaderTitle,
            drawerHeaderSubtitle = sections.shell.drawerHeaderSubtitle,
            drawerWidthDp = sections.shell.drawerWidthDp,
            drawerCornerRadiusDp = sections.shell.drawerCornerRadiusDp,
            drawerHeaderBackgroundColor = sections.shell.drawerHeaderBackgroundColor,
            drawerWallpaperEnabled = sections.shell.drawerWallpaperEnabled,
            drawerWallpaperPath = sections.shell.drawerWallpaperPath,
            drawerWallpaperHeightDp = sections.shell.drawerWallpaperHeightDp,
            drawerAvatarEnabled = sections.shell.drawerAvatarEnabled,
            drawerAvatarPath = sections.shell.drawerAvatarPath,
            drawerHeaderImageUrl = sections.shell.drawerHeaderImageUrl,
            drawerHeaderImageHeightDp = sections.shell.drawerHeaderImageHeightDp,
            drawerHeaderImageScaleMode = sections.shell.drawerHeaderImageScaleMode,
            drawerHeaderImageOverlayPreset = sections.shell.drawerHeaderImageOverlayPreset,
            drawerHeaderImageOverlayColor = sections.shell.drawerHeaderImageOverlayColor,
            drawerMenuIcon = sections.shell.drawerMenuIcon,
            defaultNavigationItemId = sections.shell.defaultNavigationItemId,
            enableSwipeNavigation = sections.shell.enableSwipeNavigation,
            navigationBackBehavior = sections.shell.navigationBackBehavior,
            topBarThemeColor = sections.shell.topBarThemeColor,
            bottomBarThemeColor = sections.shell.bottomBarThemeColor
        ),
        navigation = sections.navigation,
        security = security.copy(
            allowedHosts = sections.security.allowedHosts,
            allowExternalHosts = sections.security.allowExternalHosts,
            openOtherAppsMode = sections.security.openOtherAppsMode
        ),
        inject = inject.copy(
            globalJs = sections.inject.globalJs,
            globalCss = sections.inject.globalCss
        ),
        pageRules = sections.pageRules,
        pageEvents = sections.pageEvents
    )
}

private fun splitBlocks(text: String): List<String> {
    return text.split(BLOCK_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private const val BLOCK_SEPARATOR = "\n---\n"
