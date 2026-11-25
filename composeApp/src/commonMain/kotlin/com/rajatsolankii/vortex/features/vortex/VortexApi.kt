package com.rajatsolankii.vortex.features.vortex

import com.rajatsolankii.vortex.features.addons.httpGetText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Client for the Vortex metadata proxy configured via `VORTEX_API_HOST`.
 * Every TMDB request goes through here: `path` is the TMDB v3 endpoint and the
 * other query parameters are forwarded unchanged.
 */
object VortexApi {
    const val HOST: String = VortexConfig.API_HOST
    const val IMAGE_BASE = "https://image.tmdb.org/t/p"

    private val json = Json { ignoreUnknownKeys = true }

    fun tmdbUrl(path: String, query: Map<String, String> = emptyMap()): String =
        tmdbUrl(host = HOST, path = path, query = query)

    internal fun tmdbUrl(host: String, path: String, query: Map<String, String> = emptyMap()): String =
        buildString {
            val cleanHost = host.trim().trimEnd('/')
            if (cleanHost.isNotEmpty()) {
                append(cleanHost)
            }
            append("/api/tmdb?path=")
            append(path.trim().removePrefix("/").encodeQueryValue(keepSlashes = true))
            query.forEach { (key, value) ->
                if (key == "api_key" || value.isBlank()) return@forEach
                append('&')
                append(key.encodeQueryValue())
                append('=')
                append(value.encodeQueryValue())
            }
        }

    suspend fun tmdbText(path: String, query: Map<String, String> = emptyMap()): String {
        check(HOST.isNotBlank()) { "VORTEX_API_HOST is not configured." }
        return httpGetText(tmdbUrl(path, query))
    }

    suspend fun tmdb(path: String, query: Map<String, String> = emptyMap()): JsonObject =
        json.parseToJsonElement(tmdbText(path, query)).jsonObject

    fun image(path: String?, size: String): String? =
        path?.trim()?.takeIf { it.startsWith("/") }?.let { "$IMAGE_BASE/$size$it" }
}

private const val QUERY_HEX = "0123456789ABCDEF"

/** Percent-encodes a query value. Values that are already encoded are left as they are. */
internal fun String.encodeQueryValue(keepSlashes: Boolean = false): String =
    buildString {
        val source = this@encodeQueryValue
        var index = 0
        val bytes = source.encodeToByteArray()
        while (index < bytes.size) {
            val value = bytes[index].toInt() and 0xFF
            val char = value.toChar()
            val alreadyEncoded = char == '%' && index + 2 < bytes.size &&
                bytes[index + 1].toInt().toChar().isHexDigitChar() &&
                bytes[index + 2].toInt().toChar().isHexDigitChar()
            when {
                alreadyEncoded -> append('%')
                char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
                    char == '-' || char == '_' || char == '.' || char == '~' || char == ',' ||
                    (keepSlashes && char == '/') -> append(char)
                else -> {
                    append('%')
                    append(QUERY_HEX[value shr 4])
                    append(QUERY_HEX[value and 0x0F])
                }
            }
            index++
        }
    }

private fun Char.isHexDigitChar(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
