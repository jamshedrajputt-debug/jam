package com.flyexj.assistant
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
class VoiceInputManager(context: Context, private val onText: (String) -> Unit, private val onState: (String) -> Unit) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    fun start(languageTag: String? = null) {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) { onState("Speech recognition service unavailable"); return }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { if (active) onState("Listening…") }
                        override fun onBeginningOfSpeech() { if (active) onState("Hearing your voice…") }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() { if (active) onState("Processing speech…") }
                        override fun onError(error: Int) {
                            if (!active) return
                            active = false
                            onState(errorMessage(error))
                        }
                        override fun onResults(results: Bundle?) {
                            if (!active) return
                            active = false
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!spoken.isNullOrBlank()) onText(spoken) else onState("No speech recognized")
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            if (!active) return
                            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!partial.isNullOrBlank()) onState("Hearing: $partial")
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                if (!languageTag.isNullOrBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            active = true
            recognizer?.startListening(intent)
        } catch (_: Exception) { active = false; onState("Could not start microphone") }
    }
    fun stop() { active = false; try { recognizer?.cancel() } catch (_: Exception) {} }
    fun destroy() { stop(); try { recognizer?.destroy() } catch (_: Exception) {}; recognizer = null }
    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech service stopped"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Speech network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that—try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy—try again"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition error"
    }
}