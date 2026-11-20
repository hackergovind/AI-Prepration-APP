package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiQueueState
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * Airy Sky-Blue Frost ambient background matching the reference UI's soft top wash.
 */
@Composable
fun SkyBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val topGlow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val accentDot = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topGlow, bg, bg),
                    startY = 0f,
                    endY = size.height * 0.42f
                )
            )
            // Subtle constellation nodes on top edges like the reference header
            drawCircle(color = accentDot, radius = 3.dp.toPx(), center = Offset(size.width * 0.14f, 44.dp.toPx()))
            drawCircle(color = accentDot, radius = 2.dp.toPx(), center = Offset(size.width * 0.86f, 38.dp.toPx()))
            drawLine(
                color = accentDot.copy(alpha = 0.08f),
                start = Offset(0f, 64.dp.toPx()),
                end = Offset(size.width * 0.14f, 44.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accentDot.copy(alpha = 0.08f),
                start = Offset(size.width * 0.86f, 38.dp.toPx()),
                end = Offset(size.width, 62.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
        content()
    }
}

/**
 * 3-tile digital countdown clock widget (e.g., 02 : 45 : 18) modeled after the reference Reminder card.
 */
@Composable
fun CountdownTileClock(
    remainingMillis: Long,
    modifier: Modifier = Modifier
) {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    val (t1, t2, t3) = if (days > 0) {
        Triple(
            String.format(Locale.US, "%02d", days.coerceAtMost(99)),
            String.format(Locale.US, "%02d", hours),
            String.format(Locale.US, "%02d", minutes)
        )
    } else {
        Triple(
            String.format(Locale.US, "%02d", hours),
            String.format(Locale.US, "%02d", minutes),
            String.format(Locale.US, "%02d", seconds)
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ClockDigitBox(t1)
        Text(":", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ClockDigitBox(t2)
        Text(":", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ClockDigitBox(t3)
    }
}

@Composable
private fun ClockDigitBox(value: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
        )
    }
}

/**
 * Rounded white pill search bar with trailing pale-blue filter button, matching the reference UI.
 */
@Composable
fun PillSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onFilterClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    searchTag: String = "pill_search_input"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(searchTag)
                )
            }
            if (onFilterClick != null) {
                Surface(
                    onClick = onFilterClick,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Filter options",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerifiedPillBadge(
    label: String,
    onBlueCard: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
            tint = if (onBlueCard) Color(0xFF86EFAC) else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (onBlueCard) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun CoralBookmarkButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onBlueCard: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (onBlueCard) Color.White.copy(alpha = 0.22f) else Color(0xFFFEE2E2),
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Signature floating frosted bottom navigation bar with 4 outer tabs and a raised glowing
 * Sky-Blue diamond/squircle center quick-launch button.
 */
data class FloatingDockTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun FloatingSkyBottomBar(
    tabs: List<FloatingDockTab>,
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    onCenterActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leftTabs = tabs.take(2)
    val rightTabs = tabs.drop(2).take(2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                leftTabs.forEach { tab ->
                    DockTabItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Spacer for raised center diamond button
                Spacer(modifier = Modifier.width(68.dp))

                rightTabs.forEach { tab ->
                    DockTabItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Raised Glowing Center Diamond Action Button
        Box(
            modifier = Modifier
                .offset(y = (-22).dp)
                .size(60.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
                .graphicsLayer { rotationZ = 45f }
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF5FC3FF),
                            MaterialTheme.colorScheme.primary
                        )
                    )
                )
                .clickable(onClick = onCenterActionClick)
                .testTag("nav_center_quick_mock"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Quick Start AI Mock Interview",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = -45f }
            )
        }
    }
}

@Composable
private fun DockTabItem(
    tab: FloatingDockTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}

/**
 * Sky-Blue Gradient Hero Card used in Onboarding and Recommended Featured Cards.
 */
@Composable
fun PrepPilotHeroArt(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val startColor = Color(0xFF53BCF9)
    val endColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = size.minDimension * 0.65f,
                center = Offset(size.width * 0.90f, size.height * 0.18f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.40f,
                center = Offset(size.width * 0.10f, size.height * 0.90f)
            )
            val wavePath = Path().apply {
                moveTo(0f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.30f, size.height * 0.45f,
                    size.width * 0.65f, size.height * 0.95f,
                    size.width, size.height * 0.40f
                )
            }
            drawPath(
                path = wavePath,
                color = Color.White.copy(alpha = 0.18f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Box(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun AiRateLimitBanner(queueState: AiQueueState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = queueState.isRateLimited && !queueState.statusBanner.isNullOrBlank()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.HourglassTop,
                    contentDescription = "AI request queued",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = queueState.statusBanner.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ShimmerLoadingCard(
    label: String = "PrepPilot AI is thinking...",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(baseColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(baseColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(baseColor)
            )
        }
    }
}

@Composable
fun ErrorRetryCard(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun ScoreDimensionRow(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (score.coerceIn(0, 10) / 10f),
        animationSpec = tween(550),
        label = "score_$label"
    )
    val barColor = when {
        score >= 8 -> MaterialTheme.colorScheme.tertiary
        score >= 6 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label score $score out of 10" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$score/10",
                style = MaterialTheme.typography.labelLarge,
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun StarMethodRow(
    situation: Boolean,
    task: Boolean,
    action: Boolean,
    result: Boolean,
    note: String,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("S", "Situation", situation),
        Triple("T", "Task", task),
        Triple("A", "Action", action),
        Triple("R", "Result", result)
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STAR Method Check",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { (letter, title, present) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (present) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (present) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = "$title ${if (present) "present" else "missing"}",
                                tint = if (present) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (present) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (present) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            if (note.isNotBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VoiceWaveformCanvas(
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val barColor = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val count = 24
        val spacing = size.width / count
        val barWidth = spacing * 0.45f
        for (i in 0 until count) {
            val amplitude = if (isListening) {
                (0.28f + 0.68f * abs(sin(phase + i * 0.45f)))
            } else {
                0.18f
            }
            val barHeight = size.height * amplitude
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * spacing + spacing * 0.25f, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
