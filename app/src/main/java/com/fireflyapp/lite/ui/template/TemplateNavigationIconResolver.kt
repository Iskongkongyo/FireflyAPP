package com.fireflyapp.lite.ui.template

import android.content.Context
import android.graphics.drawable.Drawable
import com.fireflyapp.lite.core.icon.ProjectCustomIconReference
import com.fireflyapp.lite.data.model.NavigationItem

object TemplateNavigationIconResolver {
    fun resolve(
        context: Context,
        projectId: String?,
        item: NavigationItem,
        index: Int,
        selected: Boolean = false
    ): Drawable? {
        val fallback = defaultFallbackId(index)
        val baseIconValue = item.icon.trim().ifBlank { fallback }
        val iconValue = if (selected && item.selectedIcon.isNotBlank()) {
            val selectedIconValue = item.selectedIcon.trim()
            when {
                ProjectCustomIconReference.isCustomReference(selectedIconValue) -> selectedIconValue
                TemplateIconCatalog.find(selectedIconValue) != null -> selectedIconValue
                else -> baseIconValue
            }
        } else {
            baseIconValue
        }
        return TemplateRuntimeIconLoader.resolveDrawable(
            context = context,
            projectId = projectId,
            iconValue = iconValue,
            fallbackId = fallback
        )
    }

    private fun defaultFallbackId(index: Int): String {
        return if (index % 2 == 0) "home" else "docs"
    }
}
