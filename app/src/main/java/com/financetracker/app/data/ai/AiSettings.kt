package com.financetracker.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.financetracker.app.BuildConfig
import com.financetracker.app.data.backup.DeviceSecret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiProvider(val label: String) {
    GEMINI("Gemini"),
    CLAUDE("Claude")
}

data class AiSettingsState(
    val provider: AiProvider = AiProvider.GEMINI,
    val hasOwnGeminiKey: Boolean = false,
    val hasOwnClaudeKey: Boolean = false
) {
    val hasBuiltInGeminiKey: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()
    val hasBuiltInClaudeKey: Boolean get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

    fun hasKey(provider: AiProvider): Boolean = when (provider) {
        AiProvider.GEMINI -> hasOwnGeminiKey || hasBuiltInGeminiKey
        AiProvider.CLAUDE -> hasOwnClaudeKey || hasBuiltInClaudeKey
    }
}

/**
 * Which AI the app uses (Settings > General > AI assistant) and the user's own API keys.
 *
 * Gemini is the default: Google's Gemini API has a free tier, and a build can include a shared
 * free key (GEMINI_API_KEY) so AI works out of the box for everyone the app is shared with.
 * Claude needs a key — normally each person's own, entered here; a build can still include one
 * (FINANCE_APP_ANTHROPIC_API_KEY), but every copy of that build then spends from it.
 *
 * Keys entered in the app are stored encrypted with this phone's Android Keystore key, so they
 * never leave the phone (and are left out of backups).
 */
object AiSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_OWN_GEMINI = "ai_own_gemini_key"
    private const val KEY_OWN_CLAUDE = "ai_own_claude_key"

    /** Phone-specific secrets, never written into a backup. */
    val SECRET_KEYS = setOf(KEY_OWN_GEMINI, KEY_OWN_CLAUDE)

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AiSettingsState())
    val state: StateFlow<AiSettingsState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refresh()
    }

    private fun refresh() {
        val hasOwnClaude = prefs.contains(KEY_OWN_CLAUDE)
        // With no choice made yet, a build that includes a Claude key keeps using Claude (as it
        // did before this setting existed); anything else starts on the free Gemini.
        val default = if (hasOwnClaude || BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) AiProvider.CLAUDE else AiProvider.GEMINI
        _state.value = AiSettingsState(
            provider = prefs.getString(KEY_PROVIDER, null)
                ?.let { name -> AiProvider.entries.firstOrNull { it.name == name } }
                ?: default,
            hasOwnGeminiKey = prefs.contains(KEY_OWN_GEMINI),
            hasOwnClaudeKey = hasOwnClaude
        )
    }

    val provider: AiProvider get() = _state.value.provider

    fun setProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_PROVIDER, provider.name).apply()
        refresh()
    }

    /** Saves (or with a blank [key], removes) the user's own key for [provider]. */
    fun setOwnKey(provider: AiProvider, key: String?) {
        val prefKey = if (provider == AiProvider.GEMINI) KEY_OWN_GEMINI else KEY_OWN_CLAUDE
        val trimmed = key?.trim().orEmpty()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(prefKey) else putString(prefKey, DeviceSecret.encrypt(trimmed.toByteArray(Charsets.UTF_8)))
        }.commit()
        refresh()
    }

    /** The key to use for [provider]: the user's own if set, otherwise the one built into the app. */
    fun keyFor(provider: AiProvider): String? {
        val prefKey = if (provider == AiProvider.GEMINI) KEY_OWN_GEMINI else KEY_OWN_CLAUDE
        val own = if (::prefs.isInitialized) {
            prefs.getString(prefKey, null)?.let { DeviceSecret.decrypt(it) }?.let { String(it, Charsets.UTF_8) }
        } else {
            null
        }
        val builtIn = if (provider == AiProvider.GEMINI) BuildConfig.GEMINI_API_KEY else BuildConfig.ANTHROPIC_API_KEY
        return own?.takeIf { it.isNotBlank() } ?: builtIn.takeIf { it.isNotBlank() }
    }
}
