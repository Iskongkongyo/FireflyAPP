package com.fireflyapp.lite.ui.pack

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fireflyapp.lite.R
import com.fireflyapp.lite.data.model.ProjectManifest
import com.fireflyapp.lite.data.model.ProjectSummary
import com.fireflyapp.lite.data.model.TemplatePackHistoryEntry
import com.fireflyapp.lite.data.model.TemplateType
import com.fireflyapp.lite.data.model.TemplatePackExecutionResult
import com.fireflyapp.lite.data.model.TemplatePackExecutionStatus
import com.fireflyapp.lite.data.model.TemplatePackPreflight
import com.fireflyapp.lite.data.model.TemplatePackWorkspace
import com.fireflyapp.lite.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PackagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(PackagerUiState())
    val uiState: StateFlow<PackagerUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null

    fun loadProject(projectId: String, fallbackProjectName: String) {
        if (projectId == currentProjectId && _uiState.value.projectSummary != null) {
            return
        }

        currentProjectId = projectId
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            userMessage = null,
            projectSummary = ProjectSummary(
                id = projectId,
                name = fallbackProjectName.ifBlank { projectId },
                defaultUrl = "",
                template = TemplateType.TOP_BAR,
                updatedAt = 0L,
                applicationLabel = fallbackProjectName.ifBlank { projectId },
                versionName = "1.0.0",
                versionCode = 1,
                packageName = ""
            )
        )

        viewModelScope.launch {
            val summary = repository.loadProjectSummary(projectId)
            val manifest = repository.loadProjectManifest(projectId)
            val outputApkName = repository.resolveTemplatePackOutputApkName(projectId)
            val preflight = repository.inspectTemplatePackPreflight(projectId)
            val workspace = repository.inspectTemplatePackWorkspace(projectId)
            val packHistory = repository.loadTemplatePackHistory(projectId)
            val logPreview = repository.loadTemplatePackLogPreview(projectId)

            summary
                .onSuccess { projectSummary ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        projectSummary = projectSummary,
                        signingSummary = manifest.getOrNull()?.toSigningSummary(getApplication<Application>())
                            ?: DEFAULT_SIGNING_SUMMARY,
                        signingKeystorePath = manifest.getOrNull()?.signing?.keystorePath.orEmpty(),
                        outputApkName = outputApkName.getOrDefault(""),
                        packHistory = packHistory.getOrDefault(emptyList()),
                        preflight = preflight.getOrElse { throwable ->
                            TemplatePackPreflight(
                                message = throwable.message ?: appString(R.string.packager_message_preflight_inspect_failed)
                            )
                        }
                    )
                    workspace
                        .onSuccess { localWorkspace ->
                            _uiState.value = _uiState.value.copy(
                                workspace = localWorkspace,
                                buildLogPreview = logPreview.getOrDefault("")
                            )
                        }
                        .onFailure { throwable ->
                            _uiState.value = _uiState.value.copy(
                                userMessage = throwable.message ?: appString(R.string.packager_message_workspace_inspect_failed)
                            )
                        }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = throwable.message ?: appString(R.string.packager_message_project_load_failed)
                    )
                }
        }
    }

    fun prepareWorkspace() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(isPreparing = true, userMessage = null)
        viewModelScope.launch {
            repository.prepareTemplatePackWorkspace(projectId)
                .onSuccess { workspace ->
                    val buildLogPreview = repository.loadTemplatePackLogPreview(projectId).getOrDefault("")
                    _uiState.value = _uiState.value.copy(
                        isPreparing = false,
                        workspace = workspace,
                        outputApkName = workspace.artifactFileName,
                        buildLogPreview = buildLogPreview,
                        preflight = repository.inspectTemplatePackPreflight(projectId).getOrElse { throwable ->
                            _uiState.value.preflight.copy(
                                message = throwable.message ?: appString(R.string.packager_message_preflight_refresh_failed)
                            )
                        },
                        buildExecution = PackBuildExecutionUiState(
                            result = TemplatePackExecutionResult(
                                status = TemplatePackExecutionStatus.IDLE,
                                message = appString(R.string.packager_message_workspace_ready_for_repack)
                            )
                        ),
                        userMessage = appString(R.string.packager_message_workspace_prepared)
                    )
                }
                .onFailure { throwable ->
                    repository.inspectTemplatePackWorkspace(projectId)
                        .onSuccess { workspace ->
                            val buildLogPreview = repository.loadTemplatePackLogPreview(projectId).getOrDefault("")
                            _uiState.value = _uiState.value.copy(
                                isPreparing = false,
                                workspace = workspace,
                                buildLogPreview = buildLogPreview,
                                userMessage = throwable.message ?: appString(R.string.packager_message_workspace_prepare_failed)
                            )
                        }
                        .onFailure {
                            _uiState.value = _uiState.value.copy(
                                isPreparing = false,
                                userMessage = throwable.message ?: appString(R.string.packager_message_workspace_prepare_failed)
                            )
                        }
                }
        }
    }

    fun executeBuild() {
        val projectId = currentProjectId ?: return
        _uiState.value = _uiState.value.copy(
            isBuilding = true,
            buildExecution = PackBuildExecutionUiState(
                result = TemplatePackExecutionResult(
                    status = TemplatePackExecutionStatus.RUNNING,
                    message = appString(R.string.packager_message_repack_running)
                )
            ),
            userMessage = null
        )
        viewModelScope.launch {
            repository.executeTemplatePack(projectId)
                .onSuccess { result ->
                    val workspace = repository.inspectTemplatePackWorkspace(projectId).getOrNull()
                    val buildLogPreview = repository.loadTemplatePackLogPreview(projectId).getOrDefault("")
                    val packHistory = repository.loadTemplatePackHistory(projectId)
                        .getOrDefault(_uiState.value.packHistory)
                    _uiState.value = _uiState.value.copy(
                        isBuilding = false,
                        workspace = workspace ?: _uiState.value.workspace,
                        outputApkName = workspace?.artifactFileName ?: _uiState.value.outputApkName,
                        packHistory = packHistory,
                        buildExecution = PackBuildExecutionUiState(result = result.localizeMessage()),
                        buildLogPreview = buildLogPreview,
                        preflight = repository.inspectTemplatePackPreflight(projectId).getOrElse { throwable ->
                            _uiState.value.preflight.copy(
                                message = throwable.message ?: appString(R.string.packager_message_preflight_refresh_failed)
                            )
                        },
                        userMessage = result.localizeMessage().message
                    )
                }
                .onFailure { throwable ->
                    val buildLogPreview = repository.loadTemplatePackLogPreview(projectId).getOrDefault("")
                    _uiState.value = _uiState.value.copy(
                        isBuilding = false,
                        buildExecution = PackBuildExecutionUiState(
                            result = TemplatePackExecutionResult(
                                status = TemplatePackExecutionStatus.FAILED,
                                message = throwable.message ?: appString(R.string.packager_message_repack_failed)
                            )
                        ),
                        buildLogPreview = buildLogPreview,
                        preflight = repository.inspectTemplatePackPreflight(projectId).getOrElse { refreshError ->
                            _uiState.value.preflight.copy(
                                message = refreshError.message ?: appString(R.string.packager_message_preflight_refresh_failed)
                            )
                        },
                        userMessage = throwable.message ?: appString(R.string.packager_message_repack_failed)
                    )
                }
        }
    }

    fun refreshPreflight() {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                preflight = repository.inspectTemplatePackPreflight(projectId).getOrElse { throwable ->
                    _uiState.value.preflight.copy(
                        message = throwable.message ?: appString(R.string.packager_message_preflight_refresh_failed)
                    )
                }
            )
        }
    }

    fun deleteHistoryEntry(entry: TemplatePackHistoryEntry) {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            repository.deleteTemplatePackHistoryEntry(projectId, entry)
                .onSuccess { remainingHistory ->
                    _uiState.value = _uiState.value.copy(
                        packHistory = remainingHistory,
                        userMessage = appString(R.string.packager_message_history_deleted)
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        userMessage = throwable.message ?: appString(R.string.packager_message_history_delete_failed)
                    )
                }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    private fun appString(@StringRes resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun TemplatePackExecutionResult.localizeMessage(): TemplatePackExecutionResult {
        val localized = when (message) {
            "Signed APK generated and ready to install." -> appString(R.string.packager_message_signed_ready)
            "Signed APK generated and passed artifact self-check." -> appString(R.string.packager_message_signed_verified)
            "Signed APK generated with artifact self-check warnings." -> appString(R.string.packager_message_signed_with_warnings)
            "Unsigned APK is missing. Run Pack first." -> appString(R.string.packager_message_unsigned_apk_missing)
            "Unsigned APK packaging failed." -> appString(R.string.packager_message_unsigned_packaging_failed)
            "Unpacked template APK directory is missing." -> appString(R.string.packager_message_unpacked_template_missing)
            "APK signing failed." -> appString(R.string.packager_message_repack_failed)
            else -> if (message.startsWith("Artifact self-check failed: ")) {
                appString(
                    R.string.packager_message_artifact_self_check_failed,
                    message.removePrefix("Artifact self-check failed: ")
                )
            } else {
                message
            }
        }
        val localizedArtifactCheck = artifactCheck?.let { artifactCheck ->
            artifactCheck.copy(
                manifestCheck = when (artifactCheck.manifestCheck) {
                    "Binary AndroidManifest.xml structure is valid." -> appString(R.string.packager_artifact_check_manifest_valid)
                    else -> artifactCheck.manifestCheck
                },
                signatureCheck = when (artifactCheck.signatureCheck) {
                    "ApkVerifier accepted the signed APK." -> appString(R.string.packager_artifact_check_signature_valid)
                    "ApkVerifier accepted the signed APK with warnings. See pack.log for details." ->
                        appString(R.string.packager_artifact_check_signature_warning)
                    else -> artifactCheck.signatureCheck
                },
                packageParserCheck = when (artifactCheck.packageParserCheck) {
                    "PackageManager accepted the signed APK archive." ->
                        appString(R.string.packager_artifact_check_package_parser_accepted)
                    "PackageManager rejected the signed APK archive on this device. Review pack.log before installing." ->
                        appString(R.string.packager_artifact_check_package_parser_warning)
                    else -> artifactCheck.packageParserCheck
                }
            )
        }
        return copy(
            message = localized,
            artifactCheck = localizedArtifactCheck
        )
    }
}

data class PackagerUiState(
    val isLoading: Boolean = true,
    val isPreparing: Boolean = false,
    val isBuilding: Boolean = false,
    val projectSummary: ProjectSummary? = null,
    val signingSummary: String = DEFAULT_SIGNING_SUMMARY,
    val signingKeystorePath: String = "",
    val outputApkName: String = "",
    val packHistory: List<TemplatePackHistoryEntry> = emptyList(),
    val preflight: TemplatePackPreflight = TemplatePackPreflight(),
    val workspace: TemplatePackWorkspace? = null,
    val buildExecution: PackBuildExecutionUiState = PackBuildExecutionUiState(),
    val buildLogPreview: String = "",
    val userMessage: String? = null
)

data class PackBuildExecutionUiState(
    val result: TemplatePackExecutionResult? = null
)

private fun ProjectManifest.toSigningSummary(application: Application): String {
    return if (signing.mode == "custom" && signing.keystorePath.isNotBlank()) {
        val alias = signing.keyAlias.ifBlank {
            application.getString(R.string.packager_signing_alias_pending)
        }
        application.getString(R.string.packager_signing_summary_custom, alias)
    } else {
        application.getString(R.string.packager_signing_summary_default)
    }
}

private const val DEFAULT_SIGNING_SUMMARY = ""
