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
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
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
import com.fireflyapp.lite.core.notification.NotificationBridge
import com.fireflyapp.lite.core.permission.WebGeolocationHandler
import com.fireflyapp.lite.core.permission.WebPermissionHandler
import com.fireflyapp.lite.core.webview.FileChooserHandler
import com.fireflyapp.lite.core.webview.FullscreenViewHost
import com.fireflyapp.lite.core.rule.PageRuleResolver
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.webview.FireflyWebChromeClient
import com.fireflyapp.lite.core.webview.FireflyWebViewClient.PageLoadErrorState
import com.fireflyapp.lite.core.webview.FireflyWebViewClient
import com.fireflyapp.lite.core.webview.ResolvedPageInjectionApplier
import com.fireflyapp.lite.core.webview.WebPageCallback
import com.fireflyapp.lite.core.webview.WebViewConfigurator
import com.fireflyapp.lite.databinding.FragmentWebContainerBinding
import com.fireflyapp.lite.ui.main.MainViewModel
import com.fireflyapp.lite.ui.template.NavigationSwipeDirection
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
    private val webPermissionHandler by lazy {
        WebPermissionHandler(
            fragment = this,
            allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
            currentPageUrlProvider = { currentPageUrl }
        )
    }
    private val webGeolocationHandler by lazy {
        WebGeolocationHandler(
            fragment = this,
            allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
            currentPageUrlProvider = { currentPageUrl }
        )
    }
    private var chromeClient: FireflyWebChromeClient? = null
    private var pageEventDispatcher: PageEventDispatcher? = null
    private var externalAppDialog: AlertDialog? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var currentPageUrl: String? = null
    @Volatile
    private var currentPageTitle: String? = null
    private var clearHistoryOnNextPageFinished = false
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
    private var swipeGestureDetector: GestureDetectorCompat? = null
    private var pendingNavigationSwipeDirection: NavigationSwipeDirection? = null
    private var pendingNavigationSwipeExitCompleted = false
    private var pendingNavigationSwipePageReady = false
    private var pendingNavigationSwipeSkipEnterAnimation = false
    private var navigationSwipeSnapshotView: ImageView? = null
    private val hideDownloadStatusRunnable = Runnable {
        _binding?.downloadStatusContainer?.visibility = View.GONE
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
    private val notificationBridge by lazy {
        NotificationBridge(
            fragment = this,
            allowedHostsProvider = { mainViewModel.requireConfig().security.allowedHosts },
            currentPageUrlProvider = { currentPageUrl },
            dispatchPermissionResult = ::dispatchNotificationPermissionResult
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
                if (title.isNotBlank()) {
                    currentPageTitle = title
                }
                pageRuleResolver?.resolve(url)?.let { state ->
                    currentPageState = state
                    pageCallback?.onPageStateResolved(state)
                    applyPageUiStyle(state)
                    _binding?.webView?.let { webView ->
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
        currentPageState = resolver.resolve(
            requireArguments().getString(ARG_INITIAL_URL).orEmpty().ifBlank { config.app.defaultUrl }
        )

        WebViewConfigurator.apply(binding.webView, config.browser)
        swipeGestureDetector = GestureDetectorCompat(
            requireContext(),
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val listener = navigationSwipeListener ?: return false
                    val deltaX = e2.x - start.x
                    val deltaY = e2.y - start.y
                    if (abs(deltaX) < SWIPE_MIN_DISTANCE_PX) {
                        return false
                    }
                    if (abs(deltaX) < abs(deltaY) * SWIPE_HORIZONTAL_RATIO) {
                        return false
                    }
                    if (abs(velocityX) < SWIPE_MIN_VELOCITY_PX) {
                        return false
                    }
                    listener(
                        if (deltaX < 0) {
                            NavigationSwipeDirection.NEXT
                        } else {
                            NavigationSwipeDirection.PREVIOUS
                        }
                    )
                    return false
                }
            }
        )
        binding.webView.setOnTouchListener { _, event ->
            swipeGestureDetector?.onTouchEvent(event)
            false
        }
        captureDefaultUiStyle()
        binding.retryButton.setOnClickListener {
            performRetryAction()
        }
        binding.webView.addJavascriptInterface(clipboardBridge, CLIPBOARD_BRIDGE_NAME)
        binding.webView.addJavascriptInterface(notificationBridge, NOTIFICATION_BRIDGE_NAME)
        binding.webView.addJavascriptInterface(blobDownloadBridge, BLOB_BRIDGE_NAME)
        binding.webView.addJavascriptInterface(downloadMetadataBridge, DOWNLOAD_METADATA_BRIDGE_NAME)
        binding.webView.addJavascriptInterface(pageEventBridge, PAGE_EVENT_BRIDGE_NAME)
        chromeClient = FireflyWebChromeClient(
            pageCallback = pageCallback,
            onPageTitleChanged = ::handlePageTitleChanged,
            openFileChooser = fileChooserHandler::openFileChooser,
            requestWebPermission = webPermissionHandler::handle,
            cancelWebPermission = webPermissionHandler::onCanceled,
            requestGeolocationPermission = webGeolocationHandler::handle,
            cancelGeolocationPermission = webGeolocationHandler::cancelPending,
            showFullscreenView = { view ->
                (activity as? FullscreenViewHost)?.showFullscreenView(view) == true
            },
            hideFullscreenView = {
                (activity as? FullscreenViewHost)?.hideFullscreenView()
            }
        )
        binding.webView.webChromeClient = chromeClient
        binding.webView.webViewClient = FireflyWebViewClient(
            appConfig = config,
            pageRuleResolver = resolver,
            pageCallback = pageCallback,
            openExternal = ::openExternalIntent,
            onPageLoadError = { errorState ->
                if (errorState != null) {
                    errorStateLocked = true
                    showError(errorState)
                } else if (!errorStateLocked) {
                    showError(null)
                }
            },
            onPageLoadingChanged = { loading ->
                if (!errorStateLocked) {
                    showLoading(loading)
                }
            },
            onPageStarted = ::handlePageStarted,
            onResolvedPageStateChanged = { state ->
                currentPageState = state
                applyPageUiStyle(state)
            },
            onPageCommitVisible = { _, _ ->
                onNavigationSwipePageReady()
            },
            onPageFinished = { webView, url ->
                currentPageUrl = url
                installPageEventHook(webView)
                installClipboardBridge(webView)
                installNotificationBridge(webView)
                installDownloadMetadataHook(webView)
                installBlobDownloadHook(webView)
                onNavigationSwipePageReady()
                if (clearHistoryOnNextPageFinished) {
                    clearHistoryOnNextPageFinished = false
                    webView.clearHistory()
                }
                dispatchPageEvent(
                    trigger = PAGE_EVENT_TRIGGER_PAGE_FINISHED,
                    url = url,
                    title = currentPageTitle.orEmpty()
                )
            }
        )
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith(BLOB_URL_PREFIX, ignoreCase = true)) {
                triggerBlobDownload(
                    blobUrl = url,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType
                )
                return@setDownloadListener
            }

            if (!URLUtil.isValidUrl(url)) {
                Toast.makeText(requireContext(), R.string.download_failed, Toast.LENGTH_SHORT).show()
                return@setDownloadListener
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

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
            currentPageUrl = binding.webView.url
            currentPageUrl?.let {
                currentPageState = resolver.resolve(it)
                currentPageState?.let(::applyPageUiStyle)
            }
        } else if (binding.webView.url.isNullOrBlank()) {
            val initialUrl = requireArguments().getString(ARG_INITIAL_URL).orEmpty()
            currentPageUrl = initialUrl
            currentPageState = resolver.resolve(initialUrl)
            currentPageState?.let(::applyPageUiStyle)
            showLoading(true)
            binding.webView.loadUrl(initialUrl)
        }
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
        val width = currentBinding.webView.width
            .takeIf { it > 0 }
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
        resetNavigationSwipeTransition()
        pendingNavigationSwipeDirection = direction
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSkipEnterAnimation = false
        currentBinding.webView.translationX = entryTranslation
        currentBinding.webView.alpha = NAVIGATION_SWIPE_ENTRY_ALPHA
        keepWebViewHiddenUntilLoaded = true
        applyNavigationSwipeBlankBackground()
        syncWebViewVisibility()
        snapshotBitmap?.let { installNavigationSwipeSnapshot(it) }
        navigationSwipeSnapshotView?.animate()?.cancel()
        navigationSwipeSnapshotView?.animate()
            ?.translationX(exitTranslation)
            ?.alpha(NAVIGATION_SWIPE_SNAPSHOT_EXIT_ALPHA)
            ?.setDuration(NAVIGATION_SWIPE_SNAPSHOT_EXIT_DURATION_MS)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction {
                removeNavigationSwipeSnapshot()
                pendingNavigationSwipeExitCompleted = true
                pendingNavigationSwipeSkipEnterAnimation = !pendingNavigationSwipePageReady
                maybeCompleteNavigationSwipeEnterAnimation()
            }
            ?.start()
        loadUrlInternal(url, resetHistory)
    }

    private fun loadUrlInternal(url: String, resetHistory: Boolean) {
        if (errorStateLocked) {
            beginRecoveryLoad()
        } else {
            clearErrorState()
        }
        currentPageUrl = url
        clearHistoryOnNextPageFinished = resetHistory
        currentPageState = pageRuleResolver?.resolve(url)
        currentPageState?.let(::applyPageUiStyle)
        showLoading(true)
        binding.webView.loadUrl(url)
    }

    fun setNavigationSwipeListener(listener: ((NavigationSwipeDirection) -> Unit)?) {
        navigationSwipeListener = listener
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
            beginRecoveryLoad()
        } else {
            clearErrorState()
        }
        showLoading(true)
        _binding?.webView?.reload()
    }

    fun reloadIgnoringCache() {
        if (errorStateLocked) {
            beginRecoveryLoad()
        } else {
            clearErrorState()
        }
        showLoading(true)
        _binding?.webView?.apply {
            clearCache(true)
            reload()
        }
    }

    fun runJavaScript(script: String) {
        val resolvedScript = script.trim()
        if (resolvedScript.isBlank()) {
            return
        }
        _binding?.webView?.evaluateJavascript(resolvedScript, null)
    }

    private fun openExternalIntent(intent: Intent): Boolean {
        val mode = mainViewModel.requireConfig().security.openOtherAppsMode
        return when (mode) {
            "allow" -> launchExternalIntent(intent)
            "block" -> {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Opening other apps is blocked.", Toast.LENGTH_SHORT).show()
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
            ?: "another app"
        val targetLabel = when {
            browserLikeTarget -> "browser or supported app"
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
            .setTitle("Open $targetLabel?")
            .setMessage("This page wants to open $targetLabel.\n$detail")
            .setIcon(if (browserLikeTarget) null else appIcon)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open") { _, _ ->
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
                Toast.makeText(requireContext(), "No app can handle this link.", Toast.LENGTH_SHORT).show()
            }
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun goBack(): Boolean {
        val webView = _binding?.webView ?: return false
        if (!webView.canGoBack()) {
            return false
        }
        webView.goBack()
        return true
    }

    fun currentUrl(): String? {
        return currentPageUrl ?: _binding?.webView?.url
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
        return chromeClient?.exitFullscreen() == true
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

        binding.webView.evaluateJavascript(script, null)
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
                    _binding?.webView?.evaluateJavascript(resolvedScript, null)
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
        val webView = _binding?.webView ?: return
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
        val webView = _binding?.webView ?: return
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
        val webView = _binding?.webView ?: return
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
        beginRecoveryLoad()
        showLoading(true)
        binding.webView.reload()
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
            resetNavigationSwipeTransition()
        }
        syncWebViewVisibility()
    }

    private fun showLoading(show: Boolean) {
        val currentBinding = _binding ?: return
        val allowLoading = mainViewModel.requireConfig().browser.showLoadingOverlay
        val shouldShow = show &&
            allowLoading &&
            currentBinding.errorView.visibility != View.VISIBLE &&
            pendingNavigationSwipeDirection == null
        currentBinding.loadingText.text = currentPageState?.loadingText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.web_loading_message)
        currentBinding.loadingContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        updateLoadingSpinnerAnimation(shouldShow)
        if (!shouldShow && pendingNavigationSwipeDirection == null) {
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
        val currentBinding = _binding ?: return null
        val width = currentBinding.webView.width
        val height = currentBinding.webView.height
        if (width <= 0 || height <= 0) {
            return null
        }
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                currentBinding.webView.draw(canvas)
            }
        }.getOrNull()
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
        currentBinding.root.addView(snapshotView, 1)
        navigationSwipeSnapshotView = snapshotView
    }

    private fun onNavigationSwipePageReady() {
        if (pendingNavigationSwipeDirection == null) {
            return
        }
        pendingNavigationSwipePageReady = true
        maybeCompleteNavigationSwipeEnterAnimation()
    }

    private fun maybeCompleteNavigationSwipeEnterAnimation() {
        val currentBinding = _binding ?: return
        if (pendingNavigationSwipeDirection == null) {
            return
        }
        if (!pendingNavigationSwipeExitCompleted || !pendingNavigationSwipePageReady) {
            return
        }
        pendingNavigationSwipeDirection = null
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        val skipEnterAnimation = pendingNavigationSwipeSkipEnterAnimation
        pendingNavigationSwipeSkipEnterAnimation = false
        removeNavigationSwipeSnapshot()
        keepWebViewHiddenUntilLoaded = false
        clearNavigationSwipeBlankBackground()
        showLoading(false)
        syncWebViewVisibility()
        currentBinding.webView.animate().cancel()
        if (skipEnterAnimation) {
            currentBinding.webView.translationX = 0f
            currentBinding.webView.alpha = 1f
        } else {
            currentBinding.webView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(NAVIGATION_SWIPE_ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun resetNavigationSwipeTransition() {
        pendingNavigationSwipeDirection = null
        pendingNavigationSwipeExitCompleted = false
        pendingNavigationSwipePageReady = false
        pendingNavigationSwipeSkipEnterAnimation = false
        keepWebViewHiddenUntilLoaded = false
        clearNavigationSwipeBlankBackground()
        removeNavigationSwipeSnapshot()
        val currentBinding = _binding ?: return
        currentBinding.webView.animate().cancel()
        currentBinding.webView.translationX = 0f
        currentBinding.webView.alpha = 1f
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
        currentBinding.webView.visibility = when {
            currentBinding.errorView.visibility == View.VISIBLE -> View.INVISIBLE
            keepWebViewHiddenUntilLoaded -> View.INVISIBLE
            else -> View.VISIBLE
        }
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
        _binding?.webView?.onResume()
    }

    override fun onPause() {
        _binding?.webView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        resetNavigationSwipeTransition()
        externalAppDialog?.dismiss()
        externalAppDialog = null
        fileChooserHandler.cancelPending()
        webPermissionHandler.cancelPending()
        webGeolocationHandler.cancelPending()
        uiHandler.removeCallbacks(hideDownloadStatusRunnable)
        loadingSpinnerAnimator?.cancel()
        loadingSpinnerAnimator = null
        navigationSwipeListener = null
        swipeGestureDetector = null
        pendingNavigationSwipeDirection = null
        chromeClient?.exitFullscreen()
        chromeClient = null
        pageRuleResolver = null
        resolvedPageInjectionApplier = null
        pageEventDispatcher = null
        currentPageUrl = null
        currentPageTitle = null
        clearHistoryOnNextPageFinished = false
        currentPageState = null
        binding.webView.apply {
            stopLoading()
            setOnTouchListener(null)
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setDownloadListener(null)
            removeJavascriptInterface(CLIPBOARD_BRIDGE_NAME)
            removeJavascriptInterface(NOTIFICATION_BRIDGE_NAME)
            removeJavascriptInterface(BLOB_BRIDGE_NAME)
            removeJavascriptInterface(DOWNLOAD_METADATA_BRIDGE_NAME)
            removeJavascriptInterface(PAGE_EVENT_BRIDGE_NAME)
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.webView?.saveState(outState)
    }

    companion object {
        private const val ARG_INITIAL_URL = "initial_url"
        private const val CLIPBOARD_BRIDGE_NAME = "FireflyClipboardBridge"
        private const val NOTIFICATION_BRIDGE_NAME = "FireflyNotificationBridge"
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
        private const val SWIPE_MIN_DISTANCE_PX = 140f
        private const val SWIPE_MIN_VELOCITY_PX = 650f
        private const val SWIPE_HORIZONTAL_RATIO = 1.3f
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_DISTANCE_RATIO = 1f
        private const val NAVIGATION_SWIPE_ENTRY_OFFSET_RATIO = 0.55f
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_ALPHA = 0.36f
        private const val NAVIGATION_SWIPE_ENTRY_ALPHA = 0.92f
        private const val NAVIGATION_SWIPE_SNAPSHOT_EXIT_DURATION_MS = 160L
        private const val NAVIGATION_SWIPE_ENTER_DURATION_MS = 210L

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
