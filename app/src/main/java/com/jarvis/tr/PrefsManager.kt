package com.jarvis.tr

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var pitch: Float
        get() = prefs.getFloat("pitch", 0.85f)
        set(v) = prefs.edit().putFloat("pitch", v).apply()

    var rate: Float
        get() = prefs.getFloat("rate", 1.0f)
        set(v) = prefs.edit().putFloat("rate", v).apply()

    var voiceOn: Boolean
        get() = prefs.getBoolean("voice_on", true)
        set(v) = prefs.edit().putBoolean("voice_on", v).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v).apply()

    var autoRestart: Boolean
        get() = prefs.getBoolean("auto_restart", true)
        set(v) = prefs.edit().putBoolean("auto_restart", v).apply()
}