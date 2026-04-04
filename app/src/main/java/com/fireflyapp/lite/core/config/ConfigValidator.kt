package com.fireflyapp.lite.core.config

import android.net.Uri
import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.AppInfo
import com.fireflyapp.lite.data.model.NavigationConfig
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.data.model.PageEventAction
import com.fireflyapp.lite.data.model.PageEventRule
import com.fireflyapp.lite.data.model.SecurityConfig
import com.fireflyapp.lite.data.model.SSL_ERROR_HANDLING_STRICT
import com.fireflyapp.lite.data.model.supportedSslErrorHandlingModes
import com.fireflyapp.lite.core.icon.ProjectCustomIconReference
import com.fireflyapp.lite.ui.template.TemplateIconCatalog

class ConfigValidator {
    fun sanitize(config: AppConfig): AppConfig {
        val sanitizedNavigationItems = config.navigation.items
            .mapIndexed { index, item ->
                val fallbackIconId = defaultNavigationFallbackId(index)
                val sanitizedIconId = sanitizeIconReference(item.icon, fallbackIconId)
                item.copy(
                    id = item.id.trim().ifBlank { "nav_${item.title.hashCode()}" },
                    title = item.title.trim().ifBlank { "Untitled" },
                    url = item.url.trim(),
                    icon = sanitizedIconId,
                    selectedIcon = item.selectedIcon.trim().let { selectedIcon ->
                        when {
                            selectedIcon.isBlank() -> sanitizedIconId
                            else -> sanitizeIconReference(selectedIcon, fallbackIconId = sanitizedIconId)
                        }
                    },
                    badgeCount = item.badgeCount.trim(),
                    showUnreadDot = item.showUnreadDot
                )
            }
            .filter { item -> item.url.isNotBlank() }

        val defaultUrl = config.app.defaultUrl.ifBlank {
            sanitizedNavigationItems.firstOrNull()?.url.orEmpty()
        }.ifBlank {
            DEFAULT_URL
        }

        val navigationItems = sanitizedNavigationItems.ifEmpty {
            listOf(
                NavigationItem(
                    id = "home",
                    title = "Home",
                    url = defaultUrl,
                    icon = "home"
                )
            )
        }.take(MAX_NAV_ITEMS)

        val sanitizedPageEvents = config.pageEvents
            .mapIndexed { index, event ->
                val sanitizedActions = event.actions
                    .map { action ->
                        action.copy(
                            type = action.type.trim().lowercase().ifBlank { DEFAULT_PAGE_EVENT_ACTION },
                            value = action.value.trim(),
                            url = action.url.trim(),
                            script = action.script.trim()
                        )
                    }
                    .filter { action -> action.type in supportedPageEventActions }
                event.copy(
                    id = event.id.trim().ifBlank { "event_${index + 1}" },
                    enabled = event.enabled,
                    trigger = event.trigger.trim().lowercase().ifBlank { DEFAULT_PAGE_EVENT_TRIGGER },
                    match = event.match.copy(
                        urlEquals = event.match.urlEquals?.trim()?.takeIf { it.isNotBlank() },
                        urlStartsWith = event.match.urlStartsWith?.trim()?.takeIf { it.isNotBlank() },
                        urlContains = event.match.urlContains?.trim()?.takeIf { it.isNotBlank() }
                    ),
                    actions = sanitizedActions
                )
            }
            .filter { event ->
                event.trigger in supportedPageEventTriggers &&
                    event.actions.isNotEmpty()
            }
            .take(MAX_PAGE_EVENTS)

        val derivedHosts = buildList {
            extractHost(defaultUrl)?.let(::add)
            navigationItems.mapNotNullTo(this) { item -> extractHost(item.url) }
            sanitizedPageEvents.forEach { event ->
                event.match.urlEquals?.let(::extractHost)?.let(::add)
                event.match.urlStartsWith?.let(::extractHost)?.let(::add)
            }
        }

        return config.copy(
            app = config.app.copy(defaultUrl = defaultUrl),
            navigation = NavigationConfig(items = navigationItems),
            security = SecurityConfig(
                allowedHosts = (config.security.allowedHosts + derivedHosts)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                allowExternalHosts = config.security.allowExternalHosts,
                openOtherAppsMode = config.security.openOtherAppsMode
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedOpenOtherAppModes }
                    ?: DEFAULT_OPEN_OTHER_APPS_MODE,
                sslErrorHandling = config.security.sslErrorHandling
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedSslErrorHandlingModes }
                    ?: SSL_ERROR_HANDLING_STRICT
            ),
            browser = config.browser.copy(
                backAction = config.browser.backAction
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedBackActions }
                    ?: DEFAULT_BACK_ACTION,
                immersiveStatusBar = config.browser.immersiveStatusBar,
                nightMode = config.browser.nightMode
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedNightModes }
                    ?: DEFAULT_NIGHT_MODE
            ),
            shell = config.shell.copy(
                topBarShowBackButton = config.shell.topBarShowBackButton,
                topBarShowHomeButton = config.shell.topBarShowHomeButton,
                topBarShowRefreshButton = config.shell.topBarShowRefreshButton,
                topBarHomeBehavior = config.shell.topBarHomeBehavior
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedTopBarHomeBehaviors }
                    ?: "default_home",
                topBarRefreshBehavior = config.shell.topBarRefreshBehavior
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedTopBarRefreshBehaviors }
                    ?: "reload",
                topBarHomeScript = config.shell.topBarHomeScript.trim(),
                topBarRefreshScript = config.shell.topBarRefreshScript.trim(),
                topBarFollowPageTitle = config.shell.topBarFollowPageTitle,
                topBarTitleCentered = config.shell.topBarTitleCentered,
                topBarCornerRadiusDp = config.shell.topBarCornerRadiusDp.coerceIn(0, 40),
                topBarShadowDp = config.shell.topBarShadowDp.coerceIn(0, 24),
                topBarBackIcon = sanitizeIconReference(config.shell.topBarBackIcon, "back"),
                topBarHomeIcon = sanitizeIconReference(config.shell.topBarHomeIcon, "home"),
                topBarRefreshIcon = sanitizeIconReference(config.shell.topBarRefreshIcon, "refresh"),
                bottomBarShowTextLabels = config.shell.bottomBarShowTextLabels,
                bottomBarCornerRadiusDp = config.shell.bottomBarCornerRadiusDp.coerceIn(0, 40),
                bottomBarShadowDp = config.shell.bottomBarShadowDp.coerceIn(0, 24),
                bottomBarBadgeColor = config.shell.bottomBarBadgeColor.trim(),
                bottomBarBadgeTextColor = config.shell.bottomBarBadgeTextColor.trim(),
                bottomBarBadgeGravity = config.shell.bottomBarBadgeGravity.trim().ifBlank { "top_end" },
                bottomBarBadgeMaxCharacterCount = config.shell.bottomBarBadgeMaxCharacterCount.coerceIn(1, 4),
                bottomBarBadgeHorizontalOffsetDp = config.shell.bottomBarBadgeHorizontalOffsetDp.coerceIn(-24, 24),
                bottomBarBadgeVerticalOffsetDp = config.shell.bottomBarBadgeVerticalOffsetDp.coerceIn(-24, 24),
                bottomBarSelectedColor = config.shell.bottomBarSelectedColor.trim(),
                drawerHeaderTitle = config.shell.drawerHeaderTitle.trim(),
                drawerHeaderSubtitle = config.shell.drawerHeaderSubtitle.trim(),
                drawerWidthDp = config.shell.drawerWidthDp
                    .takeIf { it > 0 }
                    ?.coerceIn(240, 420)
                    ?: 0,
                drawerCornerRadiusDp = config.shell.drawerCornerRadiusDp.coerceIn(0, 40),
                drawerHeaderBackgroundColor = config.shell.drawerHeaderBackgroundColor.trim(),
                drawerWallpaperEnabled = config.shell.drawerWallpaperEnabled && config.shell.drawerWallpaperPath.trim().isNotBlank(),
                drawerWallpaperPath = config.shell.drawerWallpaperPath.trim(),
                drawerWallpaperHeightDp = config.shell.drawerWallpaperHeightDp.coerceIn(96, 220),
                drawerAvatarEnabled = config.shell.drawerAvatarEnabled && config.shell.drawerAvatarPath.trim().isNotBlank(),
                drawerAvatarPath = config.shell.drawerAvatarPath.trim(),
                drawerHeaderImageUrl = config.shell.drawerHeaderImageUrl.trim(),
                drawerHeaderImageHeightDp = config.shell.drawerHeaderImageHeightDp.coerceIn(80, 220),
                drawerHeaderImageScaleMode = config.shell.drawerHeaderImageScaleMode.trim().ifBlank { "crop" },
                drawerHeaderImageOverlayPreset = config.shell.drawerHeaderImageOverlayPreset.trim().ifBlank { "custom" },
                drawerHeaderImageOverlayColor = config.shell.drawerHeaderImageOverlayColor.trim(),
                drawerMenuIcon = sanitizeIconReference(config.shell.drawerMenuIcon, "menu"),
                defaultNavigationItemId = config.shell.defaultNavigationItemId.trim(),
                navigationBackBehavior = config.shell.navigationBackBehavior
                    .trim()
                    .lowercase()
                    .takeIf { it in supportedNavigationBackBehaviors }
                    ?: DEFAULT_NAVIGATION_BACK_BEHAVIOR,
                topBarThemeColor = config.shell.topBarThemeColor.trim(),
                bottomBarThemeColor = config.shell.bottomBarThemeColor.trim()
            ),
            pageEvents = sanitizedPageEvents
        )
    }

    fun defaultConfig(): AppConfig {
        return AppConfig(
            app = AppInfo(defaultUrl = DEFAULT_URL),
            navigation = NavigationConfig(
                items = listOf(
                    NavigationItem(
                        id = "home",
                        title = "Home",
                        url = DEFAULT_URL
                    )
                )
            )
        )
    }

    private companion object {
        const val DEFAULT_URL = "https://example.com"
        const val MAX_NAV_ITEMS = 5
        const val MAX_PAGE_EVENTS = 20
        const val DEFAULT_BACK_ACTION = "go_back_or_exit"
        const val DEFAULT_NIGHT_MODE = "off"
        const val DEFAULT_OPEN_OTHER_APPS_MODE = "ask"
        const val DEFAULT_NAVIGATION_BACK_BEHAVIOR = "web_history"
        const val DEFAULT_PAGE_EVENT_TRIGGER = "page_finished"
        const val DEFAULT_PAGE_EVENT_ACTION = "toast"
        fun defaultNavigationFallbackId(index: Int): String = if (index % 2 == 0) "home" else "docs"
        val supportedBackActions = setOf(
            "go_back_or_exit",
            "go_back_or_home",
            "disabled"
        )
        val supportedNightModes = setOf(
            "off",
            "on",
            "follow_theme"
        )
        val supportedOpenOtherAppModes = setOf(
            "ask",
            "allow",
            "block"
        )
        val supportedNavigationBackBehaviors = setOf(
            "web_history",
            "reset_on_navigation"
        )
        val supportedTopBarHomeBehaviors = setOf(
            "default_home",
            "default_navigation_item",
            "run_js"
        )
        val supportedTopBarRefreshBehaviors = setOf(
            "reload",
            "reload_ignore_cache",
            "run_js"
        )
        val supportedPageEventTriggers = setOf(
            "page_started",
            "page_finished",
            "page_title_changed",
            "page_left",
            "spa_url_changed"
        )
        val supportedPageEventActions = setOf(
            "toast",
            "load_url",
            "open_external",
            "reload",
            "reload_ignore_cache",
            "go_back",
            "copy_to_clipboard",
            "run_js"
        )
    }

    private fun extractHost(url: String): String? {
        return runCatching {
            Uri.parse(url).host?.trim()?.lowercase()
        }.getOrNull()
    }

    private fun sanitizeIconReference(iconValue: String, fallbackIconId: String): String {
        val normalizedIconValue = iconValue.trim()
        ProjectCustomIconReference.relativePathOrNull(normalizedIconValue)
            ?.let(ProjectCustomIconReference::create)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        TemplateIconCatalog.find(normalizedIconValue)
            ?.id
            ?.let { return it }
        ProjectCustomIconReference.relativePathOrNull(fallbackIconId)
            ?.let(ProjectCustomIconReference::create)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return TemplateIconCatalog.resolveIdOrDefault(fallbackIconId, "home")
    }
}
