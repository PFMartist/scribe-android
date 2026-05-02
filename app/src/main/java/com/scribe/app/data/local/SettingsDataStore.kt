package com.scribe.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scribe.app.data.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsDataStore(context: Context) {

    companion object {
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
        val lastSkillId: String = "",
        val bundledSkillsVersion: Int = 0
    )

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "scribe_secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: Flow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings = prefs.run {
        AppSettings(
            provider = try { Provider.valueOf(getString("provider", "OPENAI")!!) } catch (_: Exception) { Provider.OPENAI },
            openaiBaseUrl = getString("openai_base_url", DEFAULT_OPENAI_URL) ?: DEFAULT_OPENAI_URL,
            openaiApiKey = getString("openai_api_key", "") ?: "",
            openaiModel = getString("openai_model", DEFAULT_OPENAI_MODEL) ?: DEFAULT_OPENAI_MODEL,
            anthropicBaseUrl = getString("anthropic_base_url", DEFAULT_ANTHROPIC_URL) ?: DEFAULT_ANTHROPIC_URL,
            anthropicApiKey = getString("anthropic_api_key", "") ?: "",
            anthropicModel = getString("anthropic_model", DEFAULT_ANTHROPIC_MODEL) ?: DEFAULT_ANTHROPIC_MODEL,
            showReasoning = getBoolean("show_reasoning", true),
            aiThinking = getBoolean("ai_thinking", true),
            lastSkillId = getString("last_skill_id", "") ?: "",
            bundledSkillsVersion = getInt("bundled_skills_version", 0)
        )
    }

    private fun saveAndNotify(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
        _settings.value = loadSettings()
    }

    fun setProvider(provider: Provider) {
        saveAndNotify { putString("provider", provider.name) }
    }

    fun setOpenAIConfig(url: String, key: String, model: String) {
        saveAndNotify {
            putString("openai_base_url", url)
            putString("openai_api_key", key)
            putString("openai_model", model)
        }
    }

    fun setAnthropicConfig(url: String, key: String, model: String) {
        saveAndNotify {
            putString("anthropic_base_url", url)
            putString("anthropic_api_key", key)
            putString("anthropic_model", model)
        }
    }

    fun setShowReasoning(show: Boolean) {
        saveAndNotify { putBoolean("show_reasoning", show) }
    }

    fun setAiThinking(thinking: Boolean) {
        saveAndNotify { putBoolean("ai_thinking", thinking) }
    }

    fun setLastSkillId(id: String) {
        saveAndNotify { putString("last_skill_id", id) }
    }

    fun getBundledSkillsVersion(): Int = prefs.getInt("bundled_skills_version", 0)

    fun setBundledSkillsVersion(version: Int) {
        saveAndNotify { putInt("bundled_skills_version", version) }
    }
}
