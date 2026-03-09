package com.example.telegramclient

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)

    fun saveCredentials(appId: String, apiHash: String) {
        prefs.edit().apply {
            putString("app_id", appId)
            putString("api_hash", apiHash)
            apply()
        }
    }

    fun getAppId(): String? = prefs.getString("app_id", null)
    fun getApiHash(): String? = prefs.getString("api_hash", null)
}
