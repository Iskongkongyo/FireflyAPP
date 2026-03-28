package com.fireflyapp.lite.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fireflyapp.lite.core.config.AppConfigManager
import com.fireflyapp.lite.data.model.AppConfig
import com.fireflyapp.lite.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application.applicationContext)
    private val configManager = AppConfigManager()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var currentProjectId: String? = null

    fun requireConfig(): AppConfig {
        return checkNotNull(_uiState.value.config)
    }

    fun loadProject(projectId: String) {
        currentProjectId = projectId
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = previousState.copy(
                isLoading = true,
                errorMessage = null,
                projectId = projectId
            )

            repository.loadConfig(projectId)
                .onSuccess { config ->
                    _uiState.value = MainUiState(
                        isLoading = false,
                        projectId = projectId,
                        config = config,
                        configVersion = previousState.configVersion + 1
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = previousState.copy(
                        isLoading = false,
                        projectId = projectId,
                        errorMessage = throwable.message ?: "Failed to load project."
                    )
                }
        }
    }

    fun loadStandaloneConfig() {
        currentProjectId = null
        viewModelScope.launch {
            val previousState = _uiState.value
            _uiState.value = previousState.copy(
                isLoading = true,
                errorMessage = null,
                projectId = null
            )

            runCatching { configManager.load(getApplication()) }
                .onSuccess { config ->
                    _uiState.value = MainUiState(
                        isLoading = false,
                        projectId = null,
                        config = config,
                        configVersion = previousState.configVersion + 1
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = previousState.copy(
                        isLoading = false,
                        projectId = null,
                        errorMessage = throwable.message ?: "Failed to load app config."
                    )
                }
        }
    }

    fun reloadProject() {
        currentProjectId?.let(::loadProject) ?: loadStandaloneConfig()
    }
}

data class MainUiState(
    val isLoading: Boolean = true,
    val projectId: String? = null,
    val config: AppConfig? = null,
    val errorMessage: String? = null,
    val configVersion: Int = 0
)
