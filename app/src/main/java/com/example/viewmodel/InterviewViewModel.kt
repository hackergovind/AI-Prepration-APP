package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiQueueState
import com.example.ai.GeminiAiService
import com.example.ai.GeneratedQuestionResult
import com.example.data.local.InterviewPresetEntity
import com.example.data.local.InterviewSessionEntity
import com.example.data.local.PrepRepository
import com.example.data.local.SavedQuestionEntity
import com.example.data.local.SessionTurnEntity
import com.example.utils.SpeechAnalytics
import com.example.utils.SpeechHelper
import com.example.utils.VoiceInputState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "InterviewViewModel"

enum class InterviewPersona(val label: String, val systemPrompt: String) {
    FRIENDLY_HR(
        "Friendly HR",
        "You are a warm, approachable HR specialist exploring culture fit, communication, and motivation."
    ),
    STRICT_TECH_LEAD(
        "Strict Tech Lead",
        "You are a demanding senior engineering lead who probes deeply into trade-offs, edge cases, and precision."
    ),
    STRESS_INTERVIEWER(
        "Stress Interviewer",
        "You are a brisk, challenging high-bar interviewer who tests composure under pressure with sharp follow-ups."
    ),
    STARTUP_FOUNDER(
        "Startup Founder",
        "You are a fast-moving startup founder looking for ownership, scrappiness, product sense, and velocity."
    ),
    PANEL(
        "Panel",
        "You represent a cross-functional hiring panel balancing technical depth, product judgment, and collaboration."
    ),
    CUSTOM(
        "Custom Persona",
        ""
    )
}

data class InterviewBuilderConfig(
    val role: String = "Software Engineer",
    val company: String = "",
    val seniority: String = "Mid-Level",
    val interviewType: String = "Mixed",
    val difficulty: String = "Medium",
    val questionCount: Int = 5,
    val timeLimitSec: Int = 120,
    val language: String = "English",
    val persona: InterviewPersona = InterviewPersona.FRIENDLY_HR,
    val customPersonaPrompt: String = "",
    val useResumeAndJd: Boolean = true,
    val practiceMode: Boolean = true,
    val focusTopics: List<String> = emptyList()
)

data class LiveInterviewState(
    val sessionId: Long? = null,
    val config: InterviewBuilderConfig = InterviewBuilderConfig(),
    val currentTurnIndex: Int = 0,
    val currentQuestion: String = "",
    val currentTopic: String = "",
    val currentIsFollowUp: Boolean = false,
    val currentIsBehavioral: Boolean = false,
    val answerDraft: String = "",
    val remainingSeconds: Int = 120,
    val isVoiceMode: Boolean = false,
    val hintText: String? = null,
    val isLoadingHint: Boolean = false,
    val isGeneratingQuestion: Boolean = false,
    val isEvaluatingAnswer: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val latestFeedbackTurn: SessionTurnEntity? = null,
    val prefetchedNextQuestion: GeneratedQuestionResult? = null,
    val completedTurns: List<SessionTurnEntity> = emptyList(),
    val finalSession: InterviewSessionEntity? = null,
    val errorMessage: String? = null,
    val savedBookmarkMessage: String? = null
)

class InterviewViewModel(
    private val repository: PrepRepository,
    private val aiService: GeminiAiService,
    private val speechHelper: SpeechHelper
) : ViewModel() {

    val presets: StateFlow<List<InterviewPresetEntity>> = repository.presetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueState: StateFlow<AiQueueState> = aiService.queueState
    val voiceState: StateFlow<VoiceInputState> = speechHelper.state

    private val _builderConfig = MutableStateFlow(InterviewBuilderConfig())
    val builderConfig: StateFlow<InterviewBuilderConfig> = _builderConfig.asStateFlow()

    private val _liveState = MutableStateFlow(LiveInterviewState())
    val liveState: StateFlow<LiveInterviewState> = _liveState.asStateFlow()

    private var timerJob: Job? = null
    private var questionStartedAtMs: Long = 0L

    init {
        viewModelScope.launch {
            val profile = repository.getProfileOnce()
            val prefs = repository.getPreferencesOnce()
            _builderConfig.value = _builderConfig.value.copy(
                role = profile?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                seniority = profile?.experienceLevel ?: "Mid-Level",
                language = prefs.defaultLanguage,
                timeLimitSec = prefs.defaultAnswerTimeSec
            )
        }
    }

    fun updateConfig(transform: (InterviewBuilderConfig) -> InterviewBuilderConfig) {
        _builderConfig.value = transform(_builderConfig.value)
    }

    fun savePreset(title: String) {
        if (title.isBlank()) return
        val cfg = _builderConfig.value
        viewModelScope.launch {
            repository.savePreset(
                InterviewPresetEntity(
                    title = title.trim(),
                    role = cfg.role,
                    company = cfg.company,
                    seniority = cfg.seniority,
                    interviewType = cfg.interviewType,
                    difficulty = cfg.difficulty,
                    questionCount = cfg.questionCount,
                    timeLimitSec = cfg.timeLimitSec,
                    language = cfg.language,
                    persona = cfg.persona.label,
                    customPersonaPrompt = cfg.customPersonaPrompt,
                    useResumeAndJd = cfg.useResumeAndJd,
                    practiceMode = cfg.practiceMode
                )
            )
        }
    }

    fun loadPreset(preset: InterviewPresetEntity) {
        val persona = InterviewPersona.entries.find { it.label == preset.persona }
            ?: InterviewPersona.FRIENDLY_HR
        _builderConfig.value = InterviewBuilderConfig(
            role = preset.role,
            company = preset.company,
            seniority = preset.seniority,
            interviewType = preset.interviewType,
            difficulty = preset.difficulty,
            questionCount = preset.questionCount,
            timeLimitSec = preset.timeLimitSec,
            language = preset.language,
            persona = persona,
            customPersonaPrompt = preset.customPersonaPrompt,
            useResumeAndJd = preset.useResumeAndJd,
            practiceMode = preset.practiceMode
        )
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    fun startNewSession(
        overrideConfig: InterviewBuilderConfig? = null,
        initialCustomQuestion: String? = null,
        initialTopic: String? = null,
        startInVoiceMode: Boolean = false
    ) {
        val cfg = overrideConfig ?: _builderConfig.value
        _builderConfig.value = cfg
        timerJob?.cancel()
        speechHelper.stopListening()
        speechHelper.stopSpeaking()
        speechHelper.resetTranscript()

        viewModelScope.launch {
            val sessionId = repository.createSession(
                InterviewSessionEntity(
                    role = cfg.role.ifBlank { "Software Engineer" },
                    company = cfg.company,
                    seniority = cfg.seniority,
                    interviewType = cfg.interviewType,
                    difficulty = cfg.difficulty,
                    persona = cfg.persona.label,
                    mode = if (cfg.practiceMode) "Practice" else "Real",
                    questionCount = cfg.questionCount
                )
            )
            _liveState.value = LiveInterviewState(
                sessionId = sessionId,
                config = cfg,
                currentTurnIndex = 0,
                remainingSeconds = cfg.timeLimitSec,
                isVoiceMode = startInVoiceMode,
                isGeneratingQuestion = initialCustomQuestion == null
            )

            if (initialCustomQuestion != null) {
                presentQuestion(
                    question = initialCustomQuestion,
                    topic = initialTopic ?: cfg.interviewType,
                    isFollowUp = false,
                    isBehavioral = cfg.interviewType.equals("Behavioral", true)
                )
            } else {
                fetchNextQuestion()
            }
        }
    }

    fun startWeaknessDrill(weakTopics: List<String>) {
        val current = _builderConfig.value
        val drillConfig = current.copy(
            interviewType = "Mixed",
            difficulty = "Adaptive",
            questionCount = 4,
            practiceMode = true,
            focusTopics = weakTopics
        )
        startNewSession(overrideConfig = drillConfig)
    }

    private suspend fun fetchNextQuestion() {
        val state = _liveState.value
        val prefetched = state.prefetchedNextQuestion
        if (prefetched != null) {
            _liveState.value = state.copy(prefetchedNextQuestion = null)
            presentQuestion(
                question = prefetched.question,
                topic = prefetched.topic,
                isFollowUp = prefetched.isFollowUp,
                isBehavioral = prefetched.isBehavioral
            )
            return
        }

        val cfg = state.config
        _liveState.value = state.copy(
            isGeneratingQuestion = true,
            errorMessage = null,
            hintText = null,
            latestFeedbackTurn = null
        )
        try {
            val prefs = repository.getPreferencesOnce()
            val profile = repository.getProfileOnce()
            val historySummary = state.completedTurns.takeLast(3).joinToString("\n") { t ->
                "Q(${t.topic}): ${t.question}\nA: ${t.userAnswer.take(300)}"
            }
            val personaPrompt = if (cfg.persona == InterviewPersona.CUSTOM && cfg.customPersonaPrompt.isNotBlank()) {
                cfg.customPersonaPrompt
            } else {
                cfg.persona.systemPrompt
            }
            val generated = aiService.generateInterviewQuestion(
                apiKey = prefs.geminiApiKey,
                modelId = prefs.questionModel,
                fallbackModel = prefs.fallbackModel,
                globalTone = prefs.effectiveGlobalTone(),
                role = cfg.role,
                company = cfg.company,
                seniority = cfg.seniority,
                interviewType = cfg.interviewType,
                difficulty = cfg.difficulty,
                language = cfg.language,
                personaPrompt = personaPrompt,
                profile = profile,
                useResumeAndJd = cfg.useResumeAndJd,
                previousHistorySummary = historySummary,
                questionIndex = state.currentTurnIndex,
                totalQuestions = cfg.questionCount,
                focusTopics = cfg.focusTopics
            )
            presentQuestion(
                question = generated.question,
                topic = generated.topic,
                isFollowUp = generated.isFollowUp,
                isBehavioral = generated.isBehavioral
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Question generation failed", e)
            _liveState.value = _liveState.value.copy(
                isGeneratingQuestion = false,
                errorMessage = e.localizedMessage ?: "Failed to generate interview question."
            )
        }
    }

    private suspend fun presentQuestion(
        question: String,
        topic: String,
        isFollowUp: Boolean,
        isBehavioral: Boolean
    ) {
        val cfg = _liveState.value.config
        speechHelper.resetTranscript()
        questionStartedAtMs = System.currentTimeMillis()
        _liveState.value = _liveState.value.copy(
            isGeneratingQuestion = false,
            currentQuestion = question,
            currentTopic = topic,
            currentIsFollowUp = isFollowUp,
            currentIsBehavioral = isBehavioral,
            answerDraft = "",
            remainingSeconds = cfg.timeLimitSec,
            hintText = null,
            latestFeedbackTurn = null,
            errorMessage = null
        )
        startCountdownTimer()
        if (_liveState.value.isVoiceMode) {
            val prefs = repository.getPreferencesOnce()
            speechHelper.speak(question, prefs.ttsSpeechRate, cfg.language)
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_liveState.value.remainingSeconds > 0) {
                delay(1000L)
                _liveState.value = _liveState.value.copy(
                    remainingSeconds = (_liveState.value.remainingSeconds - 1).coerceAtLeast(0)
                )
            }
        }
    }

    fun updateAnswerDraft(text: String) {
        _liveState.value = _liveState.value.copy(answerDraft = text)
    }

    fun toggleVoiceMode(enabled: Boolean) {
        if (!enabled) {
            speechHelper.stopListening()
            speechHelper.stopSpeaking()
            val merged = mergeVoiceIntoDraft(_liveState.value.answerDraft, voiceState.value)
            _liveState.value = _liveState.value.copy(
                isVoiceMode = false,
                answerDraft = merged
            )
        } else {
            _liveState.value = _liveState.value.copy(isVoiceMode = true)
            val currentQ = _liveState.value.currentQuestion
            if (currentQ.isNotBlank()) {
                viewModelScope.launch {
                    val prefs = repository.getPreferencesOnce()
                    speechHelper.speak(currentQ, prefs.ttsSpeechRate, _liveState.value.config.language)
                }
            }
        }
    }

    fun startVoiceCapture() {
        val currentPrefix = mergeVoiceIntoDraft(_liveState.value.answerDraft, voiceState.value)
        _liveState.value = _liveState.value.copy(answerDraft = currentPrefix)
        speechHelper.startListening(
            languageName = _liveState.value.config.language,
            existingPrefix = currentPrefix
        )
    }

    fun stopVoiceCapture() {
        speechHelper.stopListening()
        val merged = mergeVoiceIntoDraft(_liveState.value.answerDraft, voiceState.value)
        if (merged.isNotBlank()) {
            _liveState.value = _liveState.value.copy(answerDraft = merged)
        }
    }

    private fun mergeVoiceIntoDraft(draft: String, voice: VoiceInputState): String {
        val spoken = listOf(voice.finalizedTranscript, voice.livePartialTranscript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return when {
            spoken.isBlank() -> draft.trim()
            draft.isBlank() -> spoken
            spoken.startsWith(draft.trim()) -> spoken
            else -> "${draft.trim()} $spoken".trim()
        }
    }

    fun speakCurrentQuestion() {
        viewModelScope.launch {
            val prefs = repository.getPreferencesOnce()
            speechHelper.speak(
                _liveState.value.currentQuestion,
                prefs.ttsSpeechRate,
                _liveState.value.config.language
            )
        }
    }

    fun requestHint() {
        val state = _liveState.value
        if (!state.config.practiceMode || state.isLoadingHint || state.currentQuestion.isBlank()) return
        viewModelScope.launch {
            _liveState.value = _liveState.value.copy(isLoadingHint = true, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val hint = aiService.generateHint(
                    apiKey = prefs.geminiApiKey,
                    modelId = prefs.questionModel,
                    fallbackModel = prefs.fallbackModel,
                    question = state.currentQuestion,
                    role = state.config.role,
                    language = state.config.language
                )
                _liveState.value = _liveState.value.copy(isLoadingHint = false, hintText = hint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hint generation failed", e)
                _liveState.value = _liveState.value.copy(
                    isLoadingHint = false,
                    errorMessage = e.localizedMessage ?: "Failed to load hint."
                )
            }
        }
    }

    fun submitCurrentAnswer(skipped: Boolean = false) {
        timerJob?.cancel()
        speechHelper.stopListening()
        speechHelper.stopSpeaking()

        val state = _liveState.value
        val sessionId = state.sessionId ?: return
        val voice = voiceState.value
        val combinedDraft = mergeVoiceIntoDraft(state.answerDraft, voice)

        if (!skipped && combinedDraft.isBlank()) {
            _liveState.value = state.copy(errorMessage = "Enter or speak an answer before submitting, or tap Skip.")
            return
        }

        val elapsedSec = ((System.currentTimeMillis() - questionStartedAtMs) / 1000L).toInt().coerceAtLeast(1)
        val speechMetrics = if (state.isVoiceMode && !skipped) {
            SpeechAnalytics.analyze(
                transcript = combinedDraft,
                durationSeconds = elapsedSec,
                longPauseCount = voice.longPauseCount
            )
        } else {
            null
        }

        viewModelScope.launch {
            _liveState.value = _liveState.value.copy(isEvaluatingAnswer = true, errorMessage = null)
            try {
                val prefs = repository.getPreferencesOnce()
                val isLastQuestion = (state.currentTurnIndex + 1) >= state.config.questionCount
                val personaPrompt = if (state.config.persona == InterviewPersona.CUSTOM && state.config.customPersonaPrompt.isNotBlank()) {
                    state.config.customPersonaPrompt
                } else {
                    state.config.persona.systemPrompt
                }

                // Combined single request for detailed feedback + next adaptive question to save free-tier quota
                val combined = if (!skipped) {
                    aiService.evaluateAnswerAndNextQuestion(
                        apiKey = prefs.geminiApiKey,
                        modelId = prefs.detailedModel,
                        fallbackModel = prefs.fallbackModel,
                        globalTone = prefs.effectiveGlobalTone(),
                        question = state.currentQuestion,
                        userAnswer = combinedDraft,
                        role = state.config.role,
                        company = state.config.company,
                        seniority = state.config.seniority,
                        interviewType = state.config.interviewType,
                        difficulty = state.config.difficulty,
                        personaPrompt = personaPrompt,
                        isBehavioral = state.currentIsBehavioral,
                        language = state.config.language,
                        includeNextQuestion = !isLastQuestion,
                        nextQuestionIndex = state.currentTurnIndex + 1,
                        totalQuestions = state.config.questionCount,
                        focusTopics = state.config.focusTopics
                    )
                } else {
                    null
                }
                val feedback = combined?.feedback

                val turn = SessionTurnEntity(
                    sessionId = sessionId,
                    turnIndex = state.currentTurnIndex,
                    question = state.currentQuestion,
                    topic = state.currentTopic,
                    isFollowUp = state.currentIsFollowUp,
                    userAnswer = if (skipped) "" else combinedDraft,
                    skipped = skipped,
                    clarityScore = feedback?.clarityScore ?: 0,
                    structureScore = feedback?.structureScore ?: 0,
                    relevanceScore = feedback?.relevanceScore ?: 0,
                    depthScore = feedback?.depthScore ?: 0,
                    confidenceScore = feedback?.confidenceScore ?: 0,
                    strengths = feedback?.strengths?.joinToString("||").orEmpty(),
                    weaknesses = feedback?.weaknesses?.joinToString("||").orEmpty(),
                    idealAnswer = feedback?.idealAnswer.orEmpty(),
                    tips = feedback?.tips?.joinToString("||").orEmpty(),
                    isBehavioral = state.currentIsBehavioral,
                    starSituation = feedback?.starSituation ?: false,
                    starTask = feedback?.starTask ?: false,
                    starAction = feedback?.starAction ?: false,
                    starResult = feedback?.starResult ?: false,
                    starFeedback = feedback?.starFeedback.orEmpty(),
                    wpm = speechMetrics?.wordsPerMinute ?: 0,
                    fillerCount = speechMetrics?.fillerWordCount ?: 0,
                    longPauseCount = speechMetrics?.longPauseCount ?: 0
                )
                val insertedId = repository.insertTurn(turn)
                val savedTurn = turn.copy(id = insertedId)
                val updatedTurns = state.completedTurns + savedTurn

                _liveState.value = _liveState.value.copy(
                    isEvaluatingAnswer = false,
                    completedTurns = updatedTurns,
                    latestFeedbackTurn = if (state.config.practiceMode && !skipped) savedTurn else null,
                    prefetchedNextQuestion = if (!isLastQuestion) combined?.nextQuestion else null
                )

                if (!state.config.practiceMode || skipped) {
                    if (isLastQuestion) {
                        finalizeSessionReport()
                    } else {
                        _liveState.value = _liveState.value.copy(currentTurnIndex = state.currentTurnIndex + 1)
                        fetchNextQuestion()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Answer evaluation failed", e)
                _liveState.value = _liveState.value.copy(
                    isEvaluatingAnswer = false,
                    errorMessage = e.localizedMessage ?: "Failed to evaluate your answer."
                )
            }
        }
    }

    fun proceedAfterFeedback() {
        val state = _liveState.value
        val isLastQuestion = (state.currentTurnIndex + 1) >= state.config.questionCount
        viewModelScope.launch {
            if (isLastQuestion) {
                finalizeSessionReport()
            } else {
                _liveState.value = state.copy(
                    currentTurnIndex = state.currentTurnIndex + 1,
                    latestFeedbackTurn = null
                )
                fetchNextQuestion()
            }
        }
    }

    fun finishEarlyAndReport() {
        timerJob?.cancel()
        speechHelper.stopListening()
        speechHelper.stopSpeaking()
        viewModelScope.launch { finalizeSessionReport() }
    }

    private suspend fun finalizeSessionReport() {
        val state = _liveState.value
        val sessionId = state.sessionId ?: return
        val turns = state.completedTurns
        if (turns.isEmpty()) {
            _liveState.value = state.copy(errorMessage = "Answer at least one question to generate a session report.")
            return
        }

        _liveState.value = state.copy(isGeneratingReport = true, errorMessage = null)
        try {
            val prefs = repository.getPreferencesOnce()
            val transcript = turns.joinToString("\n\n") { t ->
                val avg = (t.clarityScore + t.structureScore + t.relevanceScore + t.depthScore + t.confidenceScore) / 5.0
                "Q: ${t.question}\nTopic: ${t.topic}\nAnswer: ${t.userAnswer.ifBlank { "(Skipped)" }}\nTurn Avg: $avg"
            }
            val summary = aiService.generateSessionReport(
                apiKey = prefs.geminiApiKey,
                modelId = prefs.detailedModel,
                fallbackModel = prefs.fallbackModel,
                role = state.config.role,
                turnsTranscript = transcript,
                language = state.config.language
            )
            val existingSession = repository.getSessionById(sessionId) ?: return
            val completedSession = existingSession.copy(
                overallScore = summary.overallScore,
                topStrengths = summary.topStrengths.joinToString("||"),
                topImprovements = summary.topImprovements.joinToString("||"),
                nextPracticePlan = summary.nextPracticePlan,
                completed = true
            )
            repository.updateSession(completedSession)
            _liveState.value = _liveState.value.copy(
                isGeneratingReport = false,
                finalSession = completedSession
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Final report failed", e)
            _liveState.value = _liveState.value.copy(
                isGeneratingReport = false,
                errorMessage = e.localizedMessage ?: "Could not generate session report."
            )
        }
    }

    fun retryLastQuestionOrReport() {
        val state = _liveState.value
        viewModelScope.launch {
            if (state.currentQuestion.isBlank()) {
                fetchNextQuestion()
            } else if (state.completedTurns.size >= state.config.questionCount) {
                finalizeSessionReport()
            }
        }
    }

    fun bookmarkTurnToLibrary(turn: SessionTurnEntity) {
        viewModelScope.launch {
            repository.saveQuestionToBank(
                SavedQuestionEntity(
                    question = turn.question,
                    topic = turn.topic,
                    tags = _liveState.value.config.interviewType,
                    bestAnswer = turn.idealAnswer.ifBlank { turn.userAnswer },
                    isBookmarked = true,
                    isFlashcard = true
                )
            )
            _liveState.value = _liveState.value.copy(savedBookmarkMessage = "Saved to Library & Flashcards")
        }
    }

    fun clearBookmarkMessage() {
        _liveState.value = _liveState.value.copy(savedBookmarkMessage = null)
    }

    fun closeSession() {
        timerJob?.cancel()
        speechHelper.stopListening()
        speechHelper.stopSpeaking()
        _liveState.value = LiveInterviewState()
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
