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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.scenarios.ScenarioActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Service that monitors magnetometer readings to detect VR headset magnet pull-down gestures.
 * When a pull-down gesture is detected, it triggers a click action.
 */
class VrMagnetometerService : Service(), SensorEventListener {
    
    private val binder = VrMagnetometerBinder()
    private var sensorManager: SensorManager? = null
    private var magnetometer: Sensor? = null
    private var accelerometer: Sensor? = null
    
    // Magnetometer data
    private var lastMagneticField = FloatArray(3)
    private var lastAcceleration = FloatArray(3)
    private var isInitialized = false
    
    // Gesture detection
    private var lastGestureTime = 0L
    private var isGestureInProgress = false
    private var gestureThreshold = 15.0f // Minimum change in magnetic field to trigger gesture
    private var gestureCooldown = 1000L // Minimum time between gestures in milliseconds
    
    // Click action
    private var clickAction: (() -> Unit)? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main)
    private var clickJob: Job? = null
    
    // Static reference to click manager
    companion object {
        var clickManager: VrClickManager? = null
        private const val TAG = "VrMagnetometerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vr_magnetometer_channel"
        private const val CHANNEL_NAME = "VR Magnetometer Service"
    }
    
    inner class VrMagnetometerBinder : Binder() {
        fun getService(): VrMagnetometerService = this@VrMagnetometerService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeSensors()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors VR headset magnet for click gestures"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, ScenarioActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VR Magnet Clicker")
            .setContentText("Monitoring VR headset magnet for click gestures")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (magnetometer == null) {
            Log.e(TAG, "Magnetometer not available on this device")
            stopSelf()
            return
        }
        
        // Register listeners with appropriate sampling rate
        magnetometer?.let { sensor ->
            try {
                // Try fastest sampling rate first
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
                Log.i(TAG, "Using fastest sensor sampling rate")
            } catch (e: SecurityException) {
                // Fallback to game sampling rate if permission not available
                Log.w(TAG, "Fastest sampling rate not available, using game rate", e)
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        
        accelerometer?.let { sensor ->
            try {
                // Try fastest sampling rate first
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            } catch (e: SecurityException) {
                // Fallback to game sampling rate if permission not available
                Log.w(TAG, "Fastest sampling rate not available for accelerometer, using game rate", e)
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        
        Log.i(TAG, "Sensors initialized successfully")
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    handleMagnetometerData(sensorEvent.values)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    handleAccelerometerData(sensorEvent.values)
                }
            }
        }
    }
    
    private fun handleMagnetometerData(values: FloatArray) {
        if (!isInitialized) {
            lastMagneticField = values.clone()
            isInitialized = true
            return
        }
        
        // Calculate the change in magnetic field
        val deltaX = abs(values[0] - lastMagneticField[0])
        val deltaY = abs(values[1] - lastMagneticField[1])
        val deltaZ = abs(values[2] - lastMagneticField[2])
        val totalDelta = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        
        // Check if this is a significant change (magnet pull-down)
        if (totalDelta > gestureThreshold && !isGestureInProgress) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGestureTime > gestureCooldown) {
                detectGesture(values)
                lastGestureTime = currentTime
            }
        }
        
        lastMagneticField = values.clone()
    }
    
    private fun handleAccelerometerData(values: FloatArray) {
        lastAcceleration = values.clone()
    }
    
    private fun detectGesture(magneticValues: FloatArray) {
        // Check if this is a pull-down gesture based on magnetic field change
        val isPullDown = isMagnetPullDownGesture(magneticValues)
        
        if (isPullDown) {
            Log.i(TAG, "VR magnet pull-down gesture detected")
            isGestureInProgress = true
            
            // Trigger click action
            try {
                clickManager?.performVrClick()
            } catch (e: Exception) {
                Log.e(TAG, "Error performing VR click", e)
            }
            
            // Reset gesture state after a short delay
            serviceScope.launch {
                delay(500)
                isGestureInProgress = false
            }
        }
    }
    
    private fun isMagnetPullDownGesture(values: FloatArray): Boolean {
        // Simple heuristic: check if Z-axis (vertical) magnetic field changed significantly
        // This is a basic implementation - you might need to adjust based on your VR headset
        val zChange = abs(values[2] - lastMagneticField[2])
        return zChange > gestureThreshold * 0.7f // Z-axis is more sensitive for pull-down
    }
    
    fun setClickAction(action: () -> Unit) {
        clickAction = action
    }
    
    fun setGestureThreshold(threshold: Float) {
        gestureThreshold = threshold
    }
    
    fun setGestureCooldown(cooldown: Long) {
        gestureCooldown = cooldown
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        clickJob?.cancel()
        Log.i(TAG, "VrMagnetometerService destroyed")
    }
}