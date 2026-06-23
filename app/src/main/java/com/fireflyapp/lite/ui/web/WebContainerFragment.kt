package com.fireflyapp.lite.ui.web

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.webkit.WebResourceRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.clipboard.ClipboardBridge
import com.fireflyapp.lite.core.download.BlobDownloadBridge
import com.fireflyapp.lite.core.download.DownloadHandler
import com.fireflyapp.lite.core.download.DownloadHandler.DownloadEvent
import com.fireflyapp.lite.core.download.DownloadMetadataBridge
import com.fireflyapp.lite.core.event.PageEventBridge
import com.fireflyapp.lite.core.event.PageEventContext
import com.fireflyapp.lite.core.event.PageEventDispatcher
import com.fireflyapp.lite.core.nativebridge.NativeKvBridge
import com.fireflyapp.lite.core.notification.NotificationBridge
import com.fireflyapp.lite.core.permission.WebGeolocationHandler
import com.fireflyapp.lite.core.permission.WebPermissionHandler
import com.fireflyapp.lite.core.webview.FileChooserHandler
import com.fireflyapp.lite.core.webview.FireflyWebView
import com.fireflyapp.lite.core.webview.FullscreenViewHost
import com.fireflyapp.lite.core.rule.PageRuleResolver
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.FireflyWebChromeClient
import com.fireflyapp.lite.core.webview.FireflyWebViewClient.PageLoadErrorState
import com.fireflyapp.lite.core.webview.FireflyWebViewClient
import com.fireflyapp.lite.core.webview.ResolvedPageInjectionApplier
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.core.webview.WebViewConfigurator
import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.model.NavigationItem
import com.fireflyapp.lite.data.model.TemplateType
import com.fireflyapp.lite.databinding.FragmentWebContainerBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.template.NavigationSwipeDirection
import com.fireflyapp.lite.ui.template.TemplateSwipeNavigationHelper
import org.json.JSONObject
import kotlin.math.abs

class WebContainerFragment : Fragment() {
    private var _binding: FragmentWebContainerBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val fileChooserHandler = FileChooserHandler(
        fragment = this,
        allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
        currentPageUrlProvider = { currentPageUrl }
    )
    private val downloadNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val downloadHandler by lazy { DownloadHandler(requireContext().applicationContext) }
    private var pageRuleResolver: PageRuleResolver? = null
    private var resolvedPageInjectionApplier: ResolvedPageInjectionApplier? = null
    private val managedWebViews = mutableListOf<ManagedWebView>()
    private val trimmedNavigationStackEntries = mutableListOf<TrimmedNavigationStackEntry>()
    private val preloadedNavigationRoots = linkedMapOf<String, ManagedWebView>()
    private var navigationPageStackEnabled = false
    private var navigationPreloadCount = 0
    private var currentNavigationItemId: String? = null
    private var navigationItems: List<NavigationItem> = emptyList()
    private var interactiveNavigationLoading = false
    private var pendingNavigationPreloadRefresh = false
    private var isFragmentResumed = false
    private val webPermissionHandler = WebPermissionHandler(
        fragment = this,
        allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
        currentPageUrlProvider = { currentPageUrl }
    )
    private val webGeolocationHandler = WebGeolocationHandler(
        fragment = this,
        allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
        currentPageUrlProvider = { currentPageUrl }
    )
    private var chromeClient: FireflyWebChromeClient? = null
    private var pageEventDispatcher: PageEventDispatcher? = null
    private var externalAppDialog: AlertDialog? = null
    private var longPressDialog: AlertDialog? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var currentPageUrl: String? = null
    @Volatile
    private var currentPageTitle: String? = null
    private var errorStateLocked = false
    private var keepWebViewHiddenUntilLoaded = false
    private var currentPageState: ResolvedPageState? = null
    private var defaultLoadingCardColor: Int? = null
    private var defaultLoadingTextColor: Int? = null
    private var defaultLoadingIndicatorColor: IntArray? = null
    private var defaultErrorCardColor: Int? = null
    private var defaultErrorTitleColor: Int? = null
    private var defaultErrorMessageColor: Int? = null
    private var defaultRetryButtonBackgroundColor: Int? = null
    private var defaultRetryButtonTextColor: Int? = null
    private var loadingSpinnerAnimator: ObjectAnimator? = null
    private var navigationSwipeListener: ((NavigationSwipeDirection) -> Unit)? = null
    private var pendingNavigationSwipeDirection: NavigationSwipeDirection? = null
    private var pendingNavigationSwipeExitCompleted = false
    private var pendingNavigationSwipePageReady = false
    private var pendingNavigationSwipeSnapshotFinalTranslationX = 0f
    private var navigationSwipePreviewView: ImageView? = null
    private var navigationSwipePreviewManagedWebView: ManagedWebView? = null
    private var navigationSwipeInteractiveActive = false
    private var navigationSwipeInteractiveCommitted = false
    private var navigationSwipeInteractiveTargetItem: NavigationItem? = null
    private var navigationSwipeCurrentTranslationX = 0f
    private var navigationSwipeTouchStartX = 0f
    private var navigationSwipeTouchStartY = 0f
    private var navigationSwipeTouchStartScrollX = 0
    private var navigationSwipeTouchStartScrollY = 0
    private var navigationSwipeTouchSlop = 0
    private var navigationSwipeVelocityTracker: VelocityTracker? = null
    private var navigationSwipeSnapshotView: ImageView? = null
    private var lastRendererCrashUrl: String? = null
    private var lastRendererCrashAtElapsedMs: Long = 0L
    private val hideDownloadStatusRunnable = Runnable {
        _binding?.downloadStatusContainer?.visibility = View.GONE
    }
    private val refreshNavigationPreloadsRunnable = Runnable {
        refreshNavigationPreloads()
    }
    private val clipboardBridge by lazy {
        ClipboardBridge(
            fragment = this,
            allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
            currentPageUrlProvider = { currentPageUrl },
            dispatchReadResult = ::dispatchClipboardReadResult,
            dispatchWriteResult = ::dispatchClipboardWriteResult
        )
    }
    private val notificationBridge = NotificationBridge(
        fragment = this,
        allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
        currentPageUrlProvider = { currentPageUrl },
        dispatchPermissionResult = ::dispatchNotificationPermissionResult
    )
    private val nativeKvBridge by lazy {
        NativeKvBridge(
            contextProvider = { context },
            trustedHostsProvider = { mainViewModel.requireConfig().security.kvTrustedHosts },
            currentPageUrlProvider = { currentPageUrl },
            storageScopeProvider = { mainViewModel.uiState.value.projectId ?: "standalone" }
        )
    }
    private val blobDownloadBridge by lazy {
        BlobDownloadBridge(
            downloadHandler = downloadHandler,
            getCurrentPageUrl = { currentPageUrl },
            allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
            onDownloadEvent = ::handleDownloadEvent
        )
    }
    private val downloadMetadataBridge by lazy { DownloadMetadataBridge() }
    private val pageEventBridge by lazy {
        PageEventBridge(
            onSpaUrlChanged = { url, title ->
                val previousUrl = currentPageUrl.orEmpty()
                currentPageUrl = url
                activeManagedWebView()?.lastKnownUrl = url
                if (title.isNotBlank()) {
                    currentPageTitle = title
                    activeManagedWebView()?.lastKnownTitle = title
                }
                pageRuleResolver?.resolve(url)?.let { state ->
                    currentPageState = state
                    pageCallback?.onPageStateResolved(state)
                    applyPageUiStyle(state)
                    activeWebView()?.let { webView ->
                        resolvedPageInjectionApplier?.apply(webView, state)
                    }
                }
                if (previousUrl.isNotBlank() && previousUrl != url) {
                    dispatchPageEvent(
                        trigger = PAGE_EVENT_TRIGGER_SPA_URL_CHANGED,
                        url = url,
                        title = title.ifBlank { currentPageTitle.orEmpty() },
                        previousUrl = previousUrl
                    )
                }
            },
            onPageTitleChanged = { title ->
                if (title.isNotBlank() && title != currentPageTitle) {
                    currentPageTitle = title
                    activeManagedWebView()?.lastKnownTitle = title
                    dispatchPageEvent(
                        trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                        url = currentPageUrl.orEmpty(),
                        title = title
                    )
                }
            }
        )
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private val pageCallback: WebPageCallback?
        get() = parentFragment as? WebPageCallback

    private data class ManagedWebView(
        val webView: FireflyWebView,
        val chromeClient: FireflyWebChromeClient,
        var mode: ManagedWebViewMode,
        var navigationItemId: String? = null,
        var navigationRootUrl: String = "",
        var lastKnownUrl: String = "",
        var lastKnownTitle: String = "",
        var lastResolvedPageState: ResolvedPageState? = null,
        var lastLoadError: PageLoadErrorState? = null,
        var isLoading: Boolean = false,
        var clearHistoryOnNextPageFinished: Boolean = false,
        var preloadedAtElapsedMs: Long = 0L
    )

    private data class TrimmedNavigationStackEntry(
        val url: String,
        val title: String = ""
    )

    private data class LongPressTarget(
        val hitType: Int,
        val linkUrl: String = "",
        val imageUrl: String = "",
        val title: String = ""
    ) {
        val hasLink: Boolean
            get() = linkUrl.isNotBlank()

        val hasImage: Boolean
            get() = imageUrl.isNotBlank()
    }

    private data class LongPressMenuAction(
        val labelRes: Int,
        val onSelected: () -> Unit
    )

    private enum class ManagedWebViewMode {
        INTERACTIVE,
        PRELOADED
    }

    private inner class GuardedClipboardBridge(
        private val isEnabled: () -> Boolean
    ) {
        @android.webkit.JavascriptInterface
        fun readText(requestId: String?) {
            if (isEnabled()) {
                clipboardBridge.readText(requestId)
            } else if (!requestId.isNullOrBlank()) {
                dispatchClipboardReadResult(requestId, null, "clipboard access blocked")
            }
        }

        @android.webkit.JavascriptInterface
        fun writeText(requestId: String?, text: String?) {
            if (isEnabled()) {
                clipboardBridge.writeText(requestId, text)
            } else if (!requestId.isNullOrBlank()) {
                dispatchClipboardWriteResult(requestId, "clipboard access blocked")
            }
        }
    }

    private inner class GuardedNotificationBridge(
        private val isEnabled: () -> Boolean
    ) {
        @android.webkit.JavascriptInterface
        fun getPermissionState(): String {
            return if (isEnabled()) {
                notificationBridge.getPermissionState()
            } else {
                "default"
            }
        }

        @android.webkit.JavascriptInterface
        fun requestPermission(requestId: String?) {
            if (isEnabled()) {
                notificationBridge.requestPermission(requestId)
            } else if (!requestId.isNullOrBlank()) {
                dispatchNotificationPermissionResult(requestId, "denied")
            }
        }

        @android.webkit.JavascriptInterface
        fun showNotification(title: String?, body: String?, tag: String?): Boolean {
            return if (isEnabled()) {
                notificationBridge.showNotification(title, body, tag)
            } else {
                false
            }
        }
    }

    private inner class GuardedNativeKvBridge(
        private val isEnabled: () -> Boolean
    ) {
        @android.webkit.JavascriptInterface
        fun get(namespace: String?, key: String?): String? {
            return if (isEnabled()) {
                nativeKvBridge.get(namespace, key)
            } else {
                null
            }
        }

        @android.webkit.JavascriptInterface
        fun set(namespace: String?, key: String?, value: String?): Boolean {
            return if (isEnabled()) {
                nativeKvBridge.set(namespace, key, value)
            } else {
                false
            }
        }

        @android.webkit.JavascriptInterface
        fun remove(namespace: String?, key: String?): Boolean {
            return if (isEnabled()) {
                nativeKvBridge.remove(namespace, key)
            } else {
                false
            }
        }

        @android.webkit.JavascriptInterface
        fun clearNamespace(namespace: String?): Int {
            return if (isEnabled()) {
                nativeKvBridge.clearNamespace(namespace)
            } else {
                0
            }
        }
    }

    private inner class GuardedBlobDownloadBridge(
        private val isEnabled: () -> Boolean
    ) {
        @android.webkit.JavascriptInterface
        fun beginBlobDownload(sessionId: String?, fileName: String?, mimeType: String?, totalChunks: Int): Boolean {
            return if (isEnabled()) {
                blobDownloadBridge.beginBlobDownload(sessionId, fileName, mimeType, totalChunks)
            } else {
                false
            }
        }

        @android.webkit.JavascriptInterface
        fun appendBlobChunk(sessionId: String?, base64Chunk: String?, isLastChunk: Boolean) {
            if (isEnabled()) {
                blobDownloadBridge.appendBlobChunk(sessionId, base64Chunk, isLastChunk)
            }
        }

        @android.webkit.JavascriptInterface
        fun cancelBlobDownload(sessionId: String?, message: String?) {
            if (isEnabled()) {
                blobDownloadBridge.cancelBlobDownload(sessionId, message)
            }
        }
    }

    private inner class GuardedDownloadMetadataBridge(
        private val isEnabled: () -> Boolean
    ) {
        @android.webkit.JavascriptInterface
        fun rememberFileName(url: String?, fileName: String?) {
            if (isEnabled()) {
                downloadMetadataBridge.rememberFileName(url, fileName)
            }
        }
    }

    private inner class GuardedPageEventBridge(
        private val onSpaUrlChanged: (url: String, title: String) -> Unit,
        private val onPageTitleChanged: (title: String) -> Unit,
        private val isEnabled: () -> Boolean
    ) {
        private val delegate = PageEventBridge(
            onSpaUrlChanged = { url, title ->
                if (isEnabled()) {
                    onSpaUrlChanged(url, title)
                }
            },
            onPageTitleChanged = { title ->
                if (isEnabled()) {
                    onPageTitleChanged(title)
                }
            }
        )

        @android.webkit.JavascriptInterface
        fun onSpaUrlChanged(url: String?, title: String?) {
            delegate.onSpaUrlChanged(url, title)
        }

        @android.webkit.JavascriptInterface
        fun onPageTitleChanged(title: String?) {
            delegate.onPageTitleChanged(title)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = mainViewModel.requireConfig()
        val resolver = PageRuleResolver(config)
        pageRuleResolver = resolver
        resolvedPageInjectionApplier = ResolvedPageInjectionApplier()
        pageEventDispatcher = PageEventDispatcher(config)
        navigationPageStackEnabled = supportsNavigationPageStack(config)
        navigationPreloadCount = resolveNavigationPreloadCount(config)
        navigationSwipeTouchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        currentPageState = resolver.resolve(
            requireArguments().getString(ARG_INITIAL_URL).orEmpty().ifBlank { config.app.defaultUrl }
        )
        captureDefaultUiStyle()
        binding.retryButton.setOnClickListener {
            performRetryAction()
        }
        binding.loadingContainer.setOnTouchListener { _, event ->
            handleBlockingOverlayNavigationSwipeTouch(event)
        }
        binding.loadingCard.setOnTouchListener { _, event ->
            handleBlockingOverlayNavigationSwipeTouch(event)
        }
        binding.errorView.setOnTouchListener { _, event ->
            handleBlockingOverlayNavigationSwipeTouch(event)
        }
        binding.errorCard.setOnTouchListener { _, event ->
            handleBlockingOverlayNavigationSwipeTouch(event)
        }
        initializeManagedWebViewStack(
            config = config,
            resolver = resolver,
            savedInstanceState = savedInstanceState
        )
    }

    private fun supportsNavigationPageStack(config: AppConfig): Boolean {
        return config.shell.preserveNavigationPageStack &&
            config.shell.navigationBackBehavior == "reset_on_navigation" &&
            config.navigation.items.isNotEmpty() &&
            config.app.template in NAVIGATION_STACK_SUPPORTED_TEMPLATES
    }

    private fun resolveNavigationPreloadCount(config: AppConfig): Int {
        return config.shell.navigationPreloadCount
            .takeIf {
                it > 0 &&
                    config.shell.navigationBackBehavior == "reset_on_navigation" &&
                    !config.shell.preserveNavigationPageStack &&
                    config.navigation.items.size > 1 &&
                    config.app.template in NAVIGATION_PRELOAD_SUPPORTED_TEMPLATES
            }
            ?.coerceIn(0, minOf(MAX_NAVIGATION_PRELOAD_COUNT, config.navigation.items.size - 1))
            ?: 0
    }

    private fun activeManagedWebView(): ManagedWebView? = managedWebViews.lastOrNull()

    private fun activeWebView(): FireflyWebView? = activeManagedWebView()?.webView

    private fun findManagedWebView(webView: android.webkit.WebView?): ManagedWebView? {
        val candidate = webView ?: return null
        return managedWebViews.firstOrNull { it.webView === candidate }
            ?: preloadedNavigationRoots.values.firstOrNull { it.webView === candidate }
    }

    private fun allManagedWebViews(): List<ManagedWebView> {
        return managedWebViews + preloadedNavigationRoots.values
    }

    private fun isInteractiveManagedWebView(managedWebView: ManagedWebView): Boolean {
        return managedWebView.mode == ManagedWebViewMode.INTERACTIVE &&
            managedWebView.webView === activeWebView()
    }

    private fun initializeManagedWebViewStack(
        config: AppConfig,
        resolver: PageRuleResolver,
        savedInstanceState: Bundle?
    ) {
        managedWebViews.clear()
        trimmedNavigationStackEntries.clear()
        clearPreloadedNavigationRoots()
        val rootManagedWebView = configureManagedWebView(
            webView = binding.webView,
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE
        )
        managedWebViews += rootManagedWebView
        chromeClient = rootManagedWebView.chromeClient

        if (savedInstanceState != null) {
            rootManagedWebView.webView.restoreState(savedInstanceState)
            currentPageUrl = rootManagedWebView.webView.url
            currentPageTitle = rootManagedWebView.webView.title
            currentPageUrl?.let {
                currentPageState = resolver.resolve(it)
                currentPageState?.let(::applyPageUiStyle)
            }
        } else if (rootManagedWebView.webView.url.isNullOrBlank()) {
            val initialUrl = requireArguments().getString(ARG_INITIAL_URL).orEmpty()
            currentPageUrl = initialUrl
            currentPageState = resolver.resolve(initialUrl)
            currentPageState?.let(::applyPageUiStyle)
            loadUrlInternal(rootManagedWebView.webView, initialUrl, resetHistory = false)
        }

        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
    }

    private fun configureManagedWebView(
        webView: FireflyWebView,
        config: AppConfig,
        resolver: PageRuleResolver,
        mode: ManagedWebViewMode,
        navigationItemId: String? = null,
        navigationRootUrl: String = ""
    ): ManagedWebView {
        lateinit var managedWebView: ManagedWebView
        val guardedClipboardBridge = GuardedClipboardBridge {
            isInteractiveManagedWebView(managedWebView)
        }
        val guardedNotificationBridge = GuardedNotificationBridge {
            isInteractiveManagedWebView(managedWebView)
        }
        val guardedNativeKvBridge = GuardedNativeKvBridge {
            isInteractiveManagedWebView(managedWebView)
        }
        val guardedBlobDownloadBridge = GuardedBlobDownloadBridge {
            isInteractiveManagedWebView(managedWebView)
        }
        val guardedDownloadMetadataBridge = GuardedDownloadMetadataBridge {
            isInteractiveManagedWebView(managedWebView)
        }
        val guardedPageEventBridge = GuardedPageEventBridge(
            onSpaUrlChanged = { url, title ->
                val previousUrl = currentPageUrl.orEmpty()
                currentPageUrl = url
                managedWebView.lastKnownUrl = url
                if (title.isNotBlank()) {
                    currentPageTitle = title
                    managedWebView.lastKnownTitle = title
                }
                pageRuleResolver?.resolve(url)?.let { state ->
                    managedWebView.lastResolvedPageState = state
                    currentPageState = state
                    pageCallback?.onPageStateResolved(state)
                    applyPageUiStyle(state)
                    activeWebView()?.let { activeView ->
                        resolvedPageInjectionApplier?.apply(activeView, state)
                    }
                }
                if (previousUrl.isNotBlank() && previousUrl != url) {
                    dispatchPageEvent(
                        trigger = PAGE_EVENT_TRIGGER_SPA_URL_CHANGED,
                        url = url,
                        title = title.ifBlank { currentPageTitle.orEmpty() },
                        previousUrl = previousUrl
                    )
                }
            },
            onPageTitleChanged = { title ->
                if (title.isNotBlank() && title != currentPageTitle) {
                    currentPageTitle = title
                    managedWebView.lastKnownTitle = title
                    dispatchPageEvent(
                        trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                        url = currentPageUrl.orEmpty(),
                        title = title
                    )
                }
            },
            isEnabled = { isInteractiveManagedWebView(managedWebView) }
        )
        val managedPageCallback = object : WebPageCallback {
            override fun onPageTitleChanged(title: String) {
                if (isInteractiveManagedWebView(managedWebView)) {
                    pageCallback?.onPageTitleChanged(title)
                }
            }

            override fun onPageProgressChanged(progress: Int) {
                if (isInteractiveManagedWebView(managedWebView)) {
                    pageCallback?.onPageProgressChanged(progress)
                }
            }

            override fun onPageStateResolved(state: ResolvedPageState) {
                if (isInteractiveManagedWebView(managedWebView)) {
                    pageCallback?.onPageStateResolved(state)
                }
            }
        }
        WebViewConfigurator.apply(webView, config.browser)
        webView.setOnTouchListener { _, event ->
            handleNavigationSwipeTouch(managedWebView, event)
        }
        webView.setOnLongClickListener {
            handleWebViewLongPress(managedWebView)
        }
        webView.addJavascriptInterface(guardedClipboardBridge, CLIPBOARD_BRIDGE_NAME)
        webView.addJavascriptInterface(guardedNotificationBridge, NOTIFICATION_BRIDGE_NAME)
        if (config.security.enableNativeKvBridge) {
            webView.addJavascriptInterface(guardedNativeKvBridge, NATIVE_KV_BRIDGE_NAME)
        }
        webView.addJavascriptInterface(guardedBlobDownloadBridge, BLOB_BRIDGE_NAME)
        webView.addJavascriptInterface(guardedDownloadMetadataBridge, DOWNLOAD_METADATA_BRIDGE_NAME)
        webView.addJavascriptInterface(guardedPageEventBridge, PAGE_EVENT_BRIDGE_NAME)

        val managedChromeClient = FireflyWebChromeClient(
            pageCallback = managedPageCallback,
            onPageTitleChanged = { title ->
                if (title.isNotBlank()) {
                    managedWebView.lastKnownTitle = title
                }
                if (isInteractiveManagedWebView(managedWebView)) {
                    handlePageTitleChanged(title)
                }
            },
            openFileChooser = { filePathCallback, fileChooserParams ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    fileChooserHandler.openFileChooser(filePathCallback, fileChooserParams)
                } else {
                    filePathCallback.onReceiveValue(null)
                    true
                }
            },
            requestWebPermission = { request ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    webPermissionHandler.handle(request)
                } else {
                    request.deny()
                }
            },
            cancelWebPermission = { request ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    webPermissionHandler.onCanceled(request)
                } else {
                    request?.deny()
                }
            },
            requestGeolocationPermission = { origin, callback ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    webGeolocationHandler.handle(origin, callback)
                } else {
                    callback?.invoke(origin.orEmpty(), false, false)
                }
            },
            cancelGeolocationPermission = {
                if (isInteractiveManagedWebView(managedWebView)) {
                    webGeolocationHandler.cancelPending()
                }
            },
            showFullscreenView = { view ->
                isInteractiveManagedWebView(managedWebView) &&
                    (activity as? FullscreenViewHost)?.showFullscreenView(view) == true
            },
            hideFullscreenView = {
                if (isInteractiveManagedWebView(managedWebView)) {
                    (activity as? FullscreenViewHost)?.hideFullscreenView()
                }
            }
        )
        webView.webChromeClient = managedChromeClient
        webView.webViewClient = FireflyWebViewClient(
            appConfig = config,
            pageRuleResolver = resolver,
            pageCallback = managedPageCallback,
            openExternal = { intent ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    openExternalIntent(intent)
                } else {
                    true
                }
            },
            onPageLoadError = { errorState ->
                managedWebView.lastLoadError = errorState
                managedWebView.isLoading = false
                if (isInteractiveManagedWebView(managedWebView)) {
                    interactiveNavigationLoading = false
                    if (errorState != null) {
                        errorStateLocked = true
                        showError(errorState)
                    } else if (!errorStateLocked) {
                        showError(null)
                    }
                } else if (managedWebView.mode == ManagedWebViewMode.PRELOADED) {
                    requestNavigationPreloadRefresh()
                }
            },
            onPageLoadingChanged = { loading ->
                managedWebView.isLoading = loading
                if (isInteractiveManagedWebView(managedWebView)) {
                    interactiveNavigationLoading = loading
                    if (!errorStateLocked) {
                        showLoading(loading)
                    }
                    if (!loading && pendingNavigationPreloadRefresh) {
                        requestNavigationPreloadRefresh()
                    }
                }
            },
            onPageStarted = { url ->
                managedWebView.lastKnownUrl = url
                managedWebView.lastLoadError = null
                managedWebView.lastResolvedPageState = resolver.resolve(url)
                managedWebView.preloadedAtElapsedMs = 0L
                if (isInteractiveManagedWebView(managedWebView)) {
                    handlePageStarted(url)
                }
            },
            onResolvedPageStateChanged = { state ->
                managedWebView.lastResolvedPageState = state
                if (isInteractiveManagedWebView(managedWebView)) {
                    currentPageState = state
                    applyPageUiStyle(state)
                }
            },
            onPageCommitVisible = { _, _ ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    onNavigationSwipePageReady()
                }
            },
            onPageFinished = { finishedWebView, url ->
                managedWebView.lastKnownUrl = url
                val resolvedTitle = finishedWebView.title.orEmpty()
                if (resolvedTitle.isNotBlank()) {
                    managedWebView.lastKnownTitle = resolvedTitle
                }
                managedWebView.isLoading = false
                managedWebView.preloadedAtElapsedMs = SystemClock.elapsedRealtime()
                installPageEventHook(finishedWebView)
                installClipboardBridge(finishedWebView)
                installNotificationBridge(finishedWebView)
                installDownloadMetadataHook(finishedWebView)
                installBlobDownloadHook(finishedWebView)
                if (managedWebView.clearHistoryOnNextPageFinished) {
                    managedWebView.clearHistoryOnNextPageFinished = false
                    finishedWebView.clearHistory()
                }
                if (isInteractiveManagedWebView(managedWebView)) {
                    currentPageUrl = url
                    onNavigationSwipePageReady()
                    dispatchPageEvent(
                        trigger = PAGE_EVENT_TRIGGER_PAGE_FINISHED,
                        url = url,
                        title = currentPageTitle.orEmpty()
                    )
                    if (pendingNavigationPreloadRefresh) {
                        requestNavigationPreloadRefresh()
                    }
                } else if (managedWebView.mode == ManagedWebViewMode.PRELOADED) {
                    requestNavigationPreloadRefresh()
                }
            },
            onInternalNavigationRequest = { currentView, request ->
                if (isInteractiveManagedWebView(managedWebView)) {
                    handleInternalNavigationRequest(currentView, request)
                } else {
                    false
                }
            },
            onRenderProcessGone = { crashedWebView, detail ->
                handleRenderProcessGone(crashedWebView, detail)
            }
        )
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (isInteractiveManagedWebView(managedWebView)) {
                handleDownloadRequest(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType
                )
            }
        }
        managedWebView = ManagedWebView(
            webView = webView,
            chromeClient = managedChromeClient,
            mode = mode,
            navigationItemId = navigationItemId,
            navigationRootUrl = navigationRootUrl
        )
        return managedWebView
    }

    private fun createManagedWebView(
        config: AppConfig,
        resolver: PageRuleResolver,
        mode: ManagedWebViewMode,
        navigationItemId: String? = null,
        navigationRootUrl: String = ""
    ): ManagedWebView {
        val webView = FireflyWebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.webViewContainer.addView(webView)
        return configureManagedWebView(
            webView = webView,
            config = config,
            resolver = resolver,
            mode = mode,
            navigationItemId = navigationItemId,
            navigationRootUrl = navigationRootUrl
        )
    }

    private fun handleDownloadRequest(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (url.startsWith(BLOB_URL_PREFIX, ignoreCase = true)) {
            triggerBlobDownload(
                blobUrl = url,
                contentDisposition = contentDisposition,
                mimeType = mimeType
            )
            return
        }

        if (!URLUtil.isValidUrl(url)) {
            Toast.makeText(requireContext(), R.string.download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        ensureDownloadNotificationPermission()
        val result = downloadHandler.enqueue(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            referer = currentPageUrl,
            suggestedFileName = downloadMetadataBridge.consumeSuggestedFileName(url),
            onEvent = { event ->
                handleDownloadEvent(event)
            }
        )
        val messageRes = if (result.isSuccess) R.string.download_started else R.string.download_failed
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun handleWebViewLongPress(managedWebView: ManagedWebView): Boolean {
        if (!isInteractiveManagedWebView(managedWebView)) {
            return false
        }
        val webView = managedWebView.webView
        val hitTestResult = webView.hitTestResult ?: return false
        if (!shouldHandleLongPressHitType(hitTestResult.type)) {
            return false
        }
        resolveLongPressTarget(webView, hitTestResult) { target ->
            if (!isAdded || _binding == null) {
                return@resolveLongPressTarget
            }
            if (target == null || (!target.hasLink && !target.hasImage)) {
                return@resolveLongPressTarget
            }
            showLongPressMenu(target)
        }
        return true
    }

    private fun shouldHandleLongPressHitType(hitType: Int): Boolean {
        return hitType == android.webkit.WebView.HitTestResult.ANCHOR_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.PHONE_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.GEO_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.EMAIL_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.IMAGE_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.IMAGE_ANCHOR_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE ||
            hitType == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    }

    private fun resolveLongPressTarget(
        webView: FireflyWebView,
        hitTestResult: android.webkit.WebView.HitTestResult,
        onResolved: (LongPressTarget?) -> Unit
    ) {
        val fallbackExtra = hitTestResult.extra.orEmpty()
        when (hitTestResult.type) {
            android.webkit.WebView.HitTestResult.IMAGE_TYPE -> {
                requestImageRef(webView) { imageUrl ->
                    onResolved(
                        LongPressTarget(
                            hitType = hitTestResult.type,
                            imageUrl = imageUrl.ifBlank { fallbackExtra }
                        )
                    )
                }
            }

            android.webkit.WebView.HitTestResult.IMAGE_ANCHOR_TYPE,
            android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                requestFocusNodeHref(webView) { linkUrl, title, sourceUrl ->
                    val resolvedLink = normalizeLongPressUrl(
                        hitType = hitTestResult.type,
                        rawValue = linkUrl.ifBlank { fallbackExtra }
                    )
                    if (sourceUrl.isNotBlank()) {
                        onResolved(
                            LongPressTarget(
                                hitType = hitTestResult.type,
                                linkUrl = resolvedLink,
                                imageUrl = sourceUrl,
                                title = title
                            )
                        )
                    } else {
                        requestImageRef(webView) { imageUrl ->
                            onResolved(
                                LongPressTarget(
                                    hitType = hitTestResult.type,
                                    linkUrl = resolvedLink,
                                    imageUrl = imageUrl,
                                    title = title
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                requestFocusNodeHref(webView) { linkUrl, title, sourceUrl ->
                    onResolved(
                        LongPressTarget(
                            hitType = hitTestResult.type,
                            linkUrl = normalizeLongPressUrl(
                                hitType = hitTestResult.type,
                                rawValue = linkUrl.ifBlank { fallbackExtra }
                            ),
                            imageUrl = sourceUrl,
                            title = title
                        )
                    )
                }
            }
        }
    }

    private fun requestFocusNodeHref(
        webView: FireflyWebView,
        onResolved: (url: String, title: String, sourceUrl: String) -> Unit
    ) {
        val message = Message.obtain(
            Handler(Looper.getMainLooper()) { result ->
                val data = result.data
                onResolved(
                    data?.getString("url").orEmpty(),
                    data?.getString("title").orEmpty(),
                    data?.getString("src").orEmpty()
                )
                true
            }
        )
        webView.requestFocusNodeHref(message)
    }

    private fun requestImageRef(
        webView: FireflyWebView,
        onResolved: (imageUrl: String) -> Unit
    ) {
        val message = Message.obtain(
            Handler(Looper.getMainLooper()) { result ->
                onResolved(result.data?.getString("url").orEmpty())
                true
            }
        )
        webView.requestImageRef(message)
    }

    private fun normalizeLongPressUrl(hitType: Int, rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        return when (hitType) {
            android.webkit.WebView.HitTestResult.PHONE_TYPE -> {
                if (trimmed.startsWith("tel:", ignoreCase = true)) trimmed else "tel:$trimmed"
            }

            android.webkit.WebView.HitTestResult.EMAIL_TYPE -> {
                if (trimmed.startsWith("mailto:", ignoreCase = true)) trimmed else "mailto:$trimmed"
            }

            android.webkit.WebView.HitTestResult.GEO_TYPE -> {
                if (trimmed.startsWith("geo:", ignoreCase = true)) trimmed else "geo:0,0?q=${Uri.encode(trimmed)}"
            }

            else -> trimmed
        }
    }

    private fun showLongPressMenu(target: LongPressTarget) {
        val context = context ?: return
        val actions = buildLongPressMenuActions(target)
        if (actions.isEmpty()) {
            return
        }
        longPressDialog?.dismiss()
        longPressDialog = AlertDialog.Builder(context)
            .setTitle(R.string.web_long_press_options)
            .setItems(actions.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.onSelected?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        longPressDialog?.show()
    }

    private fun buildLongPressMenuActions(target: LongPressTarget): List<LongPressMenuAction> {
        val actions = mutableListOf<LongPressMenuAction>()
        if (target.hasLink) {
            actions += LongPressMenuAction(R.string.web_long_press_open_link) {
                openLongPressTarget(target.linkUrl)
            }
            actions += LongPressMenuAction(R.string.web_long_press_copy_link) {
                copyLongPressText(label = "link", text = target.linkUrl)
            }
            actions += LongPressMenuAction(R.string.web_long_press_share_link) {
                shareLongPressText(subject = target.title, text = target.linkUrl)
            }
        }
        if (target.hasImage) {
            actions += LongPressMenuAction(R.string.web_long_press_open_image) {
                openLongPressTarget(target.imageUrl)
            }
            actions += LongPressMenuAction(R.string.web_long_press_copy_image_address) {
                copyLongPressText(label = "image", text = target.imageUrl)
            }
            actions += LongPressMenuAction(R.string.web_long_press_share_image_address) {
                shareLongPressText(subject = target.title, text = target.imageUrl)
            }
            actions += LongPressMenuAction(R.string.web_long_press_download_image) {
                downloadLongPressImage(target.imageUrl)
            }
        }
        return actions
    }

    private fun openLongPressTarget(targetUrl: String) {
        if (targetUrl.isBlank()) {
            return
        }
        openExternalIntent(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
    }

    private fun copyLongPressText(label: String, text: String) {
        if (text.isBlank()) {
            return
        }
        val clipboardManager = context?.getSystemService(ClipboardManager::class.java) ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), R.string.web_long_press_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareLongPressText(subject: String, text: String) {
        if (text.isBlank()) {
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.web_long_press_share_chooser)))
        } catch (_: ActivityNotFoundException) {
            if (isAdded) {
                Toast.makeText(requireContext(), R.string.web_external_no_handler, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadLongPressImage(imageUrl: String) {
        handleDownloadRequest(
            url = imageUrl,
            userAgent = activeWebView()?.settings?.userAgentString,
            contentDisposition = null,
            mimeType = null
        )
    }

    private fun handleInternalNavigationRequest(
        webView: android.webkit.WebView,
        request: WebResourceRequest
    ): Boolean {
        val currentActiveWebView = activeWebView() ?: return false
        if (!navigationPageStackEnabled || webView !== currentActiveWebView) {
            return false
        }
        if (!request.method.equals("GET", ignoreCase = true) || request.isRedirect) {
            return false
        }
        val targetUrl = request.url?.toString().orEmpty()
        if (targetUrl.isBlank()) {
            return false
        }
        val currentUrl = currentActiveWebView.url.orEmpty()
        if (normalizeNavigationStackUrl(currentUrl) == normalizeNavigationStackUrl(targetUrl)) {
            return false
        }
        pushNavigationStackPage(targetUrl)
        return true
    }

    private fun normalizeNavigationStackUrl(url: String): String {
        return url.trim().substringBefore('#')
    }

    private fun pushNavigationStackPage(url: String) {
        val resolver = pageRuleResolver ?: return
        val config = mainViewModel.requireConfig()
        activeWebView()?.stopLoading()
        val nextManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE,
            navigationItemId = currentNavigationItemId,
            navigationRootUrl = activeManagedWebView()?.navigationRootUrl.orEmpty()
        )
        managedWebViews += nextManagedWebView
        trimManagedWebViewStackToLimit()
        chromeClient = nextManagedWebView.chromeClient
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        loadUrlInternal(nextManagedWebView.webView, url, resetHistory = false)
    }

    private fun trimManagedWebViewStackToLimit() {
        while (managedWebViews.size > NAVIGATION_PAGE_STACK_LIMIT) {
            val trimmedManagedWebView = managedWebViews.removeAt(0)
            cacheTrimmedNavigationStackEntry(trimmedManagedWebView)
            destroyManagedWebView(trimmedManagedWebView)
        }
    }

    private fun clearManagedWebViewStack() {
        if (managedWebViews.isEmpty()) {
            trimmedNavigationStackEntries.clear()
            return
        }
        managedWebViews
            .toList()
            .asReversed()
            .forEach(::destroyManagedWebView)
        managedWebViews.clear()
        trimmedNavigationStackEntries.clear()
    }

    private fun clearPreloadedNavigationRoots() {
        if (preloadedNavigationRoots.isEmpty()) {
            return
        }
        preloadedNavigationRoots.values.toList().asReversed().forEach(::destroyManagedWebView)
        preloadedNavigationRoots.clear()
    }

    private fun cacheTrimmedNavigationStackEntry(managedWebView: ManagedWebView) {
        val cachedUrl = managedWebView.lastKnownUrl
            .ifBlank { managedWebView.webView.url.orEmpty() }
            .ifBlank { managedWebView.webView.originalUrl.orEmpty() }
        if (cachedUrl.isBlank()) {
            return
        }
        trimmedNavigationStackEntries += TrimmedNavigationStackEntry(
            url = cachedUrl,
            title = managedWebView.lastKnownTitle
                .ifBlank { managedWebView.webView.title.orEmpty() }
        )
    }

    private fun replaceNavigationRoot(
        url: String,
        resetHistory: Boolean,
        navigationItemId: String? = null,
        navigationRootUrl: String = url,
        prepareWebView: ((FireflyWebView) -> Unit)? = null
    ) {
        val resolver = pageRuleResolver ?: return
        val config = mainViewModel.requireConfig()
        clearManagedWebViewStack()
        val rootManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE,
            navigationItemId = navigationItemId,
            navigationRootUrl = navigationRootUrl
        )
        prepareWebView?.invoke(rootManagedWebView.webView)
        managedWebViews += rootManagedWebView
        chromeClient = rootManagedWebView.chromeClient
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        loadUrlInternal(rootManagedWebView.webView, url, resetHistory = resetHistory)
    }

    private fun popNavigationStackPage(): Boolean {
        if (!navigationPageStackEnabled) {
            return false
        }
        if (managedWebViews.size <= 1) {
            if (trimmedNavigationStackEntries.isEmpty()) {
                return false
            }
            val trimmedEntry = trimmedNavigationStackEntries
                .removeAt(trimmedNavigationStackEntries.lastIndex)
            return restoreTrimmedNavigationStackEntry(trimmedEntry)
        }
        val currentManagedWebView = managedWebViews.removeAt(managedWebViews.lastIndex)
        val previousTitle = currentManagedWebView.webView.title.orEmpty()
        val previousUrl = currentManagedWebView.webView.url.orEmpty()
        destroyManagedWebView(currentManagedWebView)
        val restoredManagedWebView = activeManagedWebView() ?: return false
        chromeClient = restoredManagedWebView.chromeClient
        errorStateLocked = false
        keepWebViewHiddenUntilLoaded = false
        currentPageTitle = previousTitle
        val restoredUrl = restoredManagedWebView.webView.url.orEmpty()
        if (restoredUrl.isNotBlank()) {
            handlePageStarted(restoredUrl)
            pageRuleResolver?.resolve(restoredUrl)?.let { state ->
                currentPageState = state
                pageCallback?.onPageStateResolved(state)
                applyPageUiStyle(state)
            }
        }
        val restoredTitle = restoredManagedWebView.webView.title.orEmpty()
        if (restoredTitle.isNotBlank()) {
            currentPageTitle = restoredTitle
            pageCallback?.onPageTitleChanged(restoredTitle)
            dispatchPageEvent(
                trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                url = restoredUrl,
                title = restoredTitle,
                previousUrl = previousUrl
            )
        }
        showError(null)
        showLoading(false)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        return true
    }

    private fun restoreTrimmedNavigationStackEntry(entry: TrimmedNavigationStackEntry): Boolean {
        val currentManagedWebView = activeManagedWebView() ?: return false
        val resolver = pageRuleResolver ?: return false
        val config = mainViewModel.requireConfig()
        val currentIndex = managedWebViews.lastIndex
        if (currentIndex < 0) {
            return false
        }
        val previousUrl = currentManagedWebView.lastKnownUrl
            .ifBlank { currentManagedWebView.webView.url.orEmpty() }
        val previousTitle = currentManagedWebView.lastKnownTitle
            .ifBlank { currentManagedWebView.webView.title.orEmpty() }
        managedWebViews.removeAt(currentIndex)
        destroyManagedWebView(currentManagedWebView)

        val restoredManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE,
            navigationItemId = currentNavigationItemId,
            navigationRootUrl = activeManagedWebView()?.navigationRootUrl.orEmpty()
        )
        managedWebViews += restoredManagedWebView
        chromeClient = restoredManagedWebView.chromeClient
        errorStateLocked = false
        keepWebViewHiddenUntilLoaded = false
        currentPageTitle = previousTitle
        currentPageState = resolver.resolve(entry.url)
        currentPageState?.let(::applyPageUiStyle)
        if (entry.title.isNotBlank()) {
            currentPageTitle = entry.title
            pageCallback?.onPageTitleChanged(entry.title)
            dispatchPageEvent(
                trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                url = entry.url,
                title = entry.title,
                previousUrl = previousUrl
            )
        }
        showError(null)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        loadUrlInternal(restoredManagedWebView.webView, entry.url, resetHistory = true)
        return true
    }

    private fun destroyManagedWebView(
        managedWebView: ManagedWebView,
        renderProcessGone: Boolean = false
    ) {
        managedWebView.chromeClient.exitFullscreen()
        managedWebView.webView.apply {
            if (!renderProcessGone) {
                runCatching { stopLoading() }
            }
            runCatching { setOnTouchListener(null) }
            runCatching { setOnLongClickListener(null) }
            runCatching { webChromeClient = WebChromeClient() }
            runCatching { webViewClient = WebViewClient() }
            runCatching { setDownloadListener(null) }
            runCatching { removeJavascriptInterface(CLIPBOARD_BRIDGE_NAME) }
            runCatching { removeJavascriptInterface(NOTIFICATION_BRIDGE_NAME) }
            runCatching { removeJavascriptInterface(NATIVE_KV_BRIDGE_NAME) }
            runCatching { removeJavascriptInterface(BLOB_BRIDGE_NAME) }
            runCatching { removeJavascriptInterface(DOWNLOAD_METADATA_BRIDGE_NAME) }
            runCatching { removeJavascriptInterface(PAGE_EVENT_BRIDGE_NAME) }
            runCatching { (parent as? ViewGroup)?.removeView(this) }
            runCatching { destroy() }
        }
    }

    private fun updateManagedWebViewLifecycle() {
        val currentActiveWebView = activeWebView()
        val currentPreviewManagedWebView = navigationSwipePreviewManagedWebView
        allManagedWebViews().forEach { managedWebView ->
            val shouldResume = isFragmentResumed && (
                managedWebView.webView === currentActiveWebView ||
                    managedWebView === currentPreviewManagedWebView
                )
            if (shouldResume) {
                managedWebView.webView.onResume()
            } else {
                managedWebView.webView.onPause()
            }
        }
    }

    fun setNavigationItems(items: List<NavigationItem>, currentItemId: String?) {
        navigationItems = items
            .filter { it.url.isNotBlank() }
            .distinctBy { it.id }
        navigationPreloadCount = resolveNavigationPreloadCount(mainViewModel.requireConfig())
        currentNavigationItemId = currentItemId
            ?: resolveNavigationItemForUrl(currentUrl())?.id
            ?: currentNavigationItemId
            ?: navigationItems.firstOrNull()?.id
        activeManagedWebView()?.let { managedWebView ->
            val currentItem = navigationItems.firstOrNull { it.id == currentNavigationItemId }
            managedWebView.navigationItemId = currentItem?.id
            managedWebView.navigationRootUrl = currentItem?.url.orEmpty()
        }
        prunePreloadedNavigationRoots()
        requestNavigationPreloadRefresh()
    }

    private fun resolveNavigationItemForUrl(url: String?): NavigationItem? {
        val normalizedUrl = url?.trim().orEmpty()
        if (normalizedUrl.isBlank()) {
            return null
        }
        return navigationItems.firstOrNull { it.url == normalizedUrl }
            ?: navigationItems
                .filter { normalizedUrl.startsWith(it.url) }
                .maxByOrNull { it.url.length }
    }

    private fun prunePreloadedNavigationRoots() {
        val validItemIds = navigationItems.mapTo(mutableSetOf()) { it.id }
        val iterator = preloadedNavigationRoots.entries.iterator()
        while (iterator.hasNext()) {
            val (itemId, managedWebView) = iterator.next()
            if (itemId !in validItemIds || itemId == currentNavigationItemId) {
                destroyManagedWebView(managedWebView)
                iterator.remove()
            }
        }
    }

    private fun requestNavigationPreloadRefresh() {
        uiHandler.removeCallbacks(refreshNavigationPreloadsRunnable)
        if (navigationPreloadCount <= 0 || navigationItems.size <= 1) {
            pendingNavigationPreloadRefresh = false
            clearPreloadedNavigationRoots()
            return
        }
        if (interactiveNavigationLoading) {
            pendingNavigationPreloadRefresh = true
            return
        }
        pendingNavigationPreloadRefresh = false
        uiHandler.postDelayed(refreshNavigationPreloadsRunnable, NAVIGATION_PRELOAD_DELAY_MS)
    }

    private fun refreshNavigationPreloads() {
        if (_binding == null || navigationPreloadCount <= 0 || navigationItems.size <= 1) {
            clearPreloadedNavigationRoots()
            return
        }
        val currentItem = navigationItems.firstOrNull { it.id == currentNavigationItemId }
            ?: resolveNavigationItemForUrl(currentUrl())
            ?: return
        currentNavigationItemId = currentItem.id
        val targetItemIds = resolveNavigationPreloadTargets(currentItem.id)
            .take(navigationPreloadCount)
            .map { it.id }
            .toSet()
        val iterator = preloadedNavigationRoots.entries.iterator()
        while (iterator.hasNext()) {
            val (itemId, managedWebView) = iterator.next()
            if (itemId !in targetItemIds) {
                destroyManagedWebView(managedWebView)
                iterator.remove()
            }
        }
        if (preloadedNavigationRoots.values.any { it.isLoading }) {
            pendingNavigationPreloadRefresh = true
            return
        }
        val nextTarget = resolveNavigationPreloadTargets(currentItem.id)
            .take(navigationPreloadCount)
            .firstOrNull { targetItem -> !preloadedNavigationRoots.containsKey(targetItem.id) }
            ?: return
        ensureNavigationRootPreloaded(nextTarget)
    }

    private fun resolveNavigationPreloadTargets(currentItemId: String): List<NavigationItem> {
        val currentIndex = navigationItems.indexOfFirst { it.id == currentItemId }
        if (currentIndex < 0) {
            return emptyList()
        }
        val orderedTargets = mutableListOf<NavigationItem>()
        for (distance in 1 until navigationItems.size) {
            val previousIndex = currentIndex - distance
            if (previousIndex >= 0) {
                orderedTargets += navigationItems[previousIndex]
            }
            val nextIndex = currentIndex + distance
            if (nextIndex < navigationItems.size) {
                orderedTargets += navigationItems[nextIndex]
            }
        }
        return orderedTargets
    }

    private fun ensureNavigationRootPreloaded(item: NavigationItem) {
        if (item.id == currentNavigationItemId || preloadedNavigationRoots.containsKey(item.id)) {
            return
        }
        val resolver = pageRuleResolver ?: return
        val config = mainViewModel.requireConfig()
        val preloadedManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.PRELOADED,
            navigationItemId = item.id,
            navigationRootUrl = item.url
        )
        preloadedNavigationRoots[item.id] = preloadedManagedWebView
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        loadUrlInternal(preloadedManagedWebView.webView, item.url, resetHistory = true)
    }

    private fun shouldRefreshActivatedPreload(managedWebView: ManagedWebView, fallbackUrl: String): Boolean {
        if (managedWebView.isLoading) {
            return false
        }
        val resolvedUrl = managedWebView.lastKnownUrl
            .ifBlank { managedWebView.webView.url.orEmpty() }
            .ifBlank { fallbackUrl }
        if (resolvedUrl.isBlank() || managedWebView.lastLoadError != null || managedWebView.preloadedAtElapsedMs <= 0L) {
            return true
        }
        return SystemClock.elapsedRealtime() - managedWebView.preloadedAtElapsedMs > NAVIGATION_PRELOAD_MAX_AGE_MS
    }

    private fun activatePreloadedNavigationRoot(
        item: NavigationItem,
        managedWebView: ManagedWebView,
        resetHistory: Boolean,
        prepareWebView: ((FireflyWebView) -> Unit)? = null
    ) {
        val previousUrl = currentPageUrl.orEmpty()
        clearManagedWebViewStack()
        managedWebView.mode = ManagedWebViewMode.INTERACTIVE
        managedWebView.navigationItemId = item.id
        managedWebView.navigationRootUrl = item.url
        prepareWebView?.invoke(managedWebView.webView)
        managedWebViews += managedWebView
        chromeClient = managedWebView.chromeClient
        errorStateLocked = managedWebView.lastLoadError != null
        keepWebViewHiddenUntilLoaded = managedWebView.isLoading && pendingNavigationSwipeDirection != null
        interactiveNavigationLoading = managedWebView.isLoading
        val activatedUrl = managedWebView.lastKnownUrl
            .ifBlank { managedWebView.webView.url.orEmpty() }
            .ifBlank { item.url }
        if (!managedWebView.isLoading && activatedUrl.isNotBlank() && previousUrl != activatedUrl) {
            handlePageStarted(activatedUrl)
        } else {
            currentPageUrl = activatedUrl
        }
        currentPageState = managedWebView.lastResolvedPageState ?: pageRuleResolver?.resolve(activatedUrl)
        currentPageState?.let { state ->
            pageCallback?.onPageStateResolved(state)
            applyPageUiStyle(state)
        }
        val restoredTitle = managedWebView.lastKnownTitle
            .ifBlank { managedWebView.webView.title.orEmpty() }
        if (restoredTitle.isNotBlank()) {
            currentPageTitle = restoredTitle
            pageCallback?.onPageTitleChanged(restoredTitle)
            if (!managedWebView.isLoading && previousUrl != activatedUrl) {
                dispatchPageEvent(
                    trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                    url = activatedUrl,
                    title = restoredTitle,
                    previousUrl = previousUrl
                )
            }
        }
        showError(managedWebView.lastLoadError)
        showLoading(managedWebView.isLoading)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        if (shouldRefreshActivatedPreload(managedWebView, item.url)) {
            loadUrlInternal(managedWebView.webView, item.url, resetHistory = true)
        } else if (!managedWebView.isLoading) {
            pageCallback?.onPageProgressChanged(100)
            dispatchPageEvent(
                trigger = PAGE_EVENT_TRIGGER_PAGE_FINISHED,
                url = activatedUrl,
                title = currentPageTitle.orEmpty()
            )
            onNavigationSwipePageReady()
            requestNavigationPreloadRefresh()
        }
    }

    private fun openNavigationRoot(
        item: NavigationItem,
        resetHistory: Boolean,
        prepareWebView: ((FireflyWebView) -> Unit)? = null
    ) {
        currentNavigationItemId = item.id
        val preloadedManagedWebView = preloadedNavigationRoots.remove(item.id)
        if (preloadedManagedWebView != null) {
            activatePreloadedNavigationRoot(
                item = item,
                managedWebView = preloadedManagedWebView,
                resetHistory = resetHistory,
                prepareWebView = prepareWebView
            )
            return
        }
        replaceNavigationRoot(
            url = item.url,
            resetHistory = resetHistory,
            navigationItemId = item.id,
            navigationRootUrl = item.url,
            prepareWebView = prepareWebView
        )
        requestNavigationPreloadRefresh()
    }

    private fun ensureDownloadNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val context = context ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        downloadNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun loadUrl(url: String, resetHistory: Boolean = false) {
        if (_binding == null || url.isBlank()) {
            return
        }
        resetNavigationSwipeTransition()
        loadUrlInternal(url, resetHistory)
    }

    fun loadNavigationUrl(item: NavigationItem, resetHistory: Boolean = false) {
        if (_binding == null || item.url.isBlank()) {
            return
        }
        Log.d(
            TAG,
            "loadNavigationUrl item=${item.id} url=${item.url} resetHistory=$resetHistory stackEnabled=$navigationPageStackEnabled preloadCount=$navigationPreloadCount"
        )
        if (!navigationPageStackEnabled && navigationPreloadCount <= 0) {
            currentNavigationItemId = item.id
            loadUrl(item.url, resetHistory)
            return
        }
        resetNavigationSwipeTransition()
        openNavigationRoot(item, resetHistory = resetHistory || navigationPageStackEnabled)
    }

    fun loadUrlWithSwipeTransition(
        url: String,
        direction: NavigationSwipeDirection,
        resetHistory: Boolean = false
    ) {
        val currentBinding = _binding ?: return
        if (url.isBlank()) {
            return
        }
        val snapshotBitmap = captureNavigationSwipeSnapshot()
        if (currentBinding.errorView.visibility == View.VISIBLE) {
            resetNavigationSwipeTransition()
            loadUrlInternal(url, resetHistory)
            return
        }
        val width = activeWebView()?.width
            ?.takeIf { it > 0 }
            ?: currentBinding.root.width
        if (width <= 0) {
            resetNavigationSwipeTransition()
            loadUrlInternal(url, resetHistory)
            return
        }
        val exitTranslation = when (direction) {
            NavigationSwipeDirection.NEXT -> -width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
            NavigationSwipeDirection.PREVIOUS -> width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
        }
        val entryTranslation = -exitTranslation * NAVIGATION_SWIPE_ENTRY_OFFSET_RATIO
        val snapshotHoldTranslation = exitTranslation + entryTranslation
        resetNavigationSwipeTransition()
        pendingNavigationSwipeDirection = direction
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSnapshotFinalTranslationX = exitTranslation
        activeWebView()?.translationX = entryTranslation
        activeWebView()?.alpha = NAVIGATION_SWIPE_ENTRY_ALPHA
        keepWebViewHiddenUntilLoaded = true
        applyNavigationSwipeBlankBackground()
        syncWebViewVisibility()
        snapshotBitmap?.let { installNavigationSwipeSnapshot(it) }
        startNavigationSwipeSnapshotExitAnimation(snapshotHoldTranslation)
        loadUrlInternal(url, resetHistory)
    }

    fun loadNavigationUrlWithSwipeTransition(
        item: NavigationItem,
        direction: NavigationSwipeDirection,
        resetHistory: Boolean = false
    ) {
        if (_binding == null || item.url.isBlank()) {
            return
        }
        Log.d(
            TAG,
            "loadNavigationUrlWithSwipeTransition item=${item.id} url=${item.url} direction=$direction resetHistory=$resetHistory stackEnabled=$navigationPageStackEnabled preloadCount=$navigationPreloadCount"
        )
        if (continueInteractiveNavigationSwipeTransition(
                item = item,
                direction = direction,
                resetHistory = resetHistory || navigationPageStackEnabled
            )
        ) {
            return
        }
        if (!navigationPageStackEnabled && navigationPreloadCount <= 0) {
            currentNavigationItemId = item.id
            loadUrlWithSwipeTransition(item.url, direction, resetHistory)
            return
        }
        val currentBinding = _binding ?: return
        val snapshotBitmap = captureNavigationSwipeSnapshot()
        if (currentBinding.errorView.visibility == View.VISIBLE) {
            resetNavigationSwipeTransition()
            openNavigationRoot(
                item = item,
                resetHistory = resetHistory || navigationPageStackEnabled
            )
            return
        }
        val width = activeWebView()?.width
            ?.takeIf { it > 0 }
            ?: currentBinding.webViewContainer.width
                .takeIf { it > 0 }
            ?: currentBinding.root.width
        if (width <= 0) {
            resetNavigationSwipeTransition()
            openNavigationRoot(
                item = item,
                resetHistory = resetHistory || navigationPageStackEnabled
            )
            return
        }
        val exitTranslation = when (direction) {
            NavigationSwipeDirection.NEXT -> -width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
            NavigationSwipeDirection.PREVIOUS -> width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
        }
        val entryTranslation = -exitTranslation * NAVIGATION_SWIPE_ENTRY_OFFSET_RATIO
        val snapshotHoldTranslation = exitTranslation + entryTranslation
        resetNavigationSwipeTransition()
        pendingNavigationSwipeDirection = direction
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSnapshotFinalTranslationX = exitTranslation
        keepWebViewHiddenUntilLoaded = false
        applyNavigationSwipeBlankBackground()
        if (snapshotBitmap != null) {
            installNavigationSwipeSnapshot(snapshotBitmap)
        }
        startNavigationSwipeSnapshotExitAnimation(snapshotHoldTranslation)
        openNavigationRoot(
            item = item,
            resetHistory = resetHistory || navigationPageStackEnabled
        ) { webView ->
            webView.translationX = entryTranslation
            webView.alpha = NAVIGATION_SWIPE_ENTRY_ALPHA
        }
    }

    private fun loadUrlInternal(url: String, resetHistory: Boolean) {
        val webView = activeWebView() ?: return
        loadUrlInternal(webView, url, resetHistory)
    }

    private fun loadUrlInternal(
        webView: FireflyWebView,
        url: String,
        resetHistory: Boolean
    ) {
        val managedWebView = findManagedWebView(webView) ?: return
        Log.d(
            TAG,
            "loadUrlInternal url=$url resetHistory=$resetHistory mode=${managedWebView.mode} navigationItem=${managedWebView.navigationItemId} view=${describeWebView(webView)}"
        )
        managedWebView.clearHistoryOnNextPageFinished = resetHistory
        managedWebView.lastResolvedPageState = pageRuleResolver?.resolve(url)
        managedWebView.lastLoadError = null
        managedWebView.preloadedAtElapsedMs = 0L
        managedWebView.isLoading = true
        if (isInteractiveManagedWebView(managedWebView)) {
            if (errorStateLocked) {
                beginRecoveryLoad()
            } else {
                clearErrorState()
            }
            interactiveNavigationLoading = true
            currentPageUrl = url
            currentPageState = managedWebView.lastResolvedPageState
            currentPageState?.let(::applyPageUiStyle)
            showLoading(true)
        }
        webView.loadUrl(url)
    }

    fun setNavigationSwipeListener(listener: ((NavigationSwipeDirection) -> Unit)?) {
        navigationSwipeListener = listener
    }

    private fun handleBlockingOverlayNavigationSwipeTouch(event: MotionEvent): Boolean {
        val managedWebView = activeManagedWebView() ?: return false
        val canHandleNavigationSwipe =
            isInteractiveManagedWebView(managedWebView) &&
                navigationSwipeListener != null &&
                navigationItems.size > 1
        if (!canHandleNavigationSwipe &&
            !navigationSwipeInteractiveActive &&
            !navigationSwipeInteractiveCommitted
        ) {
            return false
        }
        val handled = handleNavigationSwipeTouch(managedWebView, event)
        return handled || canHandleNavigationSwipe
    }

    private fun handleNavigationSwipeTouch(
        managedWebView: ManagedWebView,
        event: MotionEvent
    ): Boolean {
        if (!isInteractiveManagedWebView(managedWebView) ||
            navigationSwipeListener == null ||
            navigationItems.size <= 1
        ) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                navigationSwipeVelocityTracker?.recycle()
                navigationSwipeVelocityTracker = VelocityTracker.obtain().apply {
                    addMovement(event)
                }
                navigationSwipeTouchStartX = event.x
                navigationSwipeTouchStartY = event.y
                navigationSwipeTouchStartScrollX = managedWebView.webView.scrollX
                navigationSwipeTouchStartScrollY = managedWebView.webView.scrollY
                if (!navigationSwipeInteractiveCommitted) {
                    navigationSwipeInteractiveActive = false
                    navigationSwipeInteractiveTargetItem = null
                    navigationSwipeCurrentTranslationX = 0f
                }
                return pendingNavigationSwipeDirection != null && navigationSwipeInteractiveCommitted
            }

            MotionEvent.ACTION_MOVE -> {
                navigationSwipeVelocityTracker?.addMovement(event)
                if (navigationSwipeInteractiveCommitted) {
                    return true
                }
                val deltaX = event.x - navigationSwipeTouchStartX
                val deltaY = event.y - navigationSwipeTouchStartY
                if (!navigationSwipeInteractiveActive) {
                    if (abs(deltaX) < navigationSwipeTouchSlop) {
                        return false
                    }
                    if (abs(deltaX) < abs(deltaY) * SWIPE_HORIZONTAL_RATIO) {
                        return false
                    }
                    val direction = if (deltaX < 0f) {
                        NavigationSwipeDirection.NEXT
                    } else {
                        NavigationSwipeDirection.PREVIOUS
                    }
                    val targetItem = resolveAdjacentNavigationItem(direction) ?: return false
                    if (!beginInteractiveNavigationSwipe(managedWebView, direction, targetItem, event)) {
                        return false
                    }
                }
                updateInteractiveNavigationSwipeProgress(deltaX)
                return true
            }

            MotionEvent.ACTION_UP -> {
                navigationSwipeVelocityTracker?.addMovement(event)
                if (navigationSwipeInteractiveActive) {
                    completeInteractiveNavigationSwipe()
                    return true
                }
                navigationSwipeVelocityTracker?.recycle()
                navigationSwipeVelocityTracker = null
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                navigationSwipeVelocityTracker?.recycle()
                navigationSwipeVelocityTracker = null
                if (navigationSwipeInteractiveActive || navigationSwipeInteractiveCommitted) {
                    cancelInteractiveNavigationSwipe(animated = false)
                    return true
                }
            }
        }
        return false
    }

    private fun beginInteractiveNavigationSwipe(
        managedWebView: ManagedWebView,
        direction: NavigationSwipeDirection,
        targetItem: NavigationItem,
        event: MotionEvent
    ): Boolean {
        if (managedWebView.webView.width <= 0 || managedWebView.webView.height <= 0) {
            return false
        }
        managedWebView.webView.parent?.requestDisallowInterceptTouchEvent(true)
        dispatchCancelToWebView(managedWebView.webView, event)
        restoreNavigationSwipeTouchStartScroll(managedWebView.webView)
        val snapshotBitmap = captureWebViewSnapshot(managedWebView.webView)
            ?: createNavigationSwipeFallbackSnapshot(
                width = managedWebView.webView.width,
                height = managedWebView.webView.height
            )
            ?: return false
        resetNavigationSwipeTransition()
        navigationSwipeVelocityTracker = VelocityTracker.obtain().apply {
            addMovement(event)
        }
        pendingNavigationSwipeDirection = direction
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        navigationSwipeInteractiveActive = true
        navigationSwipeInteractiveCommitted = false
        navigationSwipeInteractiveTargetItem = targetItem
        navigationSwipeCurrentTranslationX = 0f
        keepWebViewHiddenUntilLoaded = true
        applyNavigationSwipeBlankBackground()
        val previewWidth = resolveNavigationSwipeWidth()
        val preloadedPreviewManagedWebView = resolveNavigationSwipePreviewManagedWebView(targetItem)
        if (previewWidth != null && preloadedPreviewManagedWebView != null) {
            installNavigationSwipePreview(
                managedWebView = preloadedPreviewManagedWebView,
                translationX = resolveNavigationSwipePreviewStartTranslation(previewWidth, direction)
            )
        } else {
            captureNavigationSwipePreviewBitmap(targetItem)?.let { previewBitmap ->
                val width = previewWidth ?: resolveNavigationSwipeWidth() ?: return@let
                installNavigationSwipePreview(
                    bitmap = previewBitmap,
                    translationX = resolveNavigationSwipePreviewStartTranslation(width, direction)
                )
            }
        }
        installNavigationSwipeSnapshot(snapshotBitmap)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        return true
    }

    private fun updateInteractiveNavigationSwipeProgress(deltaX: Float) {
        val direction = pendingNavigationSwipeDirection ?: return
        val width = resolveNavigationSwipeWidth() ?: return
        val clampedTranslation = when (direction) {
            NavigationSwipeDirection.NEXT -> deltaX.coerceIn(-width.toFloat(), 0f)
            NavigationSwipeDirection.PREVIOUS -> deltaX.coerceIn(0f, width.toFloat())
        }
        navigationSwipeCurrentTranslationX = clampedTranslation
        navigationSwipeSnapshotView?.translationX = clampedTranslation
        val previewTranslation =
            resolveNavigationSwipePreviewTranslation(width, direction, clampedTranslation)
        navigationSwipePreviewView?.translationX = previewTranslation
        navigationSwipePreviewManagedWebView?.webView?.translationX = previewTranslation
    }

    private fun completeInteractiveNavigationSwipe() {
        val direction = pendingNavigationSwipeDirection ?: return
        val targetItem = navigationSwipeInteractiveTargetItem
        val width = resolveNavigationSwipeWidth() ?: run {
            cancelInteractiveNavigationSwipe(animated = false)
            return
        }
        navigationSwipeVelocityTracker?.computeCurrentVelocity(1_000)
        val velocityX = navigationSwipeVelocityTracker?.xVelocity ?: 0f
        val progress = abs(navigationSwipeCurrentTranslationX) / width.toFloat()
        val velocityMatchesDirection = when (direction) {
            NavigationSwipeDirection.NEXT -> velocityX <= -NAVIGATION_SWIPE_COMMIT_VELOCITY_PX
            NavigationSwipeDirection.PREVIOUS -> velocityX >= NAVIGATION_SWIPE_COMMIT_VELOCITY_PX
        }
        navigationSwipeVelocityTracker?.recycle()
        navigationSwipeVelocityTracker = null
        if (targetItem == null || (progress < NAVIGATION_SWIPE_COMMIT_PROGRESS && !velocityMatchesDirection)) {
            cancelInteractiveNavigationSwipe(animated = true)
            return
        }
        navigationSwipeInteractiveActive = false
        navigationSwipeInteractiveCommitted = true
        navigationSwipeListener?.invoke(direction) ?: cancelInteractiveNavigationSwipe(animated = true)
    }

    private fun cancelInteractiveNavigationSwipe(animated: Boolean) {
        val direction = pendingNavigationSwipeDirection
        val width = resolveNavigationSwipeWidth()
        val previewResetTranslation = if (direction != null && width != null) {
            resolveNavigationSwipePreviewStartTranslation(width, direction)
        } else {
            0f
        }
        navigationSwipeInteractiveActive = false
        navigationSwipeInteractiveCommitted = false
        navigationSwipeInteractiveTargetItem = null
        navigationSwipeCurrentTranslationX = 0f
        if (!animated) {
            finishCanceledNavigationSwipe()
            return
        }
        val snapshotView = navigationSwipeSnapshotView
        val previewView = navigationSwipePreviewView
        val previewManagedWebView = navigationSwipePreviewManagedWebView?.webView
        if (snapshotView == null && previewView == null && previewManagedWebView == null) {
            finishCanceledNavigationSwipe()
            return
        }
        var remainingAnimations = listOfNotNull(snapshotView, previewView, previewManagedWebView).size
        val onAnimationFinished = {
            remainingAnimations -= 1
            if (remainingAnimations == 0) {
                finishCanceledNavigationSwipe()
            }
        }
        snapshotView?.animate()?.cancel()
        snapshotView?.animate()
            ?.translationX(0f)
            ?.alpha(1f)
            ?.setDuration(NAVIGATION_SWIPE_CANCEL_DURATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction(onAnimationFinished)
            ?.start()
        previewView?.animate()?.cancel()
        previewView?.animate()
            ?.translationX(previewResetTranslation)
            ?.alpha(1f)
            ?.setDuration(NAVIGATION_SWIPE_CANCEL_DURATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction(onAnimationFinished)
            ?.start()
        previewManagedWebView?.animate()?.cancel()
        previewManagedWebView?.animate()
            ?.translationX(previewResetTranslation)
            ?.alpha(1f)
            ?.setDuration(NAVIGATION_SWIPE_CANCEL_DURATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction(onAnimationFinished)
            ?.start()
    }

    private fun finishCanceledNavigationSwipe() {
        pendingNavigationSwipeDirection = null
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSnapshotFinalTranslationX = 0f
        removeNavigationSwipeSnapshot()
        removeNavigationSwipePreview()
        keepWebViewHiddenUntilLoaded = false
        clearNavigationSwipeBlankBackground()
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
    }

    private fun continueInteractiveNavigationSwipeTransition(
        item: NavigationItem,
        direction: NavigationSwipeDirection,
        resetHistory: Boolean
    ): Boolean {
        if (!navigationSwipeInteractiveCommitted ||
            pendingNavigationSwipeDirection != direction ||
            navigationSwipeInteractiveTargetItem?.id != item.id
        ) {
            return false
        }
        val width = resolveNavigationSwipeWidth() ?: return false
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSnapshotFinalTranslationX =
            resolveNavigationSwipeExitTranslation(width, direction)
        keepWebViewHiddenUntilLoaded = true
        navigationSwipeInteractiveTargetItem = item
        if (!navigationPageStackEnabled && navigationPreloadCount <= 0) {
            currentNavigationItemId = item.id
            val currentWebView = activeWebView() ?: return false
            currentWebView.translationX = 0f
            currentWebView.alpha = 1f
            startCommittedNavigationSwipeAnimation(width, direction)
            loadUrlInternal(currentWebView, item.url, resetHistory)
            return true
        }
        startCommittedNavigationSwipeAnimation(width, direction)
        openNavigationRoot(
            item = item,
            resetHistory = resetHistory
        ) { webView ->
            webView.alpha = 1f
            if (navigationSwipePreviewManagedWebView?.webView !== webView) {
                webView.translationX = 0f
            }
        }
        return true
    }

    private fun startCommittedNavigationSwipeAnimation(
        width: Int,
        direction: NavigationSwipeDirection
    ) {
        val exitTranslation = resolveNavigationSwipeExitTranslation(width, direction)
        navigationSwipeSnapshotView?.animate()?.cancel()
        navigationSwipePreviewView?.animate()?.cancel()
        navigationSwipePreviewManagedWebView?.webView?.animate()?.cancel()
        navigationSwipePreviewView?.animate()
            ?.translationX(0f)
            ?.alpha(1f)
            ?.setDuration(NAVIGATION_SWIPE_RELEASE_DURATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        navigationSwipePreviewManagedWebView?.webView?.animate()
            ?.translationX(0f)
            ?.alpha(1f)
            ?.setDuration(NAVIGATION_SWIPE_RELEASE_DURATION_MS)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        val snapshotView = navigationSwipeSnapshotView
        if (snapshotView == null) {
            pendingNavigationSwipeExitCompleted = true
            maybeCompleteNavigationSwipeEnterAnimation()
            return
        }
        snapshotView.animate()
            .translationX(exitTranslation)
            .alpha(NAVIGATION_SWIPE_SNAPSHOT_EXIT_ALPHA)
            .setDuration(NAVIGATION_SWIPE_RELEASE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                removeNavigationSwipeSnapshot()
                pendingNavigationSwipeExitCompleted = true
                maybeCompleteNavigationSwipeEnterAnimation()
            }
            .start()
    }

    private fun resolveAdjacentNavigationItem(direction: NavigationSwipeDirection): NavigationItem? {
        return TemplateSwipeNavigationHelper.resolveAdjacentItem(
            items = navigationItems,
            currentItemId = currentNavigationItemId?.hashCode(),
            direction = direction
        )
    }

    private fun resolveNavigationSwipeWidth(): Int? {
        val currentBinding = _binding ?: return null
        return activeWebView()?.width
            ?.takeIf { it > 0 }
            ?: currentBinding.webViewContainer.width.takeIf { it > 0 }
            ?: currentBinding.root.width.takeIf { it > 0 }
    }

    private fun resolveNavigationSwipeExitTranslation(
        width: Int,
        direction: NavigationSwipeDirection
    ): Float {
        return when (direction) {
            NavigationSwipeDirection.NEXT -> -width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
            NavigationSwipeDirection.PREVIOUS -> width * NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO
        }
    }

    private fun resolveNavigationSwipePreviewStartTranslation(
        width: Int,
        direction: NavigationSwipeDirection
    ): Float {
        return when (direction) {
            NavigationSwipeDirection.NEXT -> width.toFloat()
            NavigationSwipeDirection.PREVIOUS -> -width.toFloat()
        }
    }

    private fun resolveNavigationSwipePreviewTranslation(
        width: Int,
        direction: NavigationSwipeDirection,
        currentTranslation: Float
    ): Float {
        return currentTranslation + resolveNavigationSwipePreviewStartTranslation(width, direction)
    }

    private fun dispatchCancelToWebView(webView: FireflyWebView, event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        webView.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun restoreNavigationSwipeTouchStartScroll(webView: FireflyWebView) {
        if (webView.scrollX != navigationSwipeTouchStartScrollX ||
            webView.scrollY != navigationSwipeTouchStartScrollY
        ) {
            webView.scrollTo(navigationSwipeTouchStartScrollX, navigationSwipeTouchStartScrollY)
        }
        webView.cancelLongPress()
        webView.isPressed = false
        webView.invalidate()
    }

    private fun resolveNavigationSwipePreviewManagedWebView(item: NavigationItem): ManagedWebView? {
        val preloadedManagedWebView = preloadedNavigationRoots[item.id] ?: return null
        return preloadedManagedWebView.takeUnless {
            it.isLoading || it.lastLoadError != null
        }
    }

    private fun captureNavigationSwipePreviewBitmap(item: NavigationItem): Bitmap? {
        val preloadedManagedWebView = preloadedNavigationRoots[item.id] ?: return null
        if (preloadedManagedWebView.isLoading || preloadedManagedWebView.lastLoadError != null) {
            return null
        }
        return captureWebViewSnapshot(preloadedManagedWebView.webView)
    }

    private fun captureWebViewSnapshot(webView: android.webkit.WebView?): Bitmap? {
        val targetWebView = webView ?: return null
        val width = targetWebView.width
        val height = targetWebView.height
        if (width <= 0 || height <= 0) {
            return null
        }
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                targetWebView.draw(canvas)
            }
        }.getOrNull()
    }

    private fun createNavigationSwipeFallbackSnapshot(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) {
            return null
        }
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(resolveNavigationSwipeBlankColor())
            }
        }.getOrNull()
    }

    private fun installNavigationSwipePreview(bitmap: Bitmap, translationX: Float) {
        val currentBinding = _binding ?: return
        removeNavigationSwipePreview()
        val previewView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(bitmap)
            alpha = 1f
            this.translationX = translationX
        }
        currentBinding.root.addView(previewView, 1)
        navigationSwipePreviewView = previewView
    }

    private fun installNavigationSwipePreview(managedWebView: ManagedWebView, translationX: Float) {
        removeNavigationSwipePreview()
        navigationSwipePreviewManagedWebView = managedWebView
        (managedWebView.webView.parent as? ViewGroup)?.bringChildToFront(managedWebView.webView)
        managedWebView.webView.animate().cancel()
        managedWebView.webView.visibility = View.VISIBLE
        managedWebView.webView.alpha = 1f
        managedWebView.webView.translationX = translationX
        managedWebView.webView.requestLayout()
        managedWebView.webView.invalidate()
        managedWebView.webView.bringToFront()
    }

    private fun removeNavigationSwipePreview() {
        navigationSwipePreviewManagedWebView?.let { managedWebView ->
            managedWebView.webView.animate().cancel()
            managedWebView.webView.translationX = 0f
            managedWebView.webView.alpha = 1f
            navigationSwipePreviewManagedWebView = null
        }
        val previewView = navigationSwipePreviewView ?: return
        previewView.animate().cancel()
        (previewView.parent as? ViewGroup)?.removeView(previewView)
        previewView.setImageDrawable(null)
        navigationSwipePreviewView = null
    }

    private fun fadeOutNavigationSwipePreviewIfPresent() {
        val previewView = navigationSwipePreviewView ?: return
        previewView.animate().cancel()
        previewView.animate()
            .alpha(0f)
            .setDuration(NAVIGATION_SWIPE_PREVIEW_READY_FADE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                removeNavigationSwipePreview()
            }
            .start()
    }

    private fun handlePageStarted(url: String) {
        val previousUrl = currentPageUrl.orEmpty()
        if (previousUrl.isNotBlank() && previousUrl != url) {
            dispatchPageEvent(
                trigger = PAGE_EVENT_TRIGGER_PAGE_LEFT,
                url = previousUrl,
                title = currentPageTitle.orEmpty(),
                nextUrl = url
            )
        }
        currentPageUrl = url
        dispatchPageEvent(
            trigger = PAGE_EVENT_TRIGGER_PAGE_STARTED,
            url = url,
            title = currentPageTitle.orEmpty(),
            previousUrl = previousUrl
        )
    }

    private fun handlePageTitleChanged(title: String) {
        if (title.isBlank() || title == currentPageTitle) {
            return
        }
        currentPageTitle = title
        dispatchPageEvent(
            trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
            url = currentPageUrl.orEmpty(),
            title = title
        )
    }

    fun reload() {
        if (errorStateLocked) {
            if (reloadCurrentPageFromFreshWebView()) {
                return
            }
            beginRecoveryLoad()
        } else {
            clearErrorState()
        }
        showLoading(true)
        activeWebView()?.reload()
    }

    fun reloadIgnoringCache() {
        if (errorStateLocked) {
            if (reloadCurrentPageFromFreshWebView(clearCache = true)) {
                return
            }
            beginRecoveryLoad()
        } else {
            clearErrorState()
        }
        showLoading(true)
        activeWebView()?.apply {
            clearCache(true)
            reload()
        }
    }

    fun runJavaScript(script: String) {
        val resolvedScript = script.trim()
        if (resolvedScript.isBlank()) {
            return
        }
        activeWebView()?.evaluateJavascript(resolvedScript, null)
    }

    private fun openExternalIntent(intent: Intent): Boolean {
        val mode = mainViewModel.requireConfig().security.openOtherAppsMode
        return when (mode) {
            "allow" -> launchExternalIntent(intent)
            "block" -> {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.web_external_app_blocked, Toast.LENGTH_SHORT).show()
                }
                true
            }

            else -> {
                showOpenExternalIntentDialog(intent)
                true
            }
        }
    }

    private fun showOpenExternalIntentDialog(intent: Intent) {
        val context = context ?: return
        externalAppDialog?.dismiss()
        val packageManager = context.packageManager
        val resolveInfo = runCatching {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        val appLabel = resolveInfo?.loadLabel(packageManager)?.toString().orEmpty()
        val appIcon = resolveInfo?.loadIcon(packageManager)
        val targetUri = intent.data
        val scheme = targetUri?.scheme.orEmpty().lowercase()
        val resolvedPackageName = resolveInfo?.activityInfo?.packageName.orEmpty()
        val browserLikeTarget = scheme in setOf("http", "https") &&
            intent.`package`.isNullOrBlank() &&
            intent.component == null &&
            isProbablyBrowserPackage(resolvedPackageName)
        val fallbackTarget = intent.`package`
            ?: intent.component?.packageName
            ?: intent.data?.host
            ?: intent.scheme
            ?: getString(R.string.web_external_target_fallback)
        val targetLabel = when {
            browserLikeTarget -> getString(R.string.web_external_target_browser_like)
            appLabel.isNotBlank() -> appLabel
            else -> fallbackTarget
        }
        val detail = when {
            browserLikeTarget -> intent.dataString ?: fallbackTarget
            appLabel.isNotBlank() && intent.dataString?.isNotBlank() == true -> intent.dataString
            appLabel.isNotBlank() && resolveInfo?.activityInfo?.packageName?.isNotBlank() == true -> resolveInfo.activityInfo.packageName
            else -> intent.dataString ?: resolveInfo?.activityInfo?.packageName ?: fallbackTarget
        }
        externalAppDialog = AlertDialog.Builder(context)
            .setTitle(getString(R.string.web_external_dialog_title, targetLabel))
            .setMessage(getString(R.string.web_external_dialog_message, targetLabel, detail))
            .setIcon(if (browserLikeTarget) null else appIcon)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.web_external_dialog_open) { _, _ ->
                launchExternalIntent(intent)
            }
            .create()
        externalAppDialog?.show()
    }

    private fun isProbablyBrowserPackage(packageName: String): Boolean {
        if (packageName.isBlank()) {
            return false
        }
        val normalized = packageName.lowercase()
        return normalized.contains("chrome") ||
            normalized.contains("browser") ||
            normalized.contains("firefox") ||
            normalized.contains("opera") ||
            normalized.contains("edge") ||
            normalized.contains("brave")
    }

    private fun launchExternalIntent(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            if (isAdded) {
                Toast.makeText(requireContext(), R.string.web_external_no_handler, Toast.LENGTH_SHORT).show()
            }
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun goBack(): Boolean {
        val webView = activeWebView() ?: return false
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return popNavigationStackPage()
    }

    fun currentUrl(): String? {
        return currentPageUrl ?: activeWebView()?.url
    }

    fun resolveBackNavigationAction(): BackNavigationAction {
        if (goBack()) {
            return BackNavigationAction.HANDLED
        }

        return when (mainViewModel.requireConfig().browser.backAction) {
            "go_back_or_home" -> BackNavigationAction.GO_HOME
            "disabled" -> BackNavigationAction.HANDLED
            else -> BackNavigationAction.NOT_HANDLED
        }
    }

    fun handleBackAction(): Boolean {
        return resolveBackNavigationAction() == BackNavigationAction.HANDLED
    }

    fun exitFullscreen(): Boolean {
        return activeManagedWebView()?.chromeClient?.exitFullscreen() == true
    }

    private fun triggerBlobDownload(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val guessedFileName = downloadHandler.guessFileName(blobUrl, contentDisposition, mimeType)
        Log.d(TAG, "triggerBlobDownload blobUrl=$blobUrl fileName=$guessedFileName mimeType=$mimeType")
        val script = """
            (function() {
                const blobUrl = ${JSONObject.quote(blobUrl)};
                const fileName = ${JSONObject.quote(guessedFileName)};
                const fallbackMimeType = ${JSONObject.quote(mimeType.orEmpty())};
                const chunkSize = 262144;

                function blobToPayload(blob) {
                    return new Promise(function(resolve, reject) {
                        const reader = new FileReader();
                        reader.onloadend = function() {
                            const result = String(reader.result || '');
                            const base64 = result.indexOf(',') >= 0 ? result.split(',')[1] : '';
                            resolve({
                                base64: base64,
                                mimeType: blob.type || fallbackMimeType || 'application/octet-stream'
                            });
                        };
                        reader.onerror = function() {
                            reject(new Error('blob read failed'));
                        };
                        reader.readAsDataURL(blob);
                    });
                }

                function sendPayloadToAndroid(payload, downloadFileName) {
                    const sessionId = 'blob_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                    const totalChunks = Math.max(1, Math.ceil((payload.base64 || '').length / chunkSize));
                    const beginResult = window.$BLOB_BRIDGE_NAME.beginBlobDownload(sessionId, downloadFileName, payload.mimeType, totalChunks);
                    if (!beginResult) {
                        return;
                    }

                    const base64 = payload.base64 || '';
                    if (!base64.length) {
                        window.$BLOB_BRIDGE_NAME.cancelBlobDownload(sessionId, 'empty base64 payload');
                        throw new Error('empty base64 payload');
                    }

                    for (let start = 0; start < base64.length; start += chunkSize) {
                        const end = Math.min(start + chunkSize, base64.length);
                        const chunk = base64.slice(start, end);
                        const isLastChunk = end >= base64.length;
                        window.$BLOB_BRIDGE_NAME.appendBlobChunk(sessionId, chunk, isLastChunk);
                    }
                }

                function fetchBlob() {
                    return fetch(blobUrl).then(function(response) {
                        return response.blob();
                    });
                }

                function xhrBlob() {
                    return new Promise(function(resolve, reject) {
                        try {
                            const xhr = new XMLHttpRequest();
                            xhr.open('GET', blobUrl, true);
                            xhr.responseType = 'blob';
                            xhr.onload = function() {
                                if (xhr.status === 200 || xhr.status === 0) {
                                    resolve(xhr.response);
                                } else {
                                    reject(new Error('xhr status ' + xhr.status));
                                }
                            };
                            xhr.onerror = function() {
                                reject(new Error('xhr failed'));
                            };
                            xhr.send();
                        } catch (error) {
                            reject(error);
                        }
                    });
                }

                fetchBlob()
                    .catch(function(fetchError) {
                        console.warn('blob fetch failed, fallback to xhr', fetchError);
                        return xhrBlob();
                    })
                    .then(blobToPayload)
                    .then(function(payload) {
                        sendPayloadToAndroid(payload, fileName);
                    })
                    .catch(function(error) {
                        window.$BLOB_BRIDGE_NAME.cancelBlobDownload(null, String(error));
                    });
            })();
        """.trimIndent()

        activeWebView()?.evaluateJavascript(script, null)
    }

    private fun installBlobDownloadHook(webView: android.webkit.WebView) {
        val script = """
            (function() {
                if (window.__fireflyBlobHookInstalled) {
                    return;
                }
                window.__fireflyBlobHookInstalled = true;
                var chunkSize = 262144;

                function blobToPayload(blob, fallbackMimeType) {
                    return new Promise(function(resolve, reject) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var result = String(reader.result || '');
                            var base64 = result.indexOf(',') >= 0 ? result.split(',')[1] : '';
                            resolve({
                                base64: base64,
                                mimeType: blob.type || fallbackMimeType || 'application/octet-stream'
                            });
                        };
                        reader.onerror = function() {
                            reject(new Error('blob read failed'));
                        };
                        reader.readAsDataURL(blob);
                    });
                }

                function sendPayloadToAndroid(payload, fileName) {
                    var sessionId = 'blob_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                    var totalChunks = Math.max(1, Math.ceil((payload.base64 || '').length / chunkSize));
                    var beginResult = window.$BLOB_BRIDGE_NAME.beginBlobDownload(sessionId, fileName, payload.mimeType, totalChunks);
                    if (!beginResult) {
                        return;
                    }

                    var base64 = payload.base64 || '';
                    if (!base64.length) {
                        window.$BLOB_BRIDGE_NAME.cancelBlobDownload(sessionId, 'empty base64 payload');
                        throw new Error('empty base64 payload');
                    }

                    for (var start = 0; start < base64.length; start += chunkSize) {
                        var end = Math.min(start + chunkSize, base64.length);
                        var chunk = base64.slice(start, end);
                        var isLastChunk = end >= base64.length;
                        window.$BLOB_BRIDGE_NAME.appendBlobChunk(sessionId, chunk, isLastChunk);
                    }
                }

                function readBlobUrl(blobUrl, fallbackMimeType) {
                    return new Promise(function(resolve, reject) {
                        try {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', blobUrl, true);
                            xhr.responseType = 'blob';
                            xhr.onload = function() {
                                if (xhr.status === 200 || xhr.status === 0) {
                                    resolve(xhr.response);
                                } else {
                                    reject(new Error('xhr status ' + xhr.status));
                                }
                            };
                            xhr.onerror = function() {
                                reject(new Error('xhr failed'));
                            };
                            xhr.send();
                        } catch (error) {
                            reject(error);
                        }
                    }).then(function(blob) {
                        return blobToPayload(blob, fallbackMimeType);
                    });
                }

                function handleBlobLink(anchor) {
                    if (!anchor) {
                        return false;
                    }
                    var href = anchor.href || anchor.getAttribute('href') || '';
                    if (href.indexOf('blob:') !== 0) {
                        return false;
                    }

                    var fileName = anchor.getAttribute('download') || 'download';
                    readBlobUrl(href, '')
                        .then(function(payload) {
                            sendPayloadToAndroid(payload, fileName);
                        })
                        .catch(function(error) {
                            window.$BLOB_BRIDGE_NAME.cancelBlobDownload(null, 'hook failed: ' + String(error));
                        });
                    return true;
                }

                document.addEventListener('click', function(event) {
                    var anchor = event.target && event.target.closest ? event.target.closest('a') : null;
                    if (!anchor) {
                        return;
                    }
                    if (handleBlobLink(anchor)) {
                        event.preventDefault();
                        event.stopPropagation();
                    }
                }, true);

                var originalClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    if (handleBlobLink(this)) {
                        return;
                    }
                    return originalClick.apply(this, arguments);
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun installDownloadMetadataHook(webView: android.webkit.WebView) {
        val script = """
            (function() {
                if (window.__fireflyDownloadMetadataHookInstalled) {
                    return;
                }
                window.__fireflyDownloadMetadataHookInstalled = true;
                var bridge = window.$DOWNLOAD_METADATA_BRIDGE_NAME;

                function rememberDownloadFileName(anchor) {
                    if (!anchor || !bridge || typeof bridge.rememberFileName !== 'function') {
                        return false;
                    }
                    var href = anchor.href || anchor.getAttribute('href') || '';
                    var fileName = anchor.getAttribute('download') || '';
                    if (!href || !fileName || href.indexOf('blob:') === 0) {
                        return false;
                    }
                    try {
                        bridge.rememberFileName(href, fileName);
                        return true;
                    } catch (error) {
                        return false;
                    }
                }

                document.addEventListener('click', function(event) {
                    var anchor = event.target && event.target.closest ? event.target.closest('a[download]') : null;
                    if (!anchor) {
                        return;
                    }
                    rememberDownloadFileName(anchor);
                }, true);

                var originalClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    if (this && this.hasAttribute && this.hasAttribute('download')) {
                        rememberDownloadFileName(this);
                    }
                    return originalClick.apply(this, arguments);
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun installClipboardBridge(webView: android.webkit.WebView) {
        val script = """
            (function() {
                if (window.__fireflyClipboardHookInstalled) {
                    return;
                }
                window.__fireflyClipboardHookInstalled = true;
                var callbacks = Object.create(null);
                var bridge = window.$CLIPBOARD_BRIDGE_NAME;

                window.__fireflyClipboardDispatch = function(requestId, ok, text, error) {
                    var callback = callbacks[requestId];
                    if (!callback) {
                        return;
                    }
                    delete callbacks[requestId];
                    if (ok) {
                        callback.resolve(String(text || ''));
                    } else {
                        callback.reject(new Error(String(error || 'clipboard read failed')));
                    }
                };

                function nativeReadText() {
                    return new Promise(function(resolve, reject) {
                        if (!bridge || typeof bridge.readText !== 'function') {
                            reject(new Error('clipboard bridge unavailable'));
                            return;
                        }
                        var requestId = 'clip_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                        callbacks[requestId] = { resolve: resolve, reject: reject };
                        try {
                            bridge.readText(requestId);
                        } catch (error) {
                            delete callbacks[requestId];
                            reject(error);
                        }
                    });
                }

                function nativeWriteText(text) {
                    return new Promise(function(resolve, reject) {
                        if (!bridge || typeof bridge.writeText !== 'function') {
                            reject(new Error('clipboard bridge unavailable'));
                            return;
                        }
                        var requestId = 'clip_write_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                        callbacks[requestId] = {
                            mode: 'write',
                            resolve: resolve,
                            reject: reject
                        };
                        try {
                            bridge.writeText(requestId, String(text == null ? '' : text));
                        } catch (error) {
                            delete callbacks[requestId];
                            reject(error);
                        }
                    });
                }

                window.FireflyClipboard = {
                    readText: nativeReadText,
                    writeText: nativeWriteText
                };

                if (!navigator.clipboard) {
                    Object.defineProperty(navigator, 'clipboard', {
                        configurable: true,
                        value: {
                            readText: nativeReadText,
                            writeText: nativeWriteText
                        }
                    });
                    return;
                }

                var originalReadText = typeof navigator.clipboard.readText === 'function'
                    ? navigator.clipboard.readText.bind(navigator.clipboard)
                    : null;
                var originalWriteText = typeof navigator.clipboard.writeText === 'function'
                    ? navigator.clipboard.writeText.bind(navigator.clipboard)
                    : null;

                try {
                    navigator.clipboard.readText = function() {
                        if (!originalReadText) {
                            return nativeReadText();
                        }
                        return originalReadText().catch(function() {
                            return nativeReadText();
                        });
                    };
                    navigator.clipboard.writeText = function(text) {
                        if (!originalWriteText) {
                            return nativeWriteText(text);
                        }
                        return originalWriteText(text).catch(function() {
                            return nativeWriteText(text);
                        });
                    };
                } catch (error) {
                    window.FireflyClipboard.readText = nativeReadText;
                    window.FireflyClipboard.writeText = nativeWriteText;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun installPageEventHook(webView: android.webkit.WebView) {
        val script = """
            (function() {
                if (window.__fireflyPageEventHookInstalled) {
                    return;
                }
                window.__fireflyPageEventHookInstalled = true;
                var bridge = window.$PAGE_EVENT_BRIDGE_NAME;
                if (!bridge) {
                    return;
                }

                function safeTitle() {
                    return String(document.title || '');
                }

                function safeUrl() {
                    return String(location.href || '');
                }

                function dispatchUrlChange() {
                    try {
                        if (typeof bridge.onSpaUrlChanged === 'function') {
                            bridge.onSpaUrlChanged(safeUrl(), safeTitle());
                        }
                    } catch (error) {
                        console.warn('Firefly page event url dispatch failed', error);
                    }
                }

                function dispatchTitleChange() {
                    try {
                        if (typeof bridge.onPageTitleChanged === 'function') {
                            bridge.onPageTitleChanged(safeTitle());
                        }
                    } catch (error) {
                        console.warn('Firefly page event title dispatch failed', error);
                    }
                }

                var originalPushState = history.pushState;
                history.pushState = function() {
                    var result = originalPushState.apply(this, arguments);
                    dispatchUrlChange();
                    return result;
                };

                var originalReplaceState = history.replaceState;
                history.replaceState = function() {
                    var result = originalReplaceState.apply(this, arguments);
                    dispatchUrlChange();
                    return result;
                };

                window.addEventListener('popstate', dispatchUrlChange, true);
                window.addEventListener('hashchange', dispatchUrlChange, true);

                var titleElement = document.querySelector('title');
                if (titleElement && typeof MutationObserver !== 'undefined') {
                    new MutationObserver(dispatchTitleChange).observe(titleElement, {
                        childList: true,
                        subtree: true,
                        characterData: true
                    });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun dispatchPageEvent(
        trigger: String,
        url: String,
        title: String = "",
        previousUrl: String = "",
        nextUrl: String = ""
    ) {
        val safeUrl = url.trim()
        if (safeUrl.isBlank()) {
            return
        }
        val dispatcher = pageEventDispatcher ?: return
        val eventContext = PageEventContext(
            trigger = trigger,
            url = safeUrl,
            title = title,
            previousUrl = previousUrl,
            nextUrl = nextUrl
        )
        dispatcher.resolve(eventContext).forEach { rule ->
            rule.actions.forEach { action ->
                executePageEventAction(action.type, action.value, action.url, action.script, eventContext)
            }
        }
    }

    private fun executePageEventAction(
        type: String,
        value: String,
        url: String,
        script: String,
        eventContext: PageEventContext
    ) {
        when (type) {
            "toast" -> {
                val message = resolveEventTemplate(value, eventContext)
                if (message.isNotBlank() && isAdded) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }

            "load_url" -> {
                val targetUrl = resolveEventTemplate(url.ifBlank { value }, eventContext)
                if (targetUrl.isNotBlank() && targetUrl != currentPageUrl) {
                    loadUrl(targetUrl)
                }
            }

            "open_external" -> {
                val targetUrl = resolveEventTemplate(url.ifBlank { value }, eventContext)
                if (targetUrl.isBlank()) {
                    return
                }
                openExternalIntent(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            }

            "reload" -> reload()
            "reload_ignore_cache" -> reloadIgnoringCache()
            "go_back" -> goBack()

            "copy_to_clipboard" -> {
                val text = resolveEventTemplate(value.ifBlank { eventContext.url }, eventContext)
                if (text.isBlank()) {
                    return
                }
                val clipboardManager = context?.getSystemService(ClipboardManager::class.java) ?: return
                clipboardManager.setPrimaryClip(ClipData.newPlainText("page_event", text))
            }

            "run_js" -> {
                val resolvedScript = resolveEventTemplate(script.ifBlank { value }, eventContext)
                if (resolvedScript.isNotBlank()) {
                    activeWebView()?.evaluateJavascript(resolvedScript, null)
                }
            }
        }
    }

    private fun resolveEventTemplate(value: String, context: PageEventContext): String {
        return value
            .replace("{trigger}", context.trigger)
            .replace("{url}", context.url)
            .replace("{title}", context.title)
            .replace("{previousUrl}", context.previousUrl)
            .replace("{nextUrl}", context.nextUrl)
    }

    private fun dispatchClipboardReadResult(requestId: String, text: String?, error: String?) {
        val webView = activeWebView() ?: return
        val script = buildString {
            append("(function(){")
            append("if(window.__fireflyClipboardDispatch){window.__fireflyClipboardDispatch(")
            append(JSONObject.quote(requestId))
            append(",")
            append(if (error == null) "true" else "false")
            append(",")
            append(JSONObject.quote(text.orEmpty()))
            append(",")
            append(JSONObject.quote(error.orEmpty()))
            append(");}})();")
        }
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun dispatchClipboardWriteResult(requestId: String, error: String?) {
        val webView = activeWebView() ?: return
        val script = buildString {
            append("(function(){")
            append("if(window.__fireflyClipboardDispatch){window.__fireflyClipboardDispatch(")
            append(JSONObject.quote(requestId))
            append(",")
            append(if (error == null) "true" else "false")
            append(",")
            append("''")
            append(",")
            append(JSONObject.quote(error.orEmpty()))
            append(");}})();")
        }
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun installNotificationBridge(webView: android.webkit.WebView) {
        val script = """
            (function() {
                if (window.__fireflyNotificationHookInstalled) {
                    return;
                }
                window.__fireflyNotificationHookInstalled = true;
                var callbacks = Object.create(null);
                var bridge = window.$NOTIFICATION_BRIDGE_NAME;
                var permissionState = 'default';

                function updatePermission(nextPermission) {
                    permissionState = String(nextPermission || 'default');
                    if (window.FireflyNotification) {
                        window.FireflyNotification.permission = permissionState;
                    }
                    if (window.NotificationShim) {
                        window.NotificationShim.permission = permissionState;
                    }
                }

                window.__fireflyNotificationDispatch = function(requestId, permission) {
                    updatePermission(permission);
                    var callback = callbacks[requestId];
                    if (!callback) {
                        return;
                    }
                    delete callbacks[requestId];
                    callback.resolve(permissionState);
                };

                function requestPermission(callback) {
                    return new Promise(function(resolve, reject) {
                        if (!bridge || typeof bridge.requestPermission !== 'function') {
                            reject(new Error('notification bridge unavailable'));
                            return;
                        }
                        var requestId = 'notify_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                        callbacks[requestId] = { resolve: resolve, reject: reject };
                        try {
                            bridge.requestPermission(requestId);
                        } catch (error) {
                            delete callbacks[requestId];
                            reject(error);
                        }
                    }).then(function(permission) {
                        if (typeof callback === 'function') {
                            callback(permission);
                        }
                        return permission;
                    });
                }

                function showNativeNotification(title, options) {
                    options = options || {};
                    if (permissionState !== 'granted') {
                        throw new Error('notification permission denied');
                    }
                    if (!bridge || typeof bridge.showNotification !== 'function') {
                        throw new Error('notification bridge unavailable');
                    }
                    var ok = bridge.showNotification(
                        String(title || ''),
                        String(options.body || ''),
                        String(options.tag || '')
                    );
                    if (!ok) {
                        throw new Error('native notification failed');
                    }
                }

                function NotificationShim(title, options) {
                    if (!(this instanceof NotificationShim)) {
                        throw new TypeError("Failed to construct 'Notification': Please use the 'new' operator.");
                    }
                    showNativeNotification(title, options);
                }

                NotificationShim.requestPermission = requestPermission;
                NotificationShim.permission = permissionState;
                window.NotificationShim = NotificationShim;

                window.FireflyNotification = {
                    requestPermission: requestPermission,
                    show: showNativeNotification,
                    permission: permissionState
                };

                try {
                    updatePermission(bridge && typeof bridge.getPermissionState === 'function'
                        ? bridge.getPermissionState()
                        : 'default');
                } catch (error) {
                    updatePermission('default');
                }

                if (!window.Notification) {
                    window.Notification = NotificationShim;
                    return;
                }

                try {
                    window.Notification.requestPermission = function(callback) {
                        return requestPermission(callback);
                    };
                } catch (error) {
                    window.FireflyNotification.requestPermission = requestPermission;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun dispatchNotificationPermissionResult(requestId: String, permission: String) {
        val webView = activeWebView() ?: return
        val script = buildString {
            append("(function(){")
            append("if(window.__fireflyNotificationDispatch){window.__fireflyNotificationDispatch(")
            append(JSONObject.quote(requestId))
            append(",")
            append(JSONObject.quote(permission))
            append(");}})();")
        }
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun handleDownloadEvent(event: DownloadEvent) {
        if (!shouldShowDownloadOverlay()) {
            hideDownloadStatus()
            if (event is DownloadEvent.Failure && isAdded) {
                Toast.makeText(requireContext(), getString(R.string.download_failed_text, event.reason), Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (event) {
            is DownloadEvent.Started -> {
                showDownloadStatus(
                    text = getString(R.string.download_progress_text, event.fileName),
                    progressPercent = null,
                    autoHide = false
                )
            }

            is DownloadEvent.Progress -> {
                val text = event.progressPercent?.let { percent ->
                    getString(R.string.download_progress_with_percent, event.fileName, percent)
                } ?: getString(R.string.download_progress_text, event.fileName)
                showDownloadStatus(
                    text = text,
                    progressPercent = event.progressPercent,
                    autoHide = false
                )
            }

            is DownloadEvent.Success -> {
                showDownloadStatus(
                    text = getString(R.string.download_completed_text, event.fileName),
                    progressPercent = 100,
                    autoHide = true
                )
            }

            is DownloadEvent.Failure -> {
                showDownloadStatus(
                    text = getString(R.string.download_failed_text, event.reason),
                    progressPercent = null,
                    autoHide = true
                )
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.download_failed_text, event.reason), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performRetryAction() {
        when (currentPageState?.errorRetryAction) {
            ERROR_RETRY_ACTION_GO_HOME -> {
                val homeUrl = mainViewModel.requireConfig().app.defaultUrl
                if (homeUrl.isBlank() || currentPageUrl == homeUrl) {
                    retryCurrentPage()
                } else {
                    loadUrl(homeUrl)
                }
            }

            ERROR_RETRY_ACTION_LOAD_URL -> {
                val retryUrl = currentPageState?.errorRetryUrl.orEmpty()
                if (retryUrl.isBlank()) {
                    retryCurrentPage()
                } else {
                    loadUrl(retryUrl)
                }
            }

            else -> retryCurrentPage()
        }
    }

    private fun retryCurrentPage() {
        if (reloadCurrentPageFromFreshWebView()) {
            return
        }
        beginRecoveryLoad()
        showLoading(true)
        val currentWebView = activeWebView()
        val currentUrl = currentWebView?.url.orEmpty()
        if (currentUrl.isBlank() && !currentPageUrl.isNullOrBlank()) {
            loadUrl(currentPageUrl.orEmpty())
            return
        }
        currentWebView?.reload()
    }

    private fun reloadCurrentPageFromFreshWebView(clearCache: Boolean = false): Boolean {
        val currentManagedWebView = activeManagedWebView() ?: return false
        val resolver = pageRuleResolver ?: return false
        val retryUrl = resolveManagedWebViewUrl(currentManagedWebView)
            .ifBlank { currentPageUrl.orEmpty() }
        if (retryUrl.isBlank()) {
            return false
        }
        val config = mainViewModel.requireConfig()
        val navigationItemId = currentManagedWebView.navigationItemId ?: currentNavigationItemId
        val navigationRootUrl = currentManagedWebView.navigationRootUrl
        val previousTitle = resolveManagedWebViewTitle(currentManagedWebView)
        Log.d(
            TAG,
            "reloadCurrentPageFromFreshWebView url=$retryUrl clearCache=$clearCache navigationItem=$navigationItemId navigationRoot=$navigationRootUrl"
        )
        beginRecoveryLoad()
        managedWebViews.remove(currentManagedWebView)
        destroyManagedWebView(currentManagedWebView)

        val replacementManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE,
            navigationItemId = navigationItemId,
            navigationRootUrl = navigationRootUrl
        )
        if (clearCache) {
            replacementManagedWebView.webView.clearCache(true)
        }
        managedWebViews += replacementManagedWebView
        chromeClient = replacementManagedWebView.chromeClient
        interactiveNavigationLoading = true
        currentPageUrl = retryUrl
        if (previousTitle.isNotBlank()) {
            currentPageTitle = previousTitle
        }
        currentPageState = resolver.resolve(retryUrl)
        currentPageState?.let { state ->
            pageCallback?.onPageStateResolved(state)
            applyPageUiStyle(state)
        }
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        loadUrlInternal(replacementManagedWebView.webView, retryUrl, resetHistory = false)
        return true
    }

    @RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun handleRenderProcessGone(
        webView: android.webkit.WebView,
        detail: RenderProcessGoneDetail
    ): Boolean {
        val managedWebView = findManagedWebView(webView)
        if (managedWebView == null) {
            Log.e(
                TAG,
                "handleRenderProcessGone unmanaged view=${describeWebView(webView)} didCrash=${detail.didCrash()} priorityAtExit=${detail.rendererPriorityAtExit()}"
            )
            runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
            runCatching { webView.destroy() }
            return true
        }

        val crashedUrl = resolveManagedWebViewUrl(managedWebView)
        val crashedTitle = resolveManagedWebViewTitle(managedWebView)
        val isActive = managedWebView.webView === activeWebView()
        Log.e(
            TAG,
            "handleRenderProcessGone mode=${managedWebView.mode} active=$isActive navigationItem=${managedWebView.navigationItemId} navigationRoot=${managedWebView.navigationRootUrl} url=$crashedUrl title=$crashedTitle didCrash=${detail.didCrash()} priorityAtExit=${detail.rendererPriorityAtExit()} view=${describeWebView(webView)}"
        )

        val preloadedEntry = preloadedNavigationRoots.entries.firstOrNull { it.value === managedWebView }
        if (preloadedEntry != null) {
            preloadedNavigationRoots.remove(preloadedEntry.key)
            destroyManagedWebView(managedWebView, renderProcessGone = true)
            requestNavigationPreloadRefresh()
            return true
        }

        val managedIndex = managedWebViews.indexOf(managedWebView)
        if (managedIndex < 0) {
            destroyManagedWebView(managedWebView, renderProcessGone = true)
            return true
        }

        if (!isActive && navigationPageStackEnabled) {
            cacheTrimmedNavigationStackEntry(managedWebView)
        }
        managedWebViews.removeAt(managedIndex)
        destroyManagedWebView(managedWebView, renderProcessGone = true)

        if (!isActive) {
            syncWebViewVisibility()
            updateManagedWebViewLifecycle()
            requestNavigationPreloadRefresh()
            return true
        }

        resetNavigationSwipeTransition()
        keepWebViewHiddenUntilLoaded = false
        interactiveNavigationLoading = false
        errorStateLocked = false

        if (managedWebViews.isNotEmpty()) {
            restoreActiveManagedWebViewAfterRendererCrash(crashedUrl)
        } else {
            recoverInteractiveRootAfterRendererCrash(managedWebView, crashedUrl)
        }

        if (isAdded) {
            Toast.makeText(requireContext(), R.string.web_renderer_recovered, Toast.LENGTH_SHORT).show()
        }
        requestNavigationPreloadRefresh()
        return true
    }

    private fun restoreActiveManagedWebViewAfterRendererCrash(previousUrl: String) {
        val restoredManagedWebView = activeManagedWebView() ?: return
        chromeClient = restoredManagedWebView.chromeClient
        errorStateLocked = restoredManagedWebView.lastLoadError != null
        interactiveNavigationLoading = restoredManagedWebView.isLoading
        keepWebViewHiddenUntilLoaded = false

        val restoredUrl = resolveManagedWebViewUrl(restoredManagedWebView)
        currentPageTitle = resolveManagedWebViewTitle(restoredManagedWebView)
        if (restoredUrl.isNotBlank()) {
            handlePageStarted(restoredUrl)
        } else {
            currentPageUrl = previousUrl
        }
        currentPageState = restoredManagedWebView.lastResolvedPageState
            ?: restoredUrl.takeIf { it.isNotBlank() }?.let { pageRuleResolver?.resolve(it) }
        currentPageState?.let { state ->
            pageCallback?.onPageStateResolved(state)
            applyPageUiStyle(state)
        }
        if (!currentPageTitle.isNullOrBlank()) {
            pageCallback?.onPageTitleChanged(currentPageTitle.orEmpty())
            if (restoredUrl.isNotBlank() && previousUrl != restoredUrl) {
                dispatchPageEvent(
                    trigger = PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED,
                    url = restoredUrl,
                    title = currentPageTitle.orEmpty(),
                    previousUrl = previousUrl
                )
            }
        }
        showError(restoredManagedWebView.lastLoadError)
        showLoading(restoredManagedWebView.isLoading)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
    }

    private fun recoverInteractiveRootAfterRendererCrash(
        crashedManagedWebView: ManagedWebView,
        crashedUrl: String
    ) {
        val resolver = pageRuleResolver ?: return
        val config = mainViewModel.requireConfig()
        val recoveryUrl = resolveRendererCrashRecoveryUrl(crashedManagedWebView, crashedUrl)
        Log.w(
            TAG,
            "recoverInteractiveRootAfterRendererCrash crashedUrl=$crashedUrl recoveryUrl=$recoveryUrl navigationItem=${crashedManagedWebView.navigationItemId}"
        )
        recordRendererCrash(crashedUrl)
        val replacementManagedWebView = createManagedWebView(
            config = config,
            resolver = resolver,
            mode = ManagedWebViewMode.INTERACTIVE,
            navigationItemId = crashedManagedWebView.navigationItemId ?: currentNavigationItemId,
            navigationRootUrl = crashedManagedWebView.navigationRootUrl
        )
        managedWebViews += replacementManagedWebView
        chromeClient = replacementManagedWebView.chromeClient
        currentPageUrl = recoveryUrl.ifBlank { crashedUrl }
        currentPageTitle = crashedManagedWebView.lastKnownTitle.takeIf { it.isNotBlank() }
        currentPageState = currentPageUrl?.takeIf { it.isNotBlank() }?.let { resolver.resolve(it) }
        currentPageState?.let(::applyPageUiStyle)
        showError(null)
        syncWebViewVisibility()
        updateManagedWebViewLifecycle()
        if (recoveryUrl.isNotBlank()) {
            loadUrlInternal(replacementManagedWebView.webView, recoveryUrl, resetHistory = true)
        } else {
            showLoading(false)
            showError(PageLoadErrorState.Generic)
        }
    }

    private fun resolveRendererCrashRecoveryUrl(
        crashedManagedWebView: ManagedWebView,
        crashedUrl: String
    ): String {
        val repeatedCrash = crashedUrl.isNotBlank() &&
            crashedUrl == lastRendererCrashUrl &&
            SystemClock.elapsedRealtime() - lastRendererCrashAtElapsedMs <= RENDERER_CRASH_REPEAT_WINDOW_MS
        if (!repeatedCrash && crashedUrl.isNotBlank()) {
            return crashedUrl
        }
        val navigationRootUrl = crashedManagedWebView.navigationRootUrl
        if (navigationRootUrl.isNotBlank() && navigationRootUrl != crashedUrl) {
            return navigationRootUrl
        }
        val currentNavigationUrl = navigationItems
            .firstOrNull { it.id == (crashedManagedWebView.navigationItemId ?: currentNavigationItemId) }
            ?.url
            .orEmpty()
        if (currentNavigationUrl.isNotBlank() && currentNavigationUrl != crashedUrl) {
            return currentNavigationUrl
        }
        return mainViewModel.requireConfig().app.defaultUrl
            .takeIf { it.isNotBlank() && it != crashedUrl }
            .orEmpty()
    }

    private fun recordRendererCrash(url: String) {
        lastRendererCrashUrl = url.ifBlank { null }
        lastRendererCrashAtElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun resolveManagedWebViewUrl(managedWebView: ManagedWebView): String {
        return managedWebView.lastKnownUrl
            .ifBlank { managedWebView.webView.url.orEmpty() }
            .ifBlank { managedWebView.webView.originalUrl.orEmpty() }
            .ifBlank { managedWebView.navigationRootUrl }
    }

    private fun resolveManagedWebViewTitle(managedWebView: ManagedWebView): String {
        return managedWebView.lastKnownTitle
            .ifBlank { managedWebView.webView.title.orEmpty() }
    }

    private fun describeWebView(webView: android.webkit.WebView?): String {
        return if (webView == null) {
            "null"
        } else {
            "${webView.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(webView))}"
        }
    }

    private fun shouldShowDownloadOverlay(): Boolean {
        return currentPageState?.showDownloadOverlay != false
    }

    private fun hideDownloadStatus() {
        uiHandler.removeCallbacks(hideDownloadStatusRunnable)
        _binding?.downloadStatusContainer?.visibility = View.GONE
    }

    private fun showDownloadStatus(text: String, progressPercent: Int?, autoHide: Boolean) {
        val currentBinding = _binding ?: return
        if (!shouldShowDownloadOverlay()) {
            hideDownloadStatus()
            return
        }
        uiHandler.removeCallbacks(hideDownloadStatusRunnable)
        currentBinding.downloadStatusContainer.visibility = View.VISIBLE
        currentBinding.downloadStatusText.text = text
        if (progressPercent == null) {
            currentBinding.downloadProgressIndicator.isIndeterminate = true
        } else {
            currentBinding.downloadProgressIndicator.isIndeterminate = false
            currentBinding.downloadProgressIndicator.progress = progressPercent
        }
        if (autoHide) {
            uiHandler.postDelayed(hideDownloadStatusRunnable, DOWNLOAD_STATUS_AUTO_HIDE_MS)
        }
    }

    private fun showError(errorState: PageLoadErrorState?) {
        val currentBinding = _binding ?: return
        val allowErrorView = mainViewModel.requireConfig().browser.showErrorView
        val shouldShow = errorState != null && allowErrorView
        currentBinding.errorView.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (shouldShow) {
            currentBinding.errorTitle.text = currentPageState?.errorTitle?.takeIf { it.isNotBlank() }
                ?: getString(errorState?.titleRes ?: R.string.web_error_title)
            currentBinding.errorMessage.text = currentPageState?.errorMessage?.takeIf { it.isNotBlank() }
                ?: getString(errorState?.messageRes ?: R.string.web_error_message)
        }
        if (shouldShow) {
            currentBinding.loadingContainer.visibility = View.GONE
            currentBinding.loadingContainer.setBackgroundColor(Color.TRANSPARENT)
            updateLoadingSpinnerAnimation(false)
            resetNavigationSwipeTransition()
        }
        syncWebViewVisibility()
    }

    private fun showLoading(show: Boolean) {
        val currentBinding = _binding ?: return
        val allowLoading = mainViewModel.requireConfig().browser.showLoadingOverlay
        val suppressForInteractiveSwipe =
            navigationSwipeInteractiveActive && !navigationSwipeInteractiveCommitted
        val shouldShow = show &&
            allowLoading &&
            currentBinding.errorView.visibility != View.VISIBLE &&
            !suppressForInteractiveSwipe
        currentBinding.loadingText.text = currentPageState?.loadingText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_loading_message)
        val shouldMaskHiddenWebView = shouldShow &&
            keepWebViewHiddenUntilLoaded &&
            pendingNavigationSwipeDirection == null &&
            !navigationSwipeInteractiveActive
        currentBinding.loadingContainer.setBackgroundColor(
            if (shouldMaskHiddenWebView) resolveNavigationSwipeBlankColor() else Color.TRANSPARENT
        )
        currentBinding.loadingContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        updateLoadingSpinnerAnimation(shouldShow)
        if (!shouldShow &&
            !suppressForInteractiveSwipe &&
            pendingNavigationSwipeDirection == null
        ) {
            keepWebViewHiddenUntilLoaded = false
        }
        syncWebViewVisibility()
    }

    private fun clearErrorState() {
        errorStateLocked = false
        showError(null)
    }

    private fun beginRecoveryLoad() {
        keepWebViewHiddenUntilLoaded = true
        clearErrorState()
    }

    private fun captureNavigationSwipeSnapshot(): Bitmap? {
        return captureWebViewSnapshot(activeWebView())
    }

    private fun installNavigationSwipeSnapshot(bitmap: Bitmap) {
        val currentBinding = _binding ?: return
        removeNavigationSwipeSnapshot()
        val snapshotView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(bitmap)
            alpha = 1f
            translationX = 0f
        }
        val insertIndex = if (navigationSwipePreviewView != null) 2 else 1
        currentBinding.root.addView(snapshotView, insertIndex)
        navigationSwipeSnapshotView = snapshotView
    }

    private fun startNavigationSwipeSnapshotExitAnimation(holdTranslation: Float) {
        val snapshotView = navigationSwipeSnapshotView
        if (snapshotView == null) {
            pendingNavigationSwipeExitCompleted = true
            maybeCompleteNavigationSwipeEnterAnimation()
            return
        }
        snapshotView.animate().cancel()
        snapshotView.animate()
            .translationX(holdTranslation)
            .alpha(NAVIGATION_SWIPE_SNAPSHOT_HOLD_ALPHA)
            .setDuration(NAVIGATION_SWIPE_SNAPSHOT_HOLD_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                pendingNavigationSwipeExitCompleted = true
                maybeCompleteNavigationSwipeEnterAnimation()
            }
            .start()
    }

    private fun onNavigationSwipePageReady() {
        if (pendingNavigationSwipeDirection == null) {
            return
        }
        pendingNavigationSwipePageReady = true
        keepWebViewHiddenUntilLoaded = false
        syncWebViewVisibility()
        fadeOutNavigationSwipePreviewIfPresent()
        maybeCompleteNavigationSwipeEnterAnimation()
    }

    private fun maybeCompleteNavigationSwipeEnterAnimation() {
        if (pendingNavigationSwipeDirection == null) {
            return
        }
        if (!pendingNavigationSwipeExitCompleted || !pendingNavigationSwipePageReady) {
            return
        }
        pendingNavigationSwipeDirection = null
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        val interactiveCommit = navigationSwipeInteractiveCommitted
        navigationSwipeInteractiveCommitted = false
        navigationSwipeInteractiveActive = false
        navigationSwipeInteractiveTargetItem = null
        keepWebViewHiddenUntilLoaded = false
        showLoading(false)
        syncWebViewVisibility()
        val activeWebView = activeWebView() ?: return
        val snapshotView = navigationSwipeSnapshotView
        val previewView = navigationSwipePreviewView
        activeWebView.animate().cancel()
        snapshotView?.animate()?.cancel()
        previewView?.animate()?.cancel()
        if (interactiveCommit) {
            activeWebView.translationX = 0f
            activeWebView.alpha = 1f
            if (previewView != null) {
                previewView.animate()
                    .alpha(0f)
                    .setDuration(NAVIGATION_SWIPE_PREVIEW_FADE_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        clearNavigationSwipeBlankBackground()
                        removeNavigationSwipePreview()
                    }
                    .start()
            } else {
                clearNavigationSwipeBlankBackground()
                removeNavigationSwipePreview()
            }
            removeNavigationSwipeSnapshot()
            return
        }
        activeWebView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(NAVIGATION_SWIPE_ENTER_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (snapshotView != null) {
            snapshotView.animate()
                .translationX(pendingNavigationSwipeSnapshotFinalTranslationX)
                .alpha(0f)
                .setDuration(NAVIGATION_SWIPE_ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    clearNavigationSwipeBlankBackground()
                    removeNavigationSwipeSnapshot()
                    removeNavigationSwipePreview()
                }
                .start()
        } else {
            clearNavigationSwipeBlankBackground()
            removeNavigationSwipePreview()
        }
    }

    private fun resetNavigationSwipeTransition() {
        pendingNavigationSwipeDirection = null
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSnapshotFinalTranslationX = 0f
        navigationSwipeInteractiveActive = false
        navigationSwipeInteractiveCommitted = false
        navigationSwipeInteractiveTargetItem = null
        navigationSwipeCurrentTranslationX = 0f
        navigationSwipeVelocityTracker?.recycle()
        navigationSwipeVelocityTracker = null
        keepWebViewHiddenUntilLoaded = false
        clearNavigationSwipeBlankBackground()
        removeNavigationSwipeSnapshot()
        removeNavigationSwipePreview()
        activeWebView()?.animate()?.cancel()
        activeWebView()?.translationX = 0f
        activeWebView()?.alpha = 1f
        updateManagedWebViewLifecycle()
    }

    private fun applyNavigationSwipeBlankBackground() {
        val currentBinding = _binding ?: return
        currentBinding.root.setBackgroundColor(resolveNavigationSwipeBlankColor())
    }

    private fun clearNavigationSwipeBlankBackground() {
        _binding?.root?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun resolveNavigationSwipeBlankColor(): Int {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun removeNavigationSwipeSnapshot() {
        val snapshotView = navigationSwipeSnapshotView ?: return
        snapshotView.animate().cancel()
        (snapshotView.parent as? ViewGroup)?.removeView(snapshotView)
        snapshotView.setImageDrawable(null)
        navigationSwipeSnapshotView = null
    }

    private fun applyPageUiStyle(state: ResolvedPageState) {
        val currentBinding = _binding ?: return
        currentBinding.loadingText.text = state.loadingText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_loading_message)
        currentBinding.errorTitle.text = state.errorTitle?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_error_title)
        currentBinding.errorMessage.text = state.errorMessage?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_error_message)
        currentBinding.retryButton.text = state.errorButtonText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_retry_action)

        currentBinding.loadingCard.setCardBackgroundColor(
            parseColorOrNull(state.loadingCardBackgroundColor) ?: defaultLoadingCardColor ?: Color.WHITE
        )
        currentBinding.errorCard.setCardBackgroundColor(
            parseColorOrNull(state.errorCardBackgroundColor) ?: defaultErrorCardColor ?: Color.WHITE
        )

        currentBinding.loadingText.setTextColor(
            parseColorOrNull(state.loadingTextColor) ?: defaultLoadingTextColor ?: currentBinding.loadingText.currentTextColor
        )
        currentBinding.errorTitle.setTextColor(
            parseColorOrNull(state.errorTitleColor) ?: defaultErrorTitleColor ?: currentBinding.errorTitle.currentTextColor
        )
        currentBinding.errorMessage.setTextColor(
            parseColorOrNull(state.errorMessageColor) ?: defaultErrorMessageColor ?: currentBinding.errorMessage.currentTextColor
        )

        parseColorOrNull(state.loadingIndicatorColor)?.let { color ->
            currentBinding.loadingIndicator.setIndicatorColor(color)
            currentBinding.loadingSpinnerIcon.imageTintList = ColorStateList.valueOf(color)
        } ?: defaultLoadingIndicatorColor?.takeIf { it.isNotEmpty() }?.let { colors ->
            currentBinding.loadingIndicator.setIndicatorColor(*colors)
            currentBinding.loadingSpinnerIcon.imageTintList = ColorStateList.valueOf(colors.first())
        }

        currentBinding.retryButton.backgroundTintList = ColorStateList.valueOf(
            parseColorOrNull(state.errorButtonBackgroundColor)
                ?: defaultRetryButtonBackgroundColor
                ?: resolveDefaultRetryButtonBackground(currentBinding)
        )
        currentBinding.retryButton.setTextColor(
            parseColorOrNull(state.errorButtonTextColor) ?: defaultRetryButtonTextColor ?: currentBinding.retryButton.currentTextColor
        )

        if (!state.showDownloadOverlay) {
            hideDownloadStatus()
        }
    }

    private fun captureDefaultUiStyle() {
        val currentBinding = _binding ?: return
        defaultLoadingCardColor = currentBinding.loadingCard.cardBackgroundColor.defaultColor
        defaultLoadingTextColor = currentBinding.loadingText.currentTextColor
        defaultLoadingIndicatorColor = currentBinding.loadingIndicator.indicatorColor
        defaultErrorCardColor = currentBinding.errorCard.cardBackgroundColor.defaultColor
        defaultErrorTitleColor = currentBinding.errorTitle.currentTextColor
        defaultErrorMessageColor = currentBinding.errorMessage.currentTextColor
        defaultRetryButtonBackgroundColor = currentBinding.retryButton.backgroundTintList?.defaultColor
        defaultRetryButtonTextColor = currentBinding.retryButton.currentTextColor
        currentBinding.loadingSpinnerIcon.setImageDrawable(
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_template_refresh)
        )
        currentBinding.loadingSpinnerIcon.imageTintList = ColorStateList.valueOf(
            defaultLoadingIndicatorColor?.firstOrNull() ?: currentBinding.loadingText.currentTextColor
        )
    }

    private fun parseColorOrNull(value: String?): Int? {
        val candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }
        return runCatching {
            Color.parseColor(candidate)
        }.getOrNull()
    }

    private fun resolveDefaultRetryButtonBackground(binding: FragmentWebContainerBinding): Int {
        return binding.retryButton.backgroundTintList?.defaultColor
            ?: Color.parseColor("#6750A4")
    }

    private fun syncWebViewVisibility() {
        val currentBinding = _binding ?: return
        val currentActiveWebView = activeWebView()
        val currentPreviewManagedWebView = navigationSwipePreviewManagedWebView
        var hasVisibleManagedWebView = false
        allManagedWebViews().forEach { managedWebView ->
            val visibility = when {
                currentBinding.errorView.visibility == View.VISIBLE -> View.INVISIBLE
                managedWebView === currentPreviewManagedWebView -> View.VISIBLE
                managedWebView.webView !== currentActiveWebView -> View.INVISIBLE
                keepWebViewHiddenUntilLoaded -> View.INVISIBLE
                else -> View.VISIBLE
            }
            managedWebView.webView.visibility = visibility
            if (visibility == View.VISIBLE) {
                hasVisibleManagedWebView = true
            }
        }
        currentBinding.webViewContainer.visibility = if (hasVisibleManagedWebView) View.VISIBLE else View.INVISIBLE
    }

    private fun updateLoadingSpinnerAnimation(shouldSpin: Boolean) {
        val currentBinding = _binding ?: return
        if (!shouldSpin) {
            loadingSpinnerAnimator?.cancel()
            loadingSpinnerAnimator = null
            currentBinding.loadingSpinnerIcon.rotation = 0f
            return
        }
        if (loadingSpinnerAnimator?.isRunning == true) {
            return
        }
        loadingSpinnerAnimator = ObjectAnimator.ofFloat(
            currentBinding.loadingSpinnerIcon,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = 850L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        isFragmentResumed = true
        updateManagedWebViewLifecycle()
    }

    override fun onPause() {
        isFragmentResumed = false
        updateManagedWebViewLifecycle()
        super.onPause()
    }

    override fun onDestroyView() {
        resetNavigationSwipeTransition()
        externalAppDialog?.dismiss()
        externalAppDialog = null
        longPressDialog?.dismiss()
        longPressDialog = null
        fileChooserHandler.cancelPending()
        webPermissionHandler.cancelPending()
        webGeolocationHandler.cancelPending()
        uiHandler.removeCallbacks(hideDownloadStatusRunnable)
        uiHandler.removeCallbacks(refreshNavigationPreloadsRunnable)
        loadingSpinnerAnimator?.cancel()
        loadingSpinnerAnimator = null
        navigationSwipeListener = null
        pendingNavigationSwipeDirection = null
        pendingNavigationPreloadRefresh = false
        interactiveNavigationLoading = false
        lastRendererCrashUrl = null
        lastRendererCrashAtElapsedMs = 0L
        chromeClient?.exitFullscreen()
        chromeClient = null
        pageRuleResolver = null
        resolvedPageInjectionApplier = null
        pageEventDispatcher = null
        currentPageUrl = null
        currentPageTitle = null
        currentNavigationItemId = null
        navigationItems = emptyList()
        navigationPreloadCount = 0
        currentPageState = null
        clearManagedWebViewStack()
        clearPreloadedNavigationRoots()
        isFragmentResumed = false
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeWebView()?.saveState(outState)
    }

    companion object {
        private const val ARG_INITIAL_URL = "initial_url"
        private const val CLIPBOARD_BRIDGE_NAME = "FireflyClipboardBridge"
        private const val NOTIFICATION_BRIDGE_NAME = "FireflyNotificationBridge"
        private const val NATIVE_KV_BRIDGE_NAME = "NativeBridge"
        private const val BLOB_BRIDGE_NAME = "FireflyBlobDownloadBridge"
        private const val DOWNLOAD_METADATA_BRIDGE_NAME = "FireflyDownloadMetadataBridge"
        private const val PAGE_EVENT_BRIDGE_NAME = "FireflyPageEventBridge"
        private const val BLOB_URL_PREFIX = "blob:"
        private const val TAG = "WebContainerFragment"
        private const val DOWNLOAD_STATUS_AUTO_HIDE_MS = 2_500L
        private const val ERROR_RETRY_ACTION_GO_HOME = "go_home"
        private const val ERROR_RETRY_ACTION_LOAD_URL = "load_url"
        private const val PAGE_EVENT_TRIGGER_PAGE_STARTED = "page_started"
        private const val PAGE_EVENT_TRIGGER_PAGE_FINISHED = "page_finished"
        private const val PAGE_EVENT_TRIGGER_PAGE_TITLE_CHANGED = "page_title_changed"
        private const val PAGE_EVENT_TRIGGER_PAGE_LEFT = "page_left"
        private const val PAGE_EVENT_TRIGGER_SPA_URL_CHANGED = "spa_url_changed"
        private const val SWIPE_HORIZONTAL_RATIO = 1.3f
        private const val NAVIGATION_SWIPE_COMMIT_PROGRESS = 0.28f
        private const val NAVIGATION_SWIPE_COMMIT_VELOCITY_PX = 900f
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO = 1f
        private const val NAVIGATION_SWIPE_ENTRY_OFFSET_RATIO = 0.55f
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_ALPHA = 0.36f
        private const val NAVIGATION_SWIPE_SNAPSHOT_HOLD_ALPHA = 0.96f
        private const val NAVIGATION_SWIPE_ENTRY_ALPHA = 0.92f
        private const val NAVIGATION_SWIPE_CANCEL_DURATION_MS = 160L
        private const val NAVIGATION_SWIPE_RELEASE_DURATION_MS = 180L
        private const val NAVIGATION_SWIPE_PREVIEW_FADE_DURATION_MS = 90L
        private const val NAVIGATION_SWIPE_PREVIEW_READY_FADE_DURATION_MS = 70L
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_DURATION_MS = 160L
        private const val NAVIGATION_SWIPE_SNAPSHOT_HOLD_DURATION_MS = 120L
        private const val NAVIGATION_SWIPE_ENTER_DURATION_MS = 210L
        private const val NAVIGATION_PAGE_STACK_LIMIT = 5
        private const val NAVIGATION_PRELOAD_DELAY_MS = 350L
        private const val NAVIGATION_PRELOAD_MAX_AGE_MS = 120_000L
        private const val MAX_NAVIGATION_PRELOAD_COUNT = 4
        private const val RENDERER_CRASH_REPEAT_WINDOW_MS = 5_000L
        private val NAVIGATION_STACK_SUPPORTED_TEMPLATES = setOf(
            TemplateType.BOTTOM_BAR,
            TemplateType.TOP_BAR_TABS,
            TemplateType.TOP_BAR_BOTTOM_TABS,
            TemplateType.SIDE_DRAWER
        )
        private val NAVIGATION_PRELOAD_SUPPORTED_TEMPLATES = setOf(
            TemplateType.BOTTOM_BAR,
            TemplateType.TOP_BAR_TABS,
            TemplateType.TOP_BAR_BOTTOM_TABS,
            TemplateType.SIDE_DRAWER
        )

        fun newInstance(initialUrl: String): WebContainerFragment {
            return WebContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_URL, initialUrl)
                }
            }
        }
    }

    enum class BackNavigationAction {
        HANDLED,
        GO_HOME,
        NOT_HANDLED
    }
}
