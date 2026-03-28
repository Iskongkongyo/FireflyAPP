package com.fireflyapp.lite.ui.template

object TemplateActionIconResolver {
    fun resolveBack(iconName: String): Int {
        return TemplateIconCatalog.resolveOrDefault(iconName, fallbackId = "back")
    }

    fun resolveHome(iconName: String): Int {
        return TemplateIconCatalog.resolveOrDefault(iconName, fallbackId = "home")
    }

    fun resolveRefresh(iconName: String): Int {
        return TemplateIconCatalog.resolveOrDefault(iconName, fallbackId = "refresh")
    }

    fun resolveDrawer(iconName: String): Int {
        return TemplateIconCatalog.resolveOrDefault(iconName, fallbackId = "menu")
    }
}
