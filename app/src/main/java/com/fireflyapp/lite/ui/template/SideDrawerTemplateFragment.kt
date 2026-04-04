package com.fireflyapp.lite.ui.template

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.databinding.FragmentSideDrawerTemplateBinding
import com.fireflyapp.lite.databinding.ViewDrawerHeaderBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch

class SideDrawerTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentSideDrawerTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var drawerHeaderBinding: ViewDrawerHeaderBinding? = null
    private var ruleTitleOverride: String? = null
    private var followPageTitle: Boolean = true
    private var currentNavigationItemId: Int? = null
    private var rootNavigationItemId: Int? = null
    private var drawerHeaderBasePaddingTop: Int = 0
    private var drawerHeaderBasePaddingBottom: Int = 0
    private var drawerHeaderBasePaddingStart: Int = 0
    private var drawerHeaderBasePaddingEnd: Int = 0
    private var pageWantsTopBar: Boolean = true
    private var currentStatusTopInset: Int = 0

    private val mainViewModel: MainViewModel by activityViewModels()
    private val projectId: String?
        get() = mainViewModel.uiState.value.projectId
    private val webFragment: WebContainerFragment?
        get() = childFragmentManager.findFragmentByTag(WEB_FRAGMENT_TAG) as? WebContainerFragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSideDrawerTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        val navigationItems = config.navigation.items
        val shellConfig = config.shell
        rootNavigationItemId = TemplateNavigationResolver.resolveInitialItem(
            items = navigationItems,
            preferredId = shellConfig.defaultNavigationItemId
        ).id.hashCode()
        applyWindowInsets(config.browser.immersiveStatusBar)
        setupToolbar(config.app.name)
        setupDrawerMenu(navigationItems)
        setupDrawerHeader(
            defaultTitle = config.app.name,
            headerTitle = shellConfig.drawerHeaderTitle,
            headerSubtitle = shellConfig.drawerHeaderSubtitle,
            backgroundColor = shellConfig.drawerHeaderBackgroundColor,
            wallpaperEnabled = shellConfig.drawerWallpaperEnabled,
            wallpaperPath = shellConfig.drawerWallpaperPath,
            wallpaperHeightDp = shellConfig.drawerWallpaperHeightDp,
            avatarEnabled = shellConfig.drawerAvatarEnabled,
            avatarPath = shellConfig.drawerAvatarPath
        )
        followPageTitle = shellConfig.topBarFollowPageTitle
        binding.toolbar.isTitleCentered = shellConfig.topBarTitleCentered
        binding.toolbar.navigationIcon =
            TemplateActionIconResolver.resolveDrawer(requireContext(), projectId, shellConfig.drawerMenuIcon)
        binding.toolbar.menu.findItem(R.id.action_home)?.icon =
            TemplateActionIconResolver.resolveHome(requireContext(), projectId, shellConfig.topBarHomeIcon)
        binding.toolbar.menu.findItem(R.id.action_home)?.isVisible = shellConfig.topBarShowHomeButton
        binding.toolbar.menu.findItem(R.id.action_refresh)?.icon =
            TemplateActionIconResolver.resolveRefresh(requireContext(), projectId, shellConfig.topBarRefreshIcon)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = shellConfig.topBarShowRefreshButton
        val topBarColor = TemplateThemeStyler.resolveThemeColor(
            colorValue = shellConfig.topBarThemeColor,
            fallbackView = binding.toolbar
        )
        binding.topBarContainer.setBackgroundColor(topBarColor)
        TemplateThemeStyler.applyTopBarTheme(
            toolbar = binding.toolbar,
            colorValue = shellConfig.topBarThemeColor,
            cornerRadiusDp = shellConfig.topBarCornerRadiusDp,
            shadowDp = shellConfig.topBarShadowDp
        )
        if (!config.browser.immersiveStatusBar) {
            TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = shellConfig.topBarThemeColor,
                fallbackView = binding.toolbar
            )
        }
        TemplateThemeStyler.applyDrawerWidth(binding.drawerContainer, shellConfig.drawerWidthDp)
        TemplateThemeStyler.applyDrawerTheme(
            drawerContainer = binding.drawerContainer,
            navigationView = binding.drawerNavigation,
            headerView = drawerHeaderBinding?.root,
            colorValue = shellConfig.topBarThemeColor,
            cornerRadiusDp = shellConfig.drawerCornerRadiusDp
        )

        if (savedInstanceState == null) {
            val initialItem = TemplateNavigationResolver.resolveInitialItem(
                items = navigationItems,
                preferredId = shellConfig.defaultNavigationItemId
            )
            childFragmentManager.beginTransaction()
                .replace(
                    binding.webContainer.id,
                    WebContainerFragment.newInstance(initialItem.url),
                    WEB_FRAGMENT_TAG
                )
                .commitNow()
            currentNavigationItemId = initialItem.id.hashCode()
            binding.drawerNavigation.setCheckedItem(initialItem.id.hashCode())
            binding.toolbar.title = initialItem.title
        }
        TemplateNavigationStateIconHelper.applyToDrawer(
            context = requireContext(),
            projectId = projectId,
            navigationView = binding.drawerNavigation,
            items = navigationItems,
            selectedItemId = currentNavigationItemId
        )
        bindSwipeNavigation(navigationItems)
    }

    override fun openPage(url: String, title: String?) {
        if (!title.isNullOrBlank()) {
            binding.toolbar.title = title
        }
        webFragment?.loadUrl(url)
    }

    override fun handleBackPressed(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        if (webFragment?.exitFullscreen() == true) {
            return true
        }
        when (webFragment?.resolveBackNavigationAction()) {
            WebContainerFragment.BackNavigationAction.HANDLED -> return true
            WebContainerFragment.BackNavigationAction.GO_HOME -> return navigateToRootItemIfNeeded()
            else -> Unit
        }
        if (shouldResetHistoryOnNavigation() && currentNavigationItemId != rootNavigationItemId) {
            return navigateToRootItemIfNeeded()
        }
        return false
    }

    override fun onPageTitleChanged(title: String) {
        if (followPageTitle && title.isNotBlank() && ruleTitleOverride.isNullOrBlank()) {
            binding.toolbar.title = title
        }
    }

    override fun onPageProgressChanged(progress: Int) {
        val showProgressBar = mainViewModel.requireConfig().browser.showPageProgressBar
        binding.progressIndicator.isVisible = showProgressBar && progress in 0..99
        binding.progressIndicator.progress = progress
    }

    override fun onPageStateResolved(state: ResolvedPageState) {
        ruleTitleOverride = state.title
        pageWantsTopBar = state.showTopBar
        binding.topBarContainer.isVisible = state.showTopBar
        applyTopInset()
        if (!state.title.isNullOrBlank()) {
            binding.toolbar.title = state.title
        }
    }

    override fun onDestroyView() {
        drawerHeaderBinding?.wallpaperView?.setImageDrawable(null)
        drawerHeaderBinding?.avatarView?.setImageDrawable(null)
        drawerHeaderBinding = null
        ruleTitleOverride = null
        followPageTitle = true
        currentNavigationItemId = null
        rootNavigationItemId = null
        pageWantsTopBar = true
        currentStatusTopInset = 0
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(defaultTitle: String) {
        binding.toolbar.title = defaultTitle
        binding.toolbar.setNavigationOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_home -> {
                    val shellConfig = mainViewModel.requireConfig().shell
                    if (TemplateTopBarActionResolver.isRunJavaScriptBehavior(shellConfig.topBarHomeBehavior)) {
                        webFragment?.runJavaScript(shellConfig.topBarHomeScript)
                    } else {
                        navigateHome()
                    }
                    true
                }
                R.id.action_refresh -> {
                    val shellConfig = mainViewModel.requireConfig().shell
                    TemplateTopBarActionResolver.performRefresh(
                        fragment = webFragment,
                        behavior = shellConfig.topBarRefreshBehavior,
                        script = shellConfig.topBarRefreshScript
                    )
                    true
                }

                else -> false
            }
        }
    }

    private fun setupDrawerMenu(items: List<NavigationItem>) {
        binding.drawerNavigation.menu.clear()
        items.forEachIndexed { index, item ->
            binding.drawerNavigation.menu.add(
                Menu.NONE,
                item.id.hashCode(),
                index,
                TemplateNavigationBadgeHelper.formatDrawerTitle(item)
            )
                .setIcon(TemplateNavigationIconResolver.resolve(requireContext(), projectId, item, index))
        }
        binding.drawerNavigation.setNavigationItemSelectedListener { menuItem ->
            val item = items.firstOrNull { it.id.hashCode() == menuItem.itemId }
                ?: return@setNavigationItemSelectedListener false
            binding.drawerNavigation.setCheckedItem(menuItem.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            if (currentNavigationItemId != menuItem.itemId || webFragment == null) {
                currentNavigationItemId = menuItem.itemId
                TemplateNavigationStateIconHelper.applyToDrawer(
                    context = requireContext(),
                    projectId = projectId,
                    navigationView = binding.drawerNavigation,
                    items = items,
                    selectedItemId = currentNavigationItemId
                )
                if (!item.title.isNullOrBlank()) {
                    binding.toolbar.title = item.title
                }
                webFragment?.loadUrl(item.url, resetHistory = shouldResetHistoryOnNavigation())
            }
            true
        }
    }

    private fun navigateToRootItemIfNeeded(): Boolean {
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items
        val rootItem = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = config.shell.defaultNavigationItemId
        )
        if (rootItem.url.isBlank()) {
            return false
        }
        if (webFragment?.currentUrl() == rootItem.url) {
            currentNavigationItemId = rootItem.id.hashCode()
            binding.drawerNavigation.setCheckedItem(rootItem.id.hashCode())
            TemplateNavigationStateIconHelper.applyToDrawer(
                context = requireContext(),
                projectId = projectId,
                navigationView = binding.drawerNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            binding.toolbar.title = rootItem.title
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }
        currentNavigationItemId = rootItem.id.hashCode()
        binding.drawerNavigation.setCheckedItem(rootItem.id.hashCode())
        TemplateNavigationStateIconHelper.applyToDrawer(
            context = requireContext(),
            projectId = projectId,
            navigationView = binding.drawerNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        webFragment?.loadUrl(rootItem.url, resetHistory = true)
        binding.toolbar.title = rootItem.title
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun bindSwipeNavigation(items: List<NavigationItem>) {
        webFragment?.setNavigationSwipeListener(
            if (mainViewModel.requireConfig().shell.enableSwipeNavigation && items.size > 1) {
                swipe@{ direction ->
                    val targetItem = TemplateSwipeNavigationHelper.resolveAdjacentItem(
                        items = items,
                        currentItemId = currentNavigationItemId,
                        direction = direction
                    ) ?: return@swipe
                    currentNavigationItemId = targetItem.id.hashCode()
                    binding.drawerNavigation.setCheckedItem(targetItem.id.hashCode())
                    TemplateNavigationStateIconHelper.applyToDrawer(
                        context = requireContext(),
                        projectId = projectId,
                        navigationView = binding.drawerNavigation,
                        items = items,
                        selectedItemId = currentNavigationItemId
                    )
                    if (!targetItem.title.isNullOrBlank()) {
                        binding.toolbar.title = targetItem.title
                    }
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    webFragment?.loadUrlWithSwipeTransition(
                        url = targetItem.url,
                        direction = direction,
                        resetHistory = shouldResetHistoryOnNavigation()
                    )
                }
            } else {
                null
            }
        )
    }

    private fun shouldResetHistoryOnNavigation(): Boolean {
        return mainViewModel.requireConfig().shell.navigationBackBehavior == "reset_on_navigation"
    }

    private fun setupDrawerHeader(
        defaultTitle: String,
        headerTitle: String,
        headerSubtitle: String,
        backgroundColor: String,
        wallpaperEnabled: Boolean,
        wallpaperPath: String,
        wallpaperHeightDp: Int,
        avatarEnabled: Boolean,
        avatarPath: String
    ) {
        while (binding.drawerNavigation.headerCount > 0) {
            binding.drawerNavigation.removeHeaderView(binding.drawerNavigation.getHeaderView(0))
        }
        val headerBinding = ViewDrawerHeaderBinding.inflate(layoutInflater, binding.drawerNavigation, false)
        drawerHeaderBasePaddingTop = headerBinding.headerContent.paddingTop
        drawerHeaderBasePaddingBottom = headerBinding.headerContent.paddingBottom
        drawerHeaderBasePaddingStart = headerBinding.headerContent.paddingStart
        drawerHeaderBasePaddingEnd = headerBinding.headerContent.paddingEnd
        headerBinding.avatarView.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()
        applyHeaderBackground(headerBinding, backgroundColor)
        headerBinding.titleView.text = headerTitle.ifBlank { defaultTitle }
        headerBinding.subtitleView.apply {
            isVisible = headerSubtitle.isNotBlank()
            text = headerSubtitle
        }
        applyHeaderMediaStyle(
            headerBinding = headerBinding,
            wallpaperHeightDp = wallpaperHeightDp
        )
        loadHeaderMedia(
            headerBinding = headerBinding,
            wallpaperEnabled = wallpaperEnabled,
            wallpaperPath = wallpaperPath,
            avatarEnabled = avatarEnabled,
            avatarPath = avatarPath
        )
        binding.drawerNavigation.addHeaderView(headerBinding.root)
        drawerHeaderBinding = headerBinding
        ViewCompat.requestApplyInsets(binding.drawerLayout)
    }

    private fun applyHeaderBackground(
        headerBinding: ViewDrawerHeaderBinding,
        backgroundColor: String
    ) {
        parseColorOrNull(backgroundColor)?.let { color ->
            headerBinding.headerSurface.setBackgroundColor(color)
        }
    }

    private fun applyHeaderMediaStyle(
        headerBinding: ViewDrawerHeaderBinding,
        wallpaperHeightDp: Int
    ) {
        headerBinding.wallpaperContainer.layoutParams = headerBinding.wallpaperContainer.layoutParams.apply {
            height = (wallpaperHeightDp.coerceAtLeast(96) * resources.displayMetrics.density).toInt()
        }
        headerBinding.wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
        headerBinding.wallpaperContainer.isVisible = true
    }

    private fun loadHeaderMedia(
        headerBinding: ViewDrawerHeaderBinding,
        wallpaperEnabled: Boolean,
        wallpaperPath: String,
        avatarEnabled: Boolean,
        avatarPath: String
    ) {
        headerBinding.wallpaperView.setImageDrawable(null)
        headerBinding.avatarView.setImageDrawable(null)
        headerBinding.wallpaperView.isVisible = false
        headerBinding.avatarView.isVisible = false
        val wallpaperSource = resolveProjectAssetSource(wallpaperPath)
        val avatarSource = resolveProjectAssetSource(avatarPath)
        viewLifecycleOwner.lifecycleScope.launch {
            val wallpaperBitmap = if (wallpaperEnabled) {
                TemplateHeaderImageLoader.loadBitmap(requireContext(), wallpaperSource)
            } else {
                null
            }
            val avatarBitmap = if (avatarEnabled) {
                TemplateHeaderImageLoader.loadBitmap(requireContext(), avatarSource)
            } else {
                null
            }
            if (_binding == null || drawerHeaderBinding !== headerBinding) {
                return@launch
            }
            if (wallpaperBitmap != null) {
                headerBinding.wallpaperView.setImageBitmap(wallpaperBitmap)
                headerBinding.wallpaperView.isVisible = true
            }
            if (avatarBitmap != null) {
                headerBinding.avatarView.setImageBitmap(avatarBitmap)
                headerBinding.avatarView.isVisible = true
            }
        }
    }

    private fun parseColorOrNull(value: String): Int? {
        val candidate = value.trim()
        if (candidate.isBlank()) {
            return null
        }
        return runCatching { Color.parseColor(candidate) }.getOrNull()
    }

    private fun navigateHome() {
        val config = mainViewModel.requireConfig()
        val homeTarget = TemplateTopBarActionResolver.resolveHomeTarget(
            config = config,
            navigationItems = config.navigation.items
        )
        val matchingItem = config.navigation.items.firstOrNull { it.url == homeTarget.url }
        currentNavigationItemId = matchingItem?.id?.hashCode()
        matchingItem?.let {
            binding.drawerNavigation.setCheckedItem(it.id.hashCode())
        }
        TemplateNavigationStateIconHelper.applyToDrawer(
            context = requireContext(),
            projectId = projectId,
            navigationView = binding.drawerNavigation,
            items = config.navigation.items,
            selectedItemId = currentNavigationItemId
        )
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        webFragment?.loadUrl(homeTarget.url, resetHistory = shouldResetHistoryOnNavigation())
        if (!homeTarget.title.isNullOrBlank()) {
            binding.toolbar.title = homeTarget.title
        }
    }

    private fun resolveProjectAssetSource(relativePath: String): String {
        val trimmed = relativePath.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        val uri = Uri.parse(trimmed)
        if (!uri.scheme.isNullOrBlank()) {
            return trimmed
        }
        val projectId = mainViewModel.uiState.value.projectId.orEmpty().trim()
        if (projectId.isBlank()) {
            return "asset://$trimmed"
        }
        val targetFile = requireContext().filesDir.resolve("projects").resolve(projectId).resolve(trimmed)
        return Uri.fromFile(targetFile).toString()
    }

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val contentContainer = binding.contentContainer
        val topBarContainer = binding.topBarContainer
        val drawerNavigation = binding.drawerNavigation
        val initialContentTop = contentContainer.paddingTop
        val initialTopBarTop = topBarContainer.paddingTop
        val initialNavigationTop = drawerNavigation.paddingTop
        val initialNavigationBottom = drawerNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            currentStatusTopInset = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            contentContainer.setTag(R.id.contentContainer, initialContentTop)
            topBarContainer.setTag(R.id.topBarContainer, initialTopBarTop)
            applyTopInset()
            drawerNavigation.updatePadding(
                top = initialNavigationTop,
                bottom = initialNavigationBottom + navigationBottom
            )
            drawerHeaderBinding?.headerSurface?.updatePadding(
                left = 0,
                top = 0,
                right = 0,
                bottom = 0
            )
            drawerHeaderBinding?.headerContent?.updatePadding(
                left = drawerHeaderBasePaddingStart,
                top = drawerHeaderBasePaddingTop + currentStatusTopInset,
                right = drawerHeaderBasePaddingEnd,
                bottom = drawerHeaderBasePaddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.drawerLayout)
    }

    private fun applyTopInset() {
        val contentContainer = _binding?.contentContainer ?: return
        val topBarContainer = _binding?.topBarContainer ?: return
        val initialContentTop = (contentContainer.getTag(R.id.contentContainer) as? Int) ?: 0
        val initialTopBarTop = (topBarContainer.getTag(R.id.topBarContainer) as? Int) ?: 0
        contentContainer.updatePadding(top = initialContentTop + if (pageWantsTopBar) 0 else currentStatusTopInset)
        topBarContainer.updatePadding(top = initialTopBarTop + if (pageWantsTopBar) currentStatusTopInset else 0)
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
    }
}
