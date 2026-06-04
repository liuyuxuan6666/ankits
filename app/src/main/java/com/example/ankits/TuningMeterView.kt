package com.example.ankits

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class TuningMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var cents: Float = 0f
        set(value) {
            field = value.coerceIn(-50f, 50f)
            invalidate()
        }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8E8E8.toInt()
        style = Paint.Style.FILL
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9E9E9E.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barLeft = 48f
        val barRight = w - 48f
        val barWidth = barRight - barLeft
        val barTop = h * 0.32f
        val barHeight = 16f
        val barCenterY = barTop + barHeight / 2f
        val radius = barHeight / 2f

        // Bar background
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + barHeight), radius, radius, barBgPaint)

        // Colored bar with gradient
        val gradient = LinearGradient(barLeft, 0f, barRight, 0f,
            intArrayOf(
                0xFFF44336.toInt(),  // red
                0xFFFFC107.toInt(),  // yellow
                0xFF4CAF50.toInt(),  // green
                0xFF4CAF50.toInt(),  // green
                0xFFFFC107.toInt(),  // yellow
                0xFFF44336.toInt()   // red
            ),
            floatArrayOf(0f, 0.30f, 0.45f, 0.55f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        barPaint.shader = gradient
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + barHeight), radius, radius, barPaint)
        barPaint.shader = null

        // Pointer position
        val pointerX = barLeft + (cents + 50f) / 100f * barWidth

        // Pointer color
        val pointerColor = when {
            abs(cents) <= 5f -> 0xFF4CAF50.toInt()
            abs(cents) <= 15f -> 0xFFFFC107.toInt()
            else -> 0xFFF44336.toInt()
        }

        // Triangle pointer above bar
        pointerPaint.color = pointerColor
        val path = Path()
        path.moveTo(pointerX, barTop - 16f)
        path.lineTo(pointerX - 12f, barTop)
        path.lineTo(pointerX + 12f, barTop)
        path.close()
        canvas.drawPath(path, pointerPaint)

        // Line below pointer
        linePaint.color = pointerColor
        linePaint.strokeWidth = 4f
        canvas.drawLine(pointerX, barTop + barHeight, pointerX, barTop + barHeight + 20f, linePaint)

        // Tick marks at -50, -25, 0, +25, +50
        val ticks = floatArrayOf(-50f, -25f, 0f, 25f, 50f)
        for (tick in ticks) {
            val tx = barLeft + (tick + 50f) / 100f * barWidth
            linePaint.color = 0xFFBDBDBD.toInt()
            linePaint.strokeWidth = 2f
            canvas.drawLine(tx, barTop + barHeight + 4f, tx, barTop + barHeight + 16f, linePaint)
        }

        // Labels
        tickPaint.color = 0xFF9E9E9E.toInt()
        val labelY = barTop + barHeight + 42f
        canvas.drawText("-50", barLeft, labelY, tickPaint)
        canvas.drawText("0", barLeft + barWidth / 2f, labelY, tickPaint)
        canvas.drawText("+50", barRight, labelY, tickPaint)

        // Flat / Sharp symbols
        labelPaint.color = 0xFFBDBDBD.toInt()
        canvas.drawText("\u266D", barLeft, barTop - 24f, labelPaint)
        canvas.drawText("\u266F", barRight, barTop - 24f, labelPaint)

        // Cent unit
        canvas.drawText("cents", w / 2f, labelY + 20f, tickPaint.apply { textSize = 14f })
    }
}
