package com.fireflyapp.lite.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.navigation.NavigationBarView
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.databinding.FragmentTopBarBottomTabsTemplateBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment

class TopBarBottomTabsTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentTopBarBottomTabsTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var ruleTitleOverride: String? = null
    private var isImeVisible: Boolean = false
    private var pageWantsBottomBar: Boolean = true
    private var followPageTitle: Boolean = true
    private var pageWantsTopBar: Boolean = true
    private var currentNavigationItemId: Int? = null
    private var rootNavigationItemId: Int? = null
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
        _binding = FragmentTopBarBottomTabsTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val shellConfig = config.shell
        rootNavigationItemId = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = shellConfig.defaultNavigationItemId
        ).id.hashCode()
        applyWindowInsets(config.browser.immersiveStatusBar)
        setupToolbar(config.app.name)
        followPageTitle = shellConfig.topBarFollowPageTitle
        binding.toolbar.isTitleCentered = shellConfig.topBarTitleCentered
        binding.toolbar.navigationIcon = if (shellConfig.topBarShowBackButton) {
            TemplateActionIconResolver.resolveBack(requireContext(), projectId, shellConfig.topBarBackIcon)
        } else {
            null
        }
        binding.toolbar.menu.findItem(R.id.action_home)?.icon =
            TemplateActionIconResolver.resolveHome(requireContext(), projectId, shellConfig.topBarHomeIcon)
        binding.toolbar.menu.findItem(R.id.action_home)?.isVisible = shellConfig.topBarShowHomeButton
        binding.toolbar.menu.findItem(R.id.action_refresh)?.icon =
            TemplateActionIconResolver.resolveRefresh(requireContext(), projectId, shellConfig.topBarRefreshIcon)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = shellConfig.topBarShowRefreshButton
        binding.bottomNavigation.labelVisibilityMode =
            if (shellConfig.bottomBarShowTextLabels) {
                NavigationBarView.LABEL_VISIBILITY_LABELED
            } else {
                NavigationBarView.LABEL_VISIBILITY_UNLABELED
            }
        val topBarColor = TemplateThemeStyler.resolveDisplayedThemeColor(
            colorValue = shellConfig.topBarThemeColor,
            fallbackView = binding.toolbar,
            shadowDp = shellConfig.topBarShadowDp
        )
        binding.topBarContainer.setBackgroundColor(topBarColor)
        TemplateThemeStyler.applyTopBarTheme(
            toolbar = binding.toolbar,
            colorValue = shellConfig.topBarThemeColor,
            heightDp = shellConfig.topBarHeightDp,
            iconSizeDp = shellConfig.topBarIconSizeDp,
            cornerRadiusDp = shellConfig.topBarCornerRadiusDp,
            shadowDp = shellConfig.topBarShadowDp
        )
        if (!config.browser.immersiveStatusBar) {
            TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = shellConfig.topBarThemeColor,
                fallbackView = binding.toolbar,
                shadowDp = shellConfig.topBarShadowDp
            )
        }
        TemplateThemeStyler.applyBottomBarTheme(
            bottomNavigation = binding.bottomNavigation,
            colorValue = shellConfig.bottomBarThemeColor,
            selectedColorValue = shellConfig.bottomBarSelectedColor,
            heightDp = shellConfig.bottomBarHeightDp,
            iconSizeDp = shellConfig.bottomBarIconSizeDp,
            cornerRadiusDp = shellConfig.bottomBarCornerRadiusDp,
            shadowDp = shellConfig.bottomBarShadowDp
        )
        setupBottomNavigation(items, shellConfig)

        if (savedInstanceState == null) {
            val initialItem = TemplateNavigationResolver.resolveInitialItem(
                items = items,
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
            binding.bottomNavigation.selectedItemId = initialItem.id.hashCode()
            binding.toolbar.title = initialItem.title
        }
        syncNavigationContext(items)
        currentNavigationItemId?.let { binding.bottomNavigation.selectedItemId = it }
        TemplateNavigationStateIconHelper.applyToBottomBar(
            context = requireContext(),
            projectId = projectId,
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        bindSwipeNavigation(items)
    }

    override fun openPage(url: String, title: String?) {
        if (!title.isNullOrBlank()) {
            binding.toolbar.title = title
        }
        webFragment?.loadUrl(url)
    }

    override fun handleBackPressed(): Boolean {
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
        syncNavigationStateFromCurrentUrl(updateTitleFallback = false)
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
        pageWantsBottomBar = state.showBottomBar
        applyTopInset()
        updateBottomNavigationVisibility()
        syncNavigationStateFromCurrentUrl(updateTitleFallback = state.title.isNullOrBlank())
        if (!state.title.isNullOrBlank()) {
            binding.toolbar.title = state.title
        }
    }

    override fun onDestroyView() {
        ruleTitleOverride = null
        isImeVisible = false
        pageWantsBottomBar = true
        followPageTitle = true
        pageWantsTopBar = true
        currentNavigationItemId = null
        rootNavigationItemId = null
        currentStatusTopInset = 0
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(defaultTitle: String) {
        binding.toolbar.title = defaultTitle
        if (mainViewModel.requireConfig().shell.topBarShowBackButton) {
            binding.toolbar.setNavigationOnClickListener { handleToolbarBackClick() }
        } else {
            binding.toolbar.setNavigationOnClickListener(null)
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_home -> {
                    val shellConfig = mainViewModel.requireConfig().shell
                    if (TemplateTopBarActionResolver.isRunJavaScriptBehavior(shellConfig.topBarHomeBehavior)) {
                        webFragment?.runJavaScript(shellConfig.topBarHomeScript)
                    } else {
                        navigateToTopBarTarget(shellConfig.topBarHomeBehavior)
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

    private fun handleToolbarBackClick() {
        val shellConfig = mainViewModel.requireConfig().shell
        when {
            TemplateTopBarActionResolver.isRunJavaScriptBehavior(shellConfig.topBarBackBehavior) -> {
                webFragment?.runJavaScript(shellConfig.topBarBackScript)
            }

            TemplateTopBarActionResolver.isNavigationTargetBehavior(shellConfig.topBarBackBehavior) -> {
                navigateToTopBarTarget(shellConfig.topBarBackBehavior)
            }

            else -> {
                if (!handleBackPressed()) {
                    requireActivity().finish()
                }
            }
        }
    }

    private fun navigateToTopBarTarget(behavior: String) {
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val homeTarget = TemplateTopBarActionResolver.resolveNavigationTarget(
            config = config,
            navigationItems = items,
            behavior = behavior
        )
        val matchingItem = items.firstOrNull { it.url == homeTarget.url }
        currentNavigationItemId = matchingItem?.id?.hashCode()
        matchingItem?.let {
            binding.bottomNavigation.selectedItemId = it.id.hashCode()
        }
        TemplateNavigationStateIconHelper.applyToBottomBar(
            context = requireContext(),
            projectId = projectId,
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        if (matchingItem != null) {
            webFragment?.loadNavigationUrl(matchingItem, resetHistory = shouldResetHistoryOnNavigation())
        } else {
            webFragment?.loadUrl(homeTarget.url, resetHistory = shouldResetHistoryOnNavigation())
        }
        if (!homeTarget.title.isNullOrBlank()) {
            binding.toolbar.title = homeTarget.title
        }
    }

    private fun setupBottomNavigation(
        items: List<NavigationItem>,
        shellConfig: com.fireflyapp.lite.data.model.ShellConfig
    ) {
        binding.bottomNavigation.menu.clear()
        items.forEachIndexed { index, item ->
            binding.bottomNavigation.menu.add(Menu.NONE, item.id.hashCode(), index, item.title)
                .setIcon(TemplateNavigationIconResolver.resolve(requireContext(), projectId, item, index))
        }
        TemplateNavigationBadgeHelper.apply(
            bottomNavigation = binding.bottomNavigation,
            items = items,
            badgeColorValue = shellConfig.bottomBarBadgeColor,
            badgeTextColorValue = shellConfig.bottomBarBadgeTextColor,
            badgeGravityValue = shellConfig.bottomBarBadgeGravity,
            maxCharacterCount = shellConfig.bottomBarBadgeMaxCharacterCount,
            horizontalOffsetDp = shellConfig.bottomBarBadgeHorizontalOffsetDp,
            verticalOffsetDp = shellConfig.bottomBarBadgeVerticalOffsetDp
        )
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (currentNavigationItemId == menuItem.itemId && webFragment != null) {
                return@setOnItemSelectedListener true
            }
            val item = items.firstOrNull { it.id.hashCode() == menuItem.itemId }
                ?: return@setOnItemSelectedListener false
            currentNavigationItemId = menuItem.itemId
            TemplateNavigationStateIconHelper.applyToBottomBar(
                context = requireContext(),
                projectId = projectId,
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            if (!item.title.isNullOrBlank()) {
                binding.toolbar.title = item.title
            }
            webFragment?.loadNavigationUrl(item, resetHistory = shouldResetHistoryOnNavigation())
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
    }

    private fun navigateToRootItemIfNeeded(): Boolean {
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val rootItem = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = config.shell.defaultNavigationItemId
        )
        if (rootItem.url.isBlank()) {
            return false
        }
        if (webFragment?.currentUrl() == rootItem.url) {
            currentNavigationItemId = rootItem.id.hashCode()
            binding.bottomNavigation.selectedItemId = rootItem.id.hashCode()
            TemplateNavigationStateIconHelper.applyToBottomBar(
                context = requireContext(),
                projectId = projectId,
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            binding.toolbar.title = rootItem.title
            return true
        }
        currentNavigationItemId = rootItem.id.hashCode()
        binding.bottomNavigation.selectedItemId = rootItem.id.hashCode()
        TemplateNavigationStateIconHelper.applyToBottomBar(
            context = requireContext(),
            projectId = projectId,
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        webFragment?.loadNavigationUrl(rootItem, resetHistory = true)
        binding.toolbar.title = rootItem.title
        return true
    }

    private fun syncNavigationStateFromCurrentUrl(updateTitleFallback: Boolean) {
        val binding = _binding ?: return
        val items = mainViewModel.requireConfig().navigation.items.take(MAX_ITEMS)
        val matchedItem = TemplateNavigationResolver.resolveItemForUrl(items, webFragment?.currentUrl())
            ?: return
        val matchedItemId = matchedItem.id.hashCode()
        val navigationChanged = currentNavigationItemId != matchedItemId
        if (navigationChanged) {
            currentNavigationItemId = matchedItemId
            binding.bottomNavigation.selectedItemId = matchedItemId
            TemplateNavigationStateIconHelper.applyToBottomBar(
                context = requireContext(),
                projectId = projectId,
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
        }
        if (updateTitleFallback && navigationChanged && matchedItem.title.isNotBlank()) {
            binding.toolbar.title = matchedItem.title
        }
        syncNavigationContext(items)
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
                    binding.bottomNavigation.selectedItemId = targetItem.id.hashCode()
                    TemplateNavigationStateIconHelper.applyToBottomBar(
                        context = requireContext(),
                        projectId = projectId,
                        bottomNavigation = binding.bottomNavigation,
                        items = items,
                        selectedItemId = currentNavigationItemId
                    )
                    if (!targetItem.title.isNullOrBlank()) {
                        binding.toolbar.title = targetItem.title
                    }
                    webFragment?.loadNavigationUrlWithSwipeTransition(
                        item = targetItem,
                        direction = direction,
                        resetHistory = shouldResetHistoryOnNavigation()
                    )
                }
            } else {
                null
            }
        )
    }

    private fun syncNavigationContext(items: List<NavigationItem>) {
        val resolvedItem = items.firstOrNull { it.id.hashCode() == currentNavigationItemId }
            ?: TemplateNavigationResolver.resolveItemForUrl(items, webFragment?.currentUrl())
            ?: items.firstOrNull()
        currentNavigationItemId = resolvedItem?.id?.hashCode()
        webFragment?.setNavigationItems(items, resolvedItem?.id)
    }

    private fun shouldResetHistoryOnNavigation(): Boolean {
        return mainViewModel.requireConfig().shell.navigationBackBehavior == "reset_on_navigation"
    }

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val root = binding.root
        val topBarContainer = binding.topBarContainer
        val bottomNavigation = binding.bottomNavigation
        val initialRootTop = root.paddingTop
        val initialTopBarTop = topBarContainer.paddingTop
        val initialBottomNavBottom = bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            currentStatusTopInset = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            root.setTag(R.id.mainFragmentContainer, initialRootTop)
            topBarContainer.setTag(R.id.topBarContainer, initialTopBarTop)
            applyTopInset()
            bottomNavigation.updatePadding(
                bottom = initialBottomNavBottom + if (isImeVisible) 0 else navigationBottom
            )
            updateBottomNavigationVisibility()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applyTopInset() {
        val root = _binding?.root ?: return
        val topBarContainer = _binding?.topBarContainer ?: return
        val initialRootTop = (root.getTag(R.id.mainFragmentContainer) as? Int) ?: 0
        val initialTopBarTop = (topBarContainer.getTag(R.id.topBarContainer) as? Int) ?: 0
        root.updatePadding(top = initialRootTop + if (pageWantsTopBar) 0 else currentStatusTopInset)
        topBarContainer.updatePadding(top = initialTopBarTop + if (pageWantsTopBar) currentStatusTopInset else 0)
    }

    private fun updateBottomNavigationVisibility() {
        _binding?.bottomBarContainer?.isVisible = pageWantsBottomBar && !isImeVisible
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
        const val MAX_ITEMS = 5
    }
}
