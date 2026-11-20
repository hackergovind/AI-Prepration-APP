package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.EmptyStateCard
import com.example.viewmodel.ProgressUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onStartWeaknessDrill: (List<String>) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Progress & Analytics", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Track growth & drill weak topics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        onClick = onNavigateToSettings,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // KPI Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatKpiCard(
                        title = "Sessions",
                        value = "${uiState.totalSessions}",
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        title = "Avg Score",
                        value = if (uiState.totalSessions == 0) "—"
                        else String.format(Locale.US, "%.1f", uiState.averageScore),
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        title = "Streak",
                        value = "${uiState.practiceStreak}d",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Score Trend Chart
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Score Trend Over Time", style = MaterialTheme.typography.titleMedium)
                        if (uiState.scoreTrend.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Filled.Insights,
                                title = "No completed sessions yet",
                                subtitle = "Complete your first mock interview to unlock score trend analytics."
                            )
                        } else {
                            ScoreTrendChart(points = uiState.scoreTrend)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                uiState.scoreTrend.forEach { (label, score) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            String.format(Locale.US, "%.1f", score),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Topic Strength & Weakness Heatmap + Weakness Drill
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Topic Strength & Weakness Heatmap", style = MaterialTheme.typography.titleMedium)
                        if (uiState.topicMetrics.isEmpty()) {
                            Text(
                                "Answer interview questions to reveal your strongest and weakest topics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.topicMetrics.forEach { metric ->
                                    val containerColor = when {
                                        metric.averageScore >= 7.8 -> MaterialTheme.colorScheme.tertiaryContainer
                                        metric.averageScore >= 6.0 -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    }
                                    val textColor = when {
                                        metric.averageScore >= 7.8 -> MaterialTheme.colorScheme.onTertiaryContainer
                                        metric.averageScore >= 6.0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = containerColor
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                metric.topic,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = textColor
                                            )
                                            Text(
                                                "${String.format(Locale.US, "%.1f", metric.averageScore)}/10 (${metric.attempts}x)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onStartWeaknessDrill(
                                    uiState.weakestTopics.ifEmpty { listOf("Behavioral STAR", "Technical Depth") }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("weakness_drill_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (uiState.weakestTopics.isEmpty()) "Start Targeted Practice Drill"
                                else "Weakness Drill: ${uiState.weakestTopics.joinToString(", ")}"
                            )
                        }
                    }
                }

                // Badges & Achievements
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Achievements & Badges", style = MaterialTheme.typography.titleMedium)
                        }
                        uiState.badges.forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (badge.unlocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (badge.unlocked) Icons.Filled.LocalFireDepartment else Icons.Filled.Lock,
                                        contentDescription = if (badge.unlocked) "Unlocked" else "Locked",
                                        tint = if (badge.unlocked) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(
                                            badge.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            badge.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreTrendChart(points: List<Pair<String, Double>>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillTop = lineColor.copy(alpha = 0.25f)
    val fillBottom = lineColor.copy(alpha = 0.0f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val leftPad = 12.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 12.dp.toPx()
        val usableW = size.width - leftPad - rightPad
        val usableH = size.height - topPad - bottomPad

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = topPad + usableH * (1f - fraction)
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val coords = points.mapIndexed { idx, (_, score) ->
            val x = if (points.size == 1) {
                leftPad + usableW / 2f
            } else {
                leftPad + (idx.toFloat() / (points.size - 1)) * usableW
            }
            val norm = (score.toFloat() / 10f).coerceIn(0f, 1f)
            val y = topPad + usableH * (1f - norm)
            Offset(x, y)
        }

        if (coords.size > 1) {
            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(coords.last().x, topPad + usableH)
                lineTo(coords.first().x, topPad + usableH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        coords.forEach { pt ->
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = pt)
        }
    }
}
