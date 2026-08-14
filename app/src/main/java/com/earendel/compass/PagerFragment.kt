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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.earendel.compass.databinding.FragmentPagerBinding
import com.earendel.compass.databinding.SensorAlertDialogViewBinding
import com.earendel.compass.model.SensorAccuracy
import com.earendel.compass.view.CompassViewModel
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PagerFragment : Fragment() {

    private val compassViewModel: CompassViewModel by activityViewModels()
    private val compassMenuProvider = CompassMenuProvider()

    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CompassPagerAdapter(this)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)
            }
        })

        setupMenu()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(compassMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun updateDots(position: Int) {
        binding.dot1.setImageResource(if (position == 0) R.drawable.active_dot else R.drawable.inactive_dot)
        binding.dot2.setImageResource(if (position == 1) R.drawable.active_dot else R.drawable.inactive_dot)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class CompassPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CompassFragment()
                else -> InclinometerFragment()
            }
        }
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

            menuItem.setIcon(sensorAccuracy.iconResourceId)

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

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                sensorAccuracy.iconTintAttributeResourceId
                    .let { MaterialColors.getColor(requireContext(), it, this::class.simpleName) }
                    .let { android.content.res.ColorStateList.valueOf(it) }
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
            findNavController().navigate(R.id.action_PagerFragment_to_SettingsFragment)
        }
    }
}
