package com.example.util

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

data class ThermalSnapshot(
    val thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE,
    val isPowerSaveMode: Boolean = false,
    val thermalHeadroom: Float = 0f
)

data class DisplayModeSpec(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

class AdaptiveRefreshRatePolicy(
    private val recoveryHeadroomThreshold: Float = 0.70f,
    private val downshiftHeadroomThreshold: Float = 0.85f,
    private val minModeDwellMillis: Long = 8_000L
) {
    private var currentTier: Int = 3
    private var lastTierChangeMillis: Long? = null

    fun resolveTargetRate(
        snapshot: ThermalSnapshot,
        supportedRates: List<Float>?,
        currentDisplayRate: Float,
        highRefreshRateEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Float {
        if (!highRefreshRateEnabled) {
            currentTier = 0
            lastTierChangeMillis = null
            return 0f
        }

        val headroom = snapshot.thermalHeadroom.takeIf { it.isFinite() } ?: 0f

        if (snapshot.isPowerSaveMode) {
            moveToTier(1, nowMillis)
            return 60f
        }

        val allRates = supportedRates?.filter { it > 0f && it.isFinite() }?.distinct()?.sortedDescending()
            ?: listOf(currentDisplayRate)

        val peakRate = allRates.filter { it <= 120.5f }.maxOrNull()?.coerceAtMost(120f)
            ?: (allRates.firstOrNull { it > 60f }?.coerceAtMost(120f) ?: 60f)

        if (peakRate <= 60.5f) {
            moveToTier(1, nowMillis)
            return 60f
        }

        val rate90Hz = allRates.firstOrNull { it in 85f..95f }

        fun rateForTier(tier: Int): Float = when (tier) {
            2 -> rate90Hz ?: 60f
            1 -> 60f
            else -> peakRate
        }

        val isSevereOrHigher = when (snapshot.thermalStatus) {
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> true
            else -> headroom >= 1.0f
        }

        if (isSevereOrHigher) {
            moveToTier(1, nowMillis)
            return 60f
        }

        val isModerate = snapshot.thermalStatus == PowerManager.THERMAL_STATUS_MODERATE ||
                headroom >= downshiftHeadroomThreshold

        val desiredTier = when {
            isModerate -> 2
            else -> {
                val isSafeForRecovery =
                    (snapshot.thermalStatus == PowerManager.THERMAL_STATUS_NONE ||
                            snapshot.thermalStatus == PowerManager.THERMAL_STATUS_LIGHT) &&
                            (headroom <= 0f || headroom < recoveryHeadroomThreshold)
                if (currentTier in 1..2 && !isSafeForRecovery) currentTier else 3
            }
        }

        if (desiredTier == currentTier) return rateForTier(currentTier)

        val lastChange = lastTierChangeMillis
        if (lastChange != null && nowMillis - lastChange < minModeDwellMillis) {
            return rateForTier(currentTier)
        }

        moveToTier(desiredTier, nowMillis)
        return rateForTier(desiredTier)
    }

    private fun moveToTier(tier: Int, nowMillis: Long) {
        if (tier != currentTier) {
            currentTier = tier
            lastTierChangeMillis = nowMillis
        }
    }
}

object DevicePerformanceUtils {
    fun getTargetDisplay(context: Context?, window: Window? = null): Display? {
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { return window.context.display } catch (_: Throwable) {}
        }
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { return context.display } catch (_: Throwable) {}
        }
        val wm = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return wm?.defaultDisplay
    }

    fun getCurrentRefreshRate(context: Context): Float {
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }
            display?.refreshRate ?: 60f
        } catch (_: Exception) {
            60f
        }
    }

    fun selectDisplayModeId(
        modes: List<DisplayModeSpec>,
        currentModeId: Int,
        targetRate: Float
    ): Int {
        if (targetRate <= 0f || modes.isEmpty()) return 0
        val current = modes.firstOrNull { it.modeId == currentModeId }
        val sameResolution = if (current != null) {
            modes.filter {
                it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
            }
        } else modes

        val candidates = sameResolution.ifEmpty { modes }
        return candidates
            .filter { it.refreshRate >= targetRate - 0.5f }
            .minByOrNull { it.refreshRate }
            ?.modeId
            ?: candidates.maxByOrNull { it.refreshRate }?.modeId
            ?: 0
    }

    fun applyAdaptiveRefreshRate(window: Window, targetRate: Float) {
        try {
            val display = getTargetDisplay(null, window)
            val desiredModeId = if (targetRate > 0f && display != null) {
                val modes = display.supportedModes?.map { mode ->
                    DisplayModeSpec(mode.modeId, mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
                }.orEmpty()
                selectDisplayModeId(modes, display.mode?.modeId ?: 0, targetRate)
            } else 0

            val desiredRate = if (targetRate > 0f) targetRate else 0f
            val params = window.attributes
            if (params.preferredRefreshRate == desiredRate && params.preferredDisplayModeId == desiredModeId) return
            params.preferredDisplayModeId = desiredModeId
            params.preferredRefreshRate = desiredRate
            window.attributes = params
        } catch (e: Throwable) {
            Log.w("AdaptiveRefreshRate", "Failed to apply refresh rate: ${e.message}")
        }
    }
}

class AdaptiveRefreshRateManager(
    private val activity: ComponentActivity,
    private val policy: AdaptiveRefreshRatePolicy = AdaptiveRefreshRatePolicy()
) : DefaultLifecycleObserver {

    var isHighRefreshRateEnabled: Boolean = true
        set(value) {
            field = value
            reevaluateRefreshRate()
        }

    private var currentThermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private var isPowerSaveMode: Boolean = false
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var powerSaveReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coalescedReevaluate = Runnable { reevaluateRefreshRate() }

    private fun scheduleReevaluate() {
        mainHandler.removeCallbacks(coalescedReevaluate)
        mainHandler.postDelayed(coalescedReevaluate, 2000L)
    }

    fun reevaluateRefreshRate() {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val headroom = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                powerManager?.getThermalHeadroom(10)?.takeIf { it.isFinite() } ?: 0f
            } else 0f
        } catch (_: Throwable) { 0f }

        val snapshot = ThermalSnapshot(
            thermalStatus = currentThermalStatus,
            isPowerSaveMode = isPowerSaveMode,
            thermalHeadroom = headroom
        )

        val targetDisplay = DevicePerformanceUtils.getTargetDisplay(activity, activity.window)
        val supportedRates = targetDisplay?.supportedModes?.map { it.refreshRate }
        val currentRate = targetDisplay?.refreshRate ?: 60f

        val targetRate = policy.resolveTargetRate(
            snapshot = snapshot,
            supportedRates = supportedRates,
            currentDisplayRate = currentRate,
            highRefreshRateEnabled = isHighRefreshRateEnabled
        )

        DevicePerformanceUtils.applyAdaptiveRefreshRate(activity.window, targetRate)
    }

    override fun onStart(owner: LifecycleOwner) {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isPowerSaveMode = powerManager?.isPowerSaveMode == true
        currentThermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                currentThermalStatus = status
                scheduleReevaluate()
            }
            thermalListener = listener
            try { powerManager?.addThermalStatusListener(activity.mainExecutor, listener) } catch (_: Throwable) {}
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isPowerSaveMode = powerManager?.isPowerSaveMode == true
                scheduleReevaluate()
            }
        }
        powerSaveReceiver = receiver
        try { activity.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) } catch (_: Throwable) {}

        reevaluateRefreshRate()
    }

    override fun onResume(owner: LifecycleOwner) {
        reevaluateRefreshRate()
    }

    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(coalescedReevaluate)
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try { powerManager?.removeThermalStatusListener(thermalListener!!) } catch (_: Throwable) {}
            thermalListener = null
        }
        powerSaveReceiver?.let {
            try { activity.unregisterReceiver(it) } catch (_: Throwable) {}
            powerSaveReceiver = null
        }
    }
}
