package com.fireflyapp.lite.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayout
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.databinding.FragmentTopBarTabsTemplateBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment

class TopBarTabsTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentTopBarTabsTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var ruleTitleOverride: String? = null
    private var pageWantsTopBar: Boolean = true
    private var pageWantsTabs: Boolean = true
    private var followPageTitle: Boolean = true
    private var currentNavigationItemId: Int? = null
    private var rootNavigationItemId: Int? = null
    private var currentStatusTopInset: Int = 0
    private var immersiveStatusBarEnabled: Boolean = false

    private val mainViewModel: MainViewModel by activityViewModels()
    private val webFragment: WebContainerFragment?
        get() = childFragmentManager.findFragmentByTag(WEB_FRAGMENT_TAG) as? WebContainerFragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopBarTabsTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val shellConfig = config.shell
        immersiveStatusBarEnabled = config.browser.immersiveStatusBar
        rootNavigationItemId = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = shellConfig.defaultNavigationItemId
        ).id.hashCode()
        applyWindowInsets(config.browser.immersiveStatusBar)
        setupToolbar(config.app.name)
        followPageTitle = shellConfig.topBarFollowPageTitle
        binding.toolbar.isTitleCentered = shellConfig.topBarTitleCentered
        binding.toolbar.navigationIcon = if (shellConfig.topBarShowBackButton) {
            AppCompatResources.getDrawable(
                requireContext(),
                TemplateActionIconResolver.resolveBack(shellConfig.topBarBackIcon)
            )
        } else {
            null
        }
        binding.toolbar.menu.findItem(R.id.action_home)?.icon = AppCompatResources.getDrawable(
            requireContext(),
            TemplateActionIconResolver.resolveHome(shellConfig.topBarHomeIcon)
        )
        binding.toolbar.menu.findItem(R.id.action_home)?.isVisible = shellConfig.topBarShowHomeButton
        binding.toolbar.menu.findItem(R.id.action_refresh)?.icon = AppCompatResources.getDrawable(
            requireContext(),
            TemplateActionIconResolver.resolveRefresh(shellConfig.topBarRefreshIcon)
        )
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
            shadowDp = shellConfig.topBarShadowDp,
            roundBottomCorners = false
        )
        TemplateThemeStyler.applyTabsTheme(
            tabLayout = binding.tabs,
            colorValue = shellConfig.bottomBarThemeColor,
            selectedColorValue = shellConfig.bottomBarSelectedColor,
            cornerRadiusDp = shellConfig.bottomBarCornerRadiusDp,
            shadowDp = shellConfig.bottomBarShadowDp
        )
        setupTabs(items, shellConfig)
        applyStatusBarTheme()

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
            selectTabByItemId(items, currentNavigationItemId)
            binding.toolbar.title = initialItem.title
        }
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
    }

    override fun onPageProgressChanged(progress: Int) {
        val showProgressBar = mainViewModel.requireConfig().browser.showPageProgressBar
        binding.progressIndicator.isVisible = showProgressBar && progress in 0..99
        binding.progressIndicator.progress = progress
    }

    override fun onPageStateResolved(state: ResolvedPageState) {
        ruleTitleOverride = state.title
        pageWantsTopBar = state.showTopBar
        pageWantsTabs = state.showBottomBar
        binding.topBarContainer.isVisible = state.showTopBar
        updateTabsVisibility()
        applyTopInset()
        applyStatusBarTheme()
        if (!state.title.isNullOrBlank()) {
            binding.toolbar.title = state.title
        }
    }

    override fun onDestroyView() {
        ruleTitleOverride = null
        pageWantsTopBar = true
        pageWantsTabs = true
        followPageTitle = true
        currentNavigationItemId = null
        rootNavigationItemId = null
        currentStatusTopInset = 0
        immersiveStatusBarEnabled = false
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar(defaultTitle: String) {
        binding.toolbar.title = defaultTitle
        if (mainViewModel.requireConfig().shell.topBarShowBackButton) {
            binding.toolbar.setNavigationOnClickListener {
                if (!handleBackPressed()) {
                    requireActivity().finish()
                }
            }
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

    private fun setupTabs(
        items: List<NavigationItem>,
        shellConfig: com.fireflyapp.lite.data.model.ShellConfig
    ) {
        binding.tabs.removeAllTabs()
        binding.tabs.tabMode = if (items.size > 4) TabLayout.MODE_SCROLLABLE else TabLayout.MODE_FIXED
        binding.tabs.tabGravity = if (items.size > 4) TabLayout.GRAVITY_CENTER else TabLayout.GRAVITY_FILL
        items.forEach { item ->
            binding.tabs.addTab(
                binding.tabs.newTab().setText(item.title.ifBlank { item.id })
            )
        }
        TemplateNavigationBadgeHelper.applyToTabs(
            tabLayout = binding.tabs,
            items = items,
            badgeColorValue = shellConfig.bottomBarBadgeColor,
            badgeTextColorValue = shellConfig.bottomBarBadgeTextColor,
            badgeGravityValue = shellConfig.bottomBarBadgeGravity,
            maxCharacterCount = shellConfig.bottomBarBadgeMaxCharacterCount,
            horizontalOffsetDp = shellConfig.bottomBarBadgeHorizontalOffsetDp,
            verticalOffsetDp = shellConfig.bottomBarBadgeVerticalOffsetDp
        )
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val item = items.getOrNull(tab.position) ?: return
                if (currentNavigationItemId == item.id.hashCode() && webFragment != null) {
                    return
                }
                currentNavigationItemId = item.id.hashCode()
                if (!item.title.isBlank()) {
                    binding.toolbar.title = item.title
                }
                webFragment?.loadUrl(item.url, resetHistory = shouldResetHistoryOnNavigation())
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun navigateHome() {
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val homeTarget = TemplateTopBarActionResolver.resolveHomeTarget(
            config = config,
            navigationItems = items
        )
        val matchingItem = items.firstOrNull { it.url == homeTarget.url }
        currentNavigationItemId = matchingItem?.id?.hashCode()
        selectTabByItemId(items, currentNavigationItemId)
        webFragment?.loadUrl(homeTarget.url, resetHistory = shouldResetHistoryOnNavigation())
        if (!homeTarget.title.isNullOrBlank()) {
            binding.toolbar.title = homeTarget.title
        }
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
        currentNavigationItemId = rootItem.id.hashCode()
        selectTabByItemId(items, currentNavigationItemId)
        if (webFragment?.currentUrl() == rootItem.url) {
            binding.toolbar.title = rootItem.title
            return true
        }
        webFragment?.loadUrl(rootItem.url, resetHistory = true)
        binding.toolbar.title = rootItem.title
        return true
    }

    private fun selectTabByItemId(items: List<NavigationItem>, itemId: Int?) {
        val targetIndex = items.indexOfFirst { it.id.hashCode() == itemId }
        if (targetIndex >= 0) {
            binding.tabs.getTabAt(targetIndex)?.select()
        }
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
                    selectTabByItemId(items, currentNavigationItemId)
                    if (!targetItem.title.isNullOrBlank()) {
                        binding.toolbar.title = targetItem.title
                    }
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

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val root = binding.root
        val topBarContainer = binding.topBarContainer
        val tabsContainer = binding.tabsContainer
        val initialRootTop = root.paddingTop
        val initialTopBarTop = topBarContainer.paddingTop
        val initialTabsTop = tabsContainer.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            currentStatusTopInset = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            root.setTag(R.id.mainFragmentContainer, initialRootTop)
            topBarContainer.setTag(R.id.topBarContainer, initialTopBarTop)
            tabsContainer.setTag(R.id.tabs, initialTabsTop)
            applyTopInset()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun applyTopInset() {
        val root = _binding?.root ?: return
        val topBarContainer = _binding?.topBarContainer ?: return
        val tabsContainer = _binding?.tabsContainer ?: return
        val initialRootTop = (root.getTag(R.id.mainFragmentContainer) as? Int) ?: 0
        val initialTopBarTop = (topBarContainer.getTag(R.id.topBarContainer) as? Int) ?: 0
        val initialTabsTop = (tabsContainer.getTag(R.id.tabs) as? Int) ?: 0
        root.updatePadding(
            top = initialRootTop + if (pageWantsTopBar || pageWantsTabs) 0 else currentStatusTopInset
        )
        topBarContainer.updatePadding(
            top = initialTopBarTop + if (pageWantsTopBar) currentStatusTopInset else 0
        )
        tabsContainer.updatePadding(
            top = initialTabsTop + if (!pageWantsTopBar && pageWantsTabs) currentStatusTopInset else 0
        )
    }

    private fun updateTabsVisibility() {
        _binding?.tabsContainer?.isVisible = pageWantsTabs
    }

    private fun applyStatusBarTheme() {
        val binding = _binding ?: return
        if (immersiveStatusBarEnabled) {
            return
        }
        when {
            pageWantsTopBar -> TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = mainViewModel.requireConfig().shell.topBarThemeColor,
                fallbackView = binding.toolbar
            )

            pageWantsTabs -> TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = mainViewModel.requireConfig().shell.bottomBarThemeColor,
                fallbackView = binding.tabs
            )

            else -> TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = "",
                fallbackView = binding.root
            )
        }
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
        const val MAX_ITEMS = 5
    }
}
