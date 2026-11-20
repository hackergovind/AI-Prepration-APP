package com.example

import com.example.data.local.DefaultGeminiModels
import com.example.data.local.UserPreferences
import com.example.utils.Sm2Algorithm
import com.example.utils.Sm2Quality
import com.example.utils.SpeechAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepPilotUnitTest {

    @Test
    fun geminiDefaults_matchConfiguredModelsAndEmptyKey() {
        val prefs = UserPreferences()
        assertEquals("", prefs.geminiApiKey)
        assertEquals("gemini-3.5-flash-lite", DefaultGeminiModels.QUESTION_MODEL)
        assertEquals("gemini-3.5-flash", DefaultGeminiModels.DETAILED_MODEL)
        assertEquals("gemini-2.5-flash-lite", DefaultGeminiModels.FALLBACK_MODEL)
        assertEquals(DefaultGeminiModels.QUESTION_MODEL, prefs.questionModel)
        assertEquals(DefaultGeminiModels.DETAILED_MODEL, prefs.detailedModel)
        assertEquals(DefaultGeminiModels.FALLBACK_MODEL, prefs.fallbackModel)
    }

    @Test
    fun sm2_goodSequence_increasesIntervalAndEase() {
        val now = 1_000_000L
        val first = Sm2Algorithm.calculateNextReview(
            quality = Sm2Quality.GOOD,
            previousRepetitions = 0,
            previousIntervalDays = 1,
            previousEaseFactor = 2.5,
            nowMs = now
        )
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)

        val second = Sm2Algorithm.calculateNextReview(
            quality = Sm2Quality.GOOD,
            previousRepetitions = first.repetitions,
            previousIntervalDays = first.intervalDays,
            previousEaseFactor = first.easeFactor,
            nowMs = now
        )
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)

        val third = Sm2Algorithm.calculateNextReview(
            quality = Sm2Quality.EASY,
            previousRepetitions = second.repetitions,
            previousIntervalDays = second.intervalDays,
            previousEaseFactor = second.easeFactor,
            nowMs = now
        )
        assertEquals(3, third.repetitions)
        assertTrue(third.intervalDays > 10)
    }

    @Test
    fun sm2_againResetsRepetitions() {
        val reset = Sm2Algorithm.calculateNextReview(
            quality = Sm2Quality.AGAIN,
            previousRepetitions = 4,
            previousIntervalDays = 20,
            previousEaseFactor = 2.5
        )
        assertEquals(0, reset.repetitions)
        assertEquals(1, reset.intervalDays)
    }

    @Test
    fun speechAnalytics_detectsFillersAndCalculatesWpm() {
        val transcript = "Um I basically led the migration, you know, and actually cut latency by forty percent."
        val metrics = SpeechAnalytics.analyze(
            transcript = transcript,
            durationSeconds = 30,
            longPauseCount = 2
        )
        assertEquals(15, metrics.wordCount)
        assertEquals(30, metrics.wordsPerMinute)
        assertEquals(4, metrics.fillerWordCount)
        assertEquals(2, metrics.longPauseCount)
        assertTrue(metrics.detectedFillers.contains("um"))
        assertTrue(metrics.detectedFillers.contains("you know"))
    }
}
