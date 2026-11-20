package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.GeminiAiService
import com.example.data.local.PrepDatabase
import com.example.data.local.PrepRepository
import com.example.data.local.UserPreferencesRepository
import com.example.utils.SpeechHelper

/**
 * Simple manual dependency container and ViewModelProvider.Factory using constructor injection.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = PrepDatabase.getInstance(appContext)
    private val prefsRepo = UserPreferencesRepository(appContext)
    val repository = PrepRepository(database.prepDao(), prefsRepo)
    val aiService = GeminiAiService()
    val speechHelper = SpeechHelper(appContext)
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(container.repository, container.aiService) as T
            modelClass.isAssignableFrom(InterviewViewModel::class.java) ->
                InterviewViewModel(container.repository, container.aiService, container.speechHelper) as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(container.repository, container.aiService) as T
            modelClass.isAssignableFrom(ProgressViewModel::class.java) ->
                ProgressViewModel(container.repository) as T
            modelClass.isAssignableFrom(ToolsViewModel::class.java) ->
                ToolsViewModel(container.repository, container.aiService) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
