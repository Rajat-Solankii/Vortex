package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager

object HighRefreshRateHelper {

    private const val TAG = "HighRefreshRateHelper"

    /**
     * Configures the Window to use the highest supported display refresh rate
     * (e.g. 120Hz / 90Hz / 144Hz) if the hardware and display panel support it.
     */
    fun enableHighRefreshRate(activity: Activity): Float {
        return try {
            val window = activity.window ?: return 60f
            val display = getDisplay(activity) ?: return 60f

            val modes = display.supportedModes ?: emptyArray()
            if (modes.isEmpty()) return 60f

            // Find mode with highest refresh rate (preferring 120Hz/144Hz/90Hz)
            val bestMode = modes
                .filter { it.refreshRate >= 89f }
                .maxByOrNull { it.refreshRate }
                ?: modes.maxByOrNull { it.refreshRate }

            val bestRate = bestMode?.refreshRate ?: 60f
            val layoutParams = window.attributes

            // 1. Preferred Display Mode ID (API 23+)
            if (bestMode != null) {
                layoutParams.preferredDisplayModeId = bestMode.modeId
            }

            // 2. Preferred Refresh Rate (API 23+)
            @Suppress("DEPRECATION")
            layoutParams.preferredRefreshRate = bestRate

            // 3. Preferred Min & Max Display Refresh Rate (Android 12+, API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val minField = WindowManager.LayoutParams::class.java.getField("preferredMinDisplayRefreshRate")
                    val maxField = WindowManager.LayoutParams::class.java.getField("preferredMaxDisplayRefreshRate")
                    minField.setFloat(layoutParams, bestRate)
                    maxField.setFloat(layoutParams, bestRate)
                } catch (_: Throwable) {
                    // Safe fallback
                }
            }

            window.attributes = layoutParams
            Log.d(TAG, "Successfully requested high refresh rate: ${bestRate}Hz (Mode ID: ${bestMode?.modeId})")
            bestRate
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set high refresh rate: ${e.message}")
            60f
        }
    }

    /**
     * Gets the active display refresh rate in Hz.
     */
    fun getCurrentRefreshRate(context: Context): Float {
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
            display?.refreshRate ?: 60f
        } catch (_: Exception) {
            60f
        }
    }

    @Suppress("DEPRECATION")
    private fun getDisplay(activity: Activity): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display ?: activity.windowManager.defaultDisplay
        } else {
            activity.windowManager.defaultDisplay
        }
    }
}
