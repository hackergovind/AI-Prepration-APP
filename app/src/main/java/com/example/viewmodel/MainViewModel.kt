package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiQueueState
import com.example.ai.GeminiAiService
import com.example.data.local.AccentPalette
import com.example.data.local.AiTonePreset
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.InterviewSessionEntity
import com.example.data.local.PrepPlanTaskEntity
import com.example.data.local.PrepRepository
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

sealed interface DailyChallengeUiState {
    data object Idle : DailyChallengeUiState
    data object Loading : DailyChallengeUiState
    data class Ready(val challenge: DailyChallengeEntity) : DailyChallengeUiState
    data class Error(val message: String) : DailyChallengeUiState
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

class MainViewModel(
    private val repository: PrepRepository,
    private val aiService: GeminiAiService
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isProfileReady: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        repository.profileFlow.collect { emit(true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val sessions: StateFlow<List<InterviewSessionEntity>> = repository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prepPlanTasks: StateFlow<List<PrepPlanTaskEntity>> = repository.prepPlanTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayChallenge: StateFlow<DailyChallengeEntity?> = repository.observeTodayChallenge()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val queueState: StateFlow<AiQueueState> = aiService.queueState
    val backupModelEvents: SharedFlow<String> = aiService.backupModelEvents

    private val _dailyUiState = MutableStateFlow<DailyChallengeUiState>(DailyChallengeUiState.Idle)
    val dailyUiState: StateFlow<DailyChallengeUiState> = _dailyUiState.asStateFlow()

    private val _settingsBanner = MutableStateFlow<String?>(null)
    val settingsBanner: StateFlow<String?> = _settingsBanner.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    fun saveProfile(
        name: String,
        targetRole: String,
        experienceLevel: String,
        industry: String,
        targetCompanies: String,
        resumeText: String,
        jobDescriptionText: String,
        onboardingCompleted: Boolean = true
    ) {
        viewModelScope.launch {
            repository.saveProfile(
                UserProfileEntity(
                    id = 1,
                    name = name.trim(),
                    targetRole = targetRole.trim(),
                    experienceLevel = experienceLevel,
                    industry = industry.trim(),
                    targetCompanies = targetCompanies.trim(),
                    resumeText = resumeText.trim(),
                    jobDescriptionText = jobDescriptionText.trim(),
                    onboardingCompleted = onboardingCompleted
                )
            )
        }
    }

    fun loadOrGenerateDailyChallenge() {
        viewModelScope.launch {
            val existing = todayChallenge.value
            if (existing != null) {
                _dailyUiState.value = DailyChallengeUiState.Ready(existing)
                return@launch
            }
            _dailyUiState.value = DailyChallengeUiState.Loading
            try {
                val p = repository.getProfileOnce()
                val prefs = repository.getPreferencesOnce()
                val dateKey = repository.todayDateKey()
                val generated = aiService.generateDailyChallenge(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.questionModel,
                    fallbackModel = prefs.fallbackModel,
                    role = p?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                    industry = p?.industry?.ifBlank { "Technology" } ?: "Technology",
                    dateKey = dateKey,
                    language = prefs.defaultLanguage
                )
                val entity = DailyChallengeEntity(
                    dateKey = dateKey,
                    question = generated.question,
                    topic = generated.topic,
                    hint = generated.hint
                )
                repository.saveDailyChallenge(entity)
                _dailyUiState.value = DailyChallengeUiState.Ready(entity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate daily challenge", e)
                _dailyUiState.value = DailyChallengeUiState.Error(
                    e.localizedMessage ?: "Unable to load today's challenge."
                )
            }
        }
    }

    fun submitDailyChallengeAnswer(challenge: DailyChallengeEntity, answer: String) {
        if (answer.isBlank()) return
        viewModelScope.launch {
            _dailyUiState.value = DailyChallengeUiState.Loading
            try {
                val prefs = repository.getPreferencesOnce()
                val eval = aiService.evaluateDailyChallenge(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.detailedModel,
                    fallbackModel = prefs.fallbackModel,
                    question = challenge.question,
                    answer = answer,
                    language = prefs.defaultLanguage
                )
                val updated = challenge.copy(
                    userAnswer = answer.trim(),
                    score = eval.score,
                    feedbackSummary = eval.summary,
                    idealAnswer = eval.idealAnswer,
                    completed = true
                )
                repository.saveDailyChallenge(updated)
                _dailyUiState.value = DailyChallengeUiState.Ready(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to score daily challenge", e)
                _dailyUiState.value = DailyChallengeUiState.Error(
                    e.localizedMessage ?: "Could not evaluate your answer."
                )
            }
        }
    }

    fun togglePrepTask(taskId: Long, completed: Boolean) {
        viewModelScope.launch { repository.togglePrepTask(taskId, completed) }
    }

    // Settings updates
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setAccentPalette(palette: AccentPalette) = viewModelScope.launch { repository.setAccentPalette(palette) }
    fun setGeminiApiKey(apiKey: String) = viewModelScope.launch {
        repository.setGeminiApiKey(apiKey)
        _connectionTestState.value = ConnectionTestState.Idle
    }
    fun setQuestionModel(model: String) = viewModelScope.launch { repository.setQuestionModel(model) }
    fun setDetailedModel(model: String) = viewModelScope.launch { repository.setDetailedModel(model) }
    fun setFallbackModel(model: String) = viewModelScope.launch { repository.setFallbackModel(model) }
    fun resetGeminiModelsToDefaults() = viewModelScope.launch {
        repository.resetGeminiModelsToDefaults()
        _settingsBanner.value = "Gemini models reset to defaults."
    }
    fun testGeminiConnection() {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val prefs = repository.getPreferencesOnce()
            val result = aiService.testConnection(
                apiKey = prefs.geminiApiKey,
                questionModel = prefs.questionModel,
                fallbackModel = prefs.fallbackModel
            )
            _connectionTestState.value = result.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Error(it.localizedMessage ?: "Connection failed.") }
            )
        }
    }

    fun setAiTonePreset(tone: AiTonePreset) = viewModelScope.launch { repository.setAiTonePreset(tone) }
    fun setCustomSystemPrompt(prompt: String) = viewModelScope.launch { repository.setCustomSystemPrompt(prompt) }
    fun setTtsSpeechRate(rate: Float) = viewModelScope.launch { repository.setTtsSpeechRate(rate) }
    fun setDefaultLanguage(language: String) = viewModelScope.launch { repository.setDefaultLanguage(language) }
    fun setDefaultAnswerTimeSec(sec: Int) = viewModelScope.launch { repository.setDefaultAnswerTimeSec(sec) }
    fun setReminder(title: String, targetEpochMs: Long) = viewModelScope.launch {
        repository.setReminder(title, targetEpochMs)
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            val result = repository.importBackupJson(rawJson)
            _settingsBanner.value = if (result.isSuccess) {
                "Backup imported successfully."
            } else {
                "Import failed: ${result.exceptionOrNull()?.localizedMessage ?: "Invalid JSON"}"
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            aiService.clearCache()
            _dailyUiState.value = DailyChallengeUiState.Idle
            _connectionTestState.value = ConnectionTestState.Idle
            _settingsBanner.value = "All local data cleared."
        }
    }

    fun clearSettingsBanner() {
        _settingsBanner.value = null
    }
}
