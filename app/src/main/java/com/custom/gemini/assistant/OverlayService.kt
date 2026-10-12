package com.custom.gemini.assistant

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class OverlayService : Service(), GeminiLiveManager.LiveEventsListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE = "ACTION_TOGGLE"
        private const val CHANNEL_ID = "gemini_assistant_channel"
        private const val NOTIF_ID = 101
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var liveManager: GeminiLiveManager? = null
    private var isMuted = false
    private var isShowing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_TOGGLE -> {
                if (isShowing) {
                    hideOverlay()
                } else {
                    showOverlay()
                }
            }
            ACTION_START -> showOverlay()
            ACTION_STOP -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (isShowing) return
        startForeground(NOTIF_ID, createNotification())

        val prefs = PreferencesManager(this)
        if (prefs.apiKey.isBlank()) {
            // Open MainActivity if API key is not configured
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_bar, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120 // elevated from bottom
        }

        setupViews(floatingView!!, params)
        windowManager?.addView(floatingView, params)
        isShowing = true

        // Connect to Gemini Live
        liveManager = GeminiLiveManager(
            apiKey = prefs.apiKey,
            model = prefs.modelName,
            voice = prefs.voiceName,
            thinking = prefs.thinkingMode,
            listener = this
        )
        liveManager?.connect()
    }

    private fun setupViews(view: View, params: WindowManager.LayoutParams) {
        val orb = view.findViewById<View>(R.id.orbIndicator)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val btnCapture = view.findViewById<ImageButton>(R.id.btnCapture)
        val btnMute = view.findViewById<ImageButton>(R.id.btnMute)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        // Subtle pulsing animation on orb indicator
        val scaleAnim = ObjectAnimator.ofFloat(orb, "scaleX", 1f, 1.25f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleAnimY = ObjectAnimator.ofFloat(orb, "scaleY", 1f, 1.25f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
        }
        scaleAnim.start()
        scaleAnimY.start()

        // Mute / Unmute Button
        btnMute.setOnClickListener {
            isMuted = !isMuted
            liveManager?.setMute(isMuted)
            if (isMuted) {
                btnMute.setImageResource(R.drawable.ic_mic_off)
                tvStatus.text = "Microphone Muted"
            } else {
                btnMute.setImageResource(R.drawable.ic_mic)
                tvStatus.text = "Listening..."
            }
        }

        // Close / Disconnect Button
        btnClose.setOnClickListener {
            hideOverlay()
        }

        // Screen Capture Button (Simulate snapshot prompt to Live model)
        btnCapture.setOnClickListener {
            tvStatus.text = "Analyzing screen..."
            takeDecorSnapshot()
        }

        // Drag to reposition
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY - (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun takeDecorSnapshot() {
        try {
            // Generate a placeholder bitmap or capture frame
            val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.DKGRAY)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
            }
            canvas.drawText("Active Screen View", 40f, 300f, paint)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val bytes = stream.toByteArray()
            liveManager?.sendScreenshot(bytes)
        } catch (e: Exception) {
            floatingView?.findViewById<TextView>(R.id.tvStatus)?.text = "Capture error: ${e.message}"
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return
        liveManager?.disconnect()
        liveManager = null

        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onConnected() {
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tvStatus)?.text = "Gemini Live Ready"
        }
    }

    override fun onDisconnected(reason: String) {
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tvStatus)?.text = "Disconnected"
        }
    }

    override fun onTranscription(text: String, isModel: Boolean) {
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tvStatus)?.text = text
        }
    }

    override fun onError(error: String) {
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tvStatus)?.text = "Error: $error"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gemini Floating Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gemini Assistant Active")
            .setContentText("Listening in floating overlay")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
