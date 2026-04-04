package com.fireflyapp.lite.ui.template

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.databinding.FragmentTopBarTemplateBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment

class TopBarTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentTopBarTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var ruleTitleOverride: String? = null
    private var followPageTitle: Boolean = true
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
        _binding = FragmentTopBarTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        applyWindowInsets(config.browser.immersiveStatusBar)
        followPageTitle = config.shell.topBarFollowPageTitle

        binding.toolbar.title = config.app.name
        binding.toolbar.isTitleCentered = config.shell.topBarTitleCentered
        binding.toolbar.navigationIcon = if (config.shell.topBarShowBackButton) {
            TemplateActionIconResolver.resolveBack(requireContext(), projectId, config.shell.topBarBackIcon)
        } else {
            null
        }
        binding.toolbar.menu.findItem(R.id.action_home)?.icon =
            TemplateActionIconResolver.resolveHome(requireContext(), projectId, config.shell.topBarHomeIcon)
        binding.toolbar.menu.findItem(R.id.action_home)?.isVisible = config.shell.topBarShowHomeButton
        binding.toolbar.menu.findItem(R.id.action_refresh)?.icon =
            TemplateActionIconResolver.resolveRefresh(requireContext(), projectId, config.shell.topBarRefreshIcon)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = config.shell.topBarShowRefreshButton
        val topBarColor = TemplateThemeStyler.resolveThemeColor(
            colorValue = config.shell.topBarThemeColor,
            fallbackView = binding.toolbar
        )
        binding.topBarContainer.setBackgroundColor(topBarColor)
        TemplateThemeStyler.applyTopBarTheme(
            toolbar = binding.toolbar,
            colorValue = config.shell.topBarThemeColor,
            cornerRadiusDp = config.shell.topBarCornerRadiusDp,
            shadowDp = config.shell.topBarShadowDp
        )
        if (!config.browser.immersiveStatusBar) {
            TemplateThemeStyler.applyTopBarStatusBarTheme(
                window = requireActivity().window,
                anchorView = binding.root,
                colorValue = config.shell.topBarThemeColor,
                fallbackView = binding.toolbar
            )
        }
        if (config.shell.topBarShowBackButton) {
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
                        true
                    } else {
                        navigateHomeIfNeeded()
                    }
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

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.webContainer,
                    WebContainerFragment.newInstance(config.app.defaultUrl),
                    WEB_FRAGMENT_TAG
                )
                .commitNow()
        }
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
        return when (webFragment?.resolveBackNavigationAction()) {
            WebContainerFragment.BackNavigationAction.HANDLED -> true
            WebContainerFragment.BackNavigationAction.GO_HOME -> navigateHomeIfNeeded()
            else -> false
        }
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
        ruleTitleOverride = null
        followPageTitle = true
        pageWantsTopBar = true
        currentStatusTopInset = 0
        _binding = null
        super.onDestroyView()
    }

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val root = binding.root
        val topBarContainer = binding.topBarContainer
        val initialRootTop = root.paddingTop
        val initialTopBarTop = topBarContainer.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            currentStatusTopInset = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            root.setTag(R.id.mainFragmentContainer, initialRootTop)
            topBarContainer.setTag(R.id.topBarContainer, initialTopBarTop)
            applyTopInset()
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

    private fun navigateHomeIfNeeded(): Boolean {
        val config = mainViewModel.requireConfig()
        val homeTarget = TemplateTopBarActionResolver.resolveHomeTarget(
            config = config,
            navigationItems = config.navigation.items
        )
        if (homeTarget.url.isBlank()) {
            return false
        }
        if (webFragment?.currentUrl() == homeTarget.url) {
            if (!homeTarget.title.isNullOrBlank()) {
                binding.toolbar.title = homeTarget.title
            }
            return true
        }
        webFragment?.loadUrl(homeTarget.url)
        if (!homeTarget.title.isNullOrBlank()) {
            binding.toolbar.title = homeTarget.title
        }
        return true
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
    }
}
