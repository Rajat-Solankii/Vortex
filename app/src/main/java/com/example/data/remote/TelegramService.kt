package com.example.data.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.data.remote.ApiClient.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelegramService {

    // Bot Configuration
    private const val MONITOR_BOT_TOKEN = "8768355033:AAFAFXPh5aSKlOk3jYypehNMmEd_n1g5wSE"
    private const val FEEDBACK_BOT_TOKEN = "8745560758:AAHVMwRc1-66pJrl0kplGs5cztLPEOzOkhc"
    private const val CHAT_ID = "5639829125"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun getAppVersionInfo(context: Context): Pair<String, Long> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            (pInfo.versionName ?: com.example.BuildConfig.VERSION_NAME) to code
        } catch (_: Exception) {
            com.example.BuildConfig.VERSION_NAME to com.example.BuildConfig.VERSION_CODE.toLong()
        }
    }

    /**
     * Sends device telemetry & analytics when the app opens via Monitor Bot.
     */
    suspend fun sendAppLaunchReport(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = getDeviceId(context)
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            val locale = Locale.getDefault().toLanguageTag()
            val timeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

            val (versionName, versionCode) = getAppVersionInfo(context)
            val message = """
                🚀 <b>Vortex App Launch Report</b>
                ━━━━━━━━━━━━━━━━━━
                📱 <b>Device:</b> $manufacturer $model
                🤖 <b>Android OS:</b> $androidVersion (API $sdkInt)
                🔑 <b>Device ID:</b> <code>$deviceId</code>
                🌐 <b>Locale:</b> $locale
                📦 <b>App Version:</b> v$versionName (Version Code: $versionCode)
                ⏰ <b>Time:</b> $timeString
                ━━━━━━━━━━━━━━━━━━
                <i>Vortex Monitoring Service</i>
            """.trimIndent()

            postToTelegram(
                botToken = MONITOR_BOT_TOKEN,
                chatId = CHAT_ID,
                text = message
            )
        }
    }

    /**
     * Sends user feedback or bug report to Telegram via Feedback Bot.
     */
    suspend fun sendUserFeedback(
        userName: String,
        category: String,
        messageText: String,
        context: Context
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = getDeviceId(context)
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val timeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

            val (versionName, versionCode) = getAppVersionInfo(context)
            val message = """
                💬 <b>New Vortex User Feedback</b>
                ━━━━━━━━━━━━━━━━━━
                👤 <b>From:</b> ${userName.ifBlank { "Anonymous User" }}
                🏷️ <b>Category:</b> $category
                📱 <b>Device:</b> $manufacturer $model
                📦 <b>App Version:</b> v$versionName (Version Code: $versionCode)
                🔑 <b>Device ID:</b> <code>$deviceId</code>
                ⏰ <b>Submitted At:</b> $timeString
                
                📝 <b>Message:</b>
                $messageText
                ━━━━━━━━━━━━━━━━━━
            """.trimIndent()

            postToTelegram(
                botToken = FEEDBACK_BOT_TOKEN,
                chatId = CHAT_ID,
                text = message
            )
        }
    }

    private fun postToTelegram(botToken: String, chatId: String, text: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "HTML")
            }

            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
        } catch (_: Exception) {
            "UNKNOWN_DEVICE"
        }
    }
}
