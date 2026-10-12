package com.custom.gemini.assistant

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemini_assistant_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "gemini-3.8-live") ?: "gemini-3.8-live"
        set(value) = prefs.edit().putString("model_name", value).apply()

    var voiceName: String
        get() = prefs.getString("voice_name", "Puck") ?: "Puck"
        set(value) = prefs.edit().putString("voice_name", value).apply()

    var thinkingMode: Boolean
        get() = prefs.getBoolean("thinking_mode", false)
        set(value) = prefs.edit().putBoolean("thinking_mode", value).apply()
}
