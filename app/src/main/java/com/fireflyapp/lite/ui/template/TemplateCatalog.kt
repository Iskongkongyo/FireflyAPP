package com.fireflyapp.lite.ui.template

import androidx.fragment.app.Fragment
import com.fireflyapp.lite.data.model.TemplateType

data class RuntimeShellTemplateSpec(
    val type: TemplateType,
    val label: String,
    val description: String,
    val supportsNavigationItems: Boolean,
    val supportsTopBar: Boolean,
    val supportsTopBarBackButton: Boolean,
    val supportsBottomBar: Boolean,
    val createFragment: () -> Fragment
)

object TemplateCatalog {
    val specs: List<RuntimeShellTemplateSpec> = listOf(
        RuntimeShellTemplateSpec(
            type = TemplateType.BROWSER,
            label = "Browser",
            description = "Single-page WebView shell without native top or bottom bars.",
            supportsNavigationItems = false,
            supportsTopBar = false,
            supportsTopBarBackButton = false,
            supportsBottomBar = false,
            createFragment = { BrowserTemplateFragment() }
        ),
        RuntimeShellTemplateSpec(
            type = TemplateType.IMMERSIVE_SINGLE_PAGE,
            label = "Immersive Single Page",
            description = "Single-page shell that keeps both status and navigation bars hidden by default.",
            supportsNavigationItems = false,
            supportsTopBar = false,
            supportsTopBarBackButton = false,
            supportsBottomBar = false,
            createFragment = { ImmersiveSinglePageTemplateFragment() }
        ),
        RuntimeShellTemplateSpec(
            type = TemplateType.SIDE_DRAWER,
            label = "Side Drawer",
            description = "Toolbar shell with a left drawer menu backed by configured navigation items.",
            supportsNavigationItems = true,
            supportsTopBar = true,
            supportsTopBarBackButton = false,
            supportsBottomBar = false,
            createFragment = { SideDrawerTemplateFragment() }
        ),
        RuntimeShellTemplateSpec(
            type = TemplateType.TOP_BAR_BOTTOM_TABS,
            label = "Top Bar + Bottom Tabs",
            description = "Combined shell with a native top bar and bottom tab navigation.",
            supportsNavigationItems = true,
            supportsTopBar = true,
            supportsTopBarBackButton = true,
            supportsBottomBar = true,
            createFragment = { TopBarBottomTabsTemplateFragment() }
        ),
        RuntimeShellTemplateSpec(
            type = TemplateType.TOP_BAR,
            label = "Top Bar",
            description = "Toolbar shell with page title, back action, and refresh.",
            supportsNavigationItems = false,
            supportsTopBar = true,
            supportsTopBarBackButton = true,
            supportsBottomBar = false,
            createFragment = { TopBarTemplateFragment() }
        ),
        RuntimeShellTemplateSpec(
            type = TemplateType.BOTTOM_BAR,
            label = "Bottom Bar",
            description = "Tab-style shell backed by configured navigation items.",
            supportsNavigationItems = true,
            supportsTopBar = false,
            supportsTopBarBackButton = false,
            supportsBottomBar = true,
            createFragment = { BottomBarTemplateFragment() }
        )
    )

    fun create(templateType: TemplateType): Fragment {
        return specFor(templateType).createFragment()
    }

    fun specFor(templateType: TemplateType): RuntimeShellTemplateSpec {
        return specs.firstOrNull { it.type == templateType }
            ?: specs.first { it.type == TemplateType.TOP_BAR }
    }
}
