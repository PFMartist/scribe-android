package com.scribe.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scribe.app.data.local.SettingsDataStore
import com.scribe.app.data.model.Provider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val provider: Provider = Provider.OPENAI,
    val openaiBaseUrl: String = SettingsDataStore.DEFAULT_OPENAI_URL,
    val openaiApiKey: String = "",
    val openaiModel: String = SettingsDataStore.DEFAULT_OPENAI_MODEL,
    val anthropicBaseUrl: String = SettingsDataStore.DEFAULT_ANTHROPIC_URL,
    val anthropicApiKey: String = "",
    val anthropicModel: String = SettingsDataStore.DEFAULT_ANTHROPIC_MODEL,
    val showReasoning: Boolean = true,
    val aiThinking: Boolean = true,
    val continueInBackground: Boolean = true,
    val saved: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        provider = settings.provider,
                        openaiBaseUrl = settings.openaiBaseUrl,
                        openaiApiKey = settings.openaiApiKey,
                        openaiModel = settings.openaiModel,
                        anthropicBaseUrl = settings.anthropicBaseUrl,
                        anthropicApiKey = settings.anthropicApiKey,
                        anthropicModel = settings.anthropicModel,
                        showReasoning = settings.showReasoning,
                        aiThinking = settings.aiThinking,
                        continueInBackground = settings.continueInBackground
                    )
                }
            }
        }
    }

    fun setProvider(provider: Provider) {
        viewModelScope.launch { settingsStore.setProvider(provider) }
    }

    fun saveOpenAIConfig(url: String, key: String, model: String) {
        viewModelScope.launch {
            settingsStore.setOpenAIConfig(url, key, model)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun saveAnthropicConfig(url: String, key: String, model: String) {
        viewModelScope.launch {
            settingsStore.setAnthropicConfig(url, key, model)
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun setShowReasoning(show: Boolean) {
        viewModelScope.launch { settingsStore.setShowReasoning(show) }
    }

    fun setAiThinking(thinking: Boolean) {
        viewModelScope.launch { settingsStore.setAiThinking(thinking) }
    }

    fun setContinueInBackground(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setContinueInBackground(enabled) }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }
}
