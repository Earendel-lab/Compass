/*
 * This file is part of Compass.
 * Copyright (C) 2026 Philipp Bobek <philipp.bobek@mailbox.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Compass is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.earendel.compass.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.earendel.compass.R
import com.google.android.material.color.MaterialColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

class LevelMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val LEVEL_THRESHOLD = 0.5f
    private val ORIENTATION_THRESHOLD = 45f
    private val RING_RADIUS_RATIO = 0.35f
    private val LINE_LENGTH_RATIO = 0.35f
    private val STROKE_WIDTH_DP = 6.0f
    private val DEGREE_TEXT_SIZE_SP = 48f
    private val VIBRATION_DEGREE_INTERVAL = 1f
    private val VIBRATION_DURATION_MS = 10L

    private val greenColor = ContextCompat.getColor(context, R.color.green_level)
    private val textColorPrimary = MaterialColors.getColor(this, android.R.attr.textColorPrimary)
    private val colorTertiary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(STROKE_WIDTH_DP)
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val degreeTextView: TextView = TextView(context).apply {
        setTextColor(textColorPrimary)
        textSize = DEGREE_TEXT_SIZE_SP
        text = " 0°"
        gravity = Gravity.CENTER
    }

    private var spin = 1f
    private var isHorizontal = false
    private var cx = 0f
    private var cy = 0f
    private var ringRadius = 0f
    private var lastVibrationDegree = 0
    private var isActive = false
    private var isHapticFeedbackEnabled = true
    private var isHighPrecisionEnabled = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setWillNotDraw(false)
        addView(degreeTextView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w * 0.5f
        cy = h * 0.5f
        ringRadius = min(w, h) * RING_RADIUS_RATIO
    }

    override fun onDraw(canvas: Canvas) {
        val lineLen = ringRadius * LINE_LENGTH_RATIO

        drawReferenceLines(canvas, lineLen)
        drawRotatingLine(canvas, lineLen)
        drawOuterRing(canvas)
        eraseRingInterior(canvas)
    }

    private fun drawOuterRing(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.color = getRingColor()
        canvas.drawCircle(cx, cy, ringRadius, paint)
    }

    private fun drawReferenceLines(canvas: Canvas, lineLen: Float) {
        paint.color = getReferenceLineColor()

        if (isHorizontal) {
            canvas.drawLine(cx, cy - ringRadius, cx, cy - ringRadius - lineLen, paint)
            canvas.drawLine(cx, cy + ringRadius, cx, cy + ringRadius + lineLen, paint)
        } else {
            canvas.drawLine(cx + ringRadius, cy, cx + ringRadius + lineLen, cy, paint)
            canvas.drawLine(cx - ringRadius, cy, cx - ringRadius - lineLen, cy, paint)
        }
    }

    private fun drawRotatingLine(canvas: Canvas, lineLen: Float) {
        canvas.save()
        paint.color = getRotatingLineColor()
        canvas.rotate(spin + 90f, cx, cy)
        // Draw rotating line as two segments starting from the ring radius to avoid overlapping inside
        canvas.drawLine(cx + ringRadius, cy, cx + ringRadius + lineLen, cy, paint)
        canvas.drawLine(cx - ringRadius, cy, cx - ringRadius - lineLen, cy, paint)
        canvas.restore()
    }

    private fun eraseRingInterior(canvas: Canvas) {
        canvas.drawCircle(cx, cy, ringRadius - paint.strokeWidth, clearPaint)
    }

    fun updateTilt(gx: Float, gy: Float) {
        calculateSpinAngle(gx, gy)
        updateOrientation()
        updateDegreeDisplay()
        handleHapticFeedback()
        invalidate()
    }

    private fun handleHapticFeedback() {
        if (!isActive || !isHapticFeedbackEnabled) return
        val currentDegreeInt = (spin / VIBRATION_DEGREE_INTERVAL).roundToInt()
        if (currentDegreeInt != lastVibrationDegree) {
            lastVibrationDegree = currentDegreeInt
            performHapticFeedback()
        }
    }

    fun setIsActive(active: Boolean) {
        this.isActive = active
    }

    fun setCustomHapticFeedbackEnabled(enabled: Boolean) {
        this.isHapticFeedbackEnabled = enabled
    }

    fun setHighPrecisionEnabled(enabled: Boolean) {
        this.isHighPrecisionEnabled = enabled
        invalidate()
    }

    private fun performHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }
        }
    }

    private fun calculateSpinAngle(gx: Float, gy: Float) {
        val angleRad = atan2(gy.toDouble(), -gx.toDouble())
        spin = Math.toDegrees(angleRad).toFloat()
        if (spin < 0) spin += 360f
    }

    private fun updateOrientation() {
        val normalizedAngle = normalizeAngle(spin, 180f)
        isHorizontal = normalizedAngle < ORIENTATION_THRESHOLD || normalizedAngle > (180f - ORIENTATION_THRESHOLD)
    }

    private fun updateDegreeDisplay() {
        val degreeValue = calculateDegreeValue()
        val displayDegree = if (isHighPrecisionEnabled) {
            var dv = degreeValue
            if (abs(dv) < 0.005f) dv = 0f
            String.format(Locale.getDefault(), "%.2f", dv)
        } else {
            degreeValue.roundToInt().toString()
        }

        degreeTextView.setTextColor(getDegreeTextColor())
        degreeTextView.text = " $displayDegree°"
        degreeTextView.rotation = spin - 90f
    }

    private fun calculateDegreeValue(): Float {
        val normalizedSpin = normalizeAngle(spin, 360f)
        val angleFromNearest90 = normalizedSpin % 90f
        return if (angleFromNearest90 <= ORIENTATION_THRESHOLD) angleFromNearest90 else angleFromNearest90 - 90f
    }

    private fun getRingColor() = if (isNearLevel()) greenColor else textColorPrimary
    private fun getReferenceLineColor() = if (isNearLevel()) greenColor else colorTertiary
    private fun getRotatingLineColor() = if (isNearLevel()) greenColor else textColorPrimary
    private fun getDegreeTextColor() = if (isNearLevel()) greenColor else textColorPrimary

    private fun isNearLevel(): Boolean {
        val mod = abs(spin % 90f)
        return mod < LEVEL_THRESHOLD || mod > (90f - LEVEL_THRESHOLD)
    }

    private fun normalizeAngle(angle: Float, range: Float) = ((angle % range) + range) % range
    private fun dp(v: Float) = v * resources.displayMetrics.density
}
