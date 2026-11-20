package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiQueueState
import com.example.data.local.PrepPlanTaskEntity
import com.example.ui.components.AiRateLimitBanner
import com.example.ui.components.ErrorRetryCard
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.ToolsUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TellMeAboutYourselfScreen(
    state: ToolsUiState,
    queueState: AiQueueState,
    onUpdateDraft: (String) -> Unit,
    onGenerateOrRefine: (targetSec: Int, focus: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var targetSec by remember { mutableIntStateOf(60) }
    var focus by remember { mutableStateOf("Impact & Architecture") }
    val focuses = listOf("Impact & Architecture", "Leadership", "Career Pivot", "Concise Startup Pitch")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Tell Me About Yourself") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AiRateLimitBanner(queueState = queueState)
                Text(
                    "Draft or generate a crisp elevator pitch grounded in your saved profile and resume.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Target Spoken Length", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(45, 60, 90).forEach { sec ->
                        FilterChip(
                            selected = targetSec == sec,
                            onClick = { targetSec = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }

                Text("Emphasis", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    focuses.forEach { f ->
                        FilterChip(
                            selected = focus == f,
                            onClick = { focus = f },
                            label = { Text(f) }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.tmayDraft,
                    onValueChange = onUpdateDraft,
                    label = { Text("Your Rough Draft (Optional)") },
                    placeholder = { Text("Paste what you usually say, or leave blank to generate fresh...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onGenerateOrRefine(targetSec, focus) },
                    enabled = !state.tmayLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("tmay_generate_button")
                ) {
                    Text(if (state.tmayLoading) "Refining..." else "Generate / Refine Pitch")
                }

                if (state.tmayLoading) {
                    ShimmerLoadingCard(label = "Crafting your elevator pitch...")
                }
                if (!state.errorMessage.isNullOrBlank()) {
                    ErrorRetryCard(message = state.errorMessage)
                }
                if (state.tmayResult.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = state.tmayResult,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryNegotiationScreen(
    state: ToolsUiState,
    queueState: AiQueueState,
    onUpdateContext: (role: String, company: String, targetComp: String) -> Unit,
    onStartSimulation: () -> Unit,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var messageInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Salary Negotiation Simulator") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AiRateLimitBanner(queueState = queueState)

            if (state.negMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Practice anchoring, counter-offers, and equity/signing-bonus trade-offs with an AI recruiter.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = state.negRole,
                        onValueChange = { onUpdateContext(it, state.negCompany, state.negTargetComp) },
                        label = { Text("Offer Role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.negCompany,
                        onValueChange = { onUpdateContext(state.negRole, it, state.negTargetComp) },
                        label = { Text("Company") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.negTargetComp,
                        onValueChange = { onUpdateContext(state.negRole, state.negCompany, it) },
                        label = { Text("Target Compensation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = onStartSimulation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_negotiation_button")
                    ) {
                        Text("Start Offer Call Simulation")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.negMessages) { msg ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (msg.isUser) "You" else "Recruiter",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                if (!msg.tacticTip.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            "Coach Tip (Leverage ${msg.leverageScore ?: 6}/10): ${msg.tacticTip}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.negLoading) {
                        item { ShimmerLoadingCard(label = "Recruiter is responding...") }
                    }
                    if (!state.errorMessage.isNullOrBlank()) {
                        item { ErrorRetryCard(message = state.errorMessage) }
                    }
                }

                Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .padding(12.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            label = { Text("Your counter-offer or reply") },
                            minLines = 1,
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onSendMessage(messageInput)
                                messageInput = ""
                            },
                            enabled = messageInput.isNotBlank() && !state.negLoading,
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodingRoundScreen(
    state: ToolsUiState,
    queueState: AiQueueState,
    onUpdateSettings: (language: String, difficulty: String) -> Unit,
    onUpdateCode: (String) -> Unit,
    onGenerateProblem: () -> Unit,
    onSubmitReview: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val langs = listOf("Kotlin", "Python", "Java", "TypeScript", "C++")
    val diffs = listOf("Easy", "Medium", "Hard")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Coding & Whiteboard Lab") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AiRateLimitBanner(queueState = queueState)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    langs.forEach { lang ->
                        FilterChip(
                            selected = state.codingLanguage == lang,
                            onClick = { onUpdateSettings(lang, state.codingDifficulty) },
                            label = { Text(lang) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    diffs.forEach { diff ->
                        FilterChip(
                            selected = state.codingDifficulty == diff,
                            onClick = { onUpdateSettings(state.codingLanguage, diff) },
                            label = { Text(diff) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onGenerateProblem,
                    enabled = !state.codingLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.codingLoading && state.codingProblem.isBlank()) "Generating..." else "Generate Coding Problem")
                }

                if (state.codingProblem.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Text(
                            state.codingProblem,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = state.codingEditorText,
                    onValueChange = onUpdateCode,
                    label = { Text("Code Editor (${state.codingLanguage})") },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, lineHeight = 19.sp),
                    minLines = 8,
                    maxLines = 18,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coding_editor_input")
                )

                Button(
                    onClick = onSubmitReview,
                    enabled = state.codingEditorText.isNotBlank() && !state.codingLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("coding_review_button")
                ) {
                    Text(if (state.codingLoading) "Reviewing Code..." else "Run AI Code Review")
                }

                if (state.codingLoading) {
                    ShimmerLoadingCard(label = "Analyzing complexity, edge cases & correctness...")
                }
                if (!state.errorMessage.isNullOrBlank()) {
                    ErrorRetryCard(message = state.errorMessage)
                }

                state.codingReview?.let { review ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Correctness: ${review.correctnessScore}/10",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        "Time: ${review.timeComplexity}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        "Space: ${review.spaceComplexity}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (review.edgeCases.isNotEmpty()) {
                                Text("Edge Cases", style = MaterialTheme.typography.titleSmall)
                                review.edgeCases.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (review.improvements.isNotEmpty()) {
                                Text("Improvements", style = MaterialTheme.typography.titleSmall)
                                review.improvements.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (review.refinedCode.isNotBlank()) {
                                Text("Optimal Reference Solution", style = MaterialTheme.typography.titleSmall)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Text(
                                        review.refinedCode,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepPlanScreen(
    tasks: List<PrepPlanTaskEntity>,
    isLoading: Boolean,
    errorMessage: String?,
    queueState: AiQueueState,
    weakTopics: List<String>,
    onGeneratePlan: (days: Int, weakTopics: List<String>) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedDays by remember { mutableIntStateOf(7) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Prep Plan Generator") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiRateLimitBanner(queueState = queueState)
                    Text("Select Schedule Horizon", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick = { selectedDays = days },
                                label = { Text("$days Days") }
                            )
                        }
                    }
                    Button(
                        onClick = { onGeneratePlan(selectedDays, weakTopics) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_prep_plan_button")
                    ) {
                        Text(if (isLoading) "Building $selectedDays-Day Plan..." else "Generate $selectedDays-Day Prep Plan")
                    }
                    if (isLoading) {
                        ShimmerLoadingCard(label = "Designing your daily study schedule...")
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        ErrorRetryCard(message = errorMessage)
                    }
                }
            }

            items(tasks, key = { it.id }) { task ->
                Card(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { onToggleTask(task.id, it) }
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Day ${task.dayNumber}: ${task.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        task.focusArea,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                task.description,
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
