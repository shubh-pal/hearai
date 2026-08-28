package com.hearai.app.audio

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * §6.10 Floating Overlay Bubble (opt-in, requires Accessibility permission). A small movable
 * bubble that expands into a caption strip showing the latest transcript line, visible over
 * other apps — implemented as a real overlay (not just the notification tray) so it can render
 * on top of whatever app the user is currently in.
 *
 * We bind an AccessibilityService purely to obtain the "draw over other apps" capability without
 * asking for SYSTEM_ALERT_WINDOW's separate settings-screen grant flow; it does not read screen
 * content (see accessibility_service_config.xml: canRetrieveWindowContent="false").
 */
@AndroidEntryPoint
class OverlayAccessibilityService : AccessibilityService() {

    @Inject lateinit var listeningController: ListeningController

    private var windowManager: WindowManager? = null
    private var bubbleView: TextView? = null
    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()

        listeningController.state
            .onEach { state ->
                val latest = state.recentLines.lastOrNull()
                bubbleView?.text = if (state.isListening) latest ?: "Listening…" else ""
                bubbleView?.visibility = if (state.isListening) View.VISIBLE else View.GONE
            }
            .launchIn(serviceScope)
    }

    private fun addBubble() {
        val view = TextView(this).apply {
            text = ""
            setBackgroundColor(0xCC212121.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Movable: drag to reposition (§6.10 "small movable bubble").
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager?.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        bubbleView = view
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bubbleView?.let { windowManager?.removeView(it) }
        bubbleView = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
