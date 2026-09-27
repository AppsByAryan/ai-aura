package com.example.aura.data

import android.content.Context
import android.content.SharedPreferences

class AuraPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aura_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WAKE_WORD_ENABLED = "key_wake_word_enabled"
        private const val KEY_WAKE_WORD_PHRASE = "key_wake_word_phrase"
        private const val KEY_WAKE_WORD_SENSITIVITY = "key_wake_word_sensitivity"
        private const val KEY_DIRECT_EXECUTION = "key_direct_execution"
        const val DEFAULT_WAKE_WORD = "Hey AURA"
        const val DEFAULT_SENSITIVITY = 0.7f
    }

    var isDirectExecution: Boolean
        get() = prefs.getBoolean(KEY_DIRECT_EXECUTION, true)
        set(value) = prefs.edit().putBoolean(KEY_DIRECT_EXECUTION, value).apply()

    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var wakeWordPhrase: String
        get() = prefs.getString(KEY_WAKE_WORD_PHRASE, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit().putString(KEY_WAKE_WORD_PHRASE, value.trim()).apply()

    var wakeWordSensitivity: Float
        get() = prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, value).apply()
}
