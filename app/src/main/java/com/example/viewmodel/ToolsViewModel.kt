package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiQueueState
import com.example.ai.CodingReviewResult
import com.example.ai.GeminiAiService
import com.example.data.local.PrepPlanTaskEntity
import com.example.data.local.PrepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ToolsViewModel"

data class NegotiationMessage(
    val isUser: Boolean,
    val text: String,
    val tacticTip: String? = null,
    val leverageScore: Int? = null
)

data class ToolsUiState(
    // Tell Me About Yourself
    val tmayDraft: String = "",
    val tmayResult: String = "",
    val tmayLoading: Boolean = false,
    // Salary Negotiation
    val negRole: String = "Senior Software Engineer",
    val negCompany: String = "",
    val negTargetComp: String = "$175k base + equity",
    val negMessages: List<NegotiationMessage> = emptyList(),
    val negLoading: Boolean = false,
    // Coding Round
    val codingLanguage: String = "Kotlin",
    val codingDifficulty: String = "Medium",
    val codingProblem: String = "",
    val codingEditorText: String = "fun solve(nums: IntArray): Int {\n    // Write your approach here\n    return 0\n}",
    val codingReview: CodingReviewResult? = null,
    val codingLoading: Boolean = false,
    // Prep Plan
    val prepPlanLoading: Boolean = false,
    val errorMessage: String? = null
)

class ToolsViewModel(
    private val repository: PrepRepository,
    private val aiService: GeminiAiService
) : ViewModel() {

    val prepPlanTasks: StateFlow<List<PrepPlanTaskEntity>> = repository.prepPlanTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueState: StateFlow<AiQueueState> = aiService.queueState

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repository.getProfileOnce()
            if (profile != null && profile.targetRole.isNotBlank()) {
                _uiState.value = _uiState.value.copy(
                    negRole = profile.targetRole,
                    negCompany = profile.targetCompanies.split(",").firstOrNull()?.trim().orEmpty()
                )
            }
        }
    }

    fun updateTmayDraft(text: String) {
        _uiState.value = _uiState.value.copy(tmayDraft = text)
    }

    fun generateOrRefineTmay(targetSeconds: Int, styleFocus: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tmayLoading = true, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val profile = repository.getProfileOnce()
                val output = aiService.refineTellMeAboutYourself(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.detailedModel,
                    fallbackModel = prefs.fallbackModel,
                    globalTone = prefs.effectiveGlobalTone(),
                    profile = profile,
                    draftPitch = _uiState.value.tmayDraft,
                    targetLengthSec = targetSeconds,
                    styleFocus = styleFocus,
                    language = prefs.defaultLanguage
                )
                _uiState.value = _uiState.value.copy(tmayLoading = false, tmayResult = output)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TMAY refiner failed", e)
                _uiState.value = _uiState.value.copy(
                    tmayLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to generate pitch."
                )
            }
        }
    }

    fun updateNegotiationContext(role: String, company: String, targetComp: String) {
        _uiState.value = _uiState.value.copy(negRole = role, negCompany = company, negTargetComp = targetComp)
    }

    fun startNegotiationSimulation() {
        val s = _uiState.value
        val starter = NegotiationMessage(
            isUser = false,
            text = "We're excited to extend you an offer for the ${s.negRole} role! Before we finalize the paperwork, what are your thoughts on the compensation package?",
            tacticTip = "Anchor calmly on market value and impact before sharing a hard floor.",
            leverageScore = 6
        )
        _uiState.value = s.copy(negMessages = listOf(starter), errorMessage = null)
    }

    fun sendNegotiationMessage(userText: String) {
        if (userText.isBlank()) return
        val current = _uiState.value
        val withUser = current.negMessages + NegotiationMessage(isUser = true, text = userText.trim())
        _uiState.value = current.copy(negMessages = withUser, negLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val prefs = repository.getPreferencesOnce()
                val history = current.negMessages.map { (if (it.isUser) "user" else "model") to it.text }
                val res = aiService.sendNegotiationTurn(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.questionModel,
                    fallbackModel = prefs.fallbackModel,
                    role = current.negRole,
                    company = current.negCompany,
                    targetComp = current.negTargetComp,
                    conversationHistory = history,
                    userMessage = userText.trim()
                )
                _uiState.value = _uiState.value.copy(
                    negLoading = false,
                    negMessages = withUser + NegotiationMessage(
                        isUser = false,
                        text = res.recruiterReply,
                        tacticTip = res.tacticTip,
                        leverageScore = res.leverageScore
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Negotiation turn failed", e)
                _uiState.value = _uiState.value.copy(
                    negLoading = false,
                    errorMessage = e.localizedMessage ?: "Negotiation turn failed."
                )
            }
        }
    }

    fun updateCodingSettings(language: String, difficulty: String) {
        _uiState.value = _uiState.value.copy(codingLanguage = language, codingDifficulty = difficulty)
    }

    fun updateCodeEditor(code: String) {
        _uiState.value = _uiState.value.copy(codingEditorText = code)
    }

    fun generateCodingProblem() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(codingLoading = true, codingReview = null, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val profile = repository.getProfileOnce()
                val problem = aiService.generateCodingProblem(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.questionModel,
                    fallbackModel = prefs.fallbackModel,
                    role = profile?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                    difficulty = _uiState.value.codingDifficulty,
                    language = _uiState.value.codingLanguage
                )
                _uiState.value = _uiState.value.copy(codingLoading = false, codingProblem = problem)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coding problem failed", e)
                _uiState.value = _uiState.value.copy(
                    codingLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to generate coding problem."
                )
            }
        }
    }

    fun submitCodeForReview() {
        val state = _uiState.value
        if (state.codingEditorText.isBlank()) return
        viewModelScope.launch {
            _uiState.value = state.copy(codingLoading = true, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val review = aiService.reviewCodeSolution(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.detailedModel,
                    fallbackModel = prefs.fallbackModel,
                    problem = state.codingProblem.ifBlank { "General algorithmic whiteboard solution" },
                    programmingLanguage = state.codingLanguage,
                    code = state.codingEditorText
                )
                _uiState.value = _uiState.value.copy(codingLoading = false, codingReview = review)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Code review failed", e)
                _uiState.value = _uiState.value.copy(
                    codingLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to review code."
                )
            }
        }
    }

    fun generatePrepPlan(days: Int, weakTopics: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(prepPlanLoading = true, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val profile = repository.getProfileOnce()
                val generated = aiService.generatePrepPlan(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.detailedModel,
                    fallbackModel = prefs.fallbackModel,
                    days = days,
                    profile = profile,
                    weakTopics = weakTopics,
                    language = prefs.defaultLanguage
                )
                val entities = generated.map {
                    PrepPlanTaskEntity(
                        planDays = days,
                        dayNumber = it.dayNumber,
                        title = it.title,
                        description = it.description,
                        focusArea = it.focusArea
                    )
                }
                repository.replacePrepPlan(entities)
                _uiState.value = _uiState.value.copy(prepPlanLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prep plan failed", e)
                _uiState.value = _uiState.value.copy(
                    prepPlanLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to generate prep plan."
                )
            }
        }
    }

    fun togglePrepTask(taskId: Long, completed: Boolean) {
        viewModelScope.launch { repository.togglePrepTask(taskId, completed) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
