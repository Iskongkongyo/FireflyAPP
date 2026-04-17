package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.ui.web.WebContainerFragment

data class TemplateHomeTarget(
    val url: String,
    val title: String?
)

object TemplateTopBarActionResolver {
    fun isRunJavaScriptBehavior(behavior: String): Boolean {
        return normalizeBehavior(behavior) == "run_js"
    }

    fun isNavigationTargetBehavior(behavior: String): Boolean {
        return normalizeBehavior(behavior) in navigationTargetBehaviors
    }

    fun resolveHomeTarget(config: AppConfig, navigationItems: List<NavigationItem>): TemplateHomeTarget {
        return resolveNavigationTarget(
            config = config,
            navigationItems = navigationItems,
            behavior = config.shell.topBarHomeBehavior
        )
    }

    fun resolveNavigationTarget(
        config: AppConfig,
        navigationItems: List<NavigationItem>,
        behavior: String
    ): TemplateHomeTarget {
        return when (normalizeBehavior(behavior)) {
            "default_navigation_item" -> {
                val resolvedItem = navigationItems.firstOrNull {
                    it.id == config.shell.defaultNavigationItemId.trim()
                } ?: navigationItems.firstOrNull()
                if (resolvedItem != null) {
                    TemplateHomeTarget(
                        url = resolvedItem.url,
                        title = resolvedItem.title
                    )
                } else {
                    TemplateHomeTarget(
                        url = config.app.defaultUrl,
                        title = config.app.name
                    )
                }
            }

            else -> TemplateHomeTarget(
                url = config.app.defaultUrl,
                title = config.app.name
            )
        }
    }

    fun performRefresh(fragment: WebContainerFragment?, behavior: String, script: String = "") {
        when (normalizeBehavior(behavior)) {
            "reload_ignore_cache" -> fragment?.reloadIgnoringCache()
            "run_js" -> fragment?.runJavaScript(script)
            else -> fragment?.reload()
        }
    }

    private fun normalizeBehavior(behavior: String): String {
        return behavior.trim().lowercase()
    }

    private val navigationTargetBehaviors = setOf(
        "default_home",
        "default_navigation_item"
    )
}
