package com.scribe.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.scribe.app.data.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scribe_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("provider")
        private val KEY_OPENAI_URL = stringPreferencesKey("openai_base_url")
        private val KEY_OPENAI_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
        private val KEY_ANTHROPIC_URL = stringPreferencesKey("anthropic_base_url")
        private val KEY_ANTHROPIC_KEY = stringPreferencesKey("anthropic_api_key")
        private val KEY_ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        private val KEY_SHOW_REASONING = booleanPreferencesKey("show_reasoning")
        private val KEY_AI_THINKING = booleanPreferencesKey("ai_thinking")
        private val KEY_LAST_SKILL_ID = stringPreferencesKey("last_skill_id")

        const val DEFAULT_OPENAI_URL = "https://api.deepseek.com"
        const val DEFAULT_OPENAI_MODEL = "deepseek-v4-flash"
        const val DEFAULT_ANTHROPIC_URL = "https://api.anthropic.com"
        const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6"
    }

    data class AppSettings(
        val provider: Provider = Provider.OPENAI,
        val openaiBaseUrl: String = DEFAULT_OPENAI_URL,
        val openaiApiKey: String = "",
        val openaiModel: String = DEFAULT_OPENAI_MODEL,
        val anthropicBaseUrl: String = DEFAULT_ANTHROPIC_URL,
        val anthropicApiKey: String = "",
        val anthropicModel: String = DEFAULT_ANTHROPIC_MODEL,
        val showReasoning: Boolean = true,
        val aiThinking: Boolean = true,
        val lastSkillId: String = ""
    )

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            provider = try { Provider.valueOf(prefs[KEY_PROVIDER] ?: "OPENAI") } catch (_: Exception) { Provider.OPENAI },
            openaiBaseUrl = prefs[KEY_OPENAI_URL] ?: DEFAULT_OPENAI_URL,
            openaiApiKey = prefs[KEY_OPENAI_KEY] ?: "",
            openaiModel = prefs[KEY_OPENAI_MODEL] ?: DEFAULT_OPENAI_MODEL,
            anthropicBaseUrl = prefs[KEY_ANTHROPIC_URL] ?: DEFAULT_ANTHROPIC_URL,
            anthropicApiKey = prefs[KEY_ANTHROPIC_KEY] ?: "",
            anthropicModel = prefs[KEY_ANTHROPIC_MODEL] ?: DEFAULT_ANTHROPIC_MODEL,
            showReasoning = prefs[KEY_SHOW_REASONING] ?: true,
            aiThinking = prefs[KEY_AI_THINKING] ?: true,
            lastSkillId = prefs[KEY_LAST_SKILL_ID] ?: ""
        )
    }

    suspend fun setProvider(provider: Provider) {
        context.dataStore.edit { it[KEY_PROVIDER] = provider.name }
    }

    suspend fun setOpenAIConfig(url: String, key: String, model: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_URL] = url
            it[KEY_OPENAI_KEY] = key
            it[KEY_OPENAI_MODEL] = model
        }
    }

    suspend fun setAnthropicConfig(url: String, key: String, model: String) {
        context.dataStore.edit {
            it[KEY_ANTHROPIC_URL] = url
            it[KEY_ANTHROPIC_KEY] = key
            it[KEY_ANTHROPIC_MODEL] = model
        }
    }

    suspend fun setShowReasoning(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_REASONING] = show }
    }

    suspend fun setAiThinking(thinking: Boolean) {
        context.dataStore.edit { it[KEY_AI_THINKING] = thinking }
    }

    suspend fun setLastSkillId(id: String) {
        context.dataStore.edit { it[KEY_LAST_SKILL_ID] = id }
    }
}
