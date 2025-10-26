/*
 * Copyright (C) 2025 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.vr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.databinding.ActivityVrSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity for configuring VR magnetometer settings.
 */
@AndroidEntryPoint
class VrSettingsActivity : AppCompatActivity() {
    
    @Inject lateinit var vrClickManager: VrClickManager
    
    private lateinit var binding: ActivityVrSettingsBinding
    
    companion object {
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
        private const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVrSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        updateUI()
    }
    
    private fun setupUI() {
        binding.apply {
            btnEnableVr.setOnClickListener {
                enableVrFunctionality()
            }
            
            btnDisableVr.setOnClickListener {
                disableVrFunctionality()
            }
            
            btnOpenAccessibilitySettings.setOnClickListener {
                openAccessibilitySettings()
            }
            
            btnOpenBatterySettings.setOnClickListener {
                openBatteryOptimizationSettings()
            }
            
            btnTestClick.setOnClickListener {
                testVrClick()
            }
        }
    }
    
    private fun updateUI() {
        val isEnabled = vrClickManager.isEnabled()
        binding.apply {
            btnEnableVr.isEnabled = !isEnabled
            btnDisableVr.isEnabled = isEnabled
            btnTestClick.isEnabled = isEnabled
            tvStatus.text = if (isEnabled) "VR Clicker: Enabled" else "VR Clicker: Disabled"
        }
    }
    
    private fun enableVrFunctionality() {
        vrClickManager.setEnabled(true)
        updateUI()
        Toast.makeText(this, "VR functionality enabled", Toast.LENGTH_SHORT).show()
    }
    
    private fun disableVrFunctionality() {
        vrClickManager.setEnabled(false)
        updateUI()
        Toast.makeText(this, "VR functionality disabled", Toast.LENGTH_SHORT).show()
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
    }
    
    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION)
    }
    
    private fun testVrClick() {
        vrClickManager.performVrClick()
        Toast.makeText(this, "Test click performed", Toast.LENGTH_SHORT).show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_ACCESSIBILITY -> {
                Toast.makeText(this, "Please enable Smart Auto Clicker in accessibility settings", Toast.LENGTH_LONG).show()
            }
            REQUEST_CODE_BATTERY_OPTIMIZATION -> {
                Toast.makeText(this, "Please disable battery optimization for this app", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}