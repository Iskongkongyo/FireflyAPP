package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PageOverride(
    val showTopBar: Boolean? = null,
    val showBottomBar: Boolean? = null,
    val showDownloadOverlay: Boolean? = null,
    val suppressFocusHighlight: Boolean? = null,
    val title: String? = null,
    val openExternal: Boolean? = null,
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
    val errorRetryAction: String? = null,
    val errorRetryUrl: String? = null,
    val injectJs: List<String> = emptyList(),
    val injectCss: List<String> = emptyList()
)
