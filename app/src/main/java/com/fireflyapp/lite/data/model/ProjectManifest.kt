package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    val schemaVersion: Int = 1,
    val format: String = "fireflyproj",
    val projectId: String = "",
    val projectName: String = "New Project",
    val exportedAt: Long? = null,
    val appConfigPath: String = "app-config.json",
    val appIdentity: ProjectAppIdentity = ProjectAppIdentity(),
    val branding: ProjectBranding = ProjectBranding(),
    val signing: ProjectSigning = ProjectSigning(),
    val packaging: ProjectPackaging = ProjectPackaging()
)

@Serializable
data class ProjectAppIdentity(
    val applicationLabel: String = "Firefly App",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val packageName: String = ""
)

@Serializable
data class ProjectBranding(
    val iconMode: String = "default",
    val iconPath: String = "",
    val splashMode: String = "default",
    val splashPath: String = "",
    val splashSkipEnabled: Boolean = true,
    val splashSkipSeconds: Int = 3
)

@Serializable
data class ProjectSigning(
    val mode: String = "default",
    val keystorePath: String = "",
    val storePassword: String = "",
    val keyAlias: String = "",
    val keyPassword: String = ""
)

@Serializable
data class ProjectPackaging(
    val outputApkNameTemplate: String = ""
)
