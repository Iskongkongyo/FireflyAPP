package com.fireflyapp.lite.ui.template

import android.net.Uri
import com.fireflyapp.lite.data.model.NavigationItem

object TemplateNavigationResolver {
    fun resolveInitialItem(
        items: List<NavigationItem>,
        preferredId: String
    ): NavigationItem {
        return items.firstOrNull { it.id == preferredId.trim() } ?: items.first()
    }

    fun resolveItemForUrl(
        items: List<NavigationItem>,
        currentUrl: String?
    ): NavigationItem? {
        val normalizedCurrentUrl = normalizeUrl(currentUrl)
        if (normalizedCurrentUrl.isBlank()) {
            return null
        }
        return items
            .mapNotNull { item ->
                val score = scoreUrlMatch(normalizedCurrentUrl, item.url)
                if (score > 0) item to score else null
            }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun scoreUrlMatch(currentUrl: String, itemUrl: String): Int {
        val normalizedItemUrl = normalizeUrl(itemUrl)
        if (normalizedItemUrl.isBlank()) {
            return -1
        }
        if (currentUrl == normalizedItemUrl) {
            return 4_000 + normalizedItemUrl.length
        }
        if (startsWithUrlBoundary(currentUrl, normalizedItemUrl)) {
            return 3_000 + normalizedItemUrl.length
        }

        val currentUri = runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return -1
        val itemUri = runCatching { Uri.parse(normalizedItemUrl) }.getOrNull() ?: return -1
        val sameAuthority = currentUri.scheme.equals(itemUri.scheme, ignoreCase = true) &&
            currentUri.authority.equals(itemUri.authority, ignoreCase = true)
        if (!sameAuthority) {
            return -1
        }

        val normalizedItemPath = itemUri.encodedPath.orEmpty().trimEnd('/')
        val normalizedCurrentPath = currentUri.encodedPath.orEmpty().trimEnd('/')
        if (normalizedItemPath.isBlank()) {
            return 2_000 + normalizedItemUrl.length
        }
        if (normalizedCurrentPath == normalizedItemPath) {
            return 2_500 + normalizedItemPath.length
        }
        if (normalizedCurrentPath.startsWith("$normalizedItemPath/")) {
            return 2_000 + normalizedItemPath.length
        }
        return -1
    }

    private fun startsWithUrlBoundary(currentUrl: String, itemUrl: String): Boolean {
        if (!currentUrl.startsWith(itemUrl)) {
            return false
        }
        if (currentUrl.length == itemUrl.length) {
            return true
        }
        return currentUrl[itemUrl.length] in setOf('/', '?', '#')
    }

    private fun normalizeUrl(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return ""
        }
        return trimmed
            .substringBefore('#')
            .removeSuffix("/")
    }
}
