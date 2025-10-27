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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling VR click actions triggered by magnetometer gestures.
 * Performs clicks at the center of the screen when VR headset magnet is pulled down.
 */
@Singleton
class VrClickManager @Inject constructor(
    private val actionExecutor: AndroidActionExecutor
) {
    
    private var isEnabled = false
    private var clickScope = CoroutineScope(Dispatchers.Main)
    private var context: Context? = null
    
    companion object {
        private const val TAG = "VrClickManager"
        private const val CLICK_DURATION = 50L // Click duration in milliseconds
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.i(TAG, "VR click manager ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun setContext(context: Context) {
        this.context = context
    }
    
    fun isEnabled(): Boolean = isEnabled
    
    fun setClickAction(action: () -> Unit) {
        // Store the click action for later use
        // This is a placeholder - the actual click action is handled by performVrClick()
    }
    
    fun performVrClick() {
        if (!isEnabled) {
            Log.d(TAG, "VR click manager is disabled, ignoring click")
            return
        }
        
        Log.i(TAG, "Performing VR click")
        
        clickScope.launch {
            try {
                // Create a simple tap gesture at the center of the screen
                val gesture = createCenterClickGesture()
                if (gesture != null) {
                    actionExecutor.dispatchGesture(gesture)
                    Log.d(TAG, "VR click dispatched successfully")
                } else {
                    Log.e(TAG, "Failed to create gesture - screen dimensions not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform VR click", e)
            }
        }
    }
    
    private fun createCenterClickGesture(): GestureDescription? {
        val ctx = context ?: return null
        
        // Get actual screen dimensions
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        
        Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}, clicking at ($centerX, $centerY)")
        
        val path = Path().apply {
            moveTo(centerX, centerY)
        }
        
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION))
            .build()
    }
    
    fun performVrClickAt(x: Float, y: Float) {
        if (!isEnabled) {
            Log.d(TAG, "VR click manager is disabled, ignoring click")
            return
        }
        
        Log.i(TAG, "Performing VR click at ($x, $y)")
        
        clickScope.launch {
            try {
                val gesture = createClickGestureAt(x, y)
                if (gesture != null) {
                    actionExecutor.dispatchGesture(gesture)
                    Log.d(TAG, "VR click dispatched successfully at ($x, $y)")
                } else {
                    Log.e(TAG, "Failed to create gesture at ($x, $y) - screen dimensions not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform VR click at ($x, $y)", e)
            }
        }
    }
    
    private fun createClickGestureAt(x: Float, y: Float): GestureDescription? {
        val ctx = context ?: return null
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION))
            .build()
    }
}