package com.fireflyapp.lite.core.rule

import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.PageRule
import com.fireflyapp.lite.data.model.TemplateType

class PageRuleResolver(
    private val appConfig: AppConfig
) {
    fun resolve(url: String): ResolvedPageState {
        val defaults = ResolvedPageState(
            showTopBar = appConfig.app.template in setOf(
                TemplateType.TOP_BAR,
                TemplateType.SIDE_DRAWER,
                TemplateType.TOP_BAR_TABS,
                TemplateType.TOP_BAR_BOTTOM_TABS
            ),
            showBottomBar = appConfig.app.template in setOf(
                TemplateType.BOTTOM_BAR,
                TemplateType.TOP_BAR_TABS,
                TemplateType.TOP_BAR_BOTTOM_TABS
            ),
            showDownloadOverlay = true,
            suppressFocusHighlight = false,
            errorRetryAction = DEFAULT_ERROR_RETRY_ACTION,
            injectJs = appConfig.inject.globalJs,
            injectCss = appConfig.inject.globalCss
        )

        val matchedRules = appConfig.pageRules
            .mapIndexedNotNull { index, rule ->
                if (!UrlMatcher.matches(url, rule.match)) {
                    return@mapIndexedNotNull null
                }
                MatchedRule(
                    rule = rule,
                    index = index,
                    priority = UrlMatcher.matchPriority(rule.match)
                )
            }
            .sortedWith(compareBy<MatchedRule> { it.priority }.thenBy { it.index })

        return matchedRules.fold(defaults) { current, matched ->
            current.copy(
                showTopBar = matched.rule.overrides.showTopBar ?: current.showTopBar,
                showBottomBar = matched.rule.overrides.showBottomBar ?: current.showBottomBar,
                showDownloadOverlay = matched.rule.overrides.showDownloadOverlay ?: current.showDownloadOverlay,
                suppressFocusHighlight = matched.rule.overrides.suppressFocusHighlight ?: current.suppressFocusHighlight,
                title = matched.rule.overrides.title ?: current.title,
                openExternal = matched.rule.overrides.openExternal ?: current.openExternal,
                loadingText = matched.rule.overrides.loadingText ?: current.loadingText,
                loadingCardBackgroundColor = matched.rule.overrides.loadingCardBackgroundColor ?: current.loadingCardBackgroundColor,
                loadingTextColor = matched.rule.overrides.loadingTextColor ?: current.loadingTextColor,
                loadingIndicatorColor = matched.rule.overrides.loadingIndicatorColor ?: current.loadingIndicatorColor,
                errorTitle = matched.rule.overrides.errorTitle ?: current.errorTitle,
                errorMessage = matched.rule.overrides.errorMessage ?: current.errorMessage,
                errorCardBackgroundColor = matched.rule.overrides.errorCardBackgroundColor ?: current.errorCardBackgroundColor,
                errorTitleColor = matched.rule.overrides.errorTitleColor ?: current.errorTitleColor,
                errorMessageColor = matched.rule.overrides.errorMessageColor ?: current.errorMessageColor,
                errorButtonText = matched.rule.overrides.errorButtonText ?: current.errorButtonText,
                errorButtonBackgroundColor = matched.rule.overrides.errorButtonBackgroundColor ?: current.errorButtonBackgroundColor,
                errorButtonTextColor = matched.rule.overrides.errorButtonTextColor ?: current.errorButtonTextColor,
                errorRetryAction = normalizeRetryAction(matched.rule.overrides.errorRetryAction) ?: current.errorRetryAction,
                errorRetryUrl = matched.rule.overrides.errorRetryUrl?.trim()?.takeIf { it.isNotBlank() } ?: current.errorRetryUrl,
                injectJs = current.injectJs + matched.rule.overrides.injectJs,
                injectCss = current.injectCss + matched.rule.overrides.injectCss
            )
        }
    }

    private fun normalizeRetryAction(value: String?): String? {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return normalized.takeIf { it in supportedRetryActions }
    }

    private data class MatchedRule(
        val rule: PageRule,
        val index: Int,
        val priority: Int
    )

    private companion object {
        const val DEFAULT_ERROR_RETRY_ACTION = "reload"
        val supportedRetryActions = setOf(
            DEFAULT_ERROR_RETRY_ACTION,
            "go_home",
            "load_url"
        )
    }
}
