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
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.earendel.compass.R
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

class InclinometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val greenColor = ContextCompat.getColor(context, R.color.green_level)
    private val textColorPrimary = MaterialColors.getColor(this, android.R.attr.textColorPrimary)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(6.0f) // Increased thickness as requested
    }

    private var cx = 0f
    private var cy = 0f
    private var bubbleX = 0f
    private var bubbleY = 0f

    private var ringRadius = 0f
    private var bubbleRadius = 0f
    private var inCenter = false

    private val SMOOTH = 1.0f
    private val CENTER_THRESHOLD = 0.01f
    private val VIBRATION_DURATION_MS = 15L
    private var wasInCenter = false
    private var isActive = true
    private var isHapticFeedbackEnabled = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w * 0.5f
        cy = h * 0.5f

        ringRadius = min(w, h) * 0.45f
        bubbleRadius = ringRadius * 0.12f

        bubbleX = cx
        bubbleY = cy
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = if (inCenter) greenColor else textColorPrimary

        // paint the outer ring
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, ringRadius, paint)

        // paint lines
        val half = ringRadius * 0.5f
        canvas.drawLine(cx, cy - half, cx, cy + half, paint)
        canvas.drawLine(cx - half, cy, cx + half, cy, paint)

        // paint the moving circle
        paint.style = Paint.Style.FILL
        canvas.drawCircle(bubbleX, bubbleY, bubbleRadius, paint)
    }

    fun updateTilt(pitch: Float, roll: Float) {
        var r = roll
        var p = pitch

        // Clamp so bubble never leaves circle
        val len = sqrt(r * r + p * p)
        if (len > 1f) {
            r /= len
            p /= len
        }

        val targetX = cx + r * ringRadius
        val targetY = cy + p * ringRadius

        // Proper smoothing toward target
        bubbleX += (targetX - bubbleX) * SMOOTH
        bubbleY += (targetY - bubbleY) * SMOOTH

        val distFromCenter = hypot(bubbleX - cx, bubbleY - cy)

        inCenter = distFromCenter < ringRadius * CENTER_THRESHOLD

        if (isActive && inCenter && !wasInCenter && isHapticFeedbackEnabled) {
            performHapticFeedback()
        }
        wasInCenter = inCenter

        invalidate()
    }

    fun setIsActive(active: Boolean) {
        this.isActive = active
    }

    fun setCustomHapticFeedbackEnabled(enabled: Boolean) {
        this.isHapticFeedbackEnabled = enabled
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

    private fun dp(v: Float): Float {
        return v * resources.displayMetrics.density
    }
}
