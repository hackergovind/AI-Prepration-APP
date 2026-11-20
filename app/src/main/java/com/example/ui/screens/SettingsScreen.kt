package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.local.AccentPalette
import com.example.data.local.AiTonePreset
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.viewmodel.ConnectionTestState
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    statusBanner: String?,
    connectionTestState: ConnectionTestState,
    onDismissBanner: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPalette: (AccentPalette) -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onSetQuestionModel: (String) -> Unit,
    onSetDetailedModel: (String) -> Unit,
    onSetFallbackModel: (String) -> Unit,
    onResetGeminiModels: () -> Unit,
    onTestConnection: () -> Unit,
    onSetAiTonePreset: (AiTonePreset) -> Unit,
    onSetCustomPrompt: (String) -> Unit,
    onSetTtsRate: (Float) -> Unit,
    onSetDefaultLanguage: (String) -> Unit,
    onSetDefaultAnswerTime: (Int) -> Unit,
    onExportJson: suspend () -> String,
    onImportJson: (String) -> Unit,
    onClearAllData: () -> Unit,
    onEditProfile: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf<String?>(null) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(preferences.geminiApiKey) }
    var questionModelInput by remember { mutableStateOf(preferences.questionModel) }
    var detailedModelInput by remember { mutableStateOf(preferences.detailedModel) }
    var fallbackModelInput by remember { mutableStateOf(preferences.fallbackModel) }

    LaunchedEffect(preferences.geminiApiKey) {
        if (preferences.geminiApiKey != apiKeyInput) apiKeyInput = preferences.geminiApiKey
    }
    LaunchedEffect(preferences.questionModel) {
        if (preferences.questionModel != questionModelInput) questionModelInput = preferences.questionModel
    }
    LaunchedEffect(preferences.detailedModel) {
        if (preferences.detailedModel != detailedModelInput) detailedModelInput = preferences.detailedModel
    }
    LaunchedEffect(preferences.fallbackModel) {
        if (preferences.fallbackModel != fallbackModelInput) fallbackModelInput = preferences.fallbackModel
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onExportJson()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                }.onSuccess {
                    localStatus = "Backup JSON exported successfully."
                }.onFailure {
                    localStatus = "Export failed: ${it.localizedMessage}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { raw ->
                if (!raw.isNullOrBlank()) onImportJson(raw)
                else localStatus = "Selected JSON file was empty."
            }.onFailure {
                localStatus = "Failed to read file: ${it.localizedMessage}"
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Local Data?") },
            text = { Text("This removes your profile, interview history, presets, flashcards, and prep plans from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("settings_back_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    }
                    Column {
                        Text("Settings", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Theme, Gemini model, voice & local backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val banner = statusBanner ?: localStatus
                if (!banner.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(banner, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                onDismissBanner()
                                localStatus = null
                            }) { Text("OK") }
                        }
                    }
                }

                // Appearance
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("Accent Color Palette", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentPalette.entries.forEach { palette ->
                                FilterChip(
                                    selected = preferences.accentPalette == palette,
                                    onClick = { onSetAccentPalette(palette) },
                                    label = { Text(palette.label) }
                                )
                            }
                        }
                    }
                }

                // AI Coach Configuration
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Gemini API & Voice", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                onSetGeminiApiKey(it)
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("Paste your Google AI Studio key") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        val hasBuildSecret = remember {
                            val k = BuildConfig.GEMINI_API_KEY.trim()
                            k.isNotEmpty() && k != "MY_GEMINI_API_KEY"
                        }
                        Text(
                            text = buildString {
                                if (hasBuildSecret && apiKeyInput.isBlank()) {
                                    append("A Gemini API key is active from build secrets. Enter one above only if you want to override it locally. ")
                                }
                                append("How to get a free key: visit aistudio.google.com, sign in with Google, open Get API key → Create API key, and paste it here. Stored locally on your device only.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = questionModelInput,
                            onValueChange = {
                                questionModelInput = it
                                onSetQuestionModel(it)
                            },
                            label = { Text("Questions & Voice Model") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_question_model_input")
                        )

                        OutlinedTextField(
                            value = detailedModelInput,
                            onValueChange = {
                                detailedModelInput = it
                                onSetDetailedModel(it)
                            },
                            label = { Text("Feedback, Reports & Plans Model") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_detailed_model_input")
                        )

                        OutlinedTextField(
                            value = fallbackModelInput,
                            onValueChange = {
                                fallbackModelInput = it
                                onSetFallbackModel(it)
                            },
                            label = { Text("Fallback Backup Model (429 / Quota)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_fallback_model_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onResetGeminiModels,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reset_gemini_defaults_button")
                            ) {
                                Text("Reset to defaults")
                            }
                            Button(
                                onClick = onTestConnection,
                                enabled = connectionTestState !is ConnectionTestState.Testing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_gemini_connection_button")
                            ) {
                                if (connectionTestState is ConnectionTestState.Testing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Test connection")
                            }
                        }

                        when (connectionTestState) {
                            is ConnectionTestState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = connectionTestState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    )
                                }
                            }
                            is ConnectionTestState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = connectionTestState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    )
                                }
                            }
                            else -> Unit
                        }

                        Text("Global AI Tone / System Prompt", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiTonePreset.entries.forEach { tone ->
                                FilterChip(
                                    selected = preferences.aiTonePreset == tone,
                                    onClick = { onSetAiTonePreset(tone) },
                                    label = { Text(tone.label) }
                                )
                            }
                        }

                        if (preferences.aiTonePreset == AiTonePreset.CUSTOM) {
                            OutlinedTextField(
                                value = preferences.customSystemPrompt,
                                onValueChange = onSetCustomPrompt,
                                label = { Text("Custom Global System Prompt") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            "Voice Playback Speed: ${String.format(Locale.US, "%.2fx", preferences.ttsSpeechRate)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = preferences.ttsSpeechRate,
                            onValueChange = onSetTtsRate,
                            valueRange = 0.7f..1.5f
                        )

                        Text("Default Language", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("English", "Spanish", "French", "German", "Hindi").forEach { lang ->
                                FilterChip(
                                    selected = preferences.defaultLanguage == lang,
                                    onClick = { onSetDefaultLanguage(lang) },
                                    label = { Text(lang) }
                                )
                            }
                        }

                        Text("Default Answer Time Limit", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60 to "1m", 120 to "2m", 180 to "3m", 300 to "5m").forEach { (sec, label) ->
                                FilterChip(
                                    selected = preferences.defaultAnswerTimeSec == sec,
                                    onClick = { onSetDefaultAnswerTime(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // Data & Profile Management
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Profile & Local Backup", style = MaterialTheme.typography.titleMedium)

                        OutlinedButton(
                            onClick = onEditProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit Profile, Resume & Target JD") }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("preppilot_backup.json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_json_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_json_button")
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Import JSON")
                            }
                        }

                        Button(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_all_data_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear All Data")
                        }
                    }
                }

                // Privacy & Firebase AI Logic Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Privacy",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Local Privacy & Gemini API",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "PrepPilot AI stores your Gemini API key, profile, resumes, sessions, and flashcards locally on your device only. Never share exported backups with untrusted parties.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
