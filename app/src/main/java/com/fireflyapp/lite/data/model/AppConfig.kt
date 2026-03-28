package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val schemaVersion: Int = 1,
    val app: AppInfo = AppInfo(),
    val browser: BrowserConfig = BrowserConfig(),
    val shell: ShellConfig = ShellConfig(),
    val navigation: NavigationConfig = NavigationConfig(),
    val security: SecurityConfig = SecurityConfig(),
    val inject: InjectConfig = InjectConfig(),
    val pageRules: List<PageRule> = emptyList(),
    val pageEvents: List<PageEventRule> = emptyList()
)
