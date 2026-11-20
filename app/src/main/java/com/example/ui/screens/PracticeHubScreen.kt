package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.InterviewPresetEntity
import com.example.ui.components.VerifiedPillBadge
import com.example.viewmodel.InterviewBuilderConfig
import com.example.viewmodel.InterviewPersona
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeHubScreen(
    config: InterviewBuilderConfig,
    presets: List<InterviewPresetEntity>,
    onUpdateConfig: ((InterviewBuilderConfig) -> InterviewBuilderConfig) -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (InterviewPresetEntity) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onLaunchInterview: () -> Unit,
    onLaunchQuickPack: (InterviewBuilderConfig) -> Unit,
    onOpenToolTmay: () -> Unit,
    onOpenToolSalary: () -> Unit,
    onOpenToolCoding: () -> Unit,
    onOpenToolPrepPlan: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Quick Start, 1 = Custom Builder
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetTitleInput by remember { mutableStateOf("") }

    val seniorities = listOf("Entry-Level", "Mid-Level", "Senior", "Staff / Principal", "Manager")
    val interviewTypes = listOf("Mixed", "HR", "Behavioral", "Technical", "System Design", "Case Study", "Coding")
    val difficulties = listOf("Easy", "Medium", "Hard", "Adaptive")
    val languages = listOf("English", "Spanish", "French", "German", "Hindi")
    val timeLimits = listOf(60 to "1m", 120 to "2m", 180 to "3m", 300 to "5m")

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Interview Preset") },
            text = {
                OutlinedTextField(
                    value = presetTitleInput,
                    onValueChange = { presetTitleInput = it },
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g., Stripe Backend Hard") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePreset(presetTitleInput)
                        presetTitleInput = ""
                        showSavePresetDialog = false
                    },
                    enabled = presetTitleInput.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Practice Studio", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "One-tap Smart Packs or custom rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (modeTab == 1) {
                            OutlinedButton(
                                onClick = { showSavePresetDialog = true },
                                modifier = Modifier.testTag("save_preset_button")
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
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
                }

                // Segmented Switcher: Quick Start vs Custom Builder
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Quick Start", "Custom Builder").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = modeTab == idx,
                            onClick = { modeTab = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2)
                        ) {
                            Text(label)
                        }
                    }
                }

                // Saved Presets strip (always visible if any exist)
                if (presets.isNotEmpty()) {
                    Text("Your Saved Presets", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            Surface(
                                onClick = {
                                    onLoadPreset(preset)
                                    modeTab = 1
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            preset.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "${preset.interviewType} • ${preset.difficulty}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    IconButton(onClick = { onDeletePreset(preset.id) }) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete preset",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (modeTab == 0) {
                    // Quick Start Smart Packs
                    val smartTemplates = listOf(
                        Triple(
                            "Rapid Behavioral STAR (3 Qs)",
                            "Friendly HR • STAR evaluation • 90s limit",
                            config.copy(interviewType = "Behavioral", questionCount = 3, timeLimitSec = 90, persona = InterviewPersona.FRIENDLY_HR, practiceMode = true)
                        ),
                        Triple(
                            "Strict Technical Deep-Dive (5 Qs)",
                            "Strict Tech Lead • Adaptive difficulty • 2m limit",
                            config.copy(interviewType = "Technical", difficulty = "Adaptive", questionCount = 5, timeLimitSec = 120, persona = InterviewPersona.STRICT_TECH_LEAD, practiceMode = true)
                        ),
                        Triple(
                            "Real Simulation Mode (5 Qs)",
                            "Panel Interviewer • No hints • End report",
                            config.copy(interviewType = "Mixed", difficulty = "Medium", questionCount = 5, timeLimitSec = 120, persona = InterviewPersona.PANEL, practiceMode = false)
                        ),
                        Triple(
                            "Startup Founder Culture Fit (4 Qs)",
                            "Ownership & Product Sense • Practice Mode",
                            config.copy(interviewType = "Mixed", difficulty = "Medium", questionCount = 4, timeLimitSec = 120, persona = InterviewPersona.STARTUP_FOUNDER, practiceMode = true)
                        )
                    )

                    smartTemplates.forEach { (title, subtitle, templateCfg) ->
                        Card(
                            onClick = { onLaunchQuickPack(templateCfg) },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Column {
                                        Text(title, style = MaterialTheme.typography.titleSmall)
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        VerifiedPillBadge("1-Tap Launch")
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Start",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(10.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text("Career Practice Labs", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabMiniCard("Intro Pitch", Icons.Filled.RecordVoiceOver, onOpenToolTmay, Modifier.weight(1f))
                        LabMiniCard("Negotiation", Icons.Filled.AttachMoney, onOpenToolSalary, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabMiniCard("Coding Lab", Icons.Filled.Code, onOpenToolCoding, Modifier.weight(1f))
                        LabMiniCard("Prep Plan", Icons.Filled.CalendarMonth, onOpenToolPrepPlan, Modifier.weight(1f))
                    }
                } else {
                    // Full Custom Interview Builder
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Role & Format", style = MaterialTheme.typography.titleMedium)

                            OutlinedTextField(
                                value = config.role,
                                onValueChange = { valText -> onUpdateConfig { it.copy(role = valText) } },
                                label = { Text("Role") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("builder_role_input")
                            )
                            OutlinedTextField(
                                value = config.company,
                                onValueChange = { valText -> onUpdateConfig { it.copy(company = valText) } },
                                label = { Text("Target Company (Optional)") },
                                placeholder = { Text("e.g., Google, Stripe, Startup") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Seniority", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                seniorities.forEach { s ->
                                    FilterChip(
                                        selected = config.seniority == s,
                                        onClick = { onUpdateConfig { it.copy(seniority = s) } },
                                        label = { Text(s) }
                                    )
                                }
                            }

                            Text("Interview Type", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                interviewTypes.forEach { t ->
                                    FilterChip(
                                        selected = config.interviewType == t,
                                        onClick = { onUpdateConfig { it.copy(interviewType = t) } },
                                        label = { Text(t) }
                                    )
                                }
                            }

                            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                difficulties.forEach { d ->
                                    FilterChip(
                                        selected = config.difficulty == d,
                                        onClick = { onUpdateConfig { it.copy(difficulty = d) } },
                                        label = { Text(d) }
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Interviewer & Rules", style = MaterialTheme.typography.titleMedium)

                            Text("Interviewer Persona", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InterviewPersona.entries.forEach { persona ->
                                    FilterChip(
                                        selected = config.persona == persona,
                                        onClick = { onUpdateConfig { it.copy(persona = persona) } },
                                        label = { Text(persona.label) }
                                    )
                                }
                            }

                            if (config.persona == InterviewPersona.CUSTOM) {
                                OutlinedTextField(
                                    value = config.customPersonaPrompt,
                                    onValueChange = { txt -> onUpdateConfig { it.copy(customPersonaPrompt = txt) } },
                                    label = { Text("Custom Persona System Prompt") },
                                    placeholder = { Text("Describe the interviewer's style and focus...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text("Questions: ${config.questionCount}", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = config.questionCount.toFloat(),
                                onValueChange = { v -> onUpdateConfig { it.copy(questionCount = v.roundToInt()) } },
                                valueRange = 2f..10f,
                                steps = 7
                            )

                            Text("Time Limit Per Answer", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                timeLimits.forEach { (sec, label) ->
                                    FilterChip(
                                        selected = config.timeLimitSec == sec,
                                        onClick = { onUpdateConfig { it.copy(timeLimitSec = sec) } },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Text("Language", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                languages.forEach { lang ->
                                    FilterChip(
                                        selected = config.language == lang,
                                        onClick = { onUpdateConfig { it.copy(language = lang) } },
                                        label = { Text(lang) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Use Saved Resume & JD", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Tailor questions to your uploaded resume and job description",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config.useResumeAndJd,
                                    onCheckedChange = { checked -> onUpdateConfig { it.copy(useResumeAndJd = checked) } }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (config.practiceMode) "Practice Mode" else "Real Interview Mode",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        if (config.practiceMode) "Hints enabled + instant feedback after every answer"
                                        else "No hints + feedback revealed only at final report",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config.practiceMode,
                                    onCheckedChange = { checked -> onUpdateConfig { it.copy(practiceMode = checked) } }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onLaunchInterview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("launch_interview_button"),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Custom Mock Interview", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabMiniCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}
