package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanyBriefEntity
import com.example.data.local.SavedQuestionEntity
import com.example.ui.components.CoralBookmarkButton
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorRetryCard
import com.example.ui.components.PillSearchBar
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.VerifiedPillBadge
import com.example.utils.Sm2Quality

@Composable
fun LibraryScreen(
    savedQuestions: List<SavedQuestionEntity>,
    companyBriefs: List<CompanyBriefEntity>,
    isGeneratingBrief: Boolean,
    briefError: String?,
    defaultTargetRole: String,
    onSaveQuestion: (
        id: Long,
        question: String,
        topic: String,
        tags: String,
        notes: String,
        bestAnswer: String
    ) -> Unit,
    onToggleBookmark: (SavedQuestionEntity) -> Unit,
    onDeleteQuestion: (Long) -> Unit,
    onGradeFlashcard: (SavedQuestionEntity, Sm2Quality) -> Unit,
    onPracticeQuestion: (SavedQuestionEntity) -> Unit,
    onGenerateCompanyBrief: (company: String, role: String) -> Unit,
    onDeleteCompanyBrief: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingQuestion by remember { mutableStateOf<SavedQuestionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog || editingQuestion != null) {
        QuestionEditorDialog(
            initial = editingQuestion,
            onDismiss = {
                showAddDialog = false
                editingQuestion = null
            },
            onSave = { q, topic, tags, notes, best ->
                onSaveQuestion(editingQuestion?.id ?: 0L, q, topic, tags, notes, best)
                showAddDialog = false
                editingQuestion = null
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Study Library", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedTab == 0) {
                            Surface(
                                onClick = { showAddDialog = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("library_add_question_fab")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add question", tint = Color.White)
                                }
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

                val tabs = listOf("Questions", "Flashcards", "Company Brief")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                            modifier = Modifier.testTag("library_tab_$idx")
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }

                when (selectedTab) {
                    0 -> QuestionBankTab(
                        questions = savedQuestions,
                        onEdit = { editingQuestion = it },
                        onToggleBookmark = onToggleBookmark,
                        onDelete = onDeleteQuestion,
                        onPractice = onPracticeQuestion
                    )
                    1 -> FlashcardTab(
                        cards = savedQuestions.filter { it.isFlashcard },
                        onGrade = onGradeFlashcard
                    )
                    2 -> CompanyBriefTab(
                        briefs = companyBriefs,
                        isLoading = isGeneratingBrief,
                        errorMessage = briefError,
                        defaultRole = defaultTargetRole,
                        onGenerate = onGenerateCompanyBrief,
                        onDeleteBrief = onDeleteCompanyBrief,
                        onSaveQuestionFromBrief = { q, company ->
                            onSaveQuestion(0L, q, company, "Company Brief, $company", "", "")
                        }
                    )
                }
            }
        }

        // Reference-style Floating Bottom-Right "Flashcards / Questions" Pill
        if (selectedTab != 1) {
            val dueCount = savedQuestions.count { it.isFlashcard && it.nextReviewEpochMs <= System.currentTimeMillis() }
            FloatingActionButton(
                onClick = { selectedTab = 1 },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 104.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Flashcards ($dueCount)", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QuestionBankTab(
    questions: List<SavedQuestionEntity>,
    onEdit: (SavedQuestionEntity) -> Unit,
    onToggleBookmark: (SavedQuestionEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onPractice: (SavedQuestionEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var bookmarkedOnly by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf("All") }

    val topics = remember(questions) {
        listOf("All") + questions.map { it.topic.ifBlank { "General" } }.distinct().take(6)
    }

    val filtered = questions.filter { item ->
        val matchesQuery = query.isBlank() ||
            item.question.contains(query, ignoreCase = true) ||
            item.topic.contains(query, ignoreCase = true) ||
            item.tags.contains(query, ignoreCase = true)
        val matchesBookmark = !bookmarkedOnly || item.isBookmarked
        val matchesTopic = selectedTopic == "All" || item.topic.equals(selectedTopic, ignoreCase = true)
        matchesQuery && matchesBookmark && matchesTopic
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PillSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search questions or tags...",
                onFilterClick = { bookmarkedOnly = !bookmarkedOnly },
                searchTag = "library_search_input"
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(
                        onClick = { bookmarkedOnly = !bookmarkedOnly },
                        shape = RoundedCornerShape(18.dp),
                        color = if (bookmarkedOnly) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = if (bookmarkedOnly) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            "Bookmarked",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (bookmarkedOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                items(topics) { topic ->
                    val selected = selectedTopic == topic
                    Surface(
                        onClick = { selectedTopic = topic },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            topic,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "${filtered.size} Questions Found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.QuestionAnswer,
                    title = "No saved questions yet",
                    subtitle = "Bookmark feedback questions during a mock interview or tap + to add your own."
                )
            }
        } else {
            items(filtered, key = { it.id }) { item ->
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
                            Column {
                                Text(item.topic, style = MaterialTheme.typography.titleSmall)
                                VerifiedPillBadge(
                                    label = if (item.bestAnswer.isNotBlank()) "Flashcard Ready" else "Study Note"
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CoralBookmarkButton(
                                    isBookmarked = item.isBookmarked,
                                    onClick = { onToggleBookmark(item) }
                                )
                                IconButton(onClick = { onEdit(item) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit question")
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete question")
                                }
                            }
                        }

                        Text(item.question, style = MaterialTheme.typography.titleMedium)

                        if (item.personalNotes.isNotBlank()) {
                            Text(
                                "Notes: ${item.personalNotes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.bestAnswer.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Text(
                                    "Ideal Answer: ${item.bestAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.tags.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        item.tags,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Surface(
                                onClick = { onPractice(item) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Practice", style = MaterialTheme.typography.labelLarge, color = Color.White)
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
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

@Composable
private fun FlashcardTab(
    cards: List<SavedQuestionEntity>,
    onGrade: (SavedQuestionEntity, Sm2Quality) -> Unit
) {
    val now = System.currentTimeMillis()
    val dueCards = remember(cards) {
        val due = cards.filter { it.nextReviewEpochMs <= now }
        due.ifEmpty { cards }
    }
    var cardIndex by remember(dueCards.size) { mutableIntStateOf(0) }
    var flipped by remember(cardIndex) { mutableStateOf(false) }

    if (dueCards.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Filled.Style,
            title = "No flashcards in deck",
            subtitle = "Add questions to your Library or bookmark mock interview answers to build your SM-2 spaced repetition deck."
        )
        return
    }

    val safeIndex = cardIndex.coerceIn(0, dueCards.lastIndex)
    val activeCard = dueCards[safeIndex]
    val dueCount = cards.count { it.nextReviewEpochMs <= now }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Due now: $dueCount • Total: ${cards.size}", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Interval: ${activeCard.intervalDays}d • Ease: ${String.format("%.2f", activeCard.easeFactor)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (flipped) MaterialTheme.colorScheme.surfaceContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { flipped = !flipped }
                    .testTag("flashcard_surface")
            ) {
                AnimatedContent(targetState = flipped, label = "flashcard_flip") { isBack ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isBack) "IDEAL ANSWER & NOTES" else "QUESTION (${activeCard.topic}) — TAP TO FLIP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!isBack) {
                            Text(activeCard.question, style = MaterialTheme.typography.titleLarge)
                        } else {
                            Text(
                                activeCard.bestAnswer.ifBlank { "No best answer saved yet. Edit this question in the Questions tab to add one." },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (activeCard.personalNotes.isNotBlank()) {
                                Text(
                                    "Notes: ${activeCard.personalNotes}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Rate Recall (SM-2 Spaced Repetition)", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Sm2Quality.entries.forEach { quality ->
                    OutlinedButton(
                        onClick = {
                            onGrade(activeCard, quality)
                            flipped = false
                            cardIndex = (safeIndex + 1) % dueCards.size
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Text(quality.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanyBriefTab(
    briefs: List<CompanyBriefEntity>,
    isLoading: Boolean,
    errorMessage: String?,
    defaultRole: String,
    onGenerate: (company: String, role: String) -> Unit,
    onDeleteBrief: (Long) -> Unit,
    onSaveQuestionFromBrief: (question: String, company: String) -> Unit
) {
    var companyInput by remember { mutableStateOf("") }
    var roleInput by remember(defaultRole) { mutableStateOf(defaultRole.ifBlank { "Software Engineer" }) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI Company Research Brief", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = companyInput,
                        onValueChange = { companyInput = it },
                        label = { Text("Company Name") },
                        placeholder = { Text("e.g., Google, Microsoft, Stripe") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("company_brief_input")
                    )
                    OutlinedTextField(
                        value = roleInput,
                        onValueChange = { roleInput = it },
                        label = { Text("Target Role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onGenerate(companyInput, roleInput) },
                        enabled = companyInput.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_company_brief_button")
                    ) {
                        Text(if (isLoading) "Researching..." else "Generate Research Brief")
                    }
                }
            }
        }

        if (isLoading) {
            item { ShimmerLoadingCard(label = "Building company culture & interview brief...") }
        }
        if (!errorMessage.isNullOrBlank()) {
            item { ErrorRetryCard(message = errorMessage) }
        }

        if (briefs.isEmpty() && !isLoading) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Business,
                    title = "No saved company briefs",
                    subtitle = "Enter any target company above to generate culture insights, likely questions, and insider prep tips."
                )
            }
        } else {
            items(briefs, key = { it.id }) { brief ->
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(brief.companyName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    brief.targetRole,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDeleteBrief(brief.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete brief")
                            }
                        }

                        Text("Culture & Interview Style", style = MaterialTheme.typography.titleSmall)
                        Text(brief.cultureOverview, style = MaterialTheme.typography.bodySmall)

                        val values = brief.keyValues.split("||").filter { it.isNotBlank() }
                        if (values.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                values.forEach { v ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            v,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text("Likely Questions (Tap bookmark to save)", style = MaterialTheme.typography.titleSmall)
                        brief.likelyQuestions.split("||").filter { it.isNotBlank() }.forEach { q ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• $q",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onSaveQuestionFromBrief(q, brief.companyName) }) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save question to bank")
                                }
                            }
                        }

                        Text("Preparation Tips", style = MaterialTheme.typography.titleSmall)
                        brief.preparationTips.split("||").filter { it.isNotBlank() }.forEach { tip ->
                            Text("• $tip", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditorDialog(
    initial: SavedQuestionEntity?,
    onDismiss: () -> Unit,
    onSave: (question: String, topic: String, tags: String, notes: String, bestAnswer: String) -> Unit
) {
    var question by remember { mutableStateOf(initial?.question.orEmpty()) }
    var topic by remember { mutableStateOf(initial?.topic ?: "Behavioral") }
    var tags by remember { mutableStateOf(initial?.tags.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.personalNotes.orEmpty()) }
    var bestAnswer by remember { mutableStateOf(initial?.bestAnswer.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Study Question" else "Edit Question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Personal Notes") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = bestAnswer,
                    onValueChange = { bestAnswer = it },
                    label = { Text("Best Answer (for Flashcard back)") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(question, topic, tags, notes, bestAnswer) },
                enabled = question.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
