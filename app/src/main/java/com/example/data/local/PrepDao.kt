package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrepDao {

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    // Interview Presets
    @Query("SELECT * FROM interview_presets ORDER BY createdAt DESC")
    fun observePresets(): Flow<List<InterviewPresetEntity>>

    @Query("SELECT * FROM interview_presets ORDER BY createdAt DESC")
    suspend fun getPresetsOnce(): List<InterviewPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: InterviewPresetEntity): Long

    @Query("DELETE FROM interview_presets WHERE id = :id")
    suspend fun deletePreset(id: Long)

    // Interview Sessions
    @Query("SELECT * FROM interview_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<InterviewSessionEntity>>

    @Query("SELECT * FROM interview_sessions ORDER BY createdAt DESC")
    suspend fun getSessionsOnce(): List<InterviewSessionEntity>

    @Query("SELECT * FROM interview_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): InterviewSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InterviewSessionEntity): Long

    @Update
    suspend fun updateSession(session: InterviewSessionEntity)

    @Query("DELETE FROM interview_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    // Session Turns
    @Query("SELECT * FROM session_turns WHERE sessionId = :sessionId ORDER BY turnIndex ASC")
    fun observeTurnsForSession(sessionId: Long): Flow<List<SessionTurnEntity>>

    @Query("SELECT * FROM session_turns WHERE sessionId = :sessionId ORDER BY turnIndex ASC")
    suspend fun getTurnsForSessionOnce(sessionId: Long): List<SessionTurnEntity>

    @Query("SELECT * FROM session_turns ORDER BY createdAt DESC")
    fun observeAllTurns(): Flow<List<SessionTurnEntity>>

    @Query("SELECT * FROM session_turns ORDER BY createdAt ASC")
    suspend fun getAllTurnsOnce(): List<SessionTurnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: SessionTurnEntity): Long

    @Update
    suspend fun updateTurn(turn: SessionTurnEntity)

    // Saved Questions & Flashcards
    @Query("SELECT * FROM saved_questions ORDER BY createdAt DESC")
    fun observeSavedQuestions(): Flow<List<SavedQuestionEntity>>

    @Query("SELECT * FROM saved_questions ORDER BY createdAt DESC")
    suspend fun getSavedQuestionsOnce(): List<SavedQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedQuestion(question: SavedQuestionEntity): Long

    @Query("DELETE FROM saved_questions WHERE id = :id")
    suspend fun deleteSavedQuestion(id: Long)

    // Company Briefs
    @Query("SELECT * FROM company_briefs ORDER BY createdAt DESC")
    fun observeCompanyBriefs(): Flow<List<CompanyBriefEntity>>

    @Query("SELECT * FROM company_briefs ORDER BY createdAt DESC")
    suspend fun getCompanyBriefsOnce(): List<CompanyBriefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyBrief(brief: CompanyBriefEntity): Long

    @Query("DELETE FROM company_briefs WHERE id = :id")
    suspend fun deleteCompanyBrief(id: Long)

    // Prep Plan Tasks
    @Query("SELECT * FROM prep_plan_tasks ORDER BY dayNumber ASC, id ASC")
    fun observePrepPlanTasks(): Flow<List<PrepPlanTaskEntity>>

    @Query("SELECT * FROM prep_plan_tasks ORDER BY dayNumber ASC, id ASC")
    suspend fun getPrepPlanTasksOnce(): List<PrepPlanTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrepPlanTasks(tasks: List<PrepPlanTaskEntity>)

    @Query("UPDATE prep_plan_tasks SET completed = :completed WHERE id = :taskId")
    suspend fun setPrepTaskCompleted(taskId: Long, completed: Boolean)

    @Query("DELETE FROM prep_plan_tasks")
    suspend fun clearPrepPlanTasks()

    // Daily Challenges
    @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey")
    fun observeDailyChallenge(dateKey: String): Flow<DailyChallengeEntity?>

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    fun observeAllDailyChallenges(): Flow<List<DailyChallengeEntity>>

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    suspend fun getDailyChallengesOnce(): List<DailyChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyChallenge(challenge: DailyChallengeEntity)

    // Clear All Data
    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()

    @Query("DELETE FROM interview_presets")
    suspend fun clearPresets()

    @Query("DELETE FROM interview_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM saved_questions")
    suspend fun clearSavedQuestions()

    @Query("DELETE FROM company_briefs")
    suspend fun clearCompanyBriefs()

    @Query("DELETE FROM daily_challenges")
    suspend fun clearDailyChallenges()
}
