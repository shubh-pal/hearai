package com.hearai.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.hearai.app.MainActivity
import com.hearai.app.data.repo.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * §6.9 Home Screen Widget. Deliberately does NOT attempt live word-by-word updates — "Android
 * widgets are periodic-refresh, not real-time." Shows the last summary snippet and a
 * listening on/off state, refreshed on the normal widget update cycle (see
 * hearai_widget_info.xml: updatePeriodMillis). Tapping it opens the Home screen.
 */
class HearAiWidget(
    private val sessionRepository: SessionRepository,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lastSummary = sessionRepository.observeAllSummaries().first().firstOrNull()

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(android.graphics.Color.parseColor("#EADDFF")))
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text("HearAI")
                Text(lastSummary?.text ?: "No summaries yet.")
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HearAiWidgetEntryPoint {
    fun sessionRepository(): SessionRepository
}

class HearAiWidgetReceiver : GlanceAppWidgetReceiver() {
    // A GlanceAppWidget can't be constructor-injected by Hilt directly — the platform
    // instantiates this receiver — so it reaches the repository via a Hilt entry point instead.
    override val glanceAppWidget: GlanceAppWidget
        get() = HearAiWidget(entryPoint.sessionRepository())

    private lateinit var entryPoint: HearAiWidgetEntryPoint

    override fun onReceive(context: Context, intent: android.content.Intent) {
        entryPoint = EntryPoints.get(context.applicationContext, HearAiWidgetEntryPoint::class.java)
        super.onReceive(context, intent)
    }
}
