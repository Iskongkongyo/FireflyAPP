package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.NavigationItem

object TemplateNavigationIconResolver {
    fun resolve(item: NavigationItem, index: Int, selected: Boolean = false): Int {
        val iconName = if (selected && item.selectedIcon.isNotBlank()) {
            item.selectedIcon
        } else {
            item.icon
        }
        val fallback = if (index % 2 == 0) "home" else "docs"
        return TemplateIconCatalog.resolveOrDefault(iconName, fallbackId = fallback)
    }
}
