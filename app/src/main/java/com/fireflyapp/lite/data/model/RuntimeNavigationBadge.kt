package com.fireflyapp.lite.data.model

/** A navigation badge value supplied by a web page for the current app session. */
data class RuntimeNavigationBadge(
    val mode: RuntimeNavigationBadgeMode,
    val count: Int = 0
)

enum class RuntimeNavigationBadgeMode {
    HIDDEN,
    UNREAD,
    COUNT
}
