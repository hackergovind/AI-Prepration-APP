package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.ui.navigation.PrepPilotApp
import com.example.ui.theme.PrepPilotTheme
import com.example.viewmodel.AppContainer
import com.example.viewmodel.AppViewModelFactory
import com.example.viewmodel.InterviewViewModel
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ProgressViewModel
import com.example.viewmodel.ToolsViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        appContainer = AppContainer(applicationContext)
        val factory = AppViewModelFactory(appContainer)
        val mainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        val interviewViewModel = ViewModelProvider(this, factory)[InterviewViewModel::class.java]
        val libraryViewModel = ViewModelProvider(this, factory)[LibraryViewModel::class.java]
        val progressViewModel = ViewModelProvider(this, factory)[ProgressViewModel::class.java]
        val toolsViewModel = ViewModelProvider(this, factory)[ToolsViewModel::class.java]

        setContent {
            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
            PrepPilotTheme(
                themeMode = preferences.themeMode,
                accentPalette = preferences.accentPalette
            ) {
                PrepPilotApp(
                    mainViewModel = mainViewModel,
                    interviewViewModel = interviewViewModel,
                    libraryViewModel = libraryViewModel,
                    progressViewModel = progressViewModel,
                    toolsViewModel = toolsViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        if (::appContainer.isInitialized) {
            appContainer.speechHelper.destroy()
        }
        super.onDestroy()
    }
}
