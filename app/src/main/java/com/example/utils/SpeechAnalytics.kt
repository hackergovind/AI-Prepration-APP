package com.example.utils

import kotlin.math.max
import kotlin.math.roundToInt

data class SpeechMetrics(
    val wordCount: Int,
    val wordsPerMinute: Int,
    val fillerWordCount: Int,
    val detectedFillers: List<String>,
    val longPauseCount: Int,
    val durationSeconds: Int
)

/**
 * Analyzes spoken interview transcripts for pace (WPM), filler words, and long pauses.
 */
object SpeechAnalytics {
    private val singleFillers = setOf(
        "um", "uh", "er", "ah", "like", "basically", "actually", "literally", "totally", "honestly"
    )
    private val phraseFillers = listOf("you know", "sort of", "kind of", "i mean")

    fun analyze(
        transcript: String,
        durationSeconds: Int,
        longPauseCount: Int
    ): SpeechMetrics {
        val clean = transcript.trim()
        if (clean.isEmpty()) {
            return SpeechMetrics(
                wordCount = 0,
                wordsPerMinute = 0,
                fillerWordCount = 0,
                detectedFillers = emptyList(),
                longPauseCount = longPauseCount,
                durationSeconds = durationSeconds
            )
        }

        val words = clean
            .lowercase()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-z0-9']"), "") }
            .filter { it.isNotBlank() }

        val wordCount = words.size
        val safeMinutes = max(durationSeconds, 1) / 60.0
        val wpm = if (durationSeconds > 0) (wordCount / safeMinutes).roundToInt() else 0

        val foundFillers = mutableListOf<String>()
        words.forEach { token ->
            if (token in singleFillers) {
                foundFillers.add(token)
            }
        }

        val normalizedLower = clean.lowercase()
        phraseFillers.forEach { phrase ->
            val matches = Regex("\\b${Regex.escape(phrase)}\\b").findAll(normalizedLower).count()
            repeat(matches) { foundFillers.add(phrase) }
        }

        return SpeechMetrics(
            wordCount = wordCount,
            wordsPerMinute = wpm,
            fillerWordCount = foundFillers.size,
            detectedFillers = foundFillers.distinct(),
            longPauseCount = longPauseCount,
            durationSeconds = durationSeconds
        )
    }
}
