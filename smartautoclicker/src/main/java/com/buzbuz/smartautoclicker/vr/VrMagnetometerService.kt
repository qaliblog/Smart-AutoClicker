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
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.scenarios.ScenarioActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
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
    private var gestureThresholdClick = 80.0f // Change needed for click
    private var gestureThresholdLongClick = 150.0f // Sustained change for long click
    private var longThresholdCrossStart: Long? = null
    private val longClickHoldMs = 600L
    private var gestureCooldown = 1000L // Minimum time between gestures in milliseconds
    
    // Enhanced gesture detection
    private var magneticFieldHistory = mutableListOf<FloatArray>()
    private var accelerationHistory = mutableListOf<FloatArray>()
    private val maxHistorySize = 10
    private var baselineMagneticField = FloatArray(3)
    private var isBaselineSet = false
    
    // SharedPreferences for threshold persistence
    private lateinit var sharedPreferences: SharedPreferences
    
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
        private const val PREF_NAME = "vr_settings"
        private const val PREF_THRESHOLD_CLICK = "magnetic_field_threshold_click"
        private const val PREF_THRESHOLD_LONG = "magnetic_field_threshold_long"
        private const val DEFAULT_THRESHOLD_CLICK = 80.0f
        private const val DEFAULT_THRESHOLD_LONG = 150.0f
    }
    
    inner class VrMagnetometerBinder : Binder() {
        fun getService(): VrMagnetometerService = this@VrMagnetometerService
    }
    
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadThresholdFromPreferences()
        createNotificationChannel()
        initializeSensors()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle thresholds update intent
        if (intent?.action == "UPDATE_VR_THRESHOLDS") {
            val click = intent.getFloatExtra("threshold_click", DEFAULT_THRESHOLD_CLICK)
            val long = intent.getFloatExtra("threshold_long", DEFAULT_THRESHOLD_LONG)
            setGestureThresholds(click, long)
            Log.i(TAG, "Thresholds updated to: click=$click, long=$long")
        }
        
        // Handle calibration reset intent
        if (intent?.action == "RESET_VR_CALIBRATION") {
            resetBaseline()
            Log.i(TAG, "Calibration reset requested")
        }
        // Calibrate baseline now (when magnet is far)
        if (intent?.action == "CALIBRATE_VR_BASELINE") {
            calibrateBaselineNow()
            Log.i(TAG, "Baseline calibrated from current field")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+ requires explicit foreground service type
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
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
            baselineMagneticField = values.clone()
            isInitialized = true
            return
        }
        
        // Add to history for pattern analysis
        addToHistory(magneticFieldHistory, values.clone())
        addToHistory(accelerationHistory, lastAcceleration.clone())
        
        // Set baseline after collecting some data
        if (!isBaselineSet && magneticFieldHistory.size >= 5) {
            baselineMagneticField = calculateBaseline(magneticFieldHistory)
            isBaselineSet = true
        }
        
        // Calculate the change in magnetic field
        val deltaX = abs(values[0] - lastMagneticField[0])
        val deltaY = abs(values[1] - lastMagneticField[1])
        val deltaZ = abs(values[2] - lastMagneticField[2])
        val totalDelta = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
        
        // Click threshold
        if (totalDelta > gestureThresholdClick && !isGestureInProgress) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGestureTime > gestureCooldown) {
                Log.d(TAG, "Significant magnetic field change detected - Total delta: $totalDelta, X: ${values[0]}, Y: ${values[1]}, Z: ${values[2]}")
                detectGesture(values)
                lastGestureTime = currentTime
            }
        }

        // Long click threshold tracking
        val aboveLong = totalDelta > gestureThresholdLongClick
        val now = System.currentTimeMillis()
        if (aboveLong) {
            if (longThresholdCrossStart == null) longThresholdCrossStart = now
            val heldFor = now - (longThresholdCrossStart ?: now)
            if (heldFor >= longClickHoldMs && !isGestureInProgress) {
                // Trigger long click
                try {
                    clickManager?.performVrLongClick()
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing VR long click", e)
                }
                isGestureInProgress = true
                serviceScope.launch {
                    delay(500)
                    isGestureInProgress = false
                }
                longThresholdCrossStart = null
            }
        } else {
            longThresholdCrossStart = null
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
        if (!isBaselineSet || magneticFieldHistory.size < 3) {
            return false
        }
        
        // Calculate changes from baseline
        val deltaFromBaselineX = abs(values[0] - baselineMagneticField[0])
        val deltaFromBaselineY = abs(values[1] - baselineMagneticField[1])
        val deltaFromBaselineZ = abs(values[2] - baselineMagneticField[2])
        val totalDeltaFromBaseline = sqrt(deltaFromBaselineX * deltaFromBaselineX + 
                                        deltaFromBaselineY * deltaFromBaselineY + 
                                        deltaFromBaselineZ * deltaFromBaselineZ)
        
        // Check if the change is significant enough
        if (totalDeltaFromBaseline < gestureThresholdClick) {
            return false
        }
        
        // Analyze the pattern of magnetic field changes over time
        val isPullDownPattern = analyzePullDownPattern()
        
        // Check for device movement that indicates intentional gesture
        val hasIntentionalMovement = hasIntentionalDeviceMovement()
        
        // Check if this looks like a VR headset pull-down vs phone back magnet
        val isVrHeadsetGesture = isVrHeadsetGesture(values)
        
        Log.d(TAG, "Gesture analysis: totalDelta=$totalDeltaFromBaseline, isPullDown=$isPullDownPattern, hasMovement=$hasIntentionalMovement, isVrGesture=$isVrHeadsetGesture")
        
        return isPullDownPattern && hasIntentionalMovement && isVrHeadsetGesture
    }
    
    private fun addToHistory(history: MutableList<FloatArray>, values: FloatArray) {
        history.add(values)
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }
    
    private fun calculateBaseline(history: List<FloatArray>): FloatArray {
        val baseline = FloatArray(3)
        for (values in history) {
            baseline[0] += values[0]
            baseline[1] += values[1]
            baseline[2] += values[2]
        }
        baseline[0] /= history.size
        baseline[1] /= history.size
        baseline[2] /= history.size
        return baseline
    }
    
    private fun analyzePullDownPattern(): Boolean {
        if (magneticFieldHistory.size < 3) return false
        
        // Look for a pattern where X-axis changes significantly
        // in a negative direction (more negative X values for pull-down on QMC6308)
        val recentValues = magneticFieldHistory.takeLast(3)
        val xChanges = mutableListOf<Float>()
        
        for (i in 1 until recentValues.size) {
            val xChange = recentValues[i][0] - recentValues[i-1][0]
            xChanges.add(xChange)
        }
        
        // Check if there's a consistent negative trend (X values becoming more negative)
        val negativeChanges = xChanges.count { it < 0 }
        val significantChanges = xChanges.count { abs(it) > gestureThresholdClick * 0.2f }
        
        Log.d(TAG, "Pull-down pattern analysis - X changes: $xChanges, negative: $negativeChanges, significant: $significantChanges")
        
        return negativeChanges >= 1 && significantChanges >= 1
    }
    
    private fun hasIntentionalDeviceMovement(): Boolean {
        if (accelerationHistory.size < 3) return false
        
        // Calculate acceleration magnitude changes
        val recentAccel = accelerationHistory.takeLast(3)
        val accelMagnitudes = recentAccel.map { values ->
            sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        }
        
        // Look for significant acceleration changes that indicate intentional movement
        val maxAccelChange = accelMagnitudes.zipWithNext { a, b -> abs(a - b) }.maxOrNull() ?: 0f
        return maxAccelChange > 1.0f // Threshold for intentional movement
    }
    
    private fun isVrHeadsetGesture(values: FloatArray): Boolean {
        // Optimized for QMC6308 magnetometer - X-axis shows the most significant change
        // Normal: ~-338, Pulled: ~-449 (difference ~111 units)
        
        val deltaX = abs(values[0] - lastMagneticField[0])
        val deltaY = abs(values[1] - lastMagneticField[1])
        val deltaZ = abs(values[2] - lastMagneticField[2])
        
        // For QMC6308, X-axis shows the most significant change during pull-down
        val xDominance = deltaX > (deltaY + deltaZ) * 0.8f
        
        // Check for significant X-axis change (should be around 100+ units for pull-down)
        val significantXChange = deltaX > gestureThresholdClick * 0.6f
        
        // Check if the change is in the expected direction (more negative X values)
        val isPullDownDirection = values[0] < lastMagneticField[0] && deltaX > 50.0f
        
        // Check for quick change and stabilization pattern
        val isQuickChange = magneticFieldHistory.size >= 2 && 
                           magneticFieldHistory.takeLast(2).let { recent ->
                               val prev = recent[0]
                               val curr = recent[1]
                               val xChange = abs(curr[0] - prev[0])
                               xChange > gestureThresholdClick * 0.3f
                           }
        
        // Additional validation: check if the change is significant enough from baseline
        val deltaFromBaselineX = abs(values[0] - baselineMagneticField[0])
        val isSignificantFromBaseline = deltaFromBaselineX > gestureThresholdClick * 0.7f
        
        Log.d(TAG, "QMC6308 Detection - X: ${values[0]}, deltaX: $deltaX, xDominance: $xDominance, significant: $significantXChange, pullDown: $isPullDownDirection")
        
        return xDominance && significantXChange && isPullDownDirection && isQuickChange && isSignificantFromBaseline
    }
    
    fun setClickAction(action: () -> Unit) {
        clickAction = action
    }
    
    fun setGestureThresholds(click: Float, long: Float) {
        gestureThresholdClick = click
        gestureThresholdLongClick = long
        sharedPreferences.edit()
            .putFloat(PREF_THRESHOLD_CLICK, click)
            .putFloat(PREF_THRESHOLD_LONG, long)
            .apply()
    }
    
    fun resetBaseline() {
        isBaselineSet = false
        magneticFieldHistory.clear()
        accelerationHistory.clear()
        Log.i(TAG, "Baseline reset - recalibrating magnetic field detection")
    }
    
    private fun loadThresholdFromPreferences() {
        gestureThresholdClick = sharedPreferences.getFloat(PREF_THRESHOLD_CLICK, DEFAULT_THRESHOLD_CLICK)
        gestureThresholdLongClick = sharedPreferences.getFloat(PREF_THRESHOLD_LONG, DEFAULT_THRESHOLD_LONG)
        Log.i(TAG, "Loaded thresholds from preferences: click=$gestureThresholdClick, long=$gestureThresholdLongClick")
    }

    private fun calibrateBaselineNow() {
        if (isInitialized) {
            baselineMagneticField = lastMagneticField.clone()
            isBaselineSet = true
        }
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