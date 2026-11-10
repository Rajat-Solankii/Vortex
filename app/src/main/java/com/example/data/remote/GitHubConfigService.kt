package com.example.data.remote

import android.content.Context
import com.example.data.model.update.AppReleaseAsset
import com.example.data.model.update.AppReleaseInfo
import com.example.data.model.update.SemVerComparator
import com.example.data.model.update.UpdateCheckResult
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

    const val CURRENT_VERSION_CODE = 4
    const val CURRENT_VERSION_NAME = "1.3"

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/Rajat-Solankii/VortexApp/releases/latest"

    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/Rajat-Solankii/VortexApp/main/update.json"

    private const val BLOCKED_DEVICES_URL =
        "https://raw.githubusercontent.com/Rajat-Solankii/VortexApp/main/blocked_devices.json"

    private const val FALLBACK_UPDATE_URL =
        "https://github.com/Rajat-Solankii/VortexApp/releases/latest"

    /**
     * Checks for app updates with full GitHub release details (assets, changelog, byte size).
     */
    suspend fun checkReleaseUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (currentVersion, currentCode) = TelegramService.getAppVersionInfo(context)

        // 1. Try GitHub Releases API first (provides rich body, assets, size, download url)
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .header("User-Agent", "Vortex-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrBlank()) {
                    val root = JSONObject(jsonStr)
                    val tagName = root.optString("tag_name", "")
                    val releaseName = root.optString("name", "Vortex $tagName")
                    val body = root.optString("body", "")
                    val htmlUrl = root.optString("html_url", FALLBACK_UPDATE_URL)
                    val publishedAt = root.optString("published_at", "")

                    val assetsJson = root.optJSONArray("assets")
                    val assetsList = mutableListOf<AppReleaseAsset>()
                    var apkAsset: AppReleaseAsset? = null

                    if (assetsJson != null) {
                        for (i in 0 until assetsJson.length()) {
                            val a = assetsJson.getJSONObject(i)
                            val name = a.optString("name", "")
                            val downloadUrl = a.optString("browser_download_url", "")
                            val sizeBytes = a.optLong("size", 0L)
                            val contentType = a.optString("content_type", "")
                            val asset = AppReleaseAsset(
                                name = name,
                                downloadUrl = downloadUrl,
                                sizeBytes = sizeBytes,
                                contentType = contentType,
                                architecture = "ARM64"
                            )
                            assetsList.add(asset)
                            if (name.endsWith(".apk", ignoreCase = true) && apkAsset == null) {
                                apkAsset = asset
                            }
                        }
                    }

                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        name = releaseName,
                        body = body,
                        htmlUrl = htmlUrl,
                        publishedAt = publishedAt,
                        assets = assetsList
                    )

                    val isNewer = SemVerComparator.isNewer(tagName, currentVersion)
                    return@withContext if (isNewer) {
                        UpdateCheckResult.Available(
                            release = releaseInfo,
                            currentVersion = currentVersion,
                            targetAsset = apkAsset
                        )
                    } else {
                        UpdateCheckResult.UpToDate(
                            currentVersion = currentVersion,
                            latestVersion = tagName
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to update.json
        }

        // 2. Fallback to update.json
        val updateInfoRes = checkForUpdates(context)
        val updateInfo = updateInfoRes.getOrNull()
        if (updateInfo != null && updateInfo.hasUpdate) {
            val fallbackRelease = AppReleaseInfo(
                tagName = "v${updateInfo.versionName}",
                name = "Vortex v${updateInfo.versionName}",
                body = updateInfo.message,
                htmlUrl = updateInfo.updateUrl,
                publishedAt = "",
                assets = listOf(
                    AppReleaseAsset(
                        name = "Vortex.apk",
                        downloadUrl = updateInfo.updateUrl,
                        sizeBytes = 25_000_000L,
                        contentType = "application/vnd.android.package-archive",
                        architecture = "ARM64"
                    )
                )
            )
            UpdateCheckResult.Available(
                release = fallbackRelease,
                currentVersion = currentVersion,
                targetAsset = fallbackRelease.assets.firstOrNull()
            )
        } else if (updateInfo != null) {
            UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                latestVersion = updateInfo.versionName
            )
        } else {
            UpdateCheckResult.Error("Could not connect to update servers.")
        }
    }

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

            val (_, currentCode) = TelegramService.getAppVersionInfo(context)
            val hasUpdate = remoteVersionCode > currentCode

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
            false
        }
    }
}
