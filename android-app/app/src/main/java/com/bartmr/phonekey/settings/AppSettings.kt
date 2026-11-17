package com.bartmr.phonekey.settings

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    companion object {
        const val DEFAULT_SERVER_PORT = 8765
        const val MIN_SERVER_PORT = 1024
        const val MAX_SERVER_PORT = 65535

        private const val PREFS_NAME = "phonekey_settings"
        private const val KEY_SERVER_PORT = "server_port"
    }
}
