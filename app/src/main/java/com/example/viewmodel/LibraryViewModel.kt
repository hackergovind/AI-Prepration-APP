package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiQueueState
import com.example.ai.GeminiAiService
import com.example.data.local.CompanyBriefEntity
import com.example.data.local.PrepRepository
import com.example.data.local.SavedQuestionEntity
import com.example.utils.Sm2Quality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LibraryViewModel"

class LibraryViewModel(
    private val repository: PrepRepository,
    private val aiService: GeminiAiService
) : ViewModel() {

    val savedQuestions: StateFlow<List<SavedQuestionEntity>> = repository.savedQuestionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyBriefs: StateFlow<List<CompanyBriefEntity>> = repository.companyBriefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueState: StateFlow<AiQueueState> = aiService.queueState

    private val _isGeneratingBrief = MutableStateFlow(false)
    val isGeneratingBrief: StateFlow<Boolean> = _isGeneratingBrief.asStateFlow()

    private val _briefError = MutableStateFlow<String?>(null)
    val briefError: StateFlow<String?> = _briefError.asStateFlow()

    fun saveOrUpdateQuestion(
        id: Long = 0,
        question: String,
        topic: String,
        tags: String,
        personalNotes: String,
        bestAnswer: String,
        isBookmarked: Boolean = true,
        isFlashcard: Boolean = true
    ) {
        if (question.isBlank()) return
        viewModelScope.launch {
            val existing = savedQuestions.value.find { it.id == id }
            repository.saveQuestionToBank(
                (existing ?: SavedQuestionEntity(question = question.trim(), topic = topic.trim())).copy(
                    question = question.trim(),
                    topic = topic.ifBlank { "General" }.trim(),
                    tags = tags.trim(),
                    personalNotes = personalNotes.trim(),
                    bestAnswer = bestAnswer.trim(),
                    isBookmarked = isBookmarked,
                    isFlashcard = isFlashcard
                )
            )
        }
    }

    fun toggleBookmark(item: SavedQuestionEntity) {
        viewModelScope.launch {
            repository.saveQuestionToBank(item.copy(isBookmarked = !item.isBookmarked))
        }
    }

    fun deleteQuestion(id: Long) {
        viewModelScope.launch { repository.deleteSavedQuestion(id) }
    }

    fun gradeFlashcard(card: SavedQuestionEntity, quality: Sm2Quality) {
        viewModelScope.launch {
            repository.gradeFlashcard(card, quality)
        }
    }

    fun generateCompanyBrief(companyName: String, roleOverride: String) {
        if (companyName.isBlank()) return
        viewModelScope.launch {
            _isGeneratingBrief.value = true
            _briefError.value = null
            try {
                val prefs = repository.getPreferencesOnce()
                val profile = repository.getProfileOnce()
                val targetRole = roleOverride.ifBlank {
                    profile?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer"
                }
                val result = aiService.generateCompanyBrief(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.detailedModel,
                    fallbackModel = prefs.fallbackModel,
                    companyName = companyName.trim(),
                    targetRole = targetRole,
                    language = prefs.defaultLanguage
                )
                repository.saveCompanyBrief(
                    CompanyBriefEntity(
                        companyName = companyName.trim(),
                        targetRole = targetRole,
                        cultureOverview = result.cultureOverview,
                        keyValues = result.keyValues.joinToString("||"),
                        likelyQuestions = result.likelyQuestions.joinToString("||"),
                        preparationTips = result.preparationTips.joinToString("||")
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Company brief generation failed", e)
                _briefError.value = e.localizedMessage ?: "Unable to generate company brief."
            } finally {
                _isGeneratingBrief.value = false
            }
        }
    }

    fun deleteCompanyBrief(id: Long) {
        viewModelScope.launch { repository.deleteCompanyBrief(id) }
    }
}
