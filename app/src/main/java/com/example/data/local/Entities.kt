package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val targetRole: String = "",
    val experienceLevel: String = "Mid-Level (2–5 yrs)",
    val industry: String = "Software & Tech",
    val targetCompanies: String = "", // comma-separated
    val resumeText: String = "",
    val jobDescriptionText: String = "",
    val onboardingCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interview_presets")
data class InterviewPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val role: String,
    val company: String,
    val seniority: String,
    val interviewType: String,
    val difficulty: String,
    val questionCount: Int,
    val timeLimitSec: Int,
    val language: String,
    val persona: String,
    val customPersonaPrompt: String,
    val useResumeAndJd: Boolean,
    val practiceMode: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interview_sessions")
data class InterviewSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val company: String,
    val seniority: String,
    val interviewType: String,
    val difficulty: String,
    val persona: String,
    val mode: String, // "Practice" or "Real"
    val questionCount: Int,
    val overallScore: Double = 0.0,
    val topStrengths: String = "", // "||"-delimited
    val topImprovements: String = "", // "||"-delimited
    val nextPracticePlan: String = "",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "session_turns",
    foreignKeys = [
        ForeignKey(
            entity = InterviewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SessionTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val turnIndex: Int,
    val question: String,
    val topic: String,
    val isFollowUp: Boolean = false,
    val userAnswer: String = "",
    val skipped: Boolean = false,
    val clarityScore: Int = 0,
    val structureScore: Int = 0,
    val relevanceScore: Int = 0,
    val depthScore: Int = 0,
    val confidenceScore: Int = 0,
    val strengths: String = "", // "||"-delimited
    val weaknesses: String = "", // "||"-delimited
    val idealAnswer: String = "",
    val tips: String = "", // "||"-delimited
    val isBehavioral: Boolean = false,
    val starSituation: Boolean = false,
    val starTask: Boolean = false,
    val starAction: Boolean = false,
    val starResult: Boolean = false,
    val starFeedback: String = "",
    val wpm: Int = 0,
    val fillerCount: Int = 0,
    val longPauseCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_questions")
data class SavedQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val topic: String,
    val tags: String = "", // comma-separated
    val personalNotes: String = "",
    val bestAnswer: String = "",
    val isBookmarked: Boolean = true,
    val isFlashcard: Boolean = true,
    val repetitions: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Double = 2.5,
    val nextReviewEpochMs: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "company_briefs")
data class CompanyBriefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyName: String,
    val targetRole: String,
    val cultureOverview: String,
    val keyValues: String, // "||"-delimited
    val likelyQuestions: String, // "||"-delimited
    val preparationTips: String, // "||"-delimited
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prep_plan_tasks")
data class PrepPlanTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planDays: Int,
    val dayNumber: Int,
    val title: String,
    val description: String,
    val focusArea: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_challenges")
data class DailyChallengeEntity(
    @PrimaryKey val dateKey: String, // YYYY-MM-DD
    val question: String,
    val topic: String,
    val hint: String,
    val userAnswer: String = "",
    val score: Int = 0,
    val feedbackSummary: String = "",
    val idealAnswer: String = "",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
