package com.hearai.app.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hearai.app.HearAiApplication
import com.hearai.app.MainActivity
import com.hearai.app.R
import com.hearai.app.data.model.VadSensitivity
import com.hearai.app.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Display Surface #1 (§4): a foreground service that survives backgrounding/screen-off and
 * shows a persistent, dismiss-proof notification per Android's foreground-service requirement.
 * Delegates the actual capture/VAD/streaming pipeline to [ListeningController], which is a
 * singleton shared with the UI layer so state stays consistent regardless of which one is
 * observing.
 */
@AndroidEntryPoint
class ListeningService : Service() {

    @Inject lateinit var listeningController: ListeningController
    @Inject lateinit var settingsStore: SettingsStore

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                listeningController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        startForeground(NOTIFICATION_ID, buildNotification(isSpeechActive = false))

        serviceScope.launch {
            val sensitivity = settingsStore.settings.first().vadSensitivity
            listeningController.start(sensitivity)
        }

        stateObserverJob = listeningController.state
            .onEach { state ->
                val notification = buildNotification(state.isSpeechActive)
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification)
            }
            .launchIn(serviceScope)
    }

    private fun buildNotification(isSpeechActive: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // §8 Privacy: this notification is itself the "visible/audible indicator that listening
        // is active" — it must stay legible, not just exist.
        val statusText = if (isSpeechActive) {
            getString(R.string.notification_listening_text_active)
        } else {
            getString(R.string.notification_listening_text_idle)
        }

        return NotificationCompat.Builder(this, HearAiApplication.LISTENING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_listening_title))
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.hearai.app.action.STOP_LISTENING"
        private const val NOTIFICATION_ID = 42
    }
}
