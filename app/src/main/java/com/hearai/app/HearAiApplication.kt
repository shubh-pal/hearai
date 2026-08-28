package com.hearai.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HearAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** Display Surface #1 (§4, §6.4 "actively listening" indicator) needs a channel on
     * Android 13+ where notification permission itself must also be requested. */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LISTENING_CHANNEL_ID,
                getString(R.string.notification_channel_listening_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_listening_description)
            },
        )
    }

    companion object {
        const val LISTENING_CHANNEL_ID = "hearai_listening_channel"
    }
}
