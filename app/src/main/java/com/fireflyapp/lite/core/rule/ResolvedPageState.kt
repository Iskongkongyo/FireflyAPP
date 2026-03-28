package com.fireflyapp.lite.core.rule

data class ResolvedPageState(
    val showTopBar: Boolean,
    val showBottomBar: Boolean,
    val showDownloadOverlay: Boolean = true,
    val suppressFocusHighlight: Boolean = false,
    val title: String? = null,
    val openExternal: Boolean = false,
    val loadingText: String? = null,
    val loadingCardBackgroundColor: String? = null,
    val loadingTextColor: String? = null,
    val loadingIndicatorColor: String? = null,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    val errorCardBackgroundColor: String? = null,
    val errorTitleColor: String? = null,
    val errorMessageColor: String? = null,
    val errorButtonText: String? = null,
    val errorButtonBackgroundColor: String? = null,
    val errorButtonTextColor: String? = null,
    val errorRetryAction: String = "reload",
    val errorRetryUrl: String? = null,
    val injectJs: List<String> = emptyList(),
    val injectCss: List<String> = emptyList()
)
