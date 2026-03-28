package com.fireflyapp.lite.ui.project

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fireflyapp.lite.app.AppLanguageManager
import com.fireflyapp.lite.app.AppLanguageMode
import com.fireflyapp.lite.app.HostAppNoticePrompt
import com.fireflyapp.lite.app.HostAppService
import com.fireflyapp.lite.app.HostAppUpdatePrompt
import com.fireflyapp.lite.data.model.ProjectSummary
import com.fireflyapp.lite.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application.applicationContext)
    private val hostAppService = HostAppService(application.applicationContext)
    private val preferences = application.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(
        ProjectHubUiState(
            isDashboardVisible = preferences.getBoolean(KEY_DASHBOARD_VISIBLE, true),
            isAgreementAccepted = preferences.getBoolean(KEY_USER_AGREEMENT_ACCEPTED, false),
            languageMode = AppLanguageManager.getSavedMode(application.applicationContext)
        )
    )
    val uiState: StateFlow<ProjectHubUiState> = _uiState.asStateFlow()
    private var startupChecked = false
    private var deferredNoticePrompt: HostAppNoticePrompt? = null

    init {
        refresh()
        if (_uiState.value.isAgreementAccepted) {
            checkStartupPrompts()
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, userMessage = null)
        viewModelScope.launch {
            repository.listProjects()
                .onSuccess { projects ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        projects = projects
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = throwable.message ?: getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_load_failed)
                    )
                }
        }
    }

    fun createProject() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.createProject()
                .onSuccess { project ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_created),
                        pendingOpenProjectId = project.id
                    )
                    refresh()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = throwable.message ?: getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_create_failed)
                    )
                }
        }
    }

    fun importProject(uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.importProject(uri)
                .onSuccess { project ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_imported),
                        pendingOpenProjectId = project.id
                    )
                    refresh()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = throwable.message ?: getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_import_failed)
                    )
                }
        }
    }

    fun deleteProject(projectId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.deleteProject(projectId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_deleted)
                    )
                    refresh()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userMessage = throwable.message ?: getApplication<Application>().getString(com.fireflyapp.lite.R.string.project_hub_delete_failed)
                    )
                }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun consumePendingOpenProjectId() {
        _uiState.value = _uiState.value.copy(pendingOpenProjectId = null)
    }

    fun dismissUpdatePrompt() {
        _uiState.value = _uiState.value.copy(
            updatePrompt = null,
            noticePrompt = deferredNoticePrompt
        )
        deferredNoticePrompt = null
    }

    fun dismissNoticePrompt() {
        val noticePrompt = _uiState.value.noticePrompt ?: return
        if (noticePrompt.showOnce) {
            hostAppService.markNoticeSeen(noticePrompt.noticeKey)
        }
        _uiState.value = _uiState.value.copy(noticePrompt = null)
    }

    fun setDashboardVisible(visible: Boolean) {
        preferences.edit().putBoolean(KEY_DASHBOARD_VISIBLE, visible).apply()
        _uiState.value = _uiState.value.copy(isDashboardVisible = visible)
    }

    fun acceptUserAgreement() {
        if (_uiState.value.isAgreementAccepted) {
            checkStartupPrompts()
            return
        }
        preferences.edit().putBoolean(KEY_USER_AGREEMENT_ACCEPTED, true).apply()
        _uiState.value = _uiState.value.copy(isAgreementAccepted = true)
        checkStartupPrompts()
    }

    fun setLanguageMode(mode: AppLanguageMode) {
        AppLanguageManager.setLanguageMode(getApplication<Application>().applicationContext, mode)
        _uiState.value = _uiState.value.copy(languageMode = mode)
    }

    private fun checkStartupPrompts() {
        if (startupChecked) {
            return
        }
        startupChecked = true
        viewModelScope.launch {
            val prompts = hostAppService.loadStartupPrompts()
            deferredNoticePrompt = prompts.noticePrompt
            _uiState.value = _uiState.value.copy(
                updatePrompt = prompts.updatePrompt,
                noticePrompt = if (prompts.updatePrompt == null) prompts.noticePrompt else null
            )
            if (prompts.updatePrompt == null) {
                deferredNoticePrompt = null
            }
        }
    }
}

data class ProjectHubUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectSummary> = emptyList(),
    val userMessage: String? = null,
    val pendingOpenProjectId: String? = null,
    val updatePrompt: HostAppUpdatePrompt? = null,
    val noticePrompt: HostAppNoticePrompt? = null,
    val isDashboardVisible: Boolean = true,
    val isAgreementAccepted: Boolean = false,
    val languageMode: AppLanguageMode = AppLanguageMode.FOLLOW_SYSTEM
)

private const val PREFERENCES_NAME = "project_hub_preferences"
private const val KEY_DASHBOARD_VISIBLE = "dashboard_visible"
private const val KEY_USER_AGREEMENT_ACCEPTED = "user_agreement_accepted"
