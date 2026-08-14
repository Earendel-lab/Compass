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

package com.earendel.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.earendel.compass.databinding.FragmentInclinometerBinding
import com.earendel.compass.preference.PreferenceStore
import com.earendel.compass.view.CompassViewModel
import com.earendel.compass.view.InclinometerView
import com.earendel.compass.view.LevelMeterView
import kotlin.math.abs
import kotlin.math.sqrt

class InclinometerFragment : Fragment(), SensorEventListener {

    private val compassViewModel: CompassViewModel by activityViewModels()
    private var _binding: FragmentInclinometerBinding? = null
    private val binding get() = _binding!!

    private var sensorManager: SensorManager? = null
    private var gravitySensor: Sensor? = null

    private var isInclinometerVisible = true

    private val TRANSITION_THRESHOLD = 0.5f
    private val ANIMATION_DURATION = 300L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInclinometerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        compassViewModel.hapticFeedback.observe(viewLifecycleOwner) { enabled ->
            binding.inclinometerView.setCustomHapticFeedbackEnabled(enabled)
            binding.levelMeterView.setCustomHapticFeedbackEnabled(enabled)
        }

        // Initially show inclinometer, hide level meter
        binding.inclinometerView.alpha = 1f
        binding.inclinometerView.setIsActive(true)
        binding.levelMeterView.alpha = 0f
        binding.levelMeterView.setIsActive(false)
    }

    override fun onResume() {
        super.onResume()
        gravitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_GRAVITY) return

        var gx = e.values[0]
        var gy = e.values[1]
        var gz = e.values[2]

        // Normalize gravity vector
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        gx /= norm
        gy /= norm
        gz /= norm

        // Determine which view should be visible based on device orientation
        val shouldShowInclinometer = abs(gz) > TRANSITION_THRESHOLD

        // Animate transition if orientation changed
        if (shouldShowInclinometer != isInclinometerVisible) {
            isInclinometerVisible = shouldShowInclinometer
            animateViewTransition(shouldShowInclinometer)
        }

        // Update both views
        val roll = -gx
        val pitch = gy

        binding.inclinometerView.updateTilt(pitch, roll)
        binding.levelMeterView.updateTilt(gx, gy)
    }

    private fun animateViewTransition(showInclinometer: Boolean) {
        if (showInclinometer) {
            binding.inclinometerView.animate().alpha(1f).setDuration(ANIMATION_DURATION).start()
            binding.inclinometerView.setIsActive(true)
            binding.levelMeterView.animate().alpha(0f).setDuration(ANIMATION_DURATION).start()
            binding.levelMeterView.setIsActive(false)
        } else {
            binding.inclinometerView.animate().alpha(0f).setDuration(ANIMATION_DURATION).start()
            binding.inclinometerView.setIsActive(false)
            binding.levelMeterView.animate().alpha(1f).setDuration(ANIMATION_DURATION).start()
            binding.levelMeterView.setIsActive(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
