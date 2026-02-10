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

package com.earendel.compass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.earendel.compass.view.CustomToggleSwitch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_modern, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Compass switches (Custom Toggles)
        val switchTrueNorth = view.findViewById<CustomToggleSwitch>(R.id.switch_true_north)
        val switchHaptic = view.findViewById<CustomToggleSwitch>(R.id.switch_haptic)
        switchTrueNorth.setChecked(prefs.getBoolean("true_north", false))
        switchHaptic.setChecked(prefs.getBoolean("haptic_feedback", true))
        switchTrueNorth.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean("true_north", checked).apply()
        }
        switchHaptic.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean("haptic_feedback", checked).apply()
        }

        // Rotation (Custom Toggle)
        val switchRotation = view.findViewById<CustomToggleSwitch>(R.id.switch_rotation)
        switchRotation.setChecked(prefs.getBoolean("is_rotation_enabled", true))
        switchRotation.setOnCheckedChangeListener { checked ->
            prefs.edit().putBoolean("is_rotation_enabled", checked).apply()
        }

        // Night mode radio group
        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_night_mode)
        when (prefs.getString("night_mode", "follow_system")) {
            "follow_system" -> radioGroup.check(R.id.radio_follow_system)
            "no" -> radioGroup.check(R.id.radio_light)
            "yes" -> radioGroup.check(R.id.radio_dark)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_follow_system -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    "follow_system"
                }
                R.id.radio_light -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "no"
                }
                R.id.radio_dark -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "yes"
                }
                else -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    "follow_system"
                }
            }
            prefs.edit().putString("night_mode", value).apply()
        }

        // About links
        val thirdParty = view.findViewById<TextView>(R.id.third_party_link)
        thirdParty.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_ThirdPartyLicensesFragment)
        }

        val source = view.findViewById<TextView>(R.id.source_code_link)
        source.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kr0oked/Compass"))
            startActivity(i)
        }

        val author = view.findViewById<TextView>(R.id.author_link)
        author.setOnClickListener {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:philipp.bobek@mailbox.org"))
            startActivity(i)
        }

        val license = view.findViewById<TextView>(R.id.license_link)
        license.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.txt"))
            startActivity(i)
        }

        val version = view.findViewById<TextView>(R.id.version_text)
        version.text = "${BuildConfig.VERSION_NAME}"
    }

}


