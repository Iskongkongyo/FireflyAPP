package com.fireflyapp.lite.ui.template

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Window
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fireflyapp.lite.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

object TemplateThemeStyler {
    fun applyTopBarTheme(
        toolbar: MaterialToolbar,
        colorValue: String,
        heightDp: Int = DEFAULT_TOP_BAR_HEIGHT_DP,
        iconSizeDp: Int = DEFAULT_TOP_BAR_ICON_SIZE_DP,
        cornerRadiusDp: Int = 0,
        shadowDp: Int = 0,
        roundBottomCorners: Boolean = cornerRadiusDp > 0
    ) {
        val customColor = parseColorOrNull(colorValue)
        val backgroundColor = resolveConfiguredSurfaceColor(toolbar, colorValue)
        val foregroundColor = resolveReadableForeground(backgroundColor)
        val resolvedHeightDp = heightDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_HEIGHT_DP
        val resolvedIconSizePx = dpToPx(
            toolbar.context,
            iconSizeDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_ICON_SIZE_DP
        )
        applySurfaceShape(
            view = toolbar,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_RADIUS_DP,
            shadowDp = shadowDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_SHADOW_DP,
            roundTopCorners = false,
            roundBottomCorners = roundBottomCorners && cornerRadiusDp > 0,
            applyElevationOverlay = customColor == null || shouldFallbackToThemeSurface(toolbar.context, customColor)
        )
        applyViewHeight(toolbar, resolvedHeightDp)
        toolbar.contentInsetStartWithNavigation = dpToPx(toolbar.context, 14)
        toolbar.contentInsetEndWithActions = dpToPx(toolbar.context, 10)
        toolbar.titleMarginStart = dpToPx(toolbar.context, 8)
        toolbar.titleMarginEnd = dpToPx(toolbar.context, 12)
        toolbar.setTitleTextColor(foregroundColor)
        toolbar.setSubtitleTextColor(foregroundColor)
        toolbar.navigationIcon = resizeDrawable(toolbar.navigationIcon, resolvedIconSizePx)?.also {
            it.setTint(foregroundColor)
        }
        toolbar.overflowIcon = resizeDrawable(toolbar.overflowIcon, resolvedIconSizePx)?.also {
            it.setTint(foregroundColor)
        }
        tintMenuIcons(toolbar.menu, foregroundColor, resolvedIconSizePx)
    }

    fun applyTopBarStatusBarTheme(
        window: Window,
        anchorView: View,
        colorValue: String,
        fallbackView: View,
        shadowDp: Int = 0
    ): Int {
        val backgroundColor = resolveDisplayedSurfaceColor(
            view = fallbackView,
            colorValue = colorValue,
            shadowDp = shadowDp
        )
        window.statusBarColor = backgroundColor
        WindowInsetsControllerCompat(window, anchorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(backgroundColor) > 0.5
        return backgroundColor
    }

    @ColorInt
    fun resolveThemeColor(colorValue: String, fallbackView: View): Int {
        return resolveConfiguredSurfaceColor(fallbackView, colorValue)
    }

    @ColorInt
    fun resolveDisplayedThemeColor(colorValue: String, fallbackView: View, shadowDp: Int = 0): Int {
        return resolveDisplayedSurfaceColor(
            view = fallbackView,
            colorValue = colorValue,
            shadowDp = shadowDp
        )
    }

    fun applyBottomBarTheme(
        bottomNavigation: NavigationBarView,
        colorValue: String,
        selectedColorValue: String = "",
        heightDp: Int = DEFAULT_BOTTOM_BAR_HEIGHT_DP,
        iconSizeDp: Int = DEFAULT_BOTTOM_BAR_ICON_SIZE_DP,
        cornerRadiusDp: Int = 0,
        shadowDp: Int = 0
    ) {
        val customColor = parseColorOrNull(colorValue)
        val backgroundColor = resolveConfiguredSurfaceColor(bottomNavigation, colorValue)
        val foregroundColor = resolveReadableForeground(backgroundColor)
        val selectedColor = parseColorOrNull(selectedColorValue) ?: foregroundColor
        val unselectedColor = resolveBottomBarUnselectedColor(backgroundColor)
        val resolvedHeightDp = heightDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_HEIGHT_DP
        applySurfaceShape(
            view = bottomNavigation,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_RADIUS_DP,
            shadowDp = shadowDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_SHADOW_DP,
            roundTopCorners = cornerRadiusDp > 0,
            roundBottomCorners = false,
            applyElevationOverlay = customColor == null || shouldFallbackToThemeSurface(bottomNavigation.context, customColor)
        )
        applyViewHeight(bottomNavigation, resolvedHeightDp)
        bottomNavigation.itemIconSize = dpToPx(
            bottomNavigation.context,
            iconSizeDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_ICON_SIZE_DP
        )
        bottomNavigation.itemIconTintList = buildNavigationTintList(selectedColor, unselectedColor)
        bottomNavigation.itemTextColor = buildNavigationTintList(selectedColor, unselectedColor)
        bottomNavigation.itemRippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        disableNavigationBarItemBackground(bottomNavigation)
        disableNavigationBarActiveIndicator(bottomNavigation)
    }

    fun applyTabsTheme(
        tabLayout: TabLayout,
        colorValue: String,
        selectedColorValue: String = "",
        cornerRadiusDp: Int = 0,
        shadowDp: Int = 0
    ) {
        val customColor = parseColorOrNull(colorValue)
        val backgroundColor = resolveConfiguredSurfaceColor(tabLayout, colorValue)
        val selectedColor = parseColorOrNull(selectedColorValue) ?: resolveReadableForeground(backgroundColor)
        val unselectedColor = resolveBottomBarUnselectedColor(backgroundColor)
        applySurfaceShape(
            view = tabLayout,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_RADIUS_DP,
            shadowDp = shadowDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_SHADOW_DP,
            roundTopCorners = false,
            roundBottomCorners = cornerRadiusDp > 0,
            applyElevationOverlay = customColor == null || shouldFallbackToThemeSurface(tabLayout.context, customColor)
        )
        tabLayout.setTabTextColors(unselectedColor, selectedColor)
        tabLayout.setSelectedTabIndicatorColor(selectedColor)
        tabLayout.tabRippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    fun applyDrawerTheme(
        drawerContainer: View,
        navigationView: NavigationView,
        headerView: View?,
        colorValue: String,
        cornerRadiusDp: Int = DEFAULT_DRAWER_RADIUS_DP
    ) {
        val backgroundColor = resolveConfiguredSurfaceColor(navigationView, colorValue)
        val foregroundColor = resolveReadableForeground(backgroundColor)
        val shapeAppearance = ShapeAppearanceModel.builder()
            .setTopRightCorner(CornerFamily.ROUNDED, dpToPx(navigationView.context, cornerRadiusDp.coerceAtLeast(0)).toFloat())
            .setBottomRightCorner(CornerFamily.ROUNDED, dpToPx(navigationView.context, cornerRadiusDp.coerceAtLeast(0)).toFloat())
            .build()
        val radiusPx = dpToPx(
            navigationView.context,
            cornerRadiusDp.coerceAtLeast(0)
        ).toFloat()
        drawerContainer.background = MaterialShapeDrawable(
            shapeAppearance
        ).apply {
            initializeElevationOverlay(drawerContainer.context)
            fillColor = ColorStateList.valueOf(backgroundColor)
            elevation = dpToPx(drawerContainer.context, DEFAULT_DRAWER_SHADOW_DP).toFloat()
            strokeWidth = 0f
        }
        drawerContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        drawerContainer.clipToOutline = cornerRadiusDp > 0
        navigationView.background = ColorDrawable(Color.TRANSPARENT)
        runCatching {
            NavigationView::class.java
                .getMethod("setShapeAppearanceModel", ShapeAppearanceModel::class.java)
                .invoke(navigationView, shapeAppearance)
        }
        ViewCompat.setElevation(drawerContainer, dpToPx(drawerContainer.context, DEFAULT_DRAWER_SHADOW_DP).toFloat())
        drawerContainer.invalidateOutline()
        drawerContainer.requestLayout()
        navigationView.itemIconTintList = buildNavigationTintList(
            foregroundColor,
            adjustAlpha(foregroundColor, 0.68f)
        )
        navigationView.itemTextColor = buildNavigationTintList(
            foregroundColor,
            adjustAlpha(foregroundColor, 0.68f)
        )
        headerView?.setBackgroundColor(backgroundColor)
        headerView?.findViewById<TextView?>(R.id.titleView)?.setTextColor(foregroundColor)
        headerView?.findViewById<TextView?>(R.id.subtitleView)?.setTextColor(adjustAlpha(foregroundColor, 0.78f))
    }

    fun applyDrawerWidth(drawerContainer: View, widthDp: Int) {
        val layoutParams = drawerContainer.layoutParams ?: return
        val resolvedWidthDp = widthDp.takeIf { it > 0 } ?: DEFAULT_DRAWER_WIDTH_DP
        layoutParams.width = dpToPx(drawerContainer.context, resolvedWidthDp)
        drawerContainer.layoutParams = layoutParams
    }

    private fun tintMenuIcons(menu: Menu, @ColorInt foregroundColor: Int, iconSizePx: Int) {
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon = resizeDrawable(menu.getItem(index).icon, iconSizePx)?.also {
                it.setTint(foregroundColor)
            }
        }
    }

    private fun resizeDrawable(drawable: Drawable?, sizePx: Int): Drawable? {
        val source = drawable ?: return null
        val resized = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
        resized.setBounds(0, 0, sizePx, sizePx)
        return resized
    }

    private fun buildNavigationTintList(
        @ColorInt selectedColor: Int,
        @ColorInt defaultColor: Int
    ): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(selectedColor, defaultColor)
        )
    }

    @ColorInt
    private fun resolveBottomBarUnselectedColor(@ColorInt backgroundColor: Int): Int {
        return if (ColorUtils.calculateLuminance(backgroundColor) > 0.28) {
            DEFAULT_BOTTOM_BAR_UNSELECTED_LIGHT
        } else {
            DEFAULT_BOTTOM_BAR_UNSELECTED_DARK
        }
    }

    private fun disableNavigationBarActiveIndicator(navigationBarView: NavigationBarView) {
        runCatching {
            NavigationBarView::class.java
                .getMethod("setItemActiveIndicatorEnabled", Boolean::class.javaPrimitiveType)
                .invoke(navigationBarView, false)
        }
        runCatching {
            NavigationBarView::class.java
                .getMethod("setItemActiveIndicatorColor", ColorStateList::class.java)
                .invoke(navigationBarView, ColorStateList.valueOf(Color.TRANSPARENT))
        }
    }

    private fun disableNavigationBarItemBackground(navigationBarView: NavigationBarView) {
        runCatching {
            NavigationBarView::class.java
                .getMethod("setItemBackgroundResource", Int::class.javaPrimitiveType)
                .invoke(navigationBarView, 0)
        }
    }

    @ColorInt
    private fun resolveReadableForeground(@ColorInt backgroundColor: Int): Int {
        return if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    @ColorInt
    private fun adjustAlpha(@ColorInt color: Int, alphaFactor: Float): Int {
        return ColorUtils.setAlphaComponent(color, (Color.alpha(color) * alphaFactor).toInt())
    }

    @ColorInt
    private fun resolveBackgroundColor(view: View): Int {
        return when (val background = view.background) {
            is ColorDrawable -> background.color
            is MaterialShapeDrawable -> background.fillColor?.defaultColor ?: Color.WHITE
            else -> MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        }
    }

    @ColorInt
    private fun resolveConfiguredSurfaceColor(view: View, colorValue: String): Int {
        val customColor = parseColorOrNull(colorValue)
        return when {
            customColor == null -> resolveBackgroundColor(view)
            shouldFallbackToThemeSurface(view.context, customColor) -> resolveBackgroundColor(view)
            else -> customColor
        }
    }

    @ColorInt
    private fun resolveDisplayedSurfaceColor(view: View, colorValue: String, shadowDp: Int): Int {
        val backgroundColor = resolveConfiguredSurfaceColor(view, colorValue)
        val customColor = parseColorOrNull(colorValue)
        val shouldApplyElevationOverlay =
            customColor == null || shouldFallbackToThemeSurface(view.context, customColor)
        if (!shouldApplyElevationOverlay) {
            return backgroundColor
        }
        val elevationPx = dpToPx(view.context, shadowDp.coerceAtLeast(0)).toFloat()
        return ElevationOverlayProvider(view.context).compositeOverlayIfNeeded(backgroundColor, elevationPx)
    }

    private fun shouldFallbackToThemeSurface(context: Context, @ColorInt color: Int): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES &&
            Color.alpha(color) == 255 &&
            Color.red(color) == 255 &&
            Color.green(color) == 255 &&
            Color.blue(color) == 255
    }

    private fun applySurfaceShape(
        view: View,
        @ColorInt backgroundColor: Int,
        cornerRadiusDp: Int,
        shadowDp: Int,
        roundTopCorners: Boolean,
        roundBottomCorners: Boolean,
        applyElevationOverlay: Boolean
    ) {
        val radiusPx = dpToPx(view.context, cornerRadiusDp).toFloat()
        val elevationPx = dpToPx(view.context, shadowDp).toFloat()
        val shapeAppearance = ShapeAppearanceModel.builder().apply {
            setTopLeftCorner(
                CornerFamily.ROUNDED,
                if (roundTopCorners) radiusPx else 0f
            )
            setTopRightCorner(
                CornerFamily.ROUNDED,
                if (roundTopCorners) radiusPx else 0f
            )
            setBottomLeftCorner(
                CornerFamily.ROUNDED,
                if (roundBottomCorners) radiusPx else 0f
            )
            setBottomRightCorner(
                CornerFamily.ROUNDED,
                if (roundBottomCorners) radiusPx else 0f
            )
        }.build()
        view.background = MaterialShapeDrawable(shapeAppearance).apply {
            if (applyElevationOverlay) {
                initializeElevationOverlay(view.context)
            }
            fillColor = ColorStateList.valueOf(backgroundColor)
            strokeWidth = 0f
            elevation = elevationPx
        }
        ViewCompat.setElevation(view, elevationPx)
    }

    private fun applyViewHeight(view: View, heightDp: Int) {
        val resolvedHeightPx = dpToPx(view.context, heightDp)
        view.minimumHeight = resolvedHeightPx
        val layoutParams = view.layoutParams ?: return
        if (layoutParams.height != resolvedHeightPx) {
            layoutParams.height = resolvedHeightPx
            view.layoutParams = layoutParams
        }
    }

    private fun dpToPx(context: Context, valueDp: Int): Int {
        return (valueDp.coerceAtLeast(0) * context.resources.displayMetrics.density).toInt()
    }

    @ColorInt
    private fun parseColorOrNull(value: String?): Int? {
        val candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }
        return runCatching { Color.parseColor(candidate) }.getOrNull()
    }

    private const val DEFAULT_TOP_BAR_RADIUS_DP = 0
    private const val DEFAULT_TOP_BAR_SHADOW_DP = 0
    private const val DEFAULT_TOP_BAR_HEIGHT_DP = 60
    private const val DEFAULT_TOP_BAR_ICON_SIZE_DP = 24
    private const val DEFAULT_BOTTOM_BAR_RADIUS_DP = 0
    private const val DEFAULT_BOTTOM_BAR_SHADOW_DP = 0
    private const val DEFAULT_BOTTOM_BAR_HEIGHT_DP = 68
    private const val DEFAULT_BOTTOM_BAR_ICON_SIZE_DP = 22
    private const val DEFAULT_BOTTOM_BAR_UNSELECTED_LIGHT = 0xFF4B5563.toInt()
    private const val DEFAULT_BOTTOM_BAR_UNSELECTED_DARK = 0xB3FFFFFF.toInt()
    private const val DEFAULT_DRAWER_WIDTH_DP = 320
    private const val DEFAULT_DRAWER_RADIUS_DP = 0
    private const val DEFAULT_DRAWER_SHADOW_DP = 0
}
