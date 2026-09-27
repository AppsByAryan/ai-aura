package com.example.aura.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class WakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var isEnabled = false
    private var isRunning = false
    private var isSuspended = false // Suspended while AURA is actively processing or speaking

    var wakeWordPhrase: String = "Hey AURA"
        private set

    var sensitivity: Float = 0.7f // 0.1 (low) to 1.0 (high)
        private set

    private var speechRecognizer: SpeechRecognizer? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun configure(phrase: String, sens: Float, enabled: Boolean) {
        this.wakeWordPhrase = if (phrase.isNotBlank()) phrase.trim() else "Hey AURA"
        this.sensitivity = sens.coerceIn(0.1f, 1.0f)
        this.isEnabled = enabled

        if (isEnabled && !isRunning) {
            start()
        } else if (!isEnabled && isRunning) {
            stop()
        }
    }

    fun start() {
        if (!isEnabled || isRunning) return

        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission required for wake word detection.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service unavailable on device.")
            return
        }

        isRunning = true
        onListeningStateChanged(true)
        startRecognitionCycle()
    }

    fun stop() {
        isRunning = false
        loopJob?.cancel()
        loopJob = null
        destroyRecognizer()
        onListeningStateChanged(false)
    }

    /**
     * Temporarily suspends the background wake word engine while AURA is speaking or executing
     * an action, preventing feedback loops and conserving device resources.
     */
    fun setSuspended(suspended: Boolean) {
        if (isSuspended == suspended) return
        isSuspended = suspended
        if (suspended) {
            destroyRecognizer()
            onListeningStateChanged(false)
        } else if (isRunning && isEnabled) {
            onListeningStateChanged(true)
            startRecognitionCycle()
        }
    }

    private fun startRecognitionCycle() {
        if (!isRunning || isSuspended) return

        destroyRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // In wake-word continuous cycle, timeouts and NO_MATCH are expected.
                        // We schedule the next lightweight cycle with an adaptive rest interval to save battery.
                        scheduleNextCycle(if (error == SpeechRecognizer.ERROR_NO_MATCH) 250L else 1200L)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processMatches(matches)
                        scheduleNextCycle(150L)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (partials != null && checkWakeWordMatch(partials)) {
                            // Immediate trigger on partial result for ultra-fast response!
                            triggerWakeWord()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Short speech timeout to keep detection lightweight
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleNextCycle(2000L)
        }
    }

    private fun scheduleNextCycle(delayMs: Long) {
        if (!isRunning || isSuspended) return

        loopJob?.cancel()
        loopJob = scope.launch {
            // Adaptive sleep interval: higher sensitivity uses shorter delay, conserving CPU cycles
            val adjustedDelay = (delayMs * (1.2f - (sensitivity * 0.4f))).toLong().coerceAtLeast(100L)
            delay(adjustedDelay)
            if (isActive && isRunning && !isSuspended) {
                startRecognitionCycle()
            }
        }
    }

    private fun processMatches(matches: List<String>?) {
        if (matches == null) return
        if (checkWakeWordMatch(matches)) {
            triggerWakeWord()
        }
    }

    private fun checkWakeWordMatch(phrases: List<String>): Boolean {
        val target = wakeWordPhrase.lowercase().trim()
        val normalizedTarget = target.replace(Regex("[^a-z0-9]"), "")

        return phrases.any { phrase ->
            val cleanPhrase = phrase.lowercase().trim()
            val normalizedPhrase = cleanPhrase.replace(Regex("[^a-z0-9]"), "")

            // Exact match, contains match, or phonetic sub-match
            cleanPhrase.contains(target) ||
                    normalizedPhrase.contains(normalizedTarget) ||
                    (target.contains("aura") && cleanPhrase.contains("aura")) ||
                    (target.contains("aura") && cleanPhrase.contains("ora"))
        }
    }

    private fun triggerWakeWord() {
        // Haptic pulse confirming wake word recognition
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120L)
            }
        } catch (_: Exception) {}

        // Suspend wake loop while user delivers their command
        destroyRecognizer()
        onWakeWordDetected(wakeWordPhrase)
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun release() {
        stop()
        destroyRecognizer()
    }
}
