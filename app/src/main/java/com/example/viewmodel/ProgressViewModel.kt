package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BadgeItem
import com.example.data.local.PrepRepository
import com.example.data.local.TopicMetric
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val totalSessions: Int = 0,
    val averageScore: Double = 0.0,
    val practiceStreak: Int = 0,
    val scoreTrend: List<Pair<String, Double>> = emptyList(),
    val topicMetrics: List<TopicMetric> = emptyList(),
    val weakestTopics: List<String> = emptyList(),
    val badges: List<BadgeItem> = emptyList()
)

class ProgressViewModel(repository: PrepRepository) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        repository.sessionsFlow,
        repository.allTurnsFlow,
        repository.savedQuestionsFlow,
        repository.allDailyChallengesFlow,
        repository.preferencesFlow
    ) { sessions, turns, saved, challenges, prefs ->
        val completed = sessions.filter { it.completed }.sortedBy { it.createdAt }
        val avg = if (completed.isNotEmpty()) completed.map { it.overallScore }.average() else 0.0
        val trend = completed.takeLast(10).mapIndexed { idx, s ->
            "#${idx + 1}" to s.overallScore
        }
        val topicMetrics = repository.computeTopicMetrics(turns)
        val weakest = topicMetrics.take(3).map { it.topic }
        val badges = repository.computeBadges(sessions, saved, challenges, prefs.practiceStreak)

        ProgressUiState(
            totalSessions = completed.size,
            averageScore = avg,
            practiceStreak = prefs.practiceStreak,
            scoreTrend = trend,
            topicMetrics = topicMetrics,
            weakestTopics = weakest,
            badges = badges
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())
}
