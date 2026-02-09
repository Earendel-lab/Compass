/*
 * This file is part of Compass.
 * Copyright (C) 2025 Philipp Bobek <philipp.bobek@mailbox.org>
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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.earendel.compass.databinding.FragmentCompassBinding
import com.earendel.compass.databinding.SensorAlertDialogViewBinding
import com.earendel.compass.model.AppError
import com.earendel.compass.model.Azimuth
import com.earendel.compass.model.DisplayRotation
import com.earendel.compass.model.LocationStatus
import com.earendel.compass.model.RotationVector
import com.earendel.compass.model.SensorAccuracy
import com.earendel.compass.preference.PreferenceStore
import com.earendel.compass.util.MathUtils
import com.earendel.compass.view.CompassViewModel
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executor

const val OPTION_INSTRUMENTED_TEST = "INSTRUMENTED_TEST"

private const val TAG = "CompassFragment"

class CompassFragment : Fragment() {

    private val compassViewModel: CompassViewModel by viewModels()
    private val compassMenuProvider = CompassMenuProvider()
    private val compassSensorEventListener = CompassSensorEventListener()

    private var binding: FragmentCompassBinding? = null
    private var preferenceStore: PreferenceStore? = null
    private var sensorManager: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var locationRequestCancellationSignal: CancellationSignal? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCompassBinding.inflate(inflater, container, false)
        return requireBinding().root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBinding()
        initPreferenceStore()
        setupSystemServices()
        adjustLayoutToSystemBars()
        setupMenu()
    }

    private fun initBinding() {
        val binding = requireBinding()
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = compassViewModel
        binding.locationReloadButton.setOnClickListener { requestLocation() }
    }

    private fun initPreferenceStore() {
        preferenceStore = PreferenceStore(requireContext(), lifecycle).also { preferenceStore ->
            preferenceStore.trueNorth.observe(viewLifecycleOwner) { compassViewModel.trueNorth.value = it }
            preferenceStore.hapticFeedback.observe(viewLifecycleOwner) {
                compassViewModel.hapticFeedback.value = it
            }
            preferenceStore.isRotationEnabled.observe(viewLifecycleOwner) { isEnabled ->
                requireActivity().requestedOrientation = if (isEnabled) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                compassViewModel.isRotationEnabled.value = isEnabled
            }
        }
    }

    private fun setupSystemServices() {
        sensorManager = ActivityCompat.getSystemService(requireContext(), SensorManager::class.java)
        locationManager = ActivityCompat.getSystemService(requireContext(), LocationManager::class.java)
    }

    private fun adjustLayoutToSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(requireBinding().root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
                bottomMargin = insets.bottom
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(compassMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()

        if (isInstrumentedTest()) {
            Log.i(TAG, "Skipping start of system service functionalities")
        } else {
            startSystemServiceFunctionalities()
        }

        Log.i(TAG, "Started compass")
    }

    private fun isInstrumentedTest() = requireActivity().intent.extras?.getBoolean(OPTION_INSTRUMENTED_TEST) == true

    private fun startSystemServiceFunctionalities() {
        registerSensorListener()

        if (compassViewModel.trueNorth.value == true && compassViewModel.location.value == null) {
            requestLocation()
        }
    }

    private fun registerSensorListener() {
        sensorManager
            ?.also(::registerSensorListener)
            ?: run {
                Log.w(TAG, "SensorManager not present")
                showErrorDialog(AppError.SENSOR_MANAGER_NOT_PRESENT)
            }
    }

    private fun registerSensorListener(sensorManager: SensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?.also { rotationVectorSensor -> registerRotationVectorSensorListener(sensorManager, rotationVectorSensor) }
            ?: run {
                Log.w(TAG, "Rotation vector sensor not available")
                showErrorDialog(AppError.ROTATION_VECTOR_SENSOR_NOT_AVAILABLE)
            }

        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            ?.also { magneticFieldSensor -> registerMagneticFieldSensorListener(sensorManager, magneticFieldSensor) }
            ?: run {
                Log.w(TAG, "Magnetic field sensor not available")
                showErrorDialog(AppError.MAGNETIC_FIELD_SENSOR_NOT_AVAILABLE)
            }
    }

    private fun registerRotationVectorSensorListener(sensorManager: SensorManager, rotationVectorSensor: Sensor) {
        val success = sensorManager.registerListener(
            compassSensorEventListener,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
        if (success) {
            Log.d(TAG, "Registered listener for rotation vector sensor")
        } else {
            Log.w(TAG, "Could not enable rotation vector sensor")
            showErrorDialog(AppError.ROTATION_VECTOR_SENSOR_FAILED)
        }
    }

    private fun registerMagneticFieldSensorListener(sensorManager: SensorManager, magneticFieldSensor: Sensor) {
        val success = sensorManager.registerListener(
            compassSensorEventListener,
            magneticFieldSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        if (success) {
            Log.d(TAG, "Registered listener for magnetic field sensor")
        } else {
            Log.w(TAG, "Could not enable magnetic field sensor")
            showErrorDialog(AppError.MAGNETIC_FIELD_SENSOR_FAILED)
        }
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        ) {
            registerLocationListener()
        } else {
            compassViewModel.locationStatus.value = LocationStatus.PERMISSION_DENIED
        }
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun registerLocationListener() {
        locationManager
            ?.also(::registerLocationListener)
            ?: run {
                Log.w(TAG, "LocationManager not present")
                setLocation(null)
                showErrorDialog(AppError.LOCATION_MANAGER_NOT_PRESENT)
            }
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun registerLocationListener(locationManager: LocationManager) {
        if (LocationManagerCompat.isLocationEnabled(locationManager)) {
            requestLocation(locationManager)
        } else {
            Log.w(TAG, "Location is disabled")
            setLocation(null)
            showErrorDialog(AppError.LOCATION_DISABLED)
        }
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun requestLocation(locationManager: LocationManager) {
        getBestLocationProvider(locationManager)
            ?.also { provider -> requestLocation(locationManager, provider) }
            ?: run {
                Log.w(TAG, "No LocationProvider available")
                setLocation(null)
                showErrorDialog(AppError.NO_LOCATION_PROVIDER_AVAILABLE)
            }
    }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    private fun requestLocation(locationManager: LocationManager, provider: String) {
        Log.i(TAG, "Requesting location from provider '$provider'")

        compassViewModel.locationStatus.value = LocationStatus.LOADING

        locationRequestCancellationSignal?.cancel()
        locationRequestCancellationSignal = CancellationSignal()

        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            locationRequestCancellationSignal,
            getExecutor(),
            ::setLocation
        )
    }

    private fun getExecutor(): Executor = ContextCompat.getMainExecutor(requireContext())

    private fun getBestLocationProvider(locationManager: LocationManager): String? {
        val availableProviders = locationManager.getProviders(true)

        for (preferredProvider in getPreferredProviders()) {
            if (availableProviders.contains(preferredProvider)) {
                return preferredProvider
            }
        }

        return null
    }

    private fun getPreferredProviders(): List<String> {
        val preferredProviders = mutableListOf<String>()

        if (VERSION.SDK_INT >= VERSION_CODES.S) {
            preferredProviders.add(LocationManager.FUSED_PROVIDER)
        }

        if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            preferredProviders.add(LocationManager.GPS_PROVIDER)
        }

        if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED) {
            preferredProviders.add(LocationManager.NETWORK_PROVIDER)
        }

        return preferredProviders
    }

    private fun showErrorDialog(appError: AppError) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error)
            .setIcon(R.drawable.ic_error)
            .setMessage(getString(R.string.error_message, getString(appError.messageId), appError.name))
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(compassSensorEventListener)
        locationRequestCancellationSignal?.cancel()
        Log.i(TAG, "Stopped compass")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        preferenceStore = null
        sensorManager = null
        locationManager = null
    }


    private inner class CompassMenuProvider : MenuProvider {

        private var optionsMenu: Menu? = null

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_compass, menu)
            optionsMenu = menu
            
            val sensorItem = menu.findItem(R.id.action_sensor_status)
            sensorItem.actionView?.setOnClickListener { 
                onMenuItemSelected(sensorItem)
            }
            
            compassViewModel.sensorAccuracy.observe(viewLifecycleOwner) { updateSensorStatusIcon(it) }
        }

        private fun updateSensorStatusIcon(sensorAccuracy: SensorAccuracy) {
            val menuItem = optionsMenu?.findItem(R.id.action_sensor_status) ?: return
            
            // Update legacy icon just in case
            menuItem.setIcon(sensorAccuracy.iconResourceId)

            // Update new ActionView (Vertical Pills)
            val actionView = menuItem.actionView
            if (actionView != null) {
                val pill1 = actionView.findViewById<View>(R.id.sensor_pill_small)
                val pill2 = actionView.findViewById<View>(R.id.sensor_pill_medium)
                val pill3 = actionView.findViewById<View>(R.id.sensor_pill_large)
                
                if (pill1 != null && pill2 != null && pill3 != null) {
                    val typedValue = android.util.TypedValue()
                    requireContext().theme.resolveAttribute(android.R.attr.isLightTheme, typedValue, true)
                    val isLightTheme = typedValue.data != 0
                    
                    val activeColor = ContextCompat.getColor(requireContext(), if (isLightTheme) R.color.pure_black else R.color.pure_white)
                    val inactiveColor = ContextCompat.getColor(requireContext(), R.color.gray_medium)
                    val errorColor = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    
                    when (sensorAccuracy) {
                        SensorAccuracy.HIGH -> {
                            pill1.background?.setTint(activeColor); pill1.alpha = 1.0f
                            pill2.background?.setTint(activeColor); pill2.alpha = 1.0f
                            pill3.background?.setTint(activeColor); pill3.alpha = 1.0f
                        }
                        SensorAccuracy.MEDIUM -> {
                            pill1.background?.setTint(activeColor); pill1.alpha = 1.0f
                            pill2.background?.setTint(activeColor); pill2.alpha = 1.0f
                            pill3.background?.setTint(inactiveColor); pill3.alpha = 0.2f
                        }
                        SensorAccuracy.LOW -> {
                            pill1.background?.setTint(activeColor); pill1.alpha = 1.0f
                            pill2.background?.setTint(inactiveColor); pill2.alpha = 0.2f
                            pill3.background?.setTint(inactiveColor); pill3.alpha = 0.2f
                        }
                        SensorAccuracy.UNRELIABLE, SensorAccuracy.NO_CONTACT -> {
                            pill1.background?.setTint(errorColor); pill1.alpha = 1.0f
                            pill2.background?.setTint(inactiveColor); pill2.alpha = 0.2f
                            pill3.background?.setTint(inactiveColor); pill3.alpha = 0.2f
                        }
                    }
                }
            }

            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                sensorAccuracy.iconTintAttributeResourceId
                    .let { MaterialColors.getColor(requireContext(), it, this::class.simpleName) }
                    .let { ColorStateList.valueOf(it) }
                    .also { menuItem.iconTintList = it }
            }
        }


        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_sensor_status -> {
                    showSensorStatusPopup()
                    true
                }


                R.id.action_settings -> {
                    showSettings()
                    true
                }

                else -> false
            }
        }

        private fun showSensorStatusPopup() {
            val alertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            val dialogContextInflater = LayoutInflater.from(alertDialogBuilder.context)

            val dialogBinding = SensorAlertDialogViewBinding.inflate(dialogContextInflater, null, false)
            dialogBinding.model = compassViewModel
            dialogBinding.lifecycleOwner = viewLifecycleOwner

            alertDialogBuilder
                .setTitle(R.string.sensor_status)
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }


        private fun showSettings() {
            findNavController().navigate(R.id.action_CompassFragment_to_SettingsFragment)
        }
    }


    private inner class CompassSensorEventListener : SensorEventListener {

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            when (sensor.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> setSensorAccuracy(accuracy)
                Sensor.TYPE_ROTATION_VECTOR -> Log.v(TAG, "Received rotation vector sensor accuracy $accuracy")
                else -> Log.w(TAG, "Unexpected accuracy changed event of type ${sensor.type}")
            }
        }

        private fun setSensorAccuracy(accuracy: Int) {
            val sensorAccuracy = adaptSensorAccuracy(accuracy)
            setSensorAccuracy(sensorAccuracy)
        }

        private fun adaptSensorAccuracy(accuracy: Int): SensorAccuracy {
            return when (accuracy) {
                SensorManager.SENSOR_STATUS_NO_CONTACT -> SensorAccuracy.NO_CONTACT
                SensorManager.SENSOR_STATUS_UNRELIABLE -> SensorAccuracy.UNRELIABLE
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.LOW
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.MEDIUM
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.HIGH
                else -> {
                    Log.w(TAG, "Encountered unexpected sensor accuracy value '$accuracy'")
                    SensorAccuracy.NO_CONTACT
                }
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> updateCompass(event)
                Sensor.TYPE_MAGNETIC_FIELD -> Log.v(TAG, "Received magnetic field sensor event ${event.values}")
                else -> Log.w(TAG, "Unexpected sensor changed event of type ${event.sensor.type}")
            }
        }

        private fun updateCompass(event: SensorEvent) {
            val rotationVector = RotationVector(event.values[0], event.values[1], event.values[2])
            val displayRotation = getDisplayRotation()
            val magneticAzimuth = MathUtils.calculateAzimuth(rotationVector, displayRotation)

            if (compassViewModel.trueNorth.value == true) {
                val magneticDeclination = getMagneticDeclination()
                val trueAzimuth = magneticAzimuth.plus(magneticDeclination)
                setAzimuth(trueAzimuth)
            } else {
                setAzimuth(magneticAzimuth)
            }
        }

        private fun getDisplayRotation(): DisplayRotation {
            return when (getDisplayCompat()?.rotation) {
                Surface.ROTATION_90 -> DisplayRotation.ROTATION_90
                Surface.ROTATION_180 -> DisplayRotation.ROTATION_180
                Surface.ROTATION_270 -> DisplayRotation.ROTATION_270
                else -> DisplayRotation.ROTATION_0
            }
        }

        private fun getDisplayCompat(): Display? {
            return if (VERSION.SDK_INT >= VERSION_CODES.R) {
                requireContext().display
            } else {
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay
            }
        }

        private fun getMagneticDeclination(): Float {
            return compassViewModel.location.value
                ?.let(MathUtils::getMagneticDeclination)
                ?: 0.0f
        }
    }


    private fun setLocation(location: Location?) {
        Log.i(TAG, "Location $location")
        compassViewModel.location.value = location

        compassViewModel.locationStatus.value = when (location) {
            null -> LocationStatus.NOT_PRESENT
            else -> LocationStatus.PRESENT
        }
    }

    private fun requireBinding(): FragmentCompassBinding = binding!!

    private fun requirePreferenceStore(): PreferenceStore = preferenceStore!!

    internal fun setSensorAccuracy(sensorAccuracy: SensorAccuracy) {
        Log.i(TAG, "Sensor accuracy $sensorAccuracy")
        compassViewModel.sensorAccuracy.value = sensorAccuracy
    }

    internal fun setAzimuth(azimuth: Azimuth) {
        Log.v(TAG, "Azimuth $azimuth")
        compassViewModel.azimuth.value = azimuth
    }
}
