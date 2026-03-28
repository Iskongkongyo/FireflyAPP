package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class InjectConfig(
    val globalJs: List<String> = emptyList(),
    val globalCss: List<String> = emptyList()
)
