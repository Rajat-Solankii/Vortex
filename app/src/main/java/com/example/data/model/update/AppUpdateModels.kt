package com.example.data.model.update

import java.io.File

data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
    val contentType: String = "",
    val architecture: String = "ARM64"
)

data class AppReleaseInfo(
    val tagName: String,
    val name: String? = null,
    val body: String = "",
    val htmlUrl: String = "",
    val publishedAt: String = "",
    val assets: List<AppReleaseAsset> = emptyList()
)

sealed interface UpdateCheckResult {
    data class Available(
        val release: AppReleaseInfo,
        val currentVersion: String,
        val targetAsset: AppReleaseAsset?
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateCheckResult
}

/**
 * SemVer comparator comparing versions such as "v1.2", "1.2.1", "1.3".
 */
object SemVerComparator {
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        return compare(remoteVersion, currentVersion) > 0
    }

    fun compare(v1: String, v2: String): Int {
        val clean1 = cleanVersion(v1)
        val clean2 = cleanVersion(v2)

        val parts1 = clean1.first
        val parts2 = clean2.first

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }

        val pre1 = clean1.second
        val pre2 = clean2.second

        return when {
            pre1.isEmpty() && pre2.isNotEmpty() -> 1
            pre1.isNotEmpty() && pre2.isEmpty() -> -1
            pre1.isNotEmpty() && pre2.isNotEmpty() -> pre1.compareTo(pre2)
            else -> 0
        }
    }

    private fun cleanVersion(version: String): Pair<List<Int>, String> {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        val mainPart = trimmed.substringBefore("-").substringBefore("+")
        val preRelease = if (trimmed.contains("-")) trimmed.substringAfter("-") else ""

        val numbers = mainPart.split(".")
            .mapNotNull { it.toIntOrNull() }

        return numbers to preRelease
    }
}
