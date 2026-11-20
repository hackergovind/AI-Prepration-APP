package com.example.utils

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 spaced repetition scheduler for interview flashcards.
 */
enum class Sm2Quality(val score: Int, val label: String) {
    AGAIN(0, "Again"),
    HARD(3, "Hard"),
    GOOD(4, "Good"),
    EASY(5, "Easy")
}

data class Sm2ScheduleResult(
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val nextReviewEpochMs: Long
)

object Sm2Algorithm {
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    fun calculateNextReview(
        quality: Sm2Quality,
        previousRepetitions: Int,
        previousIntervalDays: Int,
        previousEaseFactor: Double,
        nowMs: Long = System.currentTimeMillis()
    ): Sm2ScheduleResult {
        val q = quality.score
        val newRepetitions: Int
        val newInterval: Int

        if (q < 3) {
            newRepetitions = 0
            newInterval = 1
        } else {
            newRepetitions = previousRepetitions + 1
            newInterval = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> max(1, (previousIntervalDays * previousEaseFactor).roundToInt())
            }
        }

        val updatedEase = max(
            1.3,
            previousEaseFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        )

        val bonusDays = if (quality == Sm2Quality.EASY && newRepetitions > 1) {
            (newInterval * 1.25).roundToInt()
        } else {
            newInterval
        }

        return Sm2ScheduleResult(
            repetitions = newRepetitions,
            intervalDays = bonusDays,
            easeFactor = updatedEase,
            nextReviewEpochMs = nowMs + bonusDays * ONE_DAY_MS
        )
    }
}
