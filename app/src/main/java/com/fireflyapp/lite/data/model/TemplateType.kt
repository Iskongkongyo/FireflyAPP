package com.fireflyapp.lite.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TemplateType {
    @SerialName("browser")
    BROWSER,

    @SerialName("immersive_single_page")
    IMMERSIVE_SINGLE_PAGE,

    @SerialName("side_drawer")
    SIDE_DRAWER,

    @SerialName("top_bar_tabs")
    TOP_BAR_TABS,

    @SerialName("top_bar_bottom_tabs")
    TOP_BAR_BOTTOM_TABS,

    @SerialName("top_bar")
    TOP_BAR,

    @SerialName("bottom_bar")
    BOTTOM_BAR
}
