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

    fun saveDarkMode(mode: Int) = prefs.edit().putInt("dark_mode", mode).apply()
    fun getDarkMode(): Int = prefs.getInt("dark_mode", 0) // 0: System, 1: Light, 2: Dark

    fun saveColorTheme(theme: String) = prefs.edit().putString("color_theme", theme).apply()
    fun getColorTheme(): String = prefs.getString("color_theme", "Default") ?: "Default"

    fun saveChatBackground(bg: String) = prefs.edit().putString("chat_background", bg).apply()
    fun getChatBackground(): String = prefs.getString("chat_background", "Padrão") ?: "Padrão"

    fun saveCloudDriveChatId(chatId: Long) = prefs.edit().putLong("cloud_drive_chat_id", chatId).apply()
    fun getCloudDriveChatId(): Long = prefs.getLong("cloud_drive_chat_id", 0L)

    fun saveDownloadPath(path: String) = prefs.edit().putString("download_path", path).apply()
    fun getDownloadPath(): String? = prefs.getString("download_path", null)

    fun saveVideoPlayer(player: String) = prefs.edit().putString("video_player", player).apply()
    fun getVideoPlayer(): String = prefs.getString("video_player", "ExoPlayer") ?: "ExoPlayer"
}
