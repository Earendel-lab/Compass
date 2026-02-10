/*
 * This file is part of Compass.
 * Copyright (C) 2024 Philipp Bobek <philipp.bobek@mailbox.org>
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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.earendel.compass.R

class CustomToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isChecked = false
    private var thumbPosition = 0f // 0f = off, 1f = on
    private var thumbSizeProgress = 0f // 0f = small, 1f = large
    private var thumbAnimator: ValueAnimator? = null
    private var thumbSizeAnimator: ValueAnimator? = null
    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    // Paint for background track
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Paint for thumb
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Paint for shadow
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // M3 Colors - On state
    private val colorOnTrack = Color.parseColor("#000000") // AMOLED Black
    private val colorOnThumb = Color.WHITE

    // M3 Colors - Off state
    private val colorOffTrack = Color.parseColor("#808080") // Slightly darker grey when OFF
    private val colorOffThumb = Color.parseColor("#F5F5F5") // Very Light Grey/White

    // Dimensions (M3 standard: 52dp x 32dp)
    private val trackHeightDp = 32f
    private val trackWidthDp = 52f
    private val thumbRadiusMinDp = 12f  // Keep previous small thumb when off
    private val thumbRadiusMaxDp = 14f  // Keep previous larger thumb when on
    private val offShiftDp = 3f // shift the off thumb slightly toward right

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        // Interpolate track color based on position
        val trackColor = if (isChecked) colorOnTrack else colorOffTrack
        trackPaint.color = trackColor

        // Draw track with full rounded corners
        val trackRadius = height / 2
        canvas.drawRoundRect(
            RectF(0f, 0f, width, height),
            trackRadius,
            trackRadius,
            trackPaint
        )

        // Calculate thumb position
        // Calculate thumb radius based on progress
        val thumbRadiusMin = thumbRadiusMinDp.dpToPx()
        val thumbRadiusMax = thumbRadiusMaxDp.dpToPx()
        val thumbRadius = thumbRadiusMin + (thumbRadiusMax - thumbRadiusMin) * thumbSizeProgress

        // Calculate thumb position - ensure it stays within bounds
        val trackPadding = 3f // Small padding from edge
        val minX = trackPadding + thumbRadius
        val maxX = width - trackPadding - thumbRadius
        val extraShift = offShiftDp.dpToPx()
        // When off (thumbPosition=0) place thumb slightly right by extraShift, when on (1) at maxX
        val thumbX = (minX + extraShift) * (1f - thumbPosition) + maxX * thumbPosition

        // Draw shadow/elevation for thumb
        shadowPaint.color = Color.argb(20, 0, 0, 0)
        shadowPaint.setShadowLayer(6f, 0f, 2f, Color.argb(30, 0, 0, 0))
        canvas.drawCircle(thumbX, centerY, thumbRadius, shadowPaint)

        // Draw thumb with M3 style
        val thumbColor = if (isChecked) colorOnThumb else colorOffThumb
        thumbPaint.color = thumbColor
        thumbPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = trackWidthDp.dpToPx().toInt()
        val desiredHeight = trackHeightDp.dpToPx().toInt()

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(MeasureSpec.getSize(widthMeasureSpec), desiredWidth)
            else -> desiredWidth
        }

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(MeasureSpec.getSize(heightMeasureSpec), desiredHeight)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> return true
            MotionEvent.ACTION_UP -> {
                toggle()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun toggle() {
        isChecked = !isChecked
        animateThumb()
        onCheckedChangeListener?.invoke(isChecked)
    }

    fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            animateThumb()
        }
    }

    fun isChecked(): Boolean = isChecked

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }

    private fun animateThumb() {
        thumbAnimator?.cancel()
        thumbSizeAnimator?.cancel()

        // Animate thumb position
        thumbAnimator = ValueAnimator.ofFloat(
            thumbPosition,
            if (isChecked) 1f else 0f
        ).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                thumbPosition = it.animatedValue as Float
                invalidate()
            }
            start()

                // Animate thumb size
                thumbSizeAnimator = ValueAnimator.ofFloat(
                    thumbSizeProgress,
                    if (isChecked) 1f else 0f
                ).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        thumbSizeProgress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}

