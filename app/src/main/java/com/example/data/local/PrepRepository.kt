package com.example.data.local

import com.example.utils.Sm2Algorithm
import com.example.utils.Sm2Quality
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TopicMetric(
    val topic: String,
    val averageScore: Double,
    val attempts: Int
)

data class BadgeItem(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

/**
 * Single repository coordinating Room persistence, DataStore preferences, SM-2 scheduling,
 * analytics aggregation, and local JSON backup/restore.
 */
class PrepRepository(
    private val dao: PrepDao,
    private val prefsRepo: UserPreferencesRepository
) {
    val profileFlow: Flow<UserProfileEntity?> = dao.observeProfile()
    val presetsFlow: Flow<List<InterviewPresetEntity>> = dao.observePresets()
    val sessionsFlow: Flow<List<InterviewSessionEntity>> = dao.observeSessions()
    val allTurnsFlow: Flow<List<SessionTurnEntity>> = dao.observeAllTurns()
    val savedQuestionsFlow: Flow<List<SavedQuestionEntity>> = dao.observeSavedQuestions()
    val companyBriefsFlow: Flow<List<CompanyBriefEntity>> = dao.observeCompanyBriefs()
    val prepPlanTasksFlow: Flow<List<PrepPlanTaskEntity>> = dao.observePrepPlanTasks()
    val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>> = dao.observeAllDailyChallenges()
    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    fun todayDateKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun yesterdayDateKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun observeTodayChallenge(): Flow<DailyChallengeEntity?> =
        dao.observeDailyChallenge(todayDateKey())

    suspend fun getProfileOnce(): UserProfileEntity? = dao.getProfileOnce()

    suspend fun saveProfile(profile: UserProfileEntity) {
        dao.upsertProfile(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))
    }

    suspend fun savePreset(preset: InterviewPresetEntity): Long = dao.insertPreset(preset)

    suspend fun deletePreset(id: Long) = dao.deletePreset(id)

    suspend fun createSession(session: InterviewSessionEntity): Long = dao.insertSession(session)

    suspend fun updateSession(session: InterviewSessionEntity) {
        dao.updateSession(session)
        if (session.completed) {
            prefsRepo.recordPracticeForDate(todayDateKey(), yesterdayDateKey())
        }
    }

    suspend fun getSessionById(sessionId: Long): InterviewSessionEntity? = dao.getSessionById(sessionId)

    fun observeTurnsForSession(sessionId: Long): Flow<List<SessionTurnEntity>> =
        dao.observeTurnsForSession(sessionId)

    suspend fun getTurnsForSessionOnce(sessionId: Long): List<SessionTurnEntity> =
        dao.getTurnsForSessionOnce(sessionId)

    suspend fun insertTurn(turn: SessionTurnEntity): Long = dao.insertTurn(turn)

    suspend fun updateTurn(turn: SessionTurnEntity) = dao.updateTurn(turn)

    suspend fun saveQuestionToBank(question: SavedQuestionEntity): Long =
        dao.upsertSavedQuestion(question)

    suspend fun deleteSavedQuestion(id: Long) = dao.deleteSavedQuestion(id)

    suspend fun gradeFlashcard(card: SavedQuestionEntity, quality: Sm2Quality) {
        val schedule = Sm2Algorithm.calculateNextReview(
            quality = quality,
            previousRepetitions = card.repetitions,
            previousIntervalDays = card.intervalDays,
            previousEaseFactor = card.easeFactor
        )
        dao.upsertSavedQuestion(
            card.copy(
                repetitions = schedule.repetitions,
                intervalDays = schedule.intervalDays,
                easeFactor = schedule.easeFactor,
                nextReviewEpochMs = schedule.nextReviewEpochMs
            )
        )
    }

    suspend fun saveCompanyBrief(brief: CompanyBriefEntity): Long = dao.insertCompanyBrief(brief)

    suspend fun deleteCompanyBrief(id: Long) = dao.deleteCompanyBrief(id)

    suspend fun replacePrepPlan(tasks: List<PrepPlanTaskEntity>) {
        dao.clearPrepPlanTasks()
        dao.insertPrepPlanTasks(tasks)
    }

    suspend fun togglePrepTask(taskId: Long, completed: Boolean) =
        dao.setPrepTaskCompleted(taskId, completed)

    suspend fun saveDailyChallenge(challenge: DailyChallengeEntity) {
        dao.upsertDailyChallenge(challenge)
        if (challenge.completed) {
            prefsRepo.recordPracticeForDate(todayDateKey(), yesterdayDateKey())
        }
    }

    // Preferences pass-through
    suspend fun getPreferencesOnce() = prefsRepo.getPreferencesOnce()
    suspend fun setThemeMode(mode: ThemeMode) = prefsRepo.setThemeMode(mode)
    suspend fun setAccentPalette(palette: AccentPalette) = prefsRepo.setAccentPalette(palette)
    suspend fun setGeminiApiKey(apiKey: String) = prefsRepo.setGeminiApiKey(apiKey)
    suspend fun setQuestionModel(model: String) = prefsRepo.setQuestionModel(model)
    suspend fun setDetailedModel(model: String) = prefsRepo.setDetailedModel(model)
    suspend fun setFallbackModel(model: String) = prefsRepo.setFallbackModel(model)
    suspend fun resetGeminiModelsToDefaults() = prefsRepo.resetGeminiModelsToDefaults()
    suspend fun setAiTonePreset(tone: AiTonePreset) = prefsRepo.setAiTonePreset(tone)
    suspend fun setCustomSystemPrompt(prompt: String) = prefsRepo.setCustomSystemPrompt(prompt)
    suspend fun setTtsSpeechRate(rate: Float) = prefsRepo.setTtsSpeechRate(rate)
    suspend fun setDefaultLanguage(language: String) = prefsRepo.setDefaultLanguage(language)
    suspend fun setDefaultAnswerTimeSec(sec: Int) = prefsRepo.setDefaultAnswerTimeSec(sec)
    suspend fun setReminder(title: String, targetEpochMs: Long) = prefsRepo.setReminder(title, targetEpochMs)

    fun computeTopicMetrics(turns: List<SessionTurnEntity>): List<TopicMetric> {
        val answered = turns.filter { !it.skipped && (it.clarityScore + it.relevanceScore) > 0 }
        return answered.groupBy { it.topic.ifBlank { "General" } }
            .map { (topic, list) ->
                val avg = list.map { t ->
                    (t.clarityScore + t.structureScore + t.relevanceScore + t.depthScore + t.confidenceScore) / 5.0
                }.average()
                TopicMetric(topic = topic, averageScore = avg, attempts = list.size)
            }
            .sortedBy { it.averageScore }
    }

    fun computeBadges(
        sessions: List<InterviewSessionEntity>,
        savedQuestions: List<SavedQuestionEntity>,
        challenges: List<DailyChallengeEntity>,
        streak: Int
    ): List<BadgeItem> {
        val completedSessions = sessions.filter { it.completed }
        val highScore = completedSessions.any { it.overallScore >= 8.0 }
        val realModeDone = completedSessions.any { it.mode == "Real" }
        val completedChallenges = challenges.count { it.completed }

        return listOf(
            BadgeItem(
                id = "first_flight",
                title = "First Flight",
                description = "Complete your first mock interview session",
                unlocked = completedSessions.isNotEmpty()
            ),
            BadgeItem(
                id = "streak_3",
                title = "Momentum Builder",
                description = "Maintain a 3-day practice streak",
                unlocked = streak >= 3
            ),
            BadgeItem(
                id = "high_flyer",
                title = "High Flyer",
                description = "Score 8.0+ overall in a mock interview",
                unlocked = highScore
            ),
            BadgeItem(
                id = "pressure_tested",
                title = "Pressure Tested",
                description = "Complete a Real Interview mode session",
                unlocked = realModeDone
            ),
            BadgeItem(
                id = "scholar",
                title = "Curated Scholar",
                description = "Save at least 5 questions to your Library",
                unlocked = savedQuestions.size >= 5
            ),
            BadgeItem(
                id = "daily_champion",
                title = "Daily Challenger",
                description = "Finish 3 daily interview challenges",
                unlocked = completedChallenges >= 3
            )
        )
    }

    suspend fun clearAllLocalData() {
        dao.clearSessions()
        dao.clearPresets()
        dao.clearSavedQuestions()
        dao.clearCompanyBriefs()
        dao.clearPrepPlanTasks()
        dao.clearDailyChallenges()
        dao.clearProfile()
        prefsRepo.clearAllPreferences()
    }

    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        dao.getProfileOnce()?.let { p ->
            root.put("profile", JSONObject().apply {
                put("name", p.name)
                put("targetRole", p.targetRole)
                put("experienceLevel", p.experienceLevel)
                put("industry", p.industry)
                put("targetCompanies", p.targetCompanies)
                put("resumeText", p.resumeText)
                put("jobDescriptionText", p.jobDescriptionText)
                put("onboardingCompleted", p.onboardingCompleted)
            })
        }

        val presetsArr = JSONArray()
        dao.getPresetsOnce().forEach { pr ->
            presetsArr.put(JSONObject().apply {
                put("title", pr.title)
                put("role", pr.role)
                put("company", pr.company)
                put("seniority", pr.seniority)
                put("interviewType", pr.interviewType)
                put("difficulty", pr.difficulty)
                put("questionCount", pr.questionCount)
                put("timeLimitSec", pr.timeLimitSec)
                put("language", pr.language)
                put("persona", pr.persona)
                put("customPersonaPrompt", pr.customPersonaPrompt)
                put("useResumeAndJd", pr.useResumeAndJd)
                put("practiceMode", pr.practiceMode)
            })
        }
        root.put("presets", presetsArr)

        val savedArr = JSONArray()
        dao.getSavedQuestionsOnce().forEach { q ->
            savedArr.put(JSONObject().apply {
                put("question", q.question)
                put("topic", q.topic)
                put("tags", q.tags)
                put("personalNotes", q.personalNotes)
                put("bestAnswer", q.bestAnswer)
                put("isBookmarked", q.isBookmarked)
                put("isFlashcard", q.isFlashcard)
                put("repetitions", q.repetitions)
                put("intervalDays", q.intervalDays)
                put("easeFactor", q.easeFactor)
                put("nextReviewEpochMs", q.nextReviewEpochMs)
            })
        }
        root.put("savedQuestions", savedArr)

        val sessionsArr = JSONArray()
        val allTurns = dao.getAllTurnsOnce().groupBy { it.sessionId }
        dao.getSessionsOnce().forEach { s ->
            val sObj = JSONObject().apply {
                put("role", s.role)
                put("company", s.company)
                put("seniority", s.seniority)
                put("interviewType", s.interviewType)
                put("difficulty", s.difficulty)
                put("persona", s.persona)
                put("mode", s.mode)
                put("questionCount", s.questionCount)
                put("overallScore", s.overallScore)
                put("topStrengths", s.topStrengths)
                put("topImprovements", s.topImprovements)
                put("nextPracticePlan", s.nextPracticePlan)
                put("completed", s.completed)
                put("createdAt", s.createdAt)
            }
            val turnsArr = JSONArray()
            allTurns[s.id].orEmpty().forEach { t ->
                turnsArr.put(JSONObject().apply {
                    put("turnIndex", t.turnIndex)
                    put("question", t.question)
                    put("topic", t.topic)
                    put("isFollowUp", t.isFollowUp)
                    put("userAnswer", t.userAnswer)
                    put("skipped", t.skipped)
                    put("clarityScore", t.clarityScore)
                    put("structureScore", t.structureScore)
                    put("relevanceScore", t.relevanceScore)
                    put("depthScore", t.depthScore)
                    put("confidenceScore", t.confidenceScore)
                    put("strengths", t.strengths)
                    put("weaknesses", t.weaknesses)
                    put("idealAnswer", t.idealAnswer)
                    put("tips", t.tips)
                    put("isBehavioral", t.isBehavioral)
                    put("starSituation", t.starSituation)
                    put("starTask", t.starTask)
                    put("starAction", t.starAction)
                    put("starResult", t.starResult)
                    put("starFeedback", t.starFeedback)
                    put("wpm", t.wpm)
                    put("fillerCount", t.fillerCount)
                    put("longPauseCount", t.longPauseCount)
                })
            }
            sObj.put("turns", turnsArr)
            sessionsArr.put(sObj)
        }
        root.put("sessions", sessionsArr)

        val tasksArr = JSONArray()
        dao.getPrepPlanTasksOnce().forEach { task ->
            tasksArr.put(JSONObject().apply {
                put("planDays", task.planDays)
                put("dayNumber", task.dayNumber)
                put("title", task.title)
                put("description", task.description)
                put("focusArea", task.focusArea)
                put("completed", task.completed)
            })
        }
        root.put("prepPlanTasks", tasksArr)

        return root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Result<Unit> = runCatching {
        val root = JSONObject(rawJson)
        root.optJSONObject("profile")?.let { p ->
            dao.upsertProfile(
                UserProfileEntity(
                    id = 1,
                    name = p.optString("name"),
                    targetRole = p.optString("targetRole"),
                    experienceLevel = p.optString("experienceLevel", "Mid-Level (2–5 yrs)"),
                    industry = p.optString("industry", "Software & Tech"),
                    targetCompanies = p.optString("targetCompanies"),
                    resumeText = p.optString("resumeText"),
                    jobDescriptionText = p.optString("jobDescriptionText"),
                    onboardingCompleted = p.optBoolean("onboardingCompleted", true)
                )
            )
        }

        root.optJSONArray("presets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val pr = arr.getJSONObject(i)
                dao.insertPreset(
                    InterviewPresetEntity(
                        title = pr.optString("title", "Imported Preset"),
                        role = pr.optString("role"),
                        company = pr.optString("company"),
                        seniority = pr.optString("seniority", "Mid-Level"),
                        interviewType = pr.optString("interviewType", "Mixed"),
                        difficulty = pr.optString("difficulty", "Medium"),
                        questionCount = pr.optInt("questionCount", 5),
                        timeLimitSec = pr.optInt("timeLimitSec", 120),
                        language = pr.optString("language", "English"),
                        persona = pr.optString("persona", "Friendly HR"),
                        customPersonaPrompt = pr.optString("customPersonaPrompt"),
                        useResumeAndJd = pr.optBoolean("useResumeAndJd", false),
                        practiceMode = pr.optBoolean("practiceMode", true)
                    )
                )
            }
        }

        root.optJSONArray("savedQuestions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val q = arr.getJSONObject(i)
                dao.upsertSavedQuestion(
                    SavedQuestionEntity(
                        question = q.optString("question"),
                        topic = q.optString("topic", "General"),
                        tags = q.optString("tags"),
                        personalNotes = q.optString("personalNotes"),
                        bestAnswer = q.optString("bestAnswer"),
                        isBookmarked = q.optBoolean("isBookmarked", true),
                        isFlashcard = q.optBoolean("isFlashcard", true),
                        repetitions = q.optInt("repetitions", 0),
                        intervalDays = q.optInt("intervalDays", 1),
                        easeFactor = q.optDouble("easeFactor", 2.5),
                        nextReviewEpochMs = q.optLong("nextReviewEpochMs", System.currentTimeMillis())
                    )
                )
            }
        }

        root.optJSONArray("sessions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val sid = dao.insertSession(
                    InterviewSessionEntity(
                        role = s.optString("role"),
                        company = s.optString("company"),
                        seniority = s.optString("seniority"),
                        interviewType = s.optString("interviewType"),
                        difficulty = s.optString("difficulty"),
                        persona = s.optString("persona"),
                        mode = s.optString("mode", "Practice"),
                        questionCount = s.optInt("questionCount", 5),
                        overallScore = s.optDouble("overallScore", 0.0),
                        topStrengths = s.optString("topStrengths"),
                        topImprovements = s.optString("topImprovements"),
                        nextPracticePlan = s.optString("nextPracticePlan"),
                        completed = s.optBoolean("completed", true),
                        createdAt = s.optLong("createdAt", System.currentTimeMillis())
                    )
                )
                s.optJSONArray("turns")?.let { tArr ->
                    for (j in 0 until tArr.length()) {
                        val t = tArr.getJSONObject(j)
                        dao.insertTurn(
                            SessionTurnEntity(
                                sessionId = sid,
                                turnIndex = t.optInt("turnIndex", j),
                                question = t.optString("question"),
                                topic = t.optString("topic", "General"),
                                isFollowUp = t.optBoolean("isFollowUp", false),
                                userAnswer = t.optString("userAnswer"),
                                skipped = t.optBoolean("skipped", false),
                                clarityScore = t.optInt("clarityScore"),
                                structureScore = t.optInt("structureScore"),
                                relevanceScore = t.optInt("relevanceScore"),
                                depthScore = t.optInt("depthScore"),
                                confidenceScore = t.optInt("confidenceScore"),
                                strengths = t.optString("strengths"),
                                weaknesses = t.optString("weaknesses"),
                                idealAnswer = t.optString("idealAnswer"),
                                tips = t.optString("tips"),
                                isBehavioral = t.optBoolean("isBehavioral", false),
                                starSituation = t.optBoolean("starSituation", false),
                                starTask = t.optBoolean("starTask", false),
                                starAction = t.optBoolean("starAction", false),
                                starResult = t.optBoolean("starResult", false),
                                starFeedback = t.optString("starFeedback"),
                                wpm = t.optInt("wpm"),
                                fillerCount = t.optInt("fillerCount"),
                                longPauseCount = t.optInt("longPauseCount")
                            )
                        )
                    }
                }
            }
        }

        root.optJSONArray("prepPlanTasks")?.let { arr ->
            val tasks = mutableListOf<PrepPlanTaskEntity>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tasks.add(
                    PrepPlanTaskEntity(
                        planDays = t.optInt("planDays", 7),
                        dayNumber = t.optInt("dayNumber", 1),
                        title = t.optString("title"),
                        description = t.optString("description"),
                        focusArea = t.optString("focusArea"),
                        completed = t.optBoolean("completed", false)
                    )
                )
            }
            if (tasks.isNotEmpty()) {
                dao.clearPrepPlanTasks()
                dao.insertPrepPlanTasks(tasks)
            }
        }
    }
}
