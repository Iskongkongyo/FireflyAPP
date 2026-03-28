package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.NavigationItem

object TemplateNavigationResolver {
    fun resolveInitialItem(
        items: List<NavigationItem>,
        preferredId: String
    ): NavigationItem {
        return items.firstOrNull { it.id == preferredId.trim() } ?: items.first()
    }
}
