package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.DefaultGeminiModels
import com.example.data.local.UserProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "GeminiAiService"
private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val PLACEHOLDER_GEMINI_KEY = "MY_GEMINI_API_KEY"
private const val AI_TIMEOUT_MS = 60_000L
private const val MIN_REQUEST_GAP_MS = 1_100L

data class AiQueueState(
    val isBusy: Boolean = false,
    val isRateLimited: Boolean = false,
    val statusBanner: String? = null
)

data class GeneratedQuestionResult(
    val question: String,
    val topic: String,
    val isFollowUp: Boolean,
    val isBehavioral: Boolean
)

data class AnswerFeedbackResult(
    val clarityScore: Int,
    val structureScore: Int,
    val relevanceScore: Int,
    val depthScore: Int,
    val confidenceScore: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val idealAnswer: String,
    val tips: List<String>,
    val starSituation: Boolean,
    val starTask: Boolean,
    val starAction: Boolean,
    val starResult: Boolean,
    val starFeedback: String
)

data class CombinedFeedbackAndNextResult(
    val feedback: AnswerFeedbackResult,
    val nextQuestion: GeneratedQuestionResult?
)

data class SessionSummaryResult(
    val overallScore: Double,
    val topStrengths: List<String>,
    val topImprovements: List<String>,
    val nextPracticePlan: String
)

data class CompanyBriefResult(
    val cultureOverview: String,
    val keyValues: List<String>,
    val likelyQuestions: List<String>,
    val preparationTips: List<String>
)

data class CodingReviewResult(
    val correctnessScore: Int,
    val timeComplexity: String,
    val spaceComplexity: String,
    val edgeCases: List<String>,
    val improvements: List<String>,
    val refinedCode: String
)

data class PrepPlanTaskGenerated(
    val dayNumber: Int,
    val title: String,
    val description: String,
    val focusArea: String
)

data class DailyChallengeGenerated(
    val question: String,
    val topic: String,
    val hint: String
)

data class DailyChallengeFeedback(
    val score: Int,
    val summary: String,
    val idealAnswer: String
)

data class NegotiationTurnResult(
    val recruiterReply: String,
    val tacticTip: String,
    val leverageScore: Int
)

private data class GeminiChatTurn(
    val role: String,
    val text: String
)

private class GeminiHttpException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/**
 * Single centralized Gemini REST AI service for PrepPilot AI.
 * - Queues all requests sequentially via Mutex (no parallel AI calls).
 * - Retries 429 / quota / network failures with exponential backoff + jitter.
 * - Automatically switches to the configured backup Gemini model on failure and notifies UI.
 * - Requests JSON output with safe parsing + automatic single retry on invalid JSON.
 * - Caches questions, company briefs, and prep plans locally to preserve free-tier quota.
 * - Never logs or exposes the user's Gemini API key.
 */
class GeminiAiService {

    private val requestMutex = Mutex()
    private var lastRequestCompletedAt = 0L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(55, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _queueState = MutableStateFlow(AiQueueState())
    val queueState: StateFlow<AiQueueState> = _queueState.asStateFlow()

    private val _backupModelEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val backupModelEvents: SharedFlow<String> = _backupModelEvents.asSharedFlow()

    // In-memory quota-saving caches
    private val questionCache = ConcurrentHashMap<String, GeneratedQuestionResult>()
    private val companyBriefCache = ConcurrentHashMap<String, CompanyBriefResult>()
    private val prepPlanCache = ConcurrentHashMap<String, List<PrepPlanTaskGenerated>>()

    fun resolveEffectiveApiKey(userEnteredKey: String): String {
        val local = userEnteredKey.trim()
        if (local.isNotEmpty()) return local
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildKey.isNotEmpty() && buildKey != PLACEHOLDER_GEMINI_KEY) buildKey else ""
    }

    fun hasBuildSecretKey(): Boolean {
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return buildKey.isNotEmpty() && buildKey != PLACEHOLDER_GEMINI_KEY
    }

    fun clearCache() {
        questionCache.clear()
        companyBriefCache.clear()
        prepPlanCache.clear()
    }

    private fun sanitize(message: String, apiKey: String): String {
        if (apiKey.isBlank()) return message
        return message.replace(apiKey, "[REDACTED]")
    }

    private fun isRetryableOrFallbackError(error: Throwable): Boolean {
        if (error is IOException) return true
        if (error is GeminiHttpException) {
            return error.statusCode == 429 ||
                error.statusCode in 500..599 ||
                error.statusCode == 404
        }
        val msg = error.message.orEmpty()
        return msg.contains("429") ||
            msg.contains("quota", ignoreCase = true) ||
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("resource_exhausted", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true)
    }

    private suspend fun callGeminiHttp(
        apiKey: String,
        modelId: String,
        systemInstruction: String?,
        turns: List<GeminiChatTurn>,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = resolveEffectiveApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IllegalStateException("Gemini API key is missing. Add your key in Settings or the AI Studio Secrets panel.")
        }
        val safeModel = modelId.trim().ifBlank { DefaultGeminiModels.QUESTION_MODEL }
        val url = "$GEMINI_API_BASE/$safeModel:generateContent"

        val contentsArray = JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject().apply {
                        put("role", turn.role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", turn.text) })
                        })
                    }
                )
            }
        }

        val payload = JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                if (expectJson) {
                    put("responseMimeType", "application/json")
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", cleanKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiError = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    val cleanDetail = sanitize(apiError.ifBlank { "HTTP ${response.code}" }, cleanKey)
                    throw GeminiHttpException(
                        statusCode = response.code,
                        message = "Gemini API error (${response.code}): $cleanDetail"
                    )
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                    ?: throw IllegalStateException("Gemini returned no candidates.")
                if (candidates.length() == 0) {
                    throw IllegalStateException("Gemini returned an empty candidate list.")
                }
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: throw IllegalStateException("Gemini returned no text content.")
                val combined = buildString {
                    for (i in 0 until parts.length()) {
                        append(parts.getJSONObject(i).optString("text"))
                    }
                }.trim()
                if (combined.isBlank()) {
                    throw IllegalStateException("Gemini returned blank text.")
                }
                combined
            }
        } catch (e: GeminiHttpException) {
            throw e
        } catch (e: IOException) {
            throw IOException(sanitize(e.message ?: "Network error contacting Gemini API.", cleanKey), e)
        } catch (e: Exception) {
            throw IllegalStateException(sanitize(e.message ?: "Gemini request failed.", cleanKey), e)
        }
    }

    private suspend fun <T> executeQueued(
        actionLabel: String,
        apiKey: String,
        primaryModel: String,
        fallbackModel: String,
        block: suspend (activeModel: String) -> T
    ): T {
        val cleanKey = resolveEffectiveApiKey(apiKey)
        if (cleanKey.isEmpty()) {
            throw IllegalStateException("Gemini API key is missing. Open Settings to paste your Google AI Studio API key.")
        }

        return requestMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestCompletedAt
            if (lastRequestCompletedAt > 0L && elapsed < MIN_REQUEST_GAP_MS) {
                val waitMs = MIN_REQUEST_GAP_MS - elapsed
                _queueState.value = AiQueueState(
                    isBusy = true,
                    isRateLimited = true,
                    statusBanner = "Pacing Gemini requests to stay within free-tier quota..."
                )
                delay(waitMs)
            } else {
                _queueState.value = AiQueueState(isBusy = true)
            }

            val resolvedPrimary = primaryModel.trim().ifBlank { DefaultGeminiModels.QUESTION_MODEL }
            val resolvedFallback = fallbackModel.trim().ifBlank { DefaultGeminiModels.FALLBACK_MODEL }

            var lastError: Throwable? = null
            // Exponential backoff with jitter on primary model
            for (attempt in 0..2) {
                try {
                    val result = withTimeout(AI_TIMEOUT_MS) { block(resolvedPrimary) }
                    lastRequestCompletedAt = System.currentTimeMillis()
                    _queueState.value = AiQueueState()
                    return@withLock result
                } catch (e: CancellationException) {
                    _queueState.value = AiQueueState()
                    throw e
                } catch (e: TimeoutCancellationException) {
                    lastError = IllegalStateException("$actionLabel timed out after 60s.")
                    break
                } catch (e: Throwable) {
                    lastError = e
                    if (e is GeminiHttpException && (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403)) {
                        Log.e(TAG, "$actionLabel auth/request error (${e.statusCode})")
                        break
                    }
                    if (isRetryableOrFallbackError(e) && attempt < 2) {
                        val backoffMs = (1000L * (1 shl attempt)) + Random.nextLong(150L, 650L)
                        _queueState.value = AiQueueState(
                            isBusy = true,
                            isRateLimited = true,
                            statusBanner = "Rate-limited or network hiccup — retrying shortly..."
                        )
                        delay(backoffMs)
                    } else {
                        break
                    }
                }
            }

            // Automatic switch to backup model on 429 / quota / rate-limit / network / 404 errors
            val capturedError = lastError
            val canSwitchToBackup = capturedError != null &&
                isRetryableOrFallbackError(capturedError) &&
                resolvedFallback.isNotBlank() &&
                resolvedFallback != resolvedPrimary

            if (canSwitchToBackup) {
                _backupModelEvents.tryEmit("Switched to backup model")
                _queueState.value = AiQueueState(
                    isBusy = true,
                    isRateLimited = true,
                    statusBanner = "Switched to backup model ($resolvedFallback)..."
                )
                for (backupAttempt in 0..1) {
                    try {
                        val result = withTimeout(AI_TIMEOUT_MS) { block(resolvedFallback) }
                        lastRequestCompletedAt = System.currentTimeMillis()
                        _queueState.value = AiQueueState()
                        return@withLock result
                    } catch (e: CancellationException) {
                        _queueState.value = AiQueueState()
                        throw e
                    } catch (e: Throwable) {
                        lastError = e
                        if (isRetryableOrFallbackError(e) && backupAttempt == 0) {
                            delay(1000L + Random.nextLong(150L, 500L))
                        } else {
                            break
                        }
                    }
                }
            }

            _queueState.value = AiQueueState()
            val safeError = sanitize(lastError?.message ?: "$actionLabel failed.", cleanKey)
            Log.e(TAG, "$actionLabel failed: $safeError")
            throw IllegalStateException(safeError, lastError)
        }
    }

    private suspend fun requestJsonWithSingleRepairRetry(
        apiKey: String,
        modelId: String,
        systemInstruction: String,
        turns: List<GeminiChatTurn>
    ): JSONObject {
        val raw = callGeminiHttp(apiKey, modelId, systemInstruction, turns, expectJson = true)
        return try {
            parseJsonObject(raw)
        } catch (_: JSONException) {
            Log.w(TAG, "Invalid JSON received; retrying once with strict JSON reminder.")
            val repaired = callGeminiHttp(
                apiKey = apiKey,
                modelId = modelId,
                systemInstruction = "$systemInstruction\nCRITICAL: Return ONLY a single valid JSON object. No markdown fences or extra commentary.",
                turns = turns,
                expectJson = true
            )
            parseJsonObject(repaired)
        }
    }

    private fun parseJsonObject(raw: String): JSONObject {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        val slice = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
        return JSONObject(slice)
    }

    suspend fun testConnection(
        apiKey: String,
        questionModel: String,
        fallbackModel: String
    ): Result<String> = runCatching {
        var usedModel = questionModel.trim().ifBlank { DefaultGeminiModels.QUESTION_MODEL }
        val reply = executeQueued(
            actionLabel = "Connection test",
            apiKey = apiKey,
            primaryModel = usedModel,
            fallbackModel = fallbackModel
        ) { activeModel ->
            usedModel = activeModel
            callGeminiHttp(
                apiKey = apiKey,
                modelId = activeModel,
                systemInstruction = null,
                turns = listOf(GeminiChatTurn("user", "Reply with OK.")),
                expectJson = false
            )
        }
        "Connected to $usedModel (${reply.take(24).trim()})."
    }

    suspend fun generateInterviewQuestion(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        globalTone: String,
        role: String,
        company: String,
        seniority: String,
        interviewType: String,
        difficulty: String,
        language: String,
        personaPrompt: String,
        profile: UserProfileEntity?,
        useResumeAndJd: Boolean,
        previousHistorySummary: String,
        questionIndex: Int,
        totalQuestions: Int,
        focusTopics: List<String> = emptyList()
    ): GeneratedQuestionResult {
        val cacheKey = if (previousHistorySummary.isBlank()) {
            listOf(
                role, company, seniority, interviewType, difficulty, language,
                questionIndex, useResumeAndJd, focusTopics.joinToString(",")
            ).joinToString("|")
        } else null

        cacheKey?.let { key ->
            questionCache[key]?.let { return it }
        }

        val result = executeQueued("Question generation", apiKey, modelId, fallbackModel) { activeModel ->
            val sys = """Interview coach/interviewer. Tone: $globalTone. Persona: $personaPrompt.
Return JSON: {"question":string,"topic":string,"isFollowUp":boolean,"isBehavioral":boolean}"""
            val prompt = buildString {
                append("Role: $role ($seniority)")
                if (company.isNotBlank()) append(" @ $company")
                append(". Type: $interviewType, Difficulty: $difficulty, Lang: $language. Q ${questionIndex + 1}/$totalQuestions.")
                if (focusTopics.isNotEmpty()) append(" Focus: ${focusTopics.joinToString(",")}.")
                if (useResumeAndJd && profile != null) {
                    if (profile.resumeText.isNotBlank()) append("\nResume: ${profile.resumeText.take(900)}")
                    if (profile.jobDescriptionText.isNotBlank()) append("\nJD: ${profile.jobDescriptionText.take(700)}")
                }
                if (previousHistorySummary.isNotBlank()) {
                    append("\nHistory:\n$previousHistorySummary\nAsk 1 concise follow-up or next question.")
                } else {
                    append("\nAsk 1 concise opening interview question.")
                }
            }
            val json = requestJsonWithSingleRepairRetry(
                apiKey = apiKey,
                modelId = activeModel,
                systemInstruction = sys,
                turns = listOf(GeminiChatTurn("user", prompt))
            )
            GeneratedQuestionResult(
                question = json.getString("question"),
                topic = json.optString("topic", interviewType),
                isFollowUp = json.optBoolean("isFollowUp", false),
                isBehavioral = json.optBoolean(
                    "isBehavioral",
                    interviewType.equals("Behavioral", true) || interviewType.equals("HR", true)
                )
            )
        }
        cacheKey?.let { questionCache[it] = result }
        return result
    }

    suspend fun generateHint(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        question: String,
        role: String,
        language: String
    ): String = executeQueued("Hint generation", apiKey, modelId, fallbackModel) { activeModel ->
        val prompt = "Give 2-3 brief bullet hints in $language (no full answer) for a $role candidate answering: \"$question\""
        callGeminiHttp(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = "Concise interview coach.",
            turns = listOf(GeminiChatTurn("user", prompt)),
            expectJson = false
        )
    }

    /**
     * Evaluates the current answer AND optionally generates the next adaptive question in ONE request
     * to conserve free-tier quota.
     */
    suspend fun evaluateAnswerAndNextQuestion(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        globalTone: String,
        question: String,
        userAnswer: String,
        role: String,
        company: String,
        seniority: String,
        interviewType: String,
        difficulty: String,
        personaPrompt: String,
        isBehavioral: Boolean,
        language: String,
        includeNextQuestion: Boolean,
        nextQuestionIndex: Int,
        totalQuestions: Int,
        focusTopics: List<String> = emptyList()
    ): CombinedFeedbackAndNextResult = executeQueued("Answer evaluation", apiKey, modelId, fallbackModel) { activeModel ->
        val nextSchemaPart = if (includeNextQuestion) {
            ""","nextQuestion":{"question":string,"topic":string,"isFollowUp":boolean,"isBehavioral":boolean}"""
        } else {
            ""
        }
        val sys = """Interview evaluator ($globalTone). Score 1-10. Return JSON:
{"clarityScore":int,"structureScore":int,"relevanceScore":int,"depthScore":int,"confidenceScore":int,"strengths":[string],"weaknesses":[string],"idealAnswer":string,"tips":[string],"starSituation":boolean,"starTask":boolean,"starAction":boolean,"starResult":boolean,"starFeedback":string$nextSchemaPart}"""

        val prompt = buildString {
            append("Lang: $language. Role: $role ($seniority)")
            if (company.isNotBlank()) append(" @ $company")
            append(". Type: $interviewType ($difficulty). Behavioral(STAR): $isBehavioral.")
            append("\nQ: $question\nA: $userAnswer")
            if (includeNextQuestion) {
                append("\nAlso generate Q ${nextQuestionIndex + 1}/$totalQuestions as persona ($personaPrompt)")
                if (focusTopics.isNotEmpty()) append(" prioritizing ${focusTopics.joinToString(",")}")
                append("—either a sharp follow-up or a new topic.")
            }
        }

        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = listOf(GeminiChatTurn("user", prompt))
        )

        val feedback = AnswerFeedbackResult(
            clarityScore = json.optInt("clarityScore", 6).coerceIn(1, 10),
            structureScore = json.optInt("structureScore", 6).coerceIn(1, 10),
            relevanceScore = json.optInt("relevanceScore", 6).coerceIn(1, 10),
            depthScore = json.optInt("depthScore", 6).coerceIn(1, 10),
            confidenceScore = json.optInt("confidenceScore", 6).coerceIn(1, 10),
            strengths = json.toStringList("strengths"),
            weaknesses = json.toStringList("weaknesses"),
            idealAnswer = json.optString("idealAnswer"),
            tips = json.toStringList("tips"),
            starSituation = json.optBoolean("starSituation", false),
            starTask = json.optBoolean("starTask", false),
            starAction = json.optBoolean("starAction", false),
            starResult = json.optBoolean("starResult", false),
            starFeedback = json.optString("starFeedback")
        )

        val nextQObj = if (includeNextQuestion) json.optJSONObject("nextQuestion") else null
        val nextQText = nextQObj?.optString("question").orEmpty().trim()
        val nextQuestion = if (nextQText.isNotBlank()) {
            GeneratedQuestionResult(
                question = nextQText,
                topic = nextQObj!!.optString("topic", interviewType),
                isFollowUp = nextQObj.optBoolean("isFollowUp", false),
                isBehavioral = nextQObj.optBoolean(
                    "isBehavioral",
                    interviewType.equals("Behavioral", true) || interviewType.equals("HR", true)
                )
            )
        } else null

        CombinedFeedbackAndNextResult(feedback = feedback, nextQuestion = nextQuestion)
    }

    suspend fun generateSessionReport(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        role: String,
        turnsTranscript: String,
        language: String
    ): SessionSummaryResult = executeQueued("Session report", apiKey, modelId, fallbackModel) { activeModel ->
        val sys = """Return JSON: {"overallScore":number(1-10),"topStrengths":[string],"topImprovements":[string],"nextPracticePlan":string}"""
        val prompt = "Summarize mock interview in $language for $role (top 3 strengths, top 3 improvements, short plan):\n$turnsTranscript"
        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = listOf(GeminiChatTurn("user", prompt))
        )
        SessionSummaryResult(
            overallScore = json.optDouble("overallScore", 7.0).coerceIn(1.0, 10.0),
            topStrengths = json.toStringList("topStrengths").take(3),
            topImprovements = json.toStringList("topImprovements").take(3),
            nextPracticePlan = json.optString("nextPracticePlan")
        )
    }

    suspend fun generateCompanyBrief(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        companyName: String,
        targetRole: String,
        language: String
    ): CompanyBriefResult {
        val cacheKey = "${companyName.trim().lowercase()}|${targetRole.trim().lowercase()}|${language.trim().lowercase()}"
        companyBriefCache[cacheKey]?.let { return it }

        val result = executeQueued("Company brief", apiKey, modelId, fallbackModel) { activeModel ->
            val sys = """Return JSON: {"cultureOverview":string,"keyValues":[string],"likelyQuestions":[string],"preparationTips":[string]}"""
            val prompt = "Concise interview brief in $language for $companyName ($targetRole): culture, 4 values, 5 likely questions, 4 tips."
            val json = requestJsonWithSingleRepairRetry(
                apiKey = apiKey,
                modelId = activeModel,
                systemInstruction = sys,
                turns = listOf(GeminiChatTurn("user", prompt))
            )
            CompanyBriefResult(
                cultureOverview = json.optString("cultureOverview"),
                keyValues = json.toStringList("keyValues"),
                likelyQuestions = json.toStringList("likelyQuestions"),
                preparationTips = json.toStringList("preparationTips")
            )
        }
        companyBriefCache[cacheKey] = result
        return result
    }

    suspend fun refineTellMeAboutYourself(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        globalTone: String,
        profile: UserProfileEntity?,
        draftPitch: String,
        targetLengthSec: Int,
        styleFocus: String,
        language: String
    ): String = executeQueued("Pitch refiner", apiKey, modelId, fallbackModel) { activeModel ->
        val prompt = buildString {
            append("Tone: $globalTone. Craft a ~${targetLengthSec}s 'Tell Me About Yourself' pitch in $language (focus: $styleFocus).")
            if (profile != null) {
                append("\nCandidate: ${profile.name}, ${profile.targetRole} (${profile.experienceLevel}, ${profile.industry}).")
                if (profile.resumeText.isNotBlank()) append("\nResume: ${profile.resumeText.take(1000)}")
            }
            if (draftPitch.isNotBlank()) append("\nDraft: $draftPitch")
            append("\nOutput: 1) Script, 2) Key Hooks, 3) Delivery Tip.")
        }
        callGeminiHttp(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = "Concise career coach.",
            turns = listOf(GeminiChatTurn("user", prompt)),
            expectJson = false
        )
    }

    suspend fun sendNegotiationTurn(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        role: String,
        company: String,
        targetComp: String,
        conversationHistory: List<Pair<String, String>>,
        userMessage: String
    ): NegotiationTurnResult = executeQueued("Salary negotiation", apiKey, modelId, fallbackModel) { activeModel ->
        val sys = """Recruiter negotiating a $role offer at ${company.ifBlank { "the company" }} (target: $targetComp).
Reply in character plus private tip. Return JSON: {"recruiterReply":string,"tacticTip":string,"leverageScore":int(1-10)}"""
        val turns = buildList {
            conversationHistory.forEach { (speaker, msg) ->
                add(GeminiChatTurn(if (speaker == "user") "user" else "model", msg))
            }
            add(GeminiChatTurn("user", userMessage))
        }
        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = turns
        )
        NegotiationTurnResult(
            recruiterReply = json.optString("recruiterReply"),
            tacticTip = json.optString("tacticTip"),
            leverageScore = json.optInt("leverageScore", 6).coerceIn(1, 10)
        )
    }

    suspend fun generateCodingProblem(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        role: String,
        difficulty: String,
        language: String
    ): String = executeQueued("Coding problem", apiKey, modelId, fallbackModel) { activeModel ->
        val prompt = "1 concise $difficulty coding interview problem for $role ($language): Title, Statement, 1 I/O Example, Constraints."
        callGeminiHttp(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = "Senior software engineering interviewer.",
            turns = listOf(GeminiChatTurn("user", prompt)),
            expectJson = false
        )
    }

    suspend fun reviewCodeSolution(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        problem: String,
        programmingLanguage: String,
        code: String
    ): CodingReviewResult = executeQueued("Code review", apiKey, modelId, fallbackModel) { activeModel ->
        val sys = """Return JSON: {"correctnessScore":int(1-10),"timeComplexity":string,"spaceComplexity":string,"edgeCases":[string],"improvements":[string],"refinedCode":string}"""
        val prompt = "Review $programmingLanguage solution.\nProblem: $problem\nCode:\n$code"
        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = listOf(GeminiChatTurn("user", prompt))
        )
        CodingReviewResult(
            correctnessScore = json.optInt("correctnessScore", 6).coerceIn(1, 10),
            timeComplexity = json.optString("timeComplexity", "O(n)"),
            spaceComplexity = json.optString("spaceComplexity", "O(1)"),
            edgeCases = json.toStringList("edgeCases"),
            improvements = json.toStringList("improvements"),
            refinedCode = json.optString("refinedCode")
        )
    }

    suspend fun generatePrepPlan(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        days: Int,
        profile: UserProfileEntity?,
        weakTopics: List<String>,
        language: String
    ): List<PrepPlanTaskGenerated> {
        val role = profile?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer"
        val weak = weakTopics.ifEmpty { listOf("Behavioral STAR", "Core Problem Solving") }
        val cacheKey = "$days|$role|${weak.joinToString(",")}|$language"
        prepPlanCache[cacheKey]?.let { return it }

        val result = executeQueued("Prep plan", apiKey, modelId, fallbackModel) { activeModel ->
            val sys = """Return JSON: {"tasks":[{"dayNumber":int,"title":string,"description":string,"focusArea":string}]}"""
            val prompt = "Build a $days-day prep plan ($days tasks, 1/day) in $language for $role (${profile?.experienceLevel ?: "Mid-Level"}), prioritizing ${weak.joinToString(", ")}."
            val json = requestJsonWithSingleRepairRetry(
                apiKey = apiKey,
                modelId = activeModel,
                systemInstruction = sys,
                turns = listOf(GeminiChatTurn("user", prompt))
            )
            val arr = json.optJSONArray("tasks") ?: return@executeQueued emptyList()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PrepPlanTaskGenerated(
                    dayNumber = obj.optInt("dayNumber", i + 1),
                    title = obj.optString("title", "Day ${i + 1} Practice"),
                    description = obj.optString("description"),
                    focusArea = obj.optString("focusArea", "General")
                )
            }
        }
        if (result.isNotEmpty()) {
            prepPlanCache[cacheKey] = result
        }
        return result
    }

    suspend fun generateDailyChallenge(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        role: String,
        industry: String,
        dateKey: String,
        language: String
    ): DailyChallengeGenerated = executeQueued("Daily challenge", apiKey, modelId, fallbackModel) { activeModel ->
        val sys = """Return JSON: {"question":string,"topic":string,"hint":string}"""
        val prompt = "Daily interview challenge ($dateKey) in $language for $role ($industry)."
        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = listOf(GeminiChatTurn("user", prompt))
        )
        DailyChallengeGenerated(
            question = json.getString("question"),
            topic = json.optString("topic", "Daily Prep"),
            hint = json.optString("hint")
        )
    }

    suspend fun evaluateDailyChallenge(
        apiKey: String,
        modelId: String,
        fallbackModel: String,
        question: String,
        answer: String,
        language: String
    ): DailyChallengeFeedback = executeQueued("Daily challenge evaluation", apiKey, modelId, fallbackModel) { activeModel ->
        val sys = """Return JSON: {"score":int(1-10),"summary":string,"idealAnswer":string}"""
        val prompt = "Score in $language.\nQ: $question\nA: $answer"
        val json = requestJsonWithSingleRepairRetry(
            apiKey = apiKey,
            modelId = activeModel,
            systemInstruction = sys,
            turns = listOf(GeminiChatTurn("user", prompt))
        )
        DailyChallengeFeedback(
            score = json.optInt("score", 7).coerceIn(1, 10),
            summary = json.optString("summary"),
            idealAnswer = json.optString("idealAnswer")
        )
    }

    private fun JSONObject.toStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx).takeIf { it.isNotBlank() }
        }
    }
}
