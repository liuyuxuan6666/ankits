package com.example.ankits

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

class PomodoroTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.outline_variant)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.on_surface)
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
    }

    private val arcRect = RectF()
    private var progress = 1f
    private var timeText = "00:00"
    private var labelText = ""
    private var progressColor = ContextCompat.getColor(context, R.color.primary)
    private var pulseProgress = 1f

    private var pulseAnimator: ValueAnimator? = null

    fun updateProgress(remainingSec: Int, totalSec: Int) {
        progress = if (totalSec > 0) remainingSec.toFloat() / totalSec else 0f
        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        timeText = "%02d:%02d".format(minutes, seconds)
        invalidate()
    }

    fun setLabel(label: String) {
        labelText = label
        invalidate()
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        invalidate()
    }

    fun playPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pulseProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        progress = 1f
        timeText = "00:00"
        pulseProgress = 1f
        pulseAnimator?.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val strokeWidth = width * 0.08f
        val arcSize = width * 0.78f * pulseProgress
        val arcLeft = cx - arcSize / 2
        val arcTop = cy - arcSize / 2
        val arcRight = cx + arcSize / 2
        val arcBottom = cy + arcSize / 2
        arcRect.set(arcLeft, arcTop, arcRight, arcBottom)

        bgArcPaint.strokeWidth = strokeWidth
        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = progressColor

        canvas.drawArc(arcRect, -90f, 360f, false, bgArcPaint)

        val sweepAngle = progress * 360f
        if (sweepAngle > 0f) {
            canvas.drawArc(arcRect, -90f, sweepAngle, false, progressPaint)
        }

        val displayWidth = width.toFloat()
        timeTextPaint.textSize = displayWidth * 0.18f
        val timeY = cy - displayWidth * 0.03f
        canvas.drawText(timeText, cx, timeY, timeTextPaint)

        labelTextPaint.textSize = displayWidth * 0.05f
        val labelY = cy + displayWidth * 0.1f
        canvas.drawText(labelText, cx, labelY, labelTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
