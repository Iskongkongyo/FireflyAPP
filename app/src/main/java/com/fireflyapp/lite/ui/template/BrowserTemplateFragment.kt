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
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.databinding.FragmentBrowserTemplateBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.web.WebContainerFragment

class BrowserTemplateFragment : Fragment(), TemplateHost, BackPressHandler, WebPageCallback {
    private var _binding: FragmentBrowserTemplateBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var ruleTitleOverride: String? = null

    private val mainViewModel: MainViewModel by activityViewModels()
    private val webFragment: WebContainerFragment?
        get() = childFragmentManager.findFragmentByTag(WEB_FRAGMENT_TAG) as? WebContainerFragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        applyWindowInsets(config.browser.immersiveStatusBar)

        requireActivity().title = config.app.name
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    binding.webContainer.id,
                    WebContainerFragment.newInstance(config.app.defaultUrl),
                    WEB_FRAGMENT_TAG
                )
                .commitNow()
        }
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
        return when (webFragment?.resolveBackNavigationAction()) {
            WebContainerFragment.BackNavigationAction.HANDLED -> true
            WebContainerFragment.BackNavigationAction.GO_HOME -> navigateHomeIfNeeded()
            else -> false
        }
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
        if (!state.title.isNullOrBlank()) {
            requireActivity().title = state.title
        }
    }

    override fun onDestroyView() {
        ruleTitleOverride = null
        _binding = null
        super.onDestroyView()
    }

    private fun applyWindowInsets(immersiveStatusBar: Boolean) {
        val root = binding.root
        val initialTop = root.paddingTop
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusTop = if (immersiveStatusBar) 0 else insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            root.updatePadding(
                top = initialTop + statusTop,
                bottom = initialBottom + navigationBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
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
                requireActivity().title = homeTarget.title
            }
            return true
        }
        webFragment?.loadUrl(homeTarget.url)
        if (!homeTarget.title.isNullOrBlank()) {
            requireActivity().title = homeTarget.title
        }
        return true
    }

    private companion object {
        const val WEB_FRAGMENT_TAG = "web_container"
    }
}
