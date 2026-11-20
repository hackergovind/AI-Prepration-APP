package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preppilot_prefs")

object DefaultGeminiModels {
    const val QUESTION_MODEL = "gemini-3.5-flash-lite"
    const val DETAILED_MODEL = "gemini-3.5-flash"
    const val FALLBACK_MODEL = "gemini-2.5-flash-lite"
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AccentPalette(val label: String) {
    SKY_FROST("Sky Blue Frost"),
    BLUE_VIOLET("Calm Blue & Violet"),
    TEAL_SLATE("Crisp Teal & Slate"),
    INDIGO_AMBER("Warm Indigo & Amber")
}

enum class AiTonePreset(val label: String, val systemInstruction: String) {
    SUPPORTIVE(
        "Supportive",
        "Be warm, encouraging, and constructive while holding high interview standards."
    ),
    STRICT(
        "Strict",
        "Be rigorous, direct, and exacting like a senior hiring bar-raiser. Focus sharply on gaps."
    ),
    CONCISE(
        "Concise",
        "Keep questions, feedback, and tips crisp, bullet-friendly, and free of fluff."
    ),
    DETAILED(
        "Detailed",
        "Provide thorough, nuanced explanations with concrete examples and deeper architectural insight."
    ),
    CUSTOM(
        "Custom",
        ""
    )
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentPalette: AccentPalette = AccentPalette.SKY_FROST,
    val geminiApiKey: String = "",
    val questionModel: String = DefaultGeminiModels.QUESTION_MODEL,
    val detailedModel: String = DefaultGeminiModels.DETAILED_MODEL,
    val fallbackModel: String = DefaultGeminiModels.FALLBACK_MODEL,
    val aiTonePreset: AiTonePreset = AiTonePreset.SUPPORTIVE,
    val customSystemPrompt: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val defaultLanguage: String = "English",
    val defaultAnswerTimeSec: Int = 120,
    val practiceStreak: Int = 0,
    val lastPracticeDateKey: String = "",
    val reminderTitle: String = "Mock Interview Session",
    val reminderEpochMs: Long = 0L
) {
    fun effectiveGlobalTone(): String {
        return if (aiTonePreset == AiTonePreset.CUSTOM && customSystemPrompt.isNotBlank()) {
            customSystemPrompt.trim()
        } else {
            aiTonePreset.systemInstruction
        }
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val QUESTION_MODEL = stringPreferencesKey("gemini_question_model")
        val DETAILED_MODEL = stringPreferencesKey("gemini_detailed_model")
        val FALLBACK_MODEL = stringPreferencesKey("gemini_fallback_model")
        val AI_TONE = stringPreferencesKey("ai_tone")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_system_prompt")
        val TTS_RATE = floatPreferencesKey("tts_speech_rate")
        val DEFAULT_LANG = stringPreferencesKey("default_language")
        val DEFAULT_TIME_SEC = intPreferencesKey("default_answer_time_sec")
        val STREAK = intPreferencesKey("practice_streak")
        val LAST_PRACTICE_DATE = stringPreferencesKey("last_practice_date")
        val REMINDER_TITLE = stringPreferencesKey("reminder_title")
        val REMINDER_EPOCH_MS = longPreferencesKey("reminder_epoch_ms")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
            accentPalette = prefs[Keys.ACCENT_PALETTE]?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() }
                ?: AccentPalette.SKY_FROST,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            questionModel = prefs[Keys.QUESTION_MODEL]?.takeIf { it.isNotBlank() }
                ?: DefaultGeminiModels.QUESTION_MODEL,
            detailedModel = prefs[Keys.DETAILED_MODEL]?.takeIf { it.isNotBlank() }
                ?: DefaultGeminiModels.DETAILED_MODEL,
            fallbackModel = prefs[Keys.FALLBACK_MODEL]?.takeIf { it.isNotBlank() }
                ?: DefaultGeminiModels.FALLBACK_MODEL,
            aiTonePreset = prefs[Keys.AI_TONE]?.let { runCatching { AiTonePreset.valueOf(it) }.getOrNull() }
                ?: AiTonePreset.SUPPORTIVE,
            customSystemPrompt = prefs[Keys.CUSTOM_PROMPT] ?: "",
            ttsSpeechRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            defaultLanguage = prefs[Keys.DEFAULT_LANG] ?: "English",
            defaultAnswerTimeSec = prefs[Keys.DEFAULT_TIME_SEC] ?: 120,
            practiceStreak = prefs[Keys.STREAK] ?: 0,
            lastPracticeDateKey = prefs[Keys.LAST_PRACTICE_DATE] ?: "",
            reminderTitle = prefs[Keys.REMINDER_TITLE] ?: "Mock Interview Session",
            reminderEpochMs = prefs[Keys.REMINDER_EPOCH_MS] ?: 0L
        )
    }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        context.dataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { it[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }

    suspend fun setQuestionModel(model: String) {
        context.dataStore.edit { it[Keys.QUESTION_MODEL] = model.trim() }
    }

    suspend fun setDetailedModel(model: String) {
        context.dataStore.edit { it[Keys.DETAILED_MODEL] = model.trim() }
    }

    suspend fun setFallbackModel(model: String) {
        context.dataStore.edit { it[Keys.FALLBACK_MODEL] = model.trim() }
    }

    suspend fun resetGeminiModelsToDefaults() {
        context.dataStore.edit {
            it[Keys.QUESTION_MODEL] = DefaultGeminiModels.QUESTION_MODEL
            it[Keys.DETAILED_MODEL] = DefaultGeminiModels.DETAILED_MODEL
            it[Keys.FALLBACK_MODEL] = DefaultGeminiModels.FALLBACK_MODEL
        }
    }

    suspend fun setAiTonePreset(tone: AiTonePreset) {
        context.dataStore.edit { it[Keys.AI_TONE] = tone.name }
    }

    suspend fun setCustomSystemPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.CUSTOM_PROMPT] = prompt }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.TTS_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setDefaultLanguage(language: String) {
        context.dataStore.edit { it[Keys.DEFAULT_LANG] = language }
    }

    suspend fun setDefaultAnswerTimeSec(seconds: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_TIME_SEC] = seconds.coerceIn(30, 300) }
    }

    suspend fun setReminder(title: String, targetEpochMs: Long) {
        context.dataStore.edit {
            it[Keys.REMINDER_TITLE] = title.trim().ifBlank { "Mock Interview Session" }
            it[Keys.REMINDER_EPOCH_MS] = targetEpochMs
        }
    }

    suspend fun recordPracticeForDate(todayKey: String, yesterdayKey: String) {
        context.dataStore.edit { prefs ->
            val last = prefs[Keys.LAST_PRACTICE_DATE] ?: ""
            val current = prefs[Keys.STREAK] ?: 0
            val updated = when (last) {
                todayKey -> maxOf(1, current)
                yesterdayKey -> current + 1
                else -> 1
            }
            prefs[Keys.STREAK] = updated
            prefs[Keys.LAST_PRACTICE_DATE] = todayKey
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
