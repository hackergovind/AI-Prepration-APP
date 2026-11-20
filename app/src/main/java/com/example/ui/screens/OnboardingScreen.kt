package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.UserProfileEntity
import com.example.ui.components.PrepPilotHeroArt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingProfile: UserProfileEntity?,
    onSaveComplete: (
        name: String,
        targetRole: String,
        experienceLevel: String,
        industry: String,
        targetCompanies: String,
        resumeText: String,
        jobDescriptionText: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var name by remember(existingProfile) { mutableStateOf(existingProfile?.name.orEmpty()) }
    var targetRole by remember(existingProfile) { mutableStateOf(existingProfile?.targetRole.orEmpty()) }
    var experienceLevel by remember(existingProfile) {
        mutableStateOf(existingProfile?.experienceLevel?.ifBlank { "Mid-Level (2–5 yrs)" } ?: "Mid-Level (2–5 yrs)")
    }
    var industry by remember(existingProfile) {
        mutableStateOf(existingProfile?.industry?.ifBlank { "Software & Tech" } ?: "Software & Tech")
    }
    var targetCompanies by remember(existingProfile) { mutableStateOf(existingProfile?.targetCompanies.orEmpty()) }
    var resumeText by remember(existingProfile) { mutableStateOf(existingProfile?.resumeText.orEmpty()) }
    var jobDescriptionText by remember(existingProfile) { mutableStateOf(existingProfile?.jobDescriptionText.orEmpty()) }
    var fileStatus by remember { mutableStateOf<String?>(null) }

    val resumePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { text ->
                if (!text.isNullOrBlank()) {
                    resumeText = text.take(12000)
                    fileStatus = "Resume text imported locally."
                } else {
                    fileStatus = "Selected file was empty. Paste text directly if it is a binary PDF."
                }
            }.onFailure {
                fileStatus = "Could not read file. Try pasting your resume text below."
            }
        }
    }

    val levels = listOf("Entry / Intern", "Mid-Level (2–5 yrs)", "Senior (5–8 yrs)", "Staff / Lead", "Manager / Exec")
    val industries = listOf("Software & Tech", "Finance & FinTech", "Product & Design", "Healthcare", "Consulting", "E-Commerce")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrepPilotHeroArt {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.FlightTakeoff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (existingProfile?.onboardingCompleted == true) "Edit Your Profile" else "Welcome to PrepPilot AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Personalize your target role, resume, and companies so every mock interview adapts to you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Privacy protected",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "100% local storage: your profile, resume, and history stay on your device. Only prompts go to Gemini via Firebase AI Logic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Candidate Basics", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name") },
                        placeholder = { Text("e.g., Alex Rivera") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_name_input")
                    )

                    OutlinedTextField(
                        value = targetRole,
                        onValueChange = { targetRole = it },
                        label = { Text("Target Role") },
                        placeholder = { Text("e.g., Android Engineer, Product Manager") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_role_input")
                    )

                    Text("Experience Level", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        levels.forEach { lvl ->
                            FilterChip(
                                selected = experienceLevel == lvl,
                                onClick = { experienceLevel = lvl },
                                label = { Text(lvl) },
                                leadingIcon = if (experienceLevel == lvl) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Text("Industry", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        industries.forEach { ind ->
                            FilterChip(
                                selected = industry == ind,
                                onClick = { industry = ind },
                                label = { Text(ind) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetCompanies,
                        onValueChange = { targetCompanies = it },
                        label = { Text("Target Companies (comma-separated)") },
                        placeholder = { Text("e.g., Google, Stripe, Linear") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
                        Text("Resume & Job Description", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { resumePicker.launch(arrayOf("text/plain", "text/*")) },
                            modifier = Modifier.testTag("upload_resume_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .txt")
                        }
                    }

                    fileStatus?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = resumeText,
                        onValueChange = { resumeText = it },
                        label = { Text("Resume / Experience Summary (Optional)") },
                        placeholder = { Text("Paste key highlights or full resume text...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_resume_input")
                    )

                    OutlinedTextField(
                        value = jobDescriptionText,
                        onValueChange = { jobDescriptionText = it },
                        label = { Text("Target Job Description (Optional)") },
                        placeholder = { Text("Paste the JD to tailor mock interview questions...") },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_jd_input")
                    )
                }
            }

            Button(
                onClick = {
                    onSaveComplete(
                        name.ifBlank { "Candidate" },
                        targetRole.ifBlank { "Software Engineer" },
                        experienceLevel,
                        industry,
                        targetCompanies,
                        resumeText,
                        jobDescriptionText
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_complete_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (existingProfile?.onboardingCompleted == true) "Save Profile Changes" else "Launch PrepPilot AI",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
