package com.example.data.remote

import android.content.Context
import com.example.data.remote.ApiClient.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val message: String,
    val updateUrl: String,
    val hasUpdate: Boolean
)

object GitHubConfigService {

    const val CURRENT_VERSION_CODE = 2
    const val CURRENT_VERSION_NAME = "1.1"

    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/Rajat-Solankii/VortexApp/main/update.json"

    private const val BLOCKED_DEVICES_URL =
        "https://raw.githubusercontent.com/Rajat-Solankii/VortexApp/main/blocked_devices.json"

    private const val FALLBACK_UPDATE_URL =
        "https://github.com/Rajat-Solankii/Aura/releases/latest"

    /**
     * Checks update.json hosted on GitHub repository.
     * Falls back to bundled assets/update.json if offline or network failure.
     */
    suspend fun checkForUpdates(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            var jsonString: String? = null

            // 1. Attempt fetching remote update.json from GitHub
            try {
                val request = Request.Builder()
                    .url(UPDATE_JSON_URL)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    jsonString = response.body?.string()
                }
            } catch (_: Exception) {
                // Network failure, fallback to asset
            }

            // 2. Fallback to local asset if remote request failed
            if (jsonString.isNullOrBlank()) {
                jsonString = try {
                    context.assets.open("update.json").bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    null
                }
            }

            if (jsonString.isNullOrBlank()) {
                return@runCatching UpdateInfo(
                    versionCode = CURRENT_VERSION_CODE,
                    versionName = CURRENT_VERSION_NAME,
                    message = "You are on the latest version of Vortex.",
                    updateUrl = FALLBACK_UPDATE_URL,
                    hasUpdate = false
                )
            }

            val json = JSONObject(jsonString)
            val remoteVersionCode = json.optInt("versionCode", CURRENT_VERSION_CODE)
            val remoteVersionName = json.optString("versionName", CURRENT_VERSION_NAME)
            val message = json.optString("message", "A new version of Vortex is available!")
            val updateUrl = json.optString("updateUrl", FALLBACK_UPDATE_URL)

            val hasUpdate = remoteVersionCode > CURRENT_VERSION_CODE

            UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                message = message,
                updateUrl = updateUrl,
                hasUpdate = hasUpdate
            )
        }
    }

    /**
     * Checks if current device ID is present in GitHub blocked_devices.json.
     */
    suspend fun isDeviceBlocked(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        if (deviceId.isBlank() || deviceId == "UNKNOWN_DEVICE") return@withContext false
        try {
            val request = Request.Builder()
                .url(BLOCKED_DEVICES_URL)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val bodyString = response.body?.string() ?: return@withContext false
            val trimmed = bodyString.trim()

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    if (array.optString(i).equals(deviceId, ignoreCase = true)) {
                        return@withContext true
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val array = obj.optJSONArray("blocked") ?: obj.optJSONArray("blockedDevices")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        if (array.optString(i).equals(deviceId, ignoreCase = true)) {
                            return@withContext true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            // Default to allowed if network unavailable or format error
            false
        }
    }
}
