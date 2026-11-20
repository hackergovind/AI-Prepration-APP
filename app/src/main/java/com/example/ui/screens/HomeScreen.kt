package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.InterviewSessionEntity
import com.example.data.local.PrepPlanTaskEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserProfileEntity
import com.example.ui.components.CoralBookmarkButton
import com.example.ui.components.CountdownTileClock
import com.example.ui.components.ErrorRetryCard
import com.example.ui.components.PillSearchBar
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.VerifiedPillBadge
import com.example.viewmodel.DailyChallengeUiState
import com.example.viewmodel.InterviewBuilderConfig
import com.example.viewmodel.InterviewPersona
import kotlinx.coroutines.delay
import java.util.Locale

private data class RecommendedMockPack(
    val id: String,
    val badgeTitle: String,
    val verifiedLabel: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val tags: List<String>,
    val icon: ImageVector,
    val configBuilder: (UserProfileEntity?) -> InterviewBuilderConfig,
    val voiceFirst: Boolean = false
)

private data class ToolFeedCard(
    val id: String,
    val categoryLabel: String,
    val verifiedLabel: String,
    val title: String,
    val subtitle: String,
    val tags: List<String>,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    profile: UserProfileEntity?,
    preferences: UserPreferences,
    sessions: List<InterviewSessionEntity>,
    prepPlanTasks: List<PrepPlanTaskEntity>,
    dailyUiState: DailyChallengeUiState,
    todayChallenge: DailyChallengeEntity?,
    onLoadDailyChallenge: () -> Unit,
    onSubmitDailyAnswer: (DailyChallengeEntity, String) -> Unit,
    onTogglePrepTask: (Long, Boolean) -> Unit,
    onSetReminder: (title: String, targetEpochMs: Long) -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartPresetMock: (InterviewBuilderConfig, startVoice: Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenToolTmay: () -> Unit,
    onOpenToolSalary: () -> Unit,
    onOpenToolCoding: () -> Unit,
    onOpenToolPrepPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("See All") }
    var showReminderDialog by remember { mutableStateOf(false) }
    var bookmarkedPacks by remember { mutableStateOf(setOf<String>()) }

    // Live 1-second tick for the Reminder Countdown Card
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val effectiveTargetMs = remember(preferences.reminderEpochMs) {
        if (preferences.reminderEpochMs > System.currentTimeMillis()) {
            preferences.reminderEpochMs
        } else {
            System.currentTimeMillis() + (2L * 3600_000L + 45L * 60_000L)
        }
    }
    val remainingMillis = (effectiveTargetMs - nowMs).coerceAtLeast(0L)

    LaunchedEffect(todayChallenge) {
        if (todayChallenge == null && dailyUiState is DailyChallengeUiState.Idle) {
            onLoadDailyChallenge()
        }
    }

    if (showReminderDialog) {
        ReminderEditorDialog(
            initialTitle = preferences.reminderTitle,
            onDismiss = { showReminderDialog = false },
            onSave = { title, targetMs ->
                onSetReminder(title, targetMs)
                showReminderDialog = false
            }
        )
    }

    val categories = listOf("See All", "Behavioral", "Technical", "System Design", "Coding", "Voice")

    val targetCompany = profile?.targetCompanies
        ?.split(",")
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: "AI Tailored"
    val targetRole = profile?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer"

    val recommendedPacks = remember(profile, preferences) {
        listOf(
            RecommendedMockPack(
                id = "tailored",
                badgeTitle = targetCompany,
                verifiedLabel = "AI Ready",
                title = targetRole,
                subtitle = "5 Questions • Resume & JD Grounded",
                category = "Technical",
                tags = listOf("5 Qs", "Adaptive"),
                icon = Icons.Filled.Psychology,
                configBuilder = { p ->
                    InterviewBuilderConfig(
                        role = p?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                        company = targetCompany,
                        seniority = p?.experienceLevel ?: "Mid-Level",
                        interviewType = "Mixed",
                        difficulty = "Adaptive",
                        questionCount = 5,
                        timeLimitSec = preferences.defaultAnswerTimeSec,
                        language = preferences.defaultLanguage,
                        persona = InterviewPersona.FRIENDLY_HR,
                        useResumeAndJd = true,
                        practiceMode = true
                    )
                }
            ),
            RecommendedMockPack(
                id = "voice_warmup",
                badgeTitle = "Voice Studio",
                verifiedLabel = "Speech Analytics",
                title = "5-Min Voice Warm-up",
                subtitle = "3 Questions • WPM & Filler Check",
                category = "Voice",
                tags = listOf("Voice", "3 Qs"),
                icon = Icons.Filled.Mic,
                voiceFirst = true,
                configBuilder = { p ->
                    InterviewBuilderConfig(
                        role = p?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                        seniority = p?.experienceLevel ?: "Mid-Level",
                        interviewType = "Behavioral",
                        difficulty = "Easy",
                        questionCount = 3,
                        timeLimitSec = 90,
                        language = preferences.defaultLanguage,
                        persona = InterviewPersona.FRIENDLY_HR,
                        practiceMode = true
                    )
                }
            ),
            RecommendedMockPack(
                id = "star_drill",
                badgeTitle = "Behavioral",
                verifiedLabel = "STAR Check",
                title = "Behavioral STAR Drill",
                subtitle = "4 Questions • Leadership & Conflict",
                category = "Behavioral",
                tags = listOf("STAR", "4 Qs"),
                icon = Icons.Filled.RecordVoiceOver,
                configBuilder = { p ->
                    InterviewBuilderConfig(
                        role = p?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                        seniority = p?.experienceLevel ?: "Mid-Level",
                        interviewType = "Behavioral",
                        difficulty = "Medium",
                        questionCount = 4,
                        timeLimitSec = 120,
                        language = preferences.defaultLanguage,
                        persona = InterviewPersona.PANEL,
                        practiceMode = true
                    )
                }
            ),
            RecommendedMockPack(
                id = "system_design",
                badgeTitle = "Architecture",
                verifiedLabel = "Tech Lead",
                title = "System Design Round",
                subtitle = "3 Deep Prompts • Scale & Trade-offs",
                category = "System Design",
                tags = listOf("Hard", "3 Qs"),
                icon = Icons.Filled.Bolt,
                configBuilder = { p ->
                    InterviewBuilderConfig(
                        role = p?.targetRole?.ifBlank { "Software Engineer" } ?: "Software Engineer",
                        seniority = p?.experienceLevel ?: "Senior",
                        interviewType = "System Design",
                        difficulty = "Hard",
                        questionCount = 3,
                        timeLimitSec = 180,
                        language = preferences.defaultLanguage,
                        persona = InterviewPersona.STRICT_TECH_LEAD,
                        practiceMode = true
                    )
                }
            )
        )
    }

    val toolCards = remember(onOpenToolTmay, onOpenToolSalary, onOpenToolCoding, onOpenToolPrepPlan) {
        listOf(
            ToolFeedCard(
                id = "tmay",
                categoryLabel = "Pitch Coach",
                verifiedLabel = "AI Refiner",
                title = "Tell Me About Yourself",
                subtitle = "Craft a crisp 60-second opening story",
                tags = listOf("45-90s", "Resume Grounded", "Instant"),
                icon = Icons.Filled.RecordVoiceOver,
                testTag = "tool_card_tmay",
                onClick = onOpenToolTmay
            ),
            ToolFeedCard(
                id = "coding",
                categoryLabel = "Whiteboard",
                verifiedLabel = "AI Code Review",
                title = "Coding & Algorithm Lab",
                subtitle = "Write code & check Big-O complexity",
                tags = listOf("Coding", "Kotlin/Python", "Edge Cases"),
                icon = Icons.Filled.Code,
                testTag = "tool_card_coding",
                onClick = onOpenToolCoding
            ),
            ToolFeedCard(
                id = "salary",
                categoryLabel = "Offer Lab",
                verifiedLabel = "Roleplay",
                title = "Salary Negotiation Simulator",
                subtitle = "Practice counter-offers with live tactic tips",
                tags = listOf("Voice/Text", "Compensation", "Tips"),
                icon = Icons.Filled.AttachMoney,
                testTag = "tool_card_salary",
                onClick = onOpenToolSalary
            ),
            ToolFeedCard(
                id = "plan",
                categoryLabel = "Roadmap",
                verifiedLabel = "Checklist",
                title = "7 / 14 / 30-Day Prep Plan",
                subtitle = "Daily targeted tasks tailored to weak areas",
                tags = listOf("Daily Tasks", "Schedule", "Trackable"),
                icon = Icons.Filled.CalendarMonth,
                testTag = "tool_card_plan",
                onClick = onOpenToolPrepPlan
            )
        )
    }

    val filteredPacks = recommendedPacks.filter { pack ->
        val matchesCategory = selectedCategory == "See All" || pack.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
            pack.title.contains(searchQuery, ignoreCase = true) ||
            pack.badgeTitle.contains(searchQuery, ignoreCase = true) ||
            pack.subtitle.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    val filteredTools = toolCards.filter { tool ->
        val matchesCategory = selectedCategory == "See All" ||
            (selectedCategory == "Coding" && tool.id == "coding") ||
            (selectedCategory == "Voice" && (tool.id == "tmay" || tool.id == "salary")) ||
            (selectedCategory == "Behavioral" && (tool.id == "tmay" || tool.id == "plan")) ||
            (selectedCategory == "Technical" && (tool.id == "coding" || tool.id == "plan")) ||
            (selectedCategory == "System Design" && tool.id == "plan")
        val matchesQuery = searchQuery.isBlank() ||
            tool.title.contains(searchQuery, ignoreCase = true) ||
            tool.subtitle.contains(searchQuery, ignoreCase = true) ||
            tool.tags.any { it.contains(searchQuery, ignoreCase = true) }
        matchesCategory && matchesQuery
    }

    val initials = (profile?.name?.ifBlank { "Candidate" } ?: "Candidate")
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "PP" }

    val completedSessionsCount = sessions.count { it.completed }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Top Header Bar (Avatar + Role Subtitle on Left, Streak + Bell + Settings on Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onEditProfile)
                            .padding(vertical = 4.dp)
                            .testTag("home_edit_profile_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF56C0FA), MaterialTheme.colorScheme.primary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile?.name?.ifBlank { "Candidate" } ?: "Candidate",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Edit profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "$targetRole • ${completedSessionsCount} mocks",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    "${preferences.practiceStreak}d",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            onClick = { showReminderDialog = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("home_reminder_bell_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.NotificationsNone,
                                    contentDescription = "Set interview reminder",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onNavigateToSettings,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("nav_settings")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Reminder & 3-Tile Digital Countdown Card (Reference UI Signature)
                Surface(
                    onClick = { showReminderDialog = true },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_reminder_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Alarm,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = "Reminder",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preferences.reminderTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        CountdownTileClock(remainingMillis = remainingMillis)
                    }
                }

                // 3. Pill Search Bar & Filter Button
                PillSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search mocks, tools, or topics...",
                    onFilterClick = onNavigateToPractice,
                    searchTag = "home_search_input"
                )

                // 4. Horizontal Filter Pill Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val selected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = if (selected) 3.dp else 0.dp
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                // 5. Recommended Horizontal Carousel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recommended", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onNavigateToPractice) {
                        Text("See all >>", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(filteredPacks.ifEmpty { recommendedPacks }, key = { _, p -> p.id }) { idx, pack ->
                        val isBlueHero = idx == 0
                        val isSaved = pack.id in bookmarkedPacks
                        RecommendedPackCard(
                            pack = pack,
                            isBlueHero = isBlueHero,
                            isBookmarked = isSaved,
                            onBookmarkToggle = {
                                bookmarkedPacks = if (isSaved) bookmarkedPacks - pack.id else bookmarkedPacks + pack.id
                            },
                            onStart = {
                                onStartPresetMock(pack.configBuilder(profile), pack.voiceFirst)
                            },
                            modifier = if (idx == 0) Modifier.testTag("home_start_interview_button") else Modifier
                        )
                    }
                }

                // 6. Daily Challenge & Career Toolkit ("Latest Practice")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily & Quick Tools", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onOpenToolPrepPlan) {
                        Text("Prep Plan >>", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                DailyChallengeSection(
                    dailyUiState = dailyUiState,
                    todayChallenge = todayChallenge,
                    onRetry = onLoadDailyChallenge,
                    onSubmitAnswer = onSubmitDailyAnswer
                )

                // Active Prep Plan compact card when user has tasks
                if (prepPlanTasks.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Today's Prep Plan", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = onOpenToolPrepPlan) { Text("View All") }
                            }
                            prepPlanTasks.filter { !it.completed }.take(2).forEach { task ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = task.completed,
                                        onCheckedChange = { onTogglePrepTask(task.id, it) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Day ${task.dayNumber}: ${task.title}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            task.focusArea,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                filteredTools.forEach { tool ->
                    Card(
                        onClick = tool.onClick,
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(tool.testTag)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                tool.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(tool.categoryLabel, style = MaterialTheme.typography.titleSmall)
                                        VerifiedPillBadge(label = tool.verifiedLabel)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Open",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(tool.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    tool.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tool.tags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
private fun RecommendedPackCard(
    pack: RecommendedMockPack,
    isBlueHero: Boolean,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    val contentBlock: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .width(264.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isBlueHero) Color.White else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = pack.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = pack.badgeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isBlueHero) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VerifiedPillBadge(label = pack.verifiedLabel, onBlueCard = isBlueHero)
                    }
                }
                CoralBookmarkButton(
                    isBookmarked = isBookmarked,
                    onClick = onBookmarkToggle,
                    onBlueCard = isBlueHero
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = pack.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isBlueHero) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pack.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlueHero) Color.White.copy(alpha = 0.88f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pack.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBlueHero) Color.White.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBlueHero) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onStart,
                    shape = RoundedCornerShape(14.dp),
                    color = if (isBlueHero) Color.White.copy(alpha = 0.26f)
                    else MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Start",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    if (isBlueHero) {
        Box(
            modifier = modifier
                .clip(cardShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF56C1FA), Color(0xFF2192EB))
                    )
                )
                .clickable(onClick = onStart)
        ) {
            contentBlock()
        }
    } else {
        Surface(
            modifier = modifier,
            onClick = onStart,
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            contentBlock()
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, targetEpochMs: Long) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selectedHours by remember { mutableLongStateOf(2L) }
    val options = listOf(2L to "In 2 hrs", 12L to "In 12 hrs", 24L to "Tomorrow", 72L to "In 3 days")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Interview Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Interview Label") },
                    placeholder = { Text("e.g., Zoom Technical Screen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Countdown Target", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { (hrs, label) ->
                        FilterChip(
                            selected = selectedHours == hrs,
                            onClick = { selectedHours = hrs },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = System.currentTimeMillis() + selectedHours * 3600_000L
                    onSave(title, target)
                }
            ) { Text("Save Reminder") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DailyChallengeSection(
    dailyUiState: DailyChallengeUiState,
    todayChallenge: DailyChallengeEntity?,
    onRetry: () -> Unit,
    onSubmitAnswer: (DailyChallengeEntity, String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var showHint by remember { mutableStateOf(false) }

    when {
        dailyUiState is DailyChallengeUiState.Loading && todayChallenge == null -> {
            ShimmerLoadingCard(label = "Preparing today's Daily Challenge...")
        }
        dailyUiState is DailyChallengeUiState.Error && todayChallenge == null -> {
            ErrorRetryCard(message = dailyUiState.message, onRetry = onRetry)
        }
        todayChallenge != null -> {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Daily Challenge", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = todayChallenge.topic,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = todayChallenge.question,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    if (!todayChallenge.completed) {
                        if (showHint && todayChallenge.hint.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(todayChallenge.hint, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("Your 2-minute answer") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("daily_challenge_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = { showHint = !showHint }) {
                                Text(if (showHint) "Hide Hint" else "Show Hint")
                            }
                            Button(
                                onClick = { onSubmitAnswer(todayChallenge, draft) },
                                enabled = draft.isNotBlank() && dailyUiState !is DailyChallengeUiState.Loading,
                                modifier = Modifier.testTag("daily_challenge_submit")
                            ) {
                                Text(if (dailyUiState is DailyChallengeUiState.Loading) "Scoring..." else "Submit")
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "Completed • Score ${todayChallenge.score}/10",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    todayChallenge.feedbackSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                if (todayChallenge.idealAnswer.isNotBlank()) {
                                    Text(
                                        "Ideal angle: ${todayChallenge.idealAnswer}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Load today's 1-question warm-up", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRetry) { Text("Load") }
                }
            }
        }
    }
}
