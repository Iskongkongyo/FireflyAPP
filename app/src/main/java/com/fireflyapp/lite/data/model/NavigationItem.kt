package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NavigationItem(
    val id: String = "home",
    val title: String = "Home",
    val url: String = "https://ss.ixq.pp.ua",
    val icon: String = "home",
    val selectedIcon: String = "",
    val badgeCount: String = "",
    val showUnreadDot: Boolean = false
)
