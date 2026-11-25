package dev.victorstein.reycast

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

// Catppuccin Mocha, matching Termux; the violet is the Mac's Ghostty cursor colour.
internal object ReyCastColors {
    // Ends where it starts, so the sweep has no seam as it turns.
    val BORDER = intArrayOf(0xFF7C3AED.toInt(), 0xFFCBA6F7.toInt(), 0xFF89B4FA.toInt(), 0xFF7C3AED.toInt())
    const val FILL = 0xFF1E1E2E.toInt()
    const val TEXT = 0xFFCDD6F4.toInt()
}

/** A rounded border whose colours sweep around the edge, around [fillColor] or, when null, nothing. */
internal class SpinningBorder(
    private val strokePx: Float,
    private val radiusPx: Float,
    private val fillColor: Int? = ReyCastColors.FILL,
) : Drawable() {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { fillColor?.let { color = it } }
    private val rotation = Matrix()
    private val outer = RectF()
    private val inner = RectF()
    private val ring = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private var degrees = 0f

    /** Turns the colours once every [periodMs] until the returned animator is cancelled. */
    fun spin(periodMs: Long = 2500L): ValueAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = periodMs
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            degrees = it.animatedValue as Float
            invalidateSelf()
        }
        start()
    }

    override fun onBoundsChange(bounds: Rect) {
        outer.set(bounds)
        inner.set(outer)
        inner.inset(strokePx, strokePx)
        ring.reset()
        ring.addRoundRect(outer, radiusPx, radiusPx, Path.Direction.CW)
        ring.addRoundRect(inner, radiusPx - strokePx, radiusPx - strokePx, Path.Direction.CW)
        borderPaint.shader = SweepGradient(outer.centerX(), outer.centerY(), ReyCastColors.BORDER, null)
    }

    override fun draw(canvas: Canvas) {
        rotation.setRotate(degrees, outer.centerX(), outer.centerY())
        borderPaint.shader?.setLocalMatrix(rotation)
        canvas.drawPath(ring, borderPaint)
        if (fillColor != null) canvas.drawRoundRect(inner, radiusPx - strokePx, radiusPx - strokePx, fillPaint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
