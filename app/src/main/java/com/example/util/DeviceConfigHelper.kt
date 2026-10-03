package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceConfigHelper {
    var isLowEndDevice: Boolean = false
        private set

    fun initialize(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isLowEndDevice = activityManager.isLowRamDevice || 
                (activityManager.memoryClass < 128) || 
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
    }

    fun getPosterQuality(): String {
        return if (isLowEndDevice) "w342" else "w500"
    }

    fun getBackdropQuality(): String {
        return if (isLowEndDevice) "w500" else "w780"
    }
}
