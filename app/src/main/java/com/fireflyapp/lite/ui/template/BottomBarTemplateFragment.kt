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
import com.fireflyapp.lite.databinding.FragmentBottomBarTemplateBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment

class BottomBarTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentBottomBarTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var ruleTitleOverride: String? = null
    private var isImeVisible: Boolean = false
    private var pageWantsBottomBar: Boolean = true
    private var currentNavigationItemId: Int? = null
    private var rootNavigationItemId: Int? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val webFragment: WebContainerFragment?
        get() = childFragmentManager.findFragmentByTag(WEB_FRAGMENT_TAG) as? WebContainerFragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomBarTemplateBinding.inflate(inflater, container, false)
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
        binding.bottomNavigation.labelVisibilityMode =
            if (shellConfig.bottomBarShowTextLabels) {
                NavigationBarView.LABEL_VISIBILITY_LABELED
            } else {
                NavigationBarView.LABEL_VISIBILITY_UNLABELED
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
                    R.id.webContainer,
                    WebContainerFragment.newInstance(initialItem.url),
                    WEB_FRAGMENT_TAG
                )
                .commitNow()
            currentNavigationItemId = initialItem.id.hashCode()
            requireActivity().title = initialItem.title
        }
        TemplateNavigationStateIconHelper.applyToBottomBar(
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (currentNavigationItemId == menuItem.itemId && webFragment != null) {
                return@setOnItemSelectedListener true
            }
            val item = items.firstOrNull { it.id.hashCode() == menuItem.itemId } ?: return@setOnItemSelectedListener false
            currentNavigationItemId = menuItem.itemId
            TemplateNavigationStateIconHelper.applyToBottomBar(
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            openNavigationPage(item.url, item.title)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = TemplateNavigationResolver.resolveInitialItem(
                items = items,
                preferredId = shellConfig.defaultNavigationItemId
            ).id.hashCode()
        }
        bindSwipeNavigation(items)
    }

    override fun openPage(url: String, title: String?) {
        if (!title.isNullOrBlank()) {
            requireActivity().title = title
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
        if (title.isNotBlank() && ruleTitleOverride.isNullOrBlank()) {
            requireActivity().title = title
        }
    }

    override fun onPageProgressChanged(progress: Int) {
        val showProgressBar = mainViewModel.requireConfig().browser.showPageProgressBar
        binding.progressIndicator.isVisible = showProgressBar && progress in 0..99
        binding.progressIndicator.progress = progress
    }

    override fun onPageStateResolved(state: ResolvedPageState) {
        ruleTitleOverride = state.title
        pageWantsBottomBar = state.showBottomBar
        updateBottomNavigationVisibility()
        if (!state.title.isNullOrBlank()) {
            requireActivity().title = state.title
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
    }

    override fun onDestroyView() {
        ruleTitleOverride = null
        isImeVisible = false
        pageWantsBottomBar = true
        currentNavigationItemId = null
        rootNavigationItemId = null
        _binding = null
        super.onDestroyView()
    }

    private fun openNavigationPage(url: String, title: String?) {
        if (!title.isNullOrBlank()) {
            requireActivity().title = title
        }
        webFragment?.loadUrl(url, resetHistory = shouldResetHistoryOnNavigation())
    }

    private fun openNavigationPageWithSwipeTransition(
        url: String,
        title: String?,
        direction: NavigationSwipeDirection
    ) {
        if (!title.isNullOrBlank()) {
            requireActivity().title = title
        }
        webFragment?.loadUrlWithSwipeTransition(
            url = url,
            direction = direction,
            resetHistory = shouldResetHistoryOnNavigation()
        )
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
                    openNavigationPageWithSwipeTransition(targetItem.url, targetItem.title, direction)
                }
            } else {
                null
            }
        )
    }

    private fun navigateToRootItemIfNeeded(): Boolean {
        val items = mainViewModel.requireConfig().navigation.items.take(MAX_ITEMS)
        val rootItem = TemplateNavigationResolver.resolveInitialItem(
            items = items,
            preferredId = mainViewModel.requireConfig().shell.defaultNavigationItemId
        )
        if (rootItem.url.isBlank()) {
            return false
        }
        if (webFragment?.currentUrl() == rootItem.url) {
            currentNavigationItemId = rootItem.id.hashCode()
            binding.bottomNavigation.selectedItemId = rootItem.id.hashCode()
            TemplateNavigationStateIconHelper.applyToBottomBar(
                bottomNavigation = binding.bottomNavigation,
                items = items,
                selectedItemId = currentNavigationItemId
            )
            requireActivity().title = rootItem.title
            return true
        }
        currentNavigationItemId = rootItem.id.hashCode()
        binding.bottomNavigation.selectedItemId = rootItem.id.hashCode()
        TemplateNavigationStateIconHelper.applyToBottomBar(
            bottomNavigation = binding.bottomNavigation,
            items = items,
            selectedItemId = currentNavigationItemId
        )
        webFragment?.loadUrl(rootItem.url, resetHistory = true)
        requireActivity().title = rootItem.title
        return true
    }

    private fun shouldResetHistoryOnNavigation(): Boolean {
        return mainViewModel.requireConfig().shell.navigationBackBehavior == "reset_on_navigation"
    }

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val root = binding.root
        val bottomNavigation = binding.bottomNavigation
        val initialRootTop = root.paddingTop
        val initialBottomNavBottom = bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusTop = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            root.updatePadding(top = initialRootTop + statusTop)
            bottomNavigation.updatePadding(
                bottom = initialBottomNavBottom + if (isImeVisible) 0 else navigationBottom
            )
            updateBottomNavigationVisibility()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun updateBottomNavigationVisibility() {
        _binding?.bottomBarContainer?.isVisible = pageWantsBottomBar && !isImeVisible
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
        const val MAX_ITEMS = 5
    }
}
