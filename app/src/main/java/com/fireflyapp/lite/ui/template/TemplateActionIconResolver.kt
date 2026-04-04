package com.fireflyapp.lite.ui.template

import android.content.Context
import android.graphics.drawable.Drawable

object TemplateActionIconResolver {
    fun resolveBack(context: Context, projectId: String?, iconName: String): Drawable? {
        return TemplateRuntimeIconLoader.resolveDrawable(context, projectId, iconName, fallbackId = "back")
    }

    fun resolveHome(context: Context, projectId: String?, iconName: String): Drawable? {
        return TemplateRuntimeIconLoader.resolveDrawable(context, projectId, iconName, fallbackId = "home")
    }

    fun resolveRefresh(context: Context, projectId: String?, iconName: String): Drawable? {
        return TemplateRuntimeIconLoader.resolveDrawable(context, projectId, iconName, fallbackId = "refresh")
    }

    fun resolveDrawer(context: Context, projectId: String?, iconName: String): Drawable? {
        return TemplateRuntimeIconLoader.resolveDrawable(context, projectId, iconName, fallbackId = "menu")
    }
}
