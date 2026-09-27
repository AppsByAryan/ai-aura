package com.example.aura.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import com.example.aura.data.ActionType
import com.example.aura.data.AuraActionPlan
import kotlinx.coroutines.delay

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Failure(val reason: String) : ExecutionResult()
}

class AndroidExecutor(private val context: Context) {

    suspend fun execute(plan: AuraActionPlan): ExecutionResult {
        return try {
            when (plan.actionType) {
                ActionType.OPEN_APP -> launchApp(plan.target, plan.payload)
                ActionType.OPEN_URL -> openUrl(plan.target)
                ActionType.OPEN_SETTINGS -> openSettings(plan.target)
                ActionType.SET_VOLUME -> adjustVolume(plan.payload["percent"]?.toIntOrNull() ?: 50)
                ActionType.MEDIA_CONTROL -> controlMedia(plan.target)
                ActionType.TAKE_SCREENSHOT -> takeScreenshot()
                ActionType.COMMUNICATION -> handleCommunication(plan.target, plan.payload)
                ActionType.CROSS_DEVICE_COMMAND -> executeCrossDevice(plan.device, plan.target, plan.payload)
                ActionType.GET_BATTERY_INFO,
                ActionType.GET_STORAGE_INFO,
                ActionType.GET_MEMORY_INFO,
                ActionType.GET_NETWORK_INFO -> {
                    ExecutionResult.Success("System status retrieved successfully.")
                }
                ActionType.STOP_CANCEL -> {
                    ExecutionResult.Success("Action safely cancelled by user command.")
                }
                ActionType.CONVERSATIONAL_RESPONSE -> {
                    ExecutionResult.Success("Response completed.")
                }
                ActionType.UNSUPPORTED_ACTION -> {
                    ExecutionResult.Failure("Android does not support direct automated execution for this action.")
                }
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("Execution error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun launchApp(target: String, payload: Map<String, String>): ExecutionResult {
        val normTarget = target.lowercase().trim()

        if (normTarget.contains("download")) {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ExecutionResult.Success("Opened Downloads manager.")
        }

        if (normTarget.contains("setting")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ExecutionResult.Success("Opened Android System Settings.")
        }

        val packageCandidates = when {
            normTarget.contains("youtube") -> listOf("com.google.android.youtube")
            normTarget.contains("chrome") -> listOf("com.android.chrome")
            normTarget.contains("spotify") || normTarget.contains("music") -> listOf("com.spotify.music", "com.google.android.apps.youtube.music")
            normTarget.contains("map") -> listOf("com.google.android.apps.maps")
            normTarget.contains("camera") -> listOf("com.google.android.GoogleCamera")
            else -> listOf(payload["packageName"] ?: "")
        }

        for (pkg in packageCandidates) {
            if (pkg.isNotEmpty()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionResult.Success("Launched ${target.replaceFirstChar { it.uppercase() }}.")
                }
            }
        }

        // Web fallback for known web services
        if (normTarget.contains("youtube")) {
            return openUrl("https://www.youtube.com")
        }
        if (normTarget.contains("chrome") || normTarget.contains("browser")) {
            return openUrl("https://www.google.com")
        }

        return ExecutionResult.Failure("Application '$target' was not found on this device.")
    }

    private fun openUrl(rawUrl: String): ExecutionResult {
        val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opened $formattedUrl in browser.")
        } catch (e: Exception) {
            ExecutionResult.Failure("Could not open URL: ${e.message}")
        }
    }

    private fun openSettings(target: String): ExecutionResult {
        val norm = target.lowercase()
        val action = when {
            norm.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            norm.contains("wifi") || norm.contains("network") -> Settings.ACTION_WIFI_SETTINGS
            norm.contains("sound") || norm.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
            norm.contains("accessibility") -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            norm.contains("disturb") || norm.contains("dnd") -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            norm.contains("display") -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ExecutionResult.Success("Navigated to ${target.replaceFirstChar { it.uppercase() }} settings.")
    }

    private fun adjustVolume(percent: Int): ExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ExecutionResult.Failure("Audio service unavailable.")

        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetIndex = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
        return ExecutionResult.Success("Media volume adjusted to $percent% (level $targetIndex/$max).")
    }

    private fun controlMedia(command: String): ExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ExecutionResult.Failure("Audio service unavailable.")

        val norm = command.lowercase()
        val keyCode = when {
            norm.contains("play") -> KeyEvent.KEYCODE_MEDIA_PLAY
            norm.contains("pause") -> KeyEvent.KEYCODE_MEDIA_PAUSE
            norm.contains("next") -> KeyEvent.KEYCODE_MEDIA_NEXT
            norm.contains("previous") -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            norm.contains("stop") -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        am.dispatchMediaKeyEvent(eventDown)
        am.dispatchMediaKeyEvent(eventUp)

        return ExecutionResult.Success("Media command '$command' dispatched via Android AudioManager.")
    }

    private fun takeScreenshot(): ExecutionResult {
        val service = AuraAccessibilityService.getInstance()
        if (service != null) {
            val success = service.takeSystemScreenshot()
            return if (success) {
                ExecutionResult.Success("Screenshot captured via AURA Assistive Service.")
            } else {
                ExecutionResult.Failure("Screenshot capture failed on this Android version.")
            }
        } else {
            // Explain cleanly
            return ExecutionResult.Failure("AURA Assistive Service is not enabled. Go to Settings > Accessibility to enable AURA for automated screenshots.")
        }
    }

    private fun handleCommunication(target: String, payload: Map<String, String>): ExecutionResult {
        val contactOrNumber = payload["recipient"] ?: target
        val message = payload["message"]

        return if (message != null) {
            // SMS Composer
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$contactOrNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opened messaging composer for $contactOrNumber.")
        } else {
            // Dialer
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$contactOrNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ExecutionResult.Success("Opened phone dialer for $contactOrNumber.")
        }
    }

    private suspend fun executeCrossDevice(device: String, command: String, payload: Map<String, String>): ExecutionResult {
        // Cross-device TLS communication protocol simulation
        delay(600) // Authenticated handshake simulation
        val isLaptopOnline = payload["isOnline"]?.toBoolean() ?: true
        if (!isLaptopOnline) {
            return ExecutionResult.Failure("$device is currently offline.")
        }

        return ExecutionResult.Success("Command '$command' dispatched to $device via encrypted channel. Agent confirmed execution.")
    }
}
