package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.NavigationItem

enum class NavigationSwipeDirection {
    PREVIOUS,
    NEXT
}

object TemplateSwipeNavigationHelper {
    fun resolveAdjacentItem(
        items: List<NavigationItem>,
        currentItemId: Int?,
        direction: NavigationSwipeDirection
    ): NavigationItem? {
        if (items.size < 2 || currentItemId == null) {
            return null
        }
        val currentIndex = items.indexOfFirst { it.id.hashCode() == currentItemId }
        if (currentIndex == -1) {
            return null
        }
        val targetIndex = when (direction) {
            NavigationSwipeDirection.PREVIOUS -> currentIndex - 1
            NavigationSwipeDirection.NEXT -> currentIndex + 1
        }
        return items.getOrNull(targetIndex)
    }
}
