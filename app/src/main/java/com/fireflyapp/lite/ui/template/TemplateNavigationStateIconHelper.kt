package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.NavigationItem
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView

object TemplateNavigationStateIconHelper {
    fun applyToBottomBar(
        context: android.content.Context,
        projectId: String?,
        bottomNavigation: NavigationBarView,
        items: List<NavigationItem>,
        selectedItemId: Int?
    ) {
        items.forEachIndexed { index, item ->
            bottomNavigation.menu.findItem(item.id.hashCode())?.setIcon(
                TemplateNavigationIconResolver.resolve(
                    context = context,
                    projectId = projectId,
                    item = item,
                    index = index,
                    selected = item.id.hashCode() == selectedItemId
                )
            )
        }
    }

    fun applyToDrawer(
        context: android.content.Context,
        projectId: String?,
        navigationView: NavigationView,
        items: List<NavigationItem>,
        selectedItemId: Int?
    ) {
        items.forEachIndexed { index, item ->
            navigationView.menu.findItem(item.id.hashCode())?.setIcon(
                TemplateNavigationIconResolver.resolve(
                    context = context,
                    projectId = projectId,
                    item = item,
                    index = index,
                    selected = item.id.hashCode() == selectedItemId
                )
            )
        }
    }
}
