package dev.victorstein.reycast

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** Shows a message from the Mac (e.g. "Claude finished · personal") over every app; tapping it opens Termux. */
class NoticeBanner(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable { leave() }
    private var shown: View? = null
    private var shownParams: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var spinner: ValueAnimator? = null

    /** Safe from any thread; a newer notice replaces the one on screen. */
    fun show(text: String) {
        handler.post { display(text) }
    }

    /** Main thread only. Also drops notices still queued, so none appears after the service is gone. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        dismiss()
    }

    private fun display(text: String) {
        dismiss()
        val border = SpinningBorder(dp(BORDER_DP).toFloat(), dp(CORNER_DP).toFloat())
        val banner = TextView(service).apply {
            this.text = text
            setTextColor(ReyCastColors.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = border
            setPadding(dp(22), dp(14), dp(22), dp(14))
            alpha = 0f
            setOnClickListener {
                dismiss()
                openTermux()
            }
        }
        val params = bannerParams()
        windowManager.addView(banner, params)
        shown = banner
        shownParams = params
        spinner = border.spin()
        animate(banner, params, toY = dp(TOP_DP), toAlpha = 1f, ENTER_MS, OvershootInterpolator(1.4f), alphaSpeed = 4f)
        handler.postDelayed(hide, SHOW_MS)
        playChime()
    }

    // Media usage, so it plays on Vibrate at the media volume (the user's choice). No audio focus is
    // requested, so a video or song playing at the time keeps going.
    private fun playChime() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = MediaPlayer.create(service, R.raw.notice_chime, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE) ?: return
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    private fun leave() {
        val banner = shown ?: return
        val params = shownParams ?: return
        animate(banner, params, toY = -dp(OFFSCREEN_DP), toAlpha = 0f, LEAVE_MS, AccelerateInterpolator()) { dismiss() }
    }

    // Moves the window itself rather than the view: a view translated inside a wrap_content window gets clipped.
    // Alpha follows time, not the easing, and alphaSpeed > 1 makes the banner opaque early enough to see it travel.
    private fun animate(
        banner: View,
        params: WindowManager.LayoutParams,
        toY: Int,
        toAlpha: Float,
        durationMs: Long,
        easing: TimeInterpolator,
        alphaSpeed: Float = 1f,
        onFinished: () -> Unit = {},
    ) {
        animator?.cancel()
        val fromY = params.y
        val fromAlpha = banner.alpha
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = easing
            addUpdateListener {
                val progress = it.animatedValue as Float
                params.y = fromY + ((toY - fromY) * progress).toInt()
                banner.alpha = fromAlpha + (toAlpha - fromAlpha) * (it.animatedFraction * alphaSpeed).coerceAtMost(1f)
                if (banner.parent != null) windowManager.updateViewLayout(banner, params)
            }
            addListener(object : AnimatorListenerAdapter() {
                // A cancelled animation also ends; only the one still current may finish the banner.
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) onFinished()
                }
            })
            start()
        }
    }

    private fun dismiss() {
        handler.removeCallbacks(hide)
        animator?.let {
            animator = null
            it.cancel()
        }
        spinner?.cancel()
        spinner = null
        shown?.takeIf { it.parent != null }?.let(windowManager::removeView)
        shown = null
        shownParams = null
    }

    private fun openTermux() {
        val launch = service.packageManager.getLaunchIntentForPackage(TERMUX) ?: return
        try {
            service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot open Termux", e)
        }
    }

    // Not focusable, so typing in the app underneath carries on; touches outside the banner pass through.
    // NO_LIMITS lets the window start above the screen edge for the drop-in.
    private fun bannerParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = -dp(OFFSCREEN_DP)
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val SHOW_MS = 6000L
        const val ENTER_MS = 600L
        const val LEAVE_MS = 250L
        const val TOP_DP = 32
        const val OFFSCREEN_DP = 100
        const val BORDER_DP = 3
        const val CORNER_DP = 14
        const val TERMUX = "com.termux"
        const val TAG = "ReyCastNotice"
    }
}
