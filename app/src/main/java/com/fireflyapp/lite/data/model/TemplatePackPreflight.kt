package com.fireflyapp.lite.data.model

data class TemplatePackPreflight(
    val outputPackageName: String = "",
    val signerSummary: String = "",
    val signerFingerprint: String = "",
    val installState: TemplatePackInstallState = TemplatePackInstallState.NOT_READY,
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0,
    val installedSignerFingerprint: String = "",
    val message: String = ""
)

enum class TemplatePackInstallState {
    NOT_READY,
    NOT_INSTALLED,
    UPDATE_COMPATIBLE,
    SIGNATURE_CONFLICT,
    VERSION_DOWNGRADE
}
