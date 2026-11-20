package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "SpeechHelper"

data class VoiceInputState(
    val isListening: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val livePartialTranscript: String = "",
    val finalizedTranscript: String = "",
    val longPauseCount: Int = 0,
    val speakingStartMs: Long = 0L,
    val errorMessage: String? = null,
    val isRecognitionAvailable: Boolean = true
)

private data class PendingSpeech(
    val text: String,
    val speechRate: Float,
    val languageName: String
)

/**
 * Manages native Android SpeechRecognizer dictation with pause detection
 * and TextToSpeech question playback (no browser Web Speech API).
 */
class SpeechHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: PendingSpeech? = null
    private var lastSpeechActivityMs = 0L
    private var accumulatedFinal = StringBuilder()

    private val _state = MutableStateFlow(
        VoiceInputState(isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
            } else {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(isTtsSpeaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(isTtsSpeaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(isTtsSpeaking = false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(isTtsSpeaking = false)
                    }
                })
                pendingSpeech?.let { queued ->
                    pendingSpeech = null
                    speak(queued.text, queued.speechRate, queued.languageName)
                }
            }
        }
    }

    fun speak(text: String, speechRate: Float = 1.0f, languageName: String = "English") {
        if (text.isBlank()) return
        if (!ttsReady) {
            pendingSpeech = PendingSpeech(text, speechRate, languageName)
            return
        }
        val locale = localeForLanguage(languageName)
        tts?.language = locale
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preppilot_utterance")
    }

    fun stopSpeaking() {
        pendingSpeech = null
        tts?.stop()
        _state.value = _state.value.copy(isTtsSpeaking = false)
    }

    fun startListening(languageName: String = "English", existingPrefix: String = "") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                isRecognitionAvailable = false,
                errorMessage = "Speech recognition service is unavailable on this device. Use text mode instead."
            )
            return
        }

        stopSpeaking()
        accumulatedFinal = StringBuilder(existingPrefix.trim())
        val now = System.currentTimeMillis()
        lastSpeechActivityMs = now

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }

        val localeTag = localeForLanguage(languageName).toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

        _state.value = _state.value.copy(
            isListening = true,
            speakingStartMs = now,
            errorMessage = null,
            livePartialTranscript = "",
            finalizedTranscript = accumulatedFinal.toString()
        )
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = _state.value.copy(isListening = false, livePartialTranscript = "")
    }

    fun resetTranscript() {
        accumulatedFinal.clear()
        _state.value = VoiceInputState(
            isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    }

    fun destroy() {
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            val now = System.currentTimeMillis()
            if (lastSpeechActivityMs > 0L && (now - lastSpeechActivityMs) > 2500L) {
                _state.value = _state.value.copy(longPauseCount = _state.value.longPauseCount + 1)
            }
            lastSpeechActivityMs = now
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap the mic and try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap the mic to continue."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice mode."
                else -> "Voice recognition unavailable (code $error). You can type your answer."
            }
            _state.value = _state.value.copy(isListening = false, errorMessage = msg)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty().trim()
            if (text.isNotEmpty()) {
                if (accumulatedFinal.isNotEmpty()) accumulatedFinal.append(" ")
                accumulatedFinal.append(text)
            }
            lastSpeechActivityMs = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isListening = false,
                livePartialTranscript = "",
                finalizedTranscript = accumulatedFinal.toString()
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            val now = System.currentTimeMillis()
            if (partial.isNotBlank() && lastSpeechActivityMs > 0L && (now - lastSpeechActivityMs) > 2500L) {
                _state.value = _state.value.copy(longPauseCount = _state.value.longPauseCount + 1)
            }
            if (partial.isNotBlank()) {
                lastSpeechActivityMs = now
            }
            _state.value = _state.value.copy(livePartialTranscript = partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun localeForLanguage(languageName: String): Locale {
        return when (languageName.lowercase()) {
            "spanish", "español" -> Locale("es", "ES")
            "french", "français" -> Locale.FRENCH
            "german", "deutsch" -> Locale.GERMAN
            "hindi" -> Locale("hi", "IN")
            else -> Locale.US
        }
    }
}
