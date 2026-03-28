package com.fireflyapp.lite.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
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
        binding.bottomNavigation.labelVisibilityMode =
            if (shellConfig.bottomBarShowTextLabels) {
                NavigationBarView.LABEL_VISIBILITY_LABELED
            } else {
                NavigationBarView.LABEL_VISIBILITY_UNLABELED
            }
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
        TemplateThemeStyler.applyBottomBarTheme(
            bottomNavigation = binding.bottomNavigation,
            colorValue = shellConfig.bottomBarThemeColor,
            selectedColorValue = shellConfig.bottomBarSelectedColor,
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
        TemplateNavigationStateIconHelper.applyToBottomBar(
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
        if (webFragment?.handleBackAction() == true) {
            return true
        }
        if (shouldResetHistoryOnNavigation() && currentNavigationItemId != rootNavigationItemId) {
            navigateToRootItem()
            return true
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
        pageWantsBottomBar = state.showBottomBar
        applyTopInset()
        updateBottomNavigationVisibility()
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
                    navigateHome()
                    true
                }
                R.id.action_refresh -> {
                    TemplateTopBarActionResolver.performRefresh(
                        fragment = webFragment,
                        behavior = mainViewModel.requireConfig().shell.topBarRefreshBehavior
                    )
                    true
                }

                else -> false
            }
        }
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
        matchingItem?.let {
            binding.bottomNavigation.selectedItemId = it.id.hashCode()
        }
        TemplateNavigationStateIconHelper.applyToBottomBar(
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        webFragment?.loadUrl(homeTarget.url, resetHistory = shouldResetHistoryOnNavigation())
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
                .setIcon(TemplateNavigationIconResolver.resolve(item, index))
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
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            if (!item.title.isNullOrBlank()) {
                binding.toolbar.title = item.title
            }
            webFragment?.loadUrl(item.url, resetHistory = shouldResetHistoryOnNavigation())
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
    }

    private fun navigateToRootItem() {
        val config = mainViewModel.requireConfig()
        val items = config.navigation.items.take(MAX_ITEMS)
        val rootItem = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = config.shell.defaultNavigationItemId
        )
        currentNavigationItemId = rootItem.id.hashCode()
        binding.bottomNavigation.selectedItemId = rootItem.id.hashCode()
        TemplateNavigationStateIconHelper.applyToBottomBar(
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        webFragment?.loadUrl(rootItem.url, resetHistory = true)
        binding.toolbar.title = rootItem.title
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
                        bottomNavigation = binding.bottomNavigation,
                        items = items,
                        selectedItemId = currentNavigationItemId
                    )
                    if (!targetItem.title.isNullOrBlank()) {
                        binding.toolbar.title = targetItem.title
                    }
                    webFragment?.loadUrl(targetItem.url, resetHistory = shouldResetHistoryOnNavigation())
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
