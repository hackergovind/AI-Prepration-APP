package com.example.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.AiRateLimitBanner
import com.example.ui.components.FloatingDockTab
import com.example.ui.components.FloatingSkyBottomBar
import com.example.ui.components.SkyBackdrop
import com.example.ui.screens.CodingRoundScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MockInterviewScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PracticeHubScreen
import com.example.ui.screens.PrepPlanScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.SalaryNegotiationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TellMeAboutYourselfScreen
import com.example.viewmodel.InterviewViewModel
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ProgressViewModel
import com.example.viewmodel.ToolsViewModel

object Routes {
    const val HOME = "home"
    const val PRACTICE = "practice"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SESSION = "session"
    const val TOOL_TMAY = "tool_tmay"
    const val TOOL_SALARY = "tool_salary"
    const val TOOL_CODING = "tool_coding"
    const val TOOL_PREP_PLAN = "tool_prep_plan"
}

private val dockTabs = listOf(
    FloatingDockTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    FloatingDockTab(Routes.PRACTICE, "Practice", Icons.Filled.Mic, Icons.Outlined.Mic, "nav_practice"),
    FloatingDockTab(Routes.LIBRARY, "Library", Icons.Filled.Book, Icons.Outlined.Book, "nav_library"),
    FloatingDockTab(Routes.PROGRESS, "Progress", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_progress")
)

@Composable
fun PrepPilotApp(
    mainViewModel: MainViewModel,
    interviewViewModel: InterviewViewModel,
    libraryViewModel: LibraryViewModel,
    progressViewModel: ProgressViewModel,
    toolsViewModel: ToolsViewModel
) {
    val profile by mainViewModel.profile.collectAsStateWithLifecycle()
    val isProfileReady by mainViewModel.isProfileReady.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val sessions by mainViewModel.sessions.collectAsStateWithLifecycle()
    val prepTasks by mainViewModel.prepPlanTasks.collectAsStateWithLifecycle()
    val dailyUiState by mainViewModel.dailyUiState.collectAsStateWithLifecycle()
    val todayChallenge by mainViewModel.todayChallenge.collectAsStateWithLifecycle()
    val queueState by mainViewModel.queueState.collectAsStateWithLifecycle()
    val settingsBanner by mainViewModel.settingsBanner.collectAsStateWithLifecycle()
    val connectionTestState by mainViewModel.connectionTestState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(mainViewModel) {
        mainViewModel.backupModelEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val builderConfig by interviewViewModel.builderConfig.collectAsStateWithLifecycle()
    val presets by interviewViewModel.presets.collectAsStateWithLifecycle()
    val liveInterviewState by interviewViewModel.liveState.collectAsStateWithLifecycle()
    val voiceState by interviewViewModel.voiceState.collectAsStateWithLifecycle()

    val savedQuestions by libraryViewModel.savedQuestions.collectAsStateWithLifecycle()
    val companyBriefs by libraryViewModel.companyBriefs.collectAsStateWithLifecycle()
    val isGeneratingBrief by libraryViewModel.isGeneratingBrief.collectAsStateWithLifecycle()
    val briefError by libraryViewModel.briefError.collectAsStateWithLifecycle()

    val progressState by progressViewModel.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsViewModel.uiState.collectAsStateWithLifecycle()

    var editingProfileManually by remember { mutableStateOf(false) }

    if (!isProfileReady) {
        SkyBackdrop {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val needsOnboarding = profile?.onboardingCompleted != true || editingProfileManually
    if (needsOnboarding) {
        if (editingProfileManually) {
            BackHandler { editingProfileManually = false }
        }
        SkyBackdrop {
            OnboardingScreen(
                existingProfile = profile,
                onSaveComplete = { name, role, level, industry, companies, resume, jd ->
                    mainViewModel.saveProfile(
                        name = name,
                        targetRole = role,
                        experienceLevel = level,
                        industry = industry,
                        targetCompanies = companies,
                        resumeText = resume,
                        jobDescriptionText = jd,
                        onboardingCompleted = true
                    )
                    interviewViewModel.updateConfig {
                        it.copy(role = role, seniority = level)
                    }
                    editingProfileManually = false
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val showDockChrome = currentRoute in dockTabs.map { it.route }

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        SkyBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showDockChrome && useRail) {
                            NavigationRail(modifier = Modifier.statusBarsPadding()) {
                                dockTabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { switchTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                                NavigationRailItem(
                                    selected = currentRoute == Routes.SETTINGS,
                                    onClick = { navController.navigate(Routes.SETTINGS) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Routes.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = "Settings"
                                        )
                                    },
                                    label = { Text("Settings") },
                                    modifier = Modifier.testTag("nav_settings_rail")
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(if (showDockChrome) Modifier.statusBarsPadding() else Modifier)
                        ) {
                            if (showDockChrome) {
                                AiRateLimitBanner(queueState = queueState)
                            }

                            NavHost(
                                navController = navController,
                                startDestination = Routes.HOME,
                                enterTransition = { fadeIn(animationSpec = tween(220)) },
                                exitTransition = { fadeOut(animationSpec = tween(180)) }
                            ) {
                                composable(Routes.HOME) {
                                    HomeScreen(
                                        profile = profile,
                                        preferences = preferences,
                                        sessions = sessions,
                                        prepPlanTasks = prepTasks,
                                        dailyUiState = dailyUiState,
                                        todayChallenge = todayChallenge,
                                        onLoadDailyChallenge = mainViewModel::loadOrGenerateDailyChallenge,
                                        onSubmitDailyAnswer = mainViewModel::submitDailyChallengeAnswer,
                                        onTogglePrepTask = mainViewModel::togglePrepTask,
                                        onSetReminder = mainViewModel::setReminder,
                                        onNavigateToPractice = { switchTab(Routes.PRACTICE) },
                                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                        onStartPresetMock = { cfg, startVoice ->
                                            interviewViewModel.startNewSession(
                                                overrideConfig = cfg,
                                                startInVoiceMode = startVoice
                                            )
                                            navController.navigate(Routes.SESSION)
                                        },
                                        onEditProfile = { editingProfileManually = true },
                                        onOpenToolTmay = { navController.navigate(Routes.TOOL_TMAY) },
                                        onOpenToolSalary = { navController.navigate(Routes.TOOL_SALARY) },
                                        onOpenToolCoding = { navController.navigate(Routes.TOOL_CODING) },
                                        onOpenToolPrepPlan = { navController.navigate(Routes.TOOL_PREP_PLAN) }
                                    )
                                }

                                composable(Routes.PRACTICE) {
                                    PracticeHubScreen(
                                        config = builderConfig,
                                        presets = presets,
                                        onUpdateConfig = interviewViewModel::updateConfig,
                                        onSavePreset = interviewViewModel::savePreset,
                                        onLoadPreset = interviewViewModel::loadPreset,
                                        onDeletePreset = interviewViewModel::deletePreset,
                                        onLaunchInterview = {
                                            interviewViewModel.startNewSession()
                                            navController.navigate(Routes.SESSION)
                                        },
                                        onLaunchQuickPack = { cfg ->
                                            interviewViewModel.startNewSession(overrideConfig = cfg)
                                            navController.navigate(Routes.SESSION)
                                        },
                                        onOpenToolTmay = { navController.navigate(Routes.TOOL_TMAY) },
                                        onOpenToolSalary = { navController.navigate(Routes.TOOL_SALARY) },
                                        onOpenToolCoding = { navController.navigate(Routes.TOOL_CODING) },
                                        onOpenToolPrepPlan = { navController.navigate(Routes.TOOL_PREP_PLAN) },
                                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                                    )
                                }

                                composable(Routes.LIBRARY) {
                                    LibraryScreen(
                                        savedQuestions = savedQuestions,
                                        companyBriefs = companyBriefs,
                                        isGeneratingBrief = isGeneratingBrief,
                                        briefError = briefError,
                                        defaultTargetRole = profile?.targetRole.orEmpty(),
                                        onSaveQuestion = { id, q, topic, tags, notes, best ->
                                            libraryViewModel.saveOrUpdateQuestion(id, q, topic, tags, notes, best)
                                        },
                                        onToggleBookmark = libraryViewModel::toggleBookmark,
                                        onDeleteQuestion = libraryViewModel::deleteQuestion,
                                        onGradeFlashcard = libraryViewModel::gradeFlashcard,
                                        onPracticeQuestion = { item ->
                                            interviewViewModel.startNewSession(
                                                overrideConfig = builderConfig.copy(questionCount = 1),
                                                initialCustomQuestion = item.question,
                                                initialTopic = item.topic
                                            )
                                            navController.navigate(Routes.SESSION)
                                        },
                                        onGenerateCompanyBrief = libraryViewModel::generateCompanyBrief,
                                        onDeleteCompanyBrief = libraryViewModel::deleteCompanyBrief,
                                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                                    )
                                }

                                composable(Routes.PROGRESS) {
                                    ProgressScreen(
                                        uiState = progressState,
                                        onStartWeaknessDrill = { weakTopics ->
                                            interviewViewModel.startWeaknessDrill(weakTopics)
                                            navController.navigate(Routes.SESSION)
                                        },
                                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                                    )
                                }

                                composable(Routes.SETTINGS) {
                                    SettingsScreen(
                                        preferences = preferences,
                                        statusBanner = settingsBanner,
                                        connectionTestState = connectionTestState,
                                        onDismissBanner = mainViewModel::clearSettingsBanner,
                                        onSetThemeMode = mainViewModel::setThemeMode,
                                        onSetAccentPalette = mainViewModel::setAccentPalette,
                                        onSetGeminiApiKey = mainViewModel::setGeminiApiKey,
                                        onSetQuestionModel = mainViewModel::setQuestionModel,
                                        onSetDetailedModel = mainViewModel::setDetailedModel,
                                        onSetFallbackModel = mainViewModel::setFallbackModel,
                                        onResetGeminiModels = mainViewModel::resetGeminiModelsToDefaults,
                                        onTestConnection = mainViewModel::testGeminiConnection,
                                        onSetAiTonePreset = mainViewModel::setAiTonePreset,
                                        onSetCustomPrompt = mainViewModel::setCustomSystemPrompt,
                                        onSetTtsRate = mainViewModel::setTtsSpeechRate,
                                        onSetDefaultLanguage = mainViewModel::setDefaultLanguage,
                                        onSetDefaultAnswerTime = mainViewModel::setDefaultAnswerTimeSec,
                                        onExportJson = mainViewModel::exportBackupJson,
                                        onImportJson = mainViewModel::importBackupJson,
                                        onClearAllData = mainViewModel::clearAllData,
                                        onEditProfile = { editingProfileManually = true },
                                        onBack = { navController.popBackStack() },
                                        modifier = Modifier
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                    )
                                }

                                composable(Routes.SESSION) {
                                    MockInterviewScreen(
                                        state = liveInterviewState,
                                        queueState = queueState,
                                        voiceState = voiceState,
                                        onUpdateDraft = interviewViewModel::updateAnswerDraft,
                                        onToggleVoiceMode = interviewViewModel::toggleVoiceMode,
                                        onStartVoiceCapture = interviewViewModel::startVoiceCapture,
                                        onStopVoiceCapture = interviewViewModel::stopVoiceCapture,
                                        onSpeakQuestion = interviewViewModel::speakCurrentQuestion,
                                        onRequestHint = interviewViewModel::requestHint,
                                        onSubmitAnswer = interviewViewModel::submitCurrentAnswer,
                                        onProceedNext = interviewViewModel::proceedAfterFeedback,
                                        onFinishEarly = interviewViewModel::finishEarlyAndReport,
                                        onRetry = interviewViewModel::retryLastQuestionOrReport,
                                        onBookmarkTurn = interviewViewModel::bookmarkTurnToLibrary,
                                        onClearBookmarkMsg = interviewViewModel::clearBookmarkMessage,
                                        onExitSession = {
                                            interviewViewModel.closeSession()
                                            navController.popBackStack()
                                        }
                                    )
                                }

                                composable(Routes.TOOL_TMAY) {
                                    TellMeAboutYourselfScreen(
                                        state = toolsState,
                                        queueState = queueState,
                                        onUpdateDraft = toolsViewModel::updateTmayDraft,
                                        onGenerateOrRefine = toolsViewModel::generateOrRefineTmay,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Routes.TOOL_SALARY) {
                                    SalaryNegotiationScreen(
                                        state = toolsState,
                                        queueState = queueState,
                                        onUpdateContext = toolsViewModel::updateNegotiationContext,
                                        onStartSimulation = toolsViewModel::startNegotiationSimulation,
                                        onSendMessage = toolsViewModel::sendNegotiationMessage,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Routes.TOOL_CODING) {
                                    CodingRoundScreen(
                                        state = toolsState,
                                        queueState = queueState,
                                        onUpdateSettings = toolsViewModel::updateCodingSettings,
                                        onUpdateCode = toolsViewModel::updateCodeEditor,
                                        onGenerateProblem = toolsViewModel::generateCodingProblem,
                                        onSubmitReview = toolsViewModel::submitCodeForReview,
                                        onBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Routes.TOOL_PREP_PLAN) {
                                    PrepPlanScreen(
                                        tasks = prepTasks,
                                        isLoading = toolsState.prepPlanLoading,
                                        errorMessage = toolsState.errorMessage,
                                        queueState = queueState,
                                        weakTopics = progressState.weakestTopics,
                                        onGeneratePlan = toolsViewModel::generatePrepPlan,
                                        onToggleTask = toolsViewModel::togglePrepTask,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }

                    if (showDockChrome && !useRail) {
                        FloatingSkyBottomBar(
                            tabs = dockTabs,
                            currentRoute = currentRoute,
                            onTabSelected = ::switchTab,
                            onCenterActionClick = {
                                interviewViewModel.startNewSession()
                                navController.navigate(Routes.SESSION)
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}
