package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AiQueueState
import com.example.data.local.InterviewSessionEntity
import com.example.data.local.SessionTurnEntity
import com.example.ui.components.AiRateLimitBanner
import com.example.ui.components.ErrorRetryCard
import com.example.ui.components.ScoreDimensionRow
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.StarMethodRow
import com.example.ui.components.VoiceWaveformCanvas
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.utils.PdfReportExporter
import com.example.utils.VoiceInputState
import com.example.viewmodel.LiveInterviewState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockInterviewScreen(
    state: LiveInterviewState,
    queueState: AiQueueState,
    voiceState: VoiceInputState,
    onUpdateDraft: (String) -> Unit,
    onToggleVoiceMode: (Boolean) -> Unit,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: () -> Unit,
    onSpeakQuestion: () -> Unit,
    onRequestHint: () -> Unit,
    onSubmitAnswer: (skipped: Boolean) -> Unit,
    onProceedNext: () -> Unit,
    onFinishEarly: () -> Unit,
    onRetry: () -> Unit,
    onBookmarkTurn: (SessionTurnEntity) -> Unit,
    onClearBookmarkMsg: () -> Unit,
    onExitSession: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var micPermissionStatus by remember { mutableStateOf<String?>(null) }
    var pendingCaptureAfterGrant by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldStartCapture = pendingCaptureAfterGrant
        pendingCaptureAfterGrant = false
        if (granted) {
            onToggleVoiceMode(true)
            micPermissionStatus = null
            if (shouldStartCapture) {
                onStartVoiceCapture()
            }
        } else {
            micPermissionStatus = "Microphone permission denied. Switched to text input fallback."
            onToggleVoiceMode(false)
        }
    }

    val pdfCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val session = state.finalSession
        if (uri != null && session != null) {
            val res = PdfReportExporter.writePdfToUri(context, uri, session, state.completedTurns)
            micPermissionStatus = if (res.isSuccess) "PDF report exported." else "PDF export failed."
        }
    }

    LaunchedEffect(state.savedBookmarkMessage) {
        state.savedBookmarkMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearBookmarkMsg()
        }
    }

    LaunchedEffect(micPermissionStatus) {
        micPermissionStatus?.let {
            snackbarHostState.showSnackbar(it)
            micPermissionStatus = null
        }
    }

    BackHandler {
        onExitSession()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onExitSession) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit interview")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (state.finalSession != null) "Session Report"
                            else "Q${state.currentTurnIndex + 1} of ${state.config.questionCount} • ${state.config.persona.label}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${state.config.role} (${if (state.config.practiceMode) "Practice" else "Real"} Mode)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.finalSession == null) {
                        val min = state.remainingSeconds / 60
                        val sec = state.remainingSeconds % 60
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.remainingSeconds <= 15) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = String.format(Locale.US, "%d:%02d", min, sec),
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.remainingSeconds <= 15) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        if (state.completedTurns.isNotEmpty()) {
                            TextButton(onClick = onFinishEarly) {
                                Text("Finish")
                            }
                        }
                    }
                }
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

            if (state.finalSession != null) {
                FinalReportView(
                    session = state.finalSession,
                    turns = state.completedTurns,
                    onExportPdf = {
                        pdfCreateLauncher.launch("PrepPilot_Report_${System.currentTimeMillis()}.pdf")
                    },
                    onShareText = {
                        val txt = PdfReportExporter.formatAsPlainText(state.finalSession, state.completedTurns)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "PrepPilot AI Interview Report")
                            putExtra(Intent.EXTRA_TEXT, txt)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Report"))
                    },
                    onBookmarkTurn = onBookmarkTurn,
                    onDone = onExitSession,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Past turns summary
                    items(state.completedTurns, key = { it.id }) { turn ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Q${turn.turnIndex + 1}: ${turn.question}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    if (turn.skipped) "(Skipped)" else turn.userAnswer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.isGeneratingQuestion) {
                        item { ShimmerLoadingCard(label = "Interviewer is preparing your question...") }
                    } else if (state.currentQuestion.isNotBlank() && state.latestFeedbackTurn == null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = state.currentTopic.ifBlank { "Interview" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            if (state.currentIsFollowUp) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondary
                                                ) {
                                                    Text(
                                                        "Follow-up",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = onSpeakQuestion) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Read question aloud",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = state.currentQuestion,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Hint Card (Practice Mode only)
                    if (!state.hintText.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Coach Hint",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        state.hintText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    if (state.isEvaluatingAnswer || state.isGeneratingReport) {
                        item {
                            ShimmerLoadingCard(
                                label = if (state.isGeneratingReport) "Compiling final interview report..."
                                else "Analyzing your answer & STAR structure..."
                            )
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        item {
                            ErrorRetryCard(
                                message = state.errorMessage,
                                onRetry = onRetry
                            )
                        }
                    }

                    // Instant Feedback Card in Practice Mode
                    state.latestFeedbackTurn?.let { turn ->
                        item {
                            TurnFeedbackCard(
                                turn = turn,
                                onBookmark = { onBookmarkTurn(turn) },
                                onNext = onProceedNext,
                                isLastQuestion = (state.currentTurnIndex + 1) >= state.config.questionCount
                            )
                        }
                    }
                }

                // Answer Input Dock (shown when awaiting answer)
                if (state.latestFeedbackTurn == null && !state.isGeneratingQuestion && !state.isEvaluatingAnswer && !state.isGeneratingReport) {
                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Mode & Hint / Skip toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (state.isVoiceMode) {
                                                onToggleVoiceMode(false)
                                            } else {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) onToggleVoiceMode(true)
                                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier.testTag("toggle_voice_mode_button")
                                    ) {
                                        Icon(
                                            if (state.isVoiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (state.isVoiceMode) "Text Mode" else "Voice Mode")
                                    }

                                    if (state.config.practiceMode) {
                                        OutlinedButton(
                                            onClick = onRequestHint,
                                            enabled = !state.isLoadingHint,
                                            modifier = Modifier.testTag("interview_hint_button")
                                        ) {
                                            Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (state.isLoadingHint) "..." else "Hint")
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { onSubmitAnswer(true) },
                                    modifier = Modifier.testTag("interview_skip_button")
                                ) {
                                    Text("Skip")
                                    Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }

                            AnimatedVisibility(visible = state.isVoiceMode) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    VoiceWaveformCanvas(isListening = voiceState.isListening)
                                    val liveDisplay = listOf(
                                        state.answerDraft,
                                        voiceState.livePartialTranscript
                                    ).filter { it.isNotBlank() }.joinToString(" ")
                                    if (liveDisplay.isNotBlank()) {
                                        Text(
                                            text = liveDisplay,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (!voiceState.errorMessage.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = voiceState.errorMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                onClick = { onToggleVoiceMode(false) },
                                                modifier = Modifier.testTag("fallback_text_mode_button")
                                            ) {
                                                Text("Type Instead")
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (voiceState.isListening) {
                                                    onStopVoiceCapture()
                                                } else {
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        onStartVoiceCapture()
                                                    } else {
                                                        pendingCaptureAfterGrant = true
                                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                if (voiceState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (voiceState.isListening) "Stop Mic" else "Speak Answer")
                                        }
                                        Button(
                                            onClick = { onSubmitAnswer(false) },
                                            enabled = state.answerDraft.isNotBlank() ||
                                                voiceState.finalizedTranscript.isNotBlank() ||
                                                voiceState.livePartialTranscript.isNotBlank(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Submit")
                                        }
                                    }
                                }
                            }

                            if (!state.isVoiceMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = state.answerDraft,
                                        onValueChange = onUpdateDraft,
                                        label = { Text("Type your answer") },
                                        minLines = 2,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("interview_answer_input")
                                    )
                                    Button(
                                        onClick = { onSubmitAnswer(false) },
                                        enabled = state.answerDraft.isNotBlank(),
                                        modifier = Modifier
                                            .height(54.dp)
                                            .testTag("interview_submit_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit answer")
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
private fun TurnFeedbackCard(
    turn: SessionTurnEntity,
    onBookmark: () -> Unit,
    onNext: () -> Unit,
    isLastQuestion: Boolean
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Structured AI Feedback", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onBookmark) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save question to Library")
                }
            }

            ScoreDimensionRow("Clarity", turn.clarityScore)
            ScoreDimensionRow("Structure", turn.structureScore)
            ScoreDimensionRow("Relevance", turn.relevanceScore)
            ScoreDimensionRow("Depth", turn.depthScore)
            ScoreDimensionRow("Confidence", turn.confidenceScore)

            if (turn.wpm > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${turn.wpm}", style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFamily)
                            Text("WPM", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${turn.fillerCount}", style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFamily)
                            Text("Fillers", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${turn.longPauseCount}", style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMonoFamily)
                            Text("Long Pauses", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (turn.isBehavioral) {
                StarMethodRow(
                    situation = turn.starSituation,
                    task = turn.starTask,
                    action = turn.starAction,
                    result = turn.starResult,
                    note = turn.starFeedback
                )
            }

            if (turn.strengths.isNotBlank()) {
                Text("Strengths", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                turn.strengths.split("||").filter { it.isNotBlank() }.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (turn.weaknesses.isNotBlank()) {
                Text("Areas to Sharpen", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                turn.weaknesses.split("||").filter { it.isNotBlank() }.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (turn.idealAnswer.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ideal Answer", style = MaterialTheme.typography.titleSmall)
                        Text(turn.idealAnswer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (turn.tips.isNotBlank()) {
                Text("Actionable Tips", style = MaterialTheme.typography.titleSmall)
                turn.tips.split("||").filter { it.isNotBlank() }.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("interview_next_question_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isLastQuestion) "View Final Report" else "Next Question")
            }
        }
    }
}

@Composable
private fun FinalReportView(
    session: InterviewSessionEntity,
    turns: List<SessionTurnEntity>,
    onExportPdf: () -> Unit,
    onShareText: () -> Unit,
    onBookmarkTurn: (SessionTurnEntity) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", session.overallScore)} / 10",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Overall Interview Readiness",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export PDF")
                }
                OutlinedButton(
                    onClick = onShareText,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Text")
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Top 3 Strengths", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                    session.topStrengths.split("||").filter { it.isNotBlank() }.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }

                    Text("Top 3 Improvement Areas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    session.topImprovements.split("||").filter { it.isNotBlank() }.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (session.nextPracticePlan.isNotBlank()) {
                        Text("Next Practice Plan", style = MaterialTheme.typography.titleMedium)
                        Text(session.nextPracticePlan, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        items(turns, key = { it.id }) { turn ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Q${turn.turnIndex + 1}: ${turn.topic}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { onBookmarkTurn(turn) }) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save question")
                        }
                    }
                    Text(turn.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (!turn.skipped) {
                        ScoreDimensionRow("Clarity", turn.clarityScore)
                        ScoreDimensionRow("Structure", turn.structureScore)
                        ScoreDimensionRow("Relevance", turn.relevanceScore)
                        ScoreDimensionRow("Depth", turn.depthScore)
                        ScoreDimensionRow("Confidence", turn.confidenceScore)
                        if (turn.idealAnswer.isNotBlank()) {
                            Text(
                                "Ideal: ${turn.idealAnswer}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("Skipped", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Return to Practice Hub")
            }
        }
    }
}
