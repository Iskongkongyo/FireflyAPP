package com.fireflyapp.lite.core.event

import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.MatchRule
import com.fireflyapp.lite.data.model.PageEventRule

class PageEventDispatcher(
    private val appConfig: AppConfig
) {
    fun resolve(context: PageEventContext): List<PageEventRule> {
        return appConfig.pageEvents.filter { rule ->
            rule.enabled &&
                rule.trigger.equals(context.trigger, ignoreCase = true) &&
                matchesUrl(context.url, rule.match)
        }
    }

    private fun matchesUrl(url: String, matchRule: MatchRule): Boolean {
        val normalizedUrl = url.trim()
        val equals = matchRule.urlEquals?.takeIf { it.isNotBlank() }?.let { normalizedUrl == it } ?: false
        val startsWith = matchRule.urlStartsWith?.takeIf { it.isNotBlank() }?.let { normalizedUrl.startsWith(it) } ?: false
        val contains = matchRule.urlContains?.takeIf { it.isNotBlank() }?.let { normalizedUrl.contains(it) } ?: false
        val hasRule = !matchRule.urlEquals.isNullOrBlank() ||
            !matchRule.urlStartsWith.isNullOrBlank() ||
            !matchRule.urlContains.isNullOrBlank()
        return if (hasRule) {
            equals || startsWith || contains
        } else {
            true
        }
    }
}

data class PageEventContext(
    val trigger: String,
    val url: String,
    val title: String = "",
    val previousUrl: String = "",
    val nextUrl: String = ""
)
