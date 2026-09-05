package com.rescuenet.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class VoiceRecognitionEvent {
    object ReadyForSpeech : VoiceRecognitionEvent()
    object SpeechStarted : VoiceRecognitionEvent()
    data class PartialText(val text: String) : VoiceRecognitionEvent()
    data class FinalText(val text: String) : VoiceRecognitionEvent()
    data class Error(val message: String) : VoiceRecognitionEvent()
}

/**
 * Real speech-to-text for Voice Emergency Mode (Part 7), using Android's on-device/OS-level
 * SpeechRecognizer rather than a scripted transcript. Supports English and Urdu today per
 * Part 7's initial-language scope; [listen] takes a BCP-47 language tag so more languages can
 * be added later without changing this class.
 *
 * Honesty note: actual recognition quality/availability depends on what the OS and installed
 * assistant app (typically Google's) support for a given language and device — this class
 * surfaces [VoiceRecognitionEvent.Error] rather than silently failing when recognition isn't
 * available, so the UI can fall back to manual typing (see VoiceModeScreen).
 */
@Singleton
class VoiceRecognitionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(languageTag: String): Flow<VoiceRecognitionEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(VoiceRecognitionEvent.Error("Speech recognition isn't available on this device."))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(VoiceRecognitionEvent.ReadyForSpeech) }
            override fun onBeginningOfSpeech() { trySend(VoiceRecognitionEvent.SpeechStarted) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                trySend(VoiceRecognitionEvent.Error(describeError(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                trySend(VoiceRecognitionEvent.FinalText(text))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(VoiceRecognitionEvent.PartialText(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again or type instead."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — try again or type instead."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a moment of connectivity to start — try again or type instead."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice mode."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error — try again or type instead."
        else -> "Couldn't start voice recognition — try again or type instead."
    }
}

/** Maps the app's language preference ("en" | "ur") to a BCP-47 tag SpeechRecognizer expects. */
fun languagePrefToTag(languagePref: String): String = when (languagePref) {
    "ur" -> "ur-PK"
    else -> "en-US"
}
