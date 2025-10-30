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

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.SmartAutoClickerService
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
    private lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
        private const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1002
        private const val REQUEST_CODE_HIGH_SAMPLING_RATE = 1003
        private const val PREF_NAME = "vr_settings"
        private const val PREF_THRESHOLD = "magnetic_field_threshold"
        // Keep in sync with XML slider default value
        private const val DEFAULT_THRESHOLD = 80.0f
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVrSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
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
            
            btnResetCalibration.setOnClickListener {
                resetCalibration()
            }
            
            // Setup threshold slider
            sliderThreshold.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    updateThresholdValue(value)
                    saveThresholdValue(value)
                    updateVrServiceThreshold(value)
                }
            }
        }
    }
    
    private fun updateUI() {
        val isEnabled = vrClickManager.isEnabled()
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isVrServiceRunning = isVrServiceRunning()
        val hasMagnetometer = hasMagnetometer()
        
        binding.apply {
            btnEnableVr.isEnabled = !isEnabled && isAccessibilityEnabled && hasMagnetometer
            btnDisableVr.isEnabled = isEnabled
            btnTestClick.isEnabled = isEnabled && isAccessibilityEnabled
            
            val statusText = when {
                !hasMagnetometer -> "VR Clicker: Device has no magnetometer sensor"
                !isAccessibilityEnabled -> "VR Clicker: Accessibility service not enabled"
                isEnabled && isVrServiceRunning -> "VR Clicker: Enabled and Running"
                isEnabled -> "VR Clicker: Enabled but service not running"
                else -> "VR Clicker: Disabled"
            }
            tvStatus.text = statusText
            
            // Load, clamp to slider range, persist if corrected, and set value
            val storedThreshold = getThresholdValue()
            val clampedThreshold = clampToSliderRange(storedThreshold)
            if (clampedThreshold != storedThreshold) saveThresholdValue(clampedThreshold)
            sliderThreshold.value = clampedThreshold
            updateThresholdValue(clampedThreshold)
        }
    }
    
    private fun enableVrFunctionality() {
        // Check if device has magnetometer
        if (!hasMagnetometer()) {
            Toast.makeText(this, "This device does not have a magnetometer sensor required for VR functionality", Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable accessibility service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        
        // Check for high sampling rate permission on Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.HIGH_SAMPLING_RATE_SENSORS),
                    REQUEST_CODE_HIGH_SAMPLING_RATE
                )
                return
            }
        }
        
        // Start VR service through the accessibility service
        val intent = Intent(this, SmartAutoClickerService::class.java)
        intent.action = "START_VR_SERVICE"
        startService(intent)
        
        vrClickManager.setEnabled(true)
        updateUI()
        Toast.makeText(this, "VR functionality enabled", Toast.LENGTH_SHORT).show()
    }
    
    private fun disableVrFunctionality() {
        // Stop VR service through the accessibility service
        val intent = Intent(this, SmartAutoClickerService::class.java)
        intent.action = "STOP_VR_SERVICE"
        startService(intent)
        
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
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_CODE_HIGH_SAMPLING_RATE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, try to enable VR functionality again
                    enableVrFunctionality()
                } else {
                    Toast.makeText(this, "High sampling rate permission denied. VR functionality will work with reduced sensitivity.", Toast.LENGTH_LONG).show()
                    // Still try to enable VR functionality with fallback sampling rate
                    enableVrFunctionality()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val serviceName = ComponentName(
                    packageName,
                    SmartAutoClickerService::class.java.name
                ).flattenToString()
                return settingValue.contains(serviceName)
            }
        }
        return false
    }
    
    private fun isVrServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (serviceInfo in runningServices) {
            if (serviceInfo.service.className == VrMagnetometerService::class.java.name) {
                return true
            }
        }
        return false
    }
    
    private fun hasMagnetometer(): Boolean {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        return magnetometer != null
    }
    
    private fun getThresholdValue(): Float {
        return sharedPreferences.getFloat(PREF_THRESHOLD, DEFAULT_THRESHOLD)
    }
    
    private fun saveThresholdValue(value: Float) {
        val clamped = clampToSliderRange(value)
        sharedPreferences.edit()
            .putFloat(PREF_THRESHOLD, clamped)
            .apply()
    }
    
    private fun updateThresholdValue(value: Float) {
        binding.tvThresholdValue.text = "Current Threshold: ${String.format("%.1f", value)}"
    }
    
    private fun updateVrServiceThreshold(value: Float) {
        // Send threshold update to VR service through accessibility service
        val intent = Intent(this, SmartAutoClickerService::class.java)
        intent.action = "UPDATE_VR_THRESHOLD"
        intent.putExtra("threshold", clampToSliderRange(value))
        startService(intent)
    }
    
    private fun resetCalibration() {
        // Reset calibration in VR service
        val intent = Intent(this, SmartAutoClickerService::class.java)
        intent.action = "RESET_VR_CALIBRATION"
        startService(intent)
        
        Toast.makeText(this, "Calibration reset. The app will recalibrate magnetic field detection.", Toast.LENGTH_LONG).show()
    }

    private fun clampToSliderRange(value: Float): Float {
        // binding is initialized when this is called
        val min = binding.sliderThreshold.valueFrom
        val max = binding.sliderThreshold.valueTo
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
}