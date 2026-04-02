package com.fireflyapp.lite.ui.template

import android.content.Context
import android.graphics.Color
import com.fireflyapp.lite.data.model.NavigationItem
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.tabs.TabLayout

object TemplateNavigationBadgeHelper {
    fun apply(
        bottomNavigation: NavigationBarView,
        items: List<NavigationItem>,
        badgeColorValue: String = "",
        badgeTextColorValue: String = "",
        badgeGravityValue: String = "top_end",
        maxCharacterCount: Int = 2,
        horizontalOffsetDp: Int = 0,
        verticalOffsetDp: Int = 0
    ) {
        val resolvedBadgeColor = parseColorOrNull(badgeColorValue)
        val resolvedBadgeTextColor = parseColorOrNull(badgeTextColorValue)
        val resolvedBadgeGravity = resolveBadgeGravity(badgeGravityValue)
        val horizontalOffsetPx = dpToPx(bottomNavigation.context, horizontalOffsetDp)
        val verticalOffsetPx = dpToPx(bottomNavigation.context, verticalOffsetDp)
        items.forEach { item ->
            val itemId = item.id.hashCode()
            val badgeCount = item.badgeCount.trim().toIntOrNull()?.takeIf { it > 0 }
            when {
                badgeCount != null -> {
                    bottomNavigation.getOrCreateBadge(itemId).apply {
                        isVisible = true
                        number = badgeCount
                        this.maxCharacterCount = maxCharacterCount.coerceIn(1, 4)
                        badgeGravity = resolvedBadgeGravity
                        backgroundColor = resolvedBadgeColor ?: backgroundColor
                        resolvedBadgeTextColor?.let { badgeTextColor = it }
                        horizontalOffset = horizontalOffsetPx
                        verticalOffset = verticalOffsetPx
                    }
                }

                item.showUnreadDot -> {
                    bottomNavigation.getOrCreateBadge(itemId).apply {
                        clearNumber()
                        isVisible = true
                        this.maxCharacterCount = maxCharacterCount.coerceIn(1, 4)
                        badgeGravity = resolvedBadgeGravity
                        backgroundColor = resolvedBadgeColor ?: backgroundColor
                        horizontalOffset = horizontalOffsetPx
                        verticalOffset = verticalOffsetPx
                    }
                }

                else -> bottomNavigation.removeBadge(itemId)
            }
        }
    }

    fun applyToTabs(
        tabLayout: TabLayout,
        items: List<NavigationItem>,
        badgeColorValue: String = "",
        badgeTextColorValue: String = "",
        badgeGravityValue: String = "top_end",
        maxCharacterCount: Int = 2,
        horizontalOffsetDp: Int = 0,
        verticalOffsetDp: Int = 0
    ) {
        val resolvedBadgeColor = parseColorOrNull(badgeColorValue)
        val resolvedBadgeTextColor = parseColorOrNull(badgeTextColorValue)
        val resolvedBadgeGravity = resolveBadgeGravity(badgeGravityValue)
        val horizontalOffsetPx = dpToPx(tabLayout.context, horizontalOffsetDp)
        val verticalOffsetPx = dpToPx(tabLayout.context, verticalOffsetDp)
        items.forEachIndexed { index, item ->
            val tab = tabLayout.getTabAt(index) ?: return@forEachIndexed
            val badgeCount = item.badgeCount.trim().toIntOrNull()?.takeIf { it > 0 }
            when {
                badgeCount != null -> {
                    tab.orCreateBadge.apply {
                        isVisible = true
                        number = badgeCount
                        this.maxCharacterCount = maxCharacterCount.coerceIn(1, 4)
                        badgeGravity = resolvedBadgeGravity
                        backgroundColor = resolvedBadgeColor ?: backgroundColor
                        resolvedBadgeTextColor?.let { badgeTextColor = it }
                        horizontalOffset = horizontalOffsetPx
                        verticalOffset = verticalOffsetPx
                    }
                }

                item.showUnreadDot -> {
                    tab.orCreateBadge.apply {
                        clearNumber()
                        isVisible = true
                        this.maxCharacterCount = maxCharacterCount.coerceIn(1, 4)
                        badgeGravity = resolvedBadgeGravity
                        backgroundColor = resolvedBadgeColor ?: backgroundColor
                        horizontalOffset = horizontalOffsetPx
                        verticalOffset = verticalOffsetPx
                    }
                }

                else -> tab.removeBadge()
            }
        }
    }

    fun formatDrawerTitle(item: NavigationItem): String {
        val badgeCount = item.badgeCount.trim()
        return when {
            badgeCount.isNotBlank() -> "${item.title} ($badgeCount)"
            item.showUnreadDot -> "${item.title} *"
            else -> item.title
        }
    }

    private fun parseColorOrNull(value: String): Int? {
        val candidate = value.trim()
        if (candidate.isBlank()) {
            return null
        }
        return runCatching { Color.parseColor(candidate) }.getOrNull()
    }

    private fun dpToPx(context: Context, valueDp: Int): Int {
        return (valueDp * context.resources.displayMetrics.density).toInt()
    }

    private fun resolveBadgeGravity(value: String): Int {
        return when (value.trim().lowercase()) {
            "top_start", "start" -> BadgeDrawable.TOP_START
            "bottom_start" -> BadgeDrawable.BOTTOM_START
            "bottom_end", "end" -> BadgeDrawable.BOTTOM_END
            else -> BadgeDrawable.TOP_END
        }
    }
}
