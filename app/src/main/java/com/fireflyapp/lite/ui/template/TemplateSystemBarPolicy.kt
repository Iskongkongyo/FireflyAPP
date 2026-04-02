package com.fireflyapp.lite.ui.template

import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.TemplateType

object TemplateSystemBarPolicy {
    fun resolve(config: AppConfig): RuntimeSystemBarMode {
        return when (config.app.template) {
            TemplateType.IMMERSIVE_SINGLE_PAGE -> RuntimeSystemBarMode.FULLSCREEN_IMMERSIVE
            TemplateType.BROWSER,
            TemplateType.SIDE_DRAWER,
            TemplateType.TOP_BAR_TABS,
            TemplateType.TOP_BAR_BOTTOM_TABS,
            TemplateType.TOP_BAR,
            TemplateType.BOTTOM_BAR -> {
                if (config.browser.immersiveStatusBar) {
                    RuntimeSystemBarMode.STATUS_ONLY_IMMERSIVE
                } else {
                    RuntimeSystemBarMode.DEFAULT
                }
            }
        }
    }
}
