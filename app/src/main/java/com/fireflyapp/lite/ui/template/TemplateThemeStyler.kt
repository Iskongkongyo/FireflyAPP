package com.fireflyapp.lite.ui.template

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

object TemplateThemeStyler {
    fun applyTopBarTheme(
        toolbar: MaterialToolbar,
        colorValue: String,
        cornerRadiusDp: Int = 0,
        shadowDp: Int = 0
    ) {
        val customColor = parseColorOrNull(colorValue)
        val backgroundColor = customColor ?: resolveBackgroundColor(toolbar)
        val foregroundColor = resolveReadableForeground(backgroundColor)
        applySurfaceShape(
            view = toolbar,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_RADIUS_DP,
            shadowDp = shadowDp.takeIf { it > 0 } ?: DEFAULT_TOP_BAR_SHADOW_DP,
            roundTopCorners = false,
            roundBottomCorners = cornerRadiusDp > 0,
            applyElevationOverlay = customColor == null
        )
        toolbar.contentInsetStartWithNavigation = dpToPx(toolbar.context, 14)
        toolbar.contentInsetEndWithActions = dpToPx(toolbar.context, 10)
        toolbar.titleMarginStart = dpToPx(toolbar.context, 8)
        toolbar.titleMarginEnd = dpToPx(toolbar.context, 12)
        toolbar.setTitleTextColor(foregroundColor)
        toolbar.setSubtitleTextColor(foregroundColor)
        toolbar.navigationIcon?.setTint(foregroundColor)
        toolbar.overflowIcon?.setTint(foregroundColor)
        tintMenuIcons(toolbar.menu, foregroundColor)
    }

    fun applyTopBarStatusBarTheme(
        window: Window,
        anchorView: View,
        colorValue: String,
        fallbackView: View
    ): Int {
        val backgroundColor = resolveThemeColor(colorValue, fallbackView)
        window.statusBarColor = backgroundColor
        WindowInsetsControllerCompat(window, anchorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(backgroundColor) > 0.5
        return backgroundColor
    }

    @ColorInt
    fun resolveThemeColor(colorValue: String, fallbackView: View): Int {
        return parseColorOrNull(colorValue) ?: resolveBackgroundColor(fallbackView)
    }

    fun applyBottomBarTheme(
        bottomNavigation: NavigationBarView,
        colorValue: String,
        selectedColorValue: String = "",
        cornerRadiusDp: Int = 0,
        shadowDp: Int = 0
    ) {
        val customColor = parseColorOrNull(colorValue)
        val backgroundColor = customColor ?: resolveBackgroundColor(bottomNavigation)
        val foregroundColor = resolveReadableForeground(backgroundColor)
        val selectedColor = parseColorOrNull(selectedColorValue) ?: foregroundColor
        val unselectedColor = resolveBottomBarUnselectedColor(backgroundColor)
        applySurfaceShape(
            view = bottomNavigation,
            backgroundColor = backgroundColor,
            cornerRadiusDp = cornerRadiusDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_RADIUS_DP,
            shadowDp = shadowDp.takeIf { it > 0 } ?: DEFAULT_BOTTOM_BAR_SHADOW_DP,
            roundTopCorners = cornerRadiusDp > 0,
            roundBottomCorners = false,
            applyElevationOverlay = customColor == null
        )
        bottomNavigation.itemIconTintList = buildNavigationTintList(selectedColor, unselectedColor)
        bottomNavigation.itemTextColor = buildNavigationTintList(selectedColor, unselectedColor)
        bottomNavigation.itemRippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        disableNavigationBarItemBackground(bottomNavigation)
        disableNavigationBarActiveIndicator(bottomNavigation)
    }

    fun applyDrawerTheme(
        drawerContainer: View,
        navigationView: NavigationView,
        headerView: View?,
        colorValue: String,
        cornerRadiusDp: Int = DEFAULT_DRAWER_RADIUS_DP
    ) {
        val backgroundColor = parseColorOrNull(colorValue) ?: resolveBackgroundColor(navigationView)
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

    private fun tintMenuIcons(menu: Menu, @ColorInt foregroundColor: Int) {
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon?.setTint(foregroundColor)
        }
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
            else -> Color.WHITE
        }
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
    private const val DEFAULT_BOTTOM_BAR_RADIUS_DP = 0
    private const val DEFAULT_BOTTOM_BAR_SHADOW_DP = 0
    private const val DEFAULT_BOTTOM_BAR_UNSELECTED_LIGHT = 0xFF4B5563.toInt()
    private const val DEFAULT_BOTTOM_BAR_UNSELECTED_DARK = 0xB3FFFFFF.toInt()
    private const val DEFAULT_DRAWER_WIDTH_DP = 320
    private const val DEFAULT_DRAWER_RADIUS_DP = 0
    private const val DEFAULT_DRAWER_SHADOW_DP = 0
}
