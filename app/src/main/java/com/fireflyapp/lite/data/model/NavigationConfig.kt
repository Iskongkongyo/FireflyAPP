package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NavigationConfig(
    val items: List<NavigationItem> = emptyList()
)
