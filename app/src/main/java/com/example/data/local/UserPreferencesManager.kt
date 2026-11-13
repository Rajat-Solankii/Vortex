package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vortex_preferences")

class UserPreferencesManager(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_SELECTED_GENRES = stringSetPreferencesKey("selected_genres")
        val KEY_HIGH_REFRESH_RATE = booleanPreferencesKey("high_refresh_rate")
        val KEY_AMOLED_THEME = booleanPreferencesKey("amoled_theme")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val KEY_RECENT_SEARCHES = stringSetPreferencesKey("recent_searches_set")
        val KEY_STREAM_RESOLUTION = stringPreferencesKey("stream_resolution")
        val KEY_DEFAULT_SERVER = stringPreferencesKey("default_server")
        val KEY_SEEK_INTERVAL = intPreferencesKey("seek_interval")
        val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
        val KEY_GESTURE_CONTROLS = booleanPreferencesKey("gesture_controls")
        val KEY_AUTO_SUBTITLES = booleanPreferencesKey("auto_subtitles")
        val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val KEY_AUDIO_LANGUAGE = stringPreferencesKey("audio_language")
        val KEY_SUBTITLE_FONT_SIZE = floatPreferencesKey("subtitle_font_size")
        val KEY_SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val KEY_SUBTITLE_OPACITY = floatPreferencesKey("subtitle_opacity")
        val KEY_FPS_OVERLAY = booleanPreferencesKey("fps_overlay")
        val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        val KEY_NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val KEY_POSTER_DENSITY = stringPreferencesKey("poster_density")
        val KEY_DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val KEY_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val KEY_STORAGE_LOCATION = stringPreferencesKey("storage_location")
        val KEY_SAFE_SEARCH = booleanPreferencesKey("safe_search")
        val KEY_WATCH_REGION = stringPreferencesKey("watch_region")
        val KEY_DEFAULT_HOME_TAB = stringPreferencesKey("default_home_tab")
        val KEY_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val KEY_AVATAR_ID = stringPreferencesKey("user_avatar_id")
        val KEY_CUSTOM_AVATAR_PATH = stringPreferencesKey("custom_avatar_path")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    val selectedGenres: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_GENRES] ?: setOf("Action", "Sci-Fi", "Anime")
    }

    suspend fun setSelectedGenres(genres: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_GENRES] = genres
        }
    }

    val isHighRefreshRate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIGH_REFRESH_RATE] ?: true
    }

    suspend fun setHighRefreshRate(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGH_REFRESH_RATE] = enabled
        }
    }

    val isAmoledTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val mode = prefs[KEY_THEME_MODE]
        if (mode != null) mode == "AMOLED" else (prefs[KEY_AMOLED_THEME] ?: false)
    }

    suspend fun setAmoledTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AMOLED_THEME] = enabled
            prefs[KEY_THEME_MODE] = if (enabled) "AMOLED" else "Dark"
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: if (prefs[KEY_AMOLED_THEME] == true) "AMOLED" else "Dark"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
            prefs[KEY_AMOLED_THEME] = mode == "AMOLED"
        }
    }

    val isDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_USE_DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLOR] = enabled
        }
    }

    val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_SEARCHES]?.toList() ?: emptyList()
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]?.toMutableSet() ?: mutableSetOf()
            current.remove(trimmed)
            val updated = mutableSetOf(trimmed).apply { addAll(current.take(9)) }
            prefs[KEY_RECENT_SEARCHES] = updated
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]?.toMutableSet() ?: mutableSetOf()
            current.remove(query.trim())
            prefs[KEY_RECENT_SEARCHES] = current
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECENT_SEARCHES] = emptySet()
        }
    }

    val streamResolution: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_STREAM_RESOLUTION] ?: "1080p FHD (Recommended)"
    }

    suspend fun setStreamResolution(resolution: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STREAM_RESOLUTION] = resolution
        }
    }

    val defaultServer: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_SERVER] ?: "Server 1 (Auto CDN)"
    }

    suspend fun setDefaultServer(server: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_SERVER] = server
        }
    }

    val seekSkipInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEEK_INTERVAL] ?: 10
    }

    suspend fun setSeekSkipInterval(interval: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEEK_INTERVAL] = interval
        }
    }

    val isAutoPlayNext: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTOPLAY_NEXT] ?: true
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTOPLAY_NEXT] = enabled
        }
    }

    val isBackgroundAudio: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_AUDIO] ?: false
    }

    suspend fun setBackgroundAudio(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_AUDIO] = enabled
        }
    }

    val isAutoPip: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_PIP] ?: true
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_PIP] = enabled
        }
    }

    val isGestureControls: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_GESTURE_CONTROLS] ?: true
    }

    suspend fun setGestureControls(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GESTURE_CONTROLS] = enabled
        }
    }

    val isAutoSubtitles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SUBTITLES] ?: true
    }

    suspend fun setAutoSubtitles(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_SUBTITLES] = enabled
        }
    }

    val preferredAudioLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_LANGUAGE] ?: "English"
    }

    suspend fun setPreferredAudioLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_LANGUAGE] = language
        }
    }

    val preferredSubtitleLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBTITLE_LANGUAGE] ?: "English"
    }

    suspend fun setPreferredSubtitleLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBTITLE_LANGUAGE] = language
        }
    }

    val subtitleFontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBTITLE_FONT_SIZE] ?: 16f
    }

    suspend fun setSubtitleFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBTITLE_FONT_SIZE] = size
        }
    }

    val subtitleStyle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBTITLE_STYLE] ?: "White with black border"
    }

    suspend fun setSubtitleStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBTITLE_STYLE] = style
        }
    }

    val subtitleOpacity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBTITLE_OPACITY] ?: 0.8f
    }

    suspend fun setSubtitleOpacity(opacity: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBTITLE_OPACITY] = opacity
        }
    }

    val isFpsOverlayEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FPS_OVERLAY] ?: false
    }

    suspend fun setFpsOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FPS_OVERLAY] = enabled
        }
    }

    val isReducedMotion: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REDUCED_MOTION] ?: false
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REDUCED_MOTION] = enabled
        }
    }

    val accentColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR] ?: "Vortex Cyan"
    }

    suspend fun setAccentColor(color: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCENT_COLOR] = color
        }
    }

    val navBarStyle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NAV_BAR_STYLE] ?: "Floating Expressive Pill"
    }

    suspend fun setNavBarStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAV_BAR_STYLE] = style
        }
    }

    val posterDensity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_POSTER_DENSITY] ?: "Comfortable"
    }

    suspend fun setPosterDensity(density: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_POSTER_DENSITY] = density
        }
    }

    val downloadQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_QUALITY] ?: "1080p"
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_QUALITY] = quality
        }
    }

    val isDownloadWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_WIFI_ONLY] ?: true
    }

    suspend fun setDownloadWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    val storageLocation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_STORAGE_LOCATION] ?: "Internal Storage"
    }

    suspend fun setStorageLocation(location: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STORAGE_LOCATION] = location
        }
    }

    val isSafeSearchEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SAFE_SEARCH] ?: false
    }

    suspend fun setSafeSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAFE_SEARCH] = enabled
        }
    }

    val watchRegion: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WATCH_REGION] ?: "United States"
    }

    suspend fun setWatchRegion(region: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WATCH_REGION] = region
        }
    }

    val defaultHomeTab: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_HOME_TAB] ?: "All"
    }

    suspend fun setDefaultHomeTab(tab: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_HOME_TAB] = tab
        }
    }

    val displayName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISPLAY_NAME] ?: "Vortex Explorer"
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = name
        }
    }

    val avatarId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AVATAR_ID] ?: "cosmic"
    }

    suspend fun setAvatarId(avatar: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR_ID] = avatar
        }
    }

    val customAvatarPath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_AVATAR_PATH]
    }

    suspend fun setCustomAvatarPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path != null) {
                prefs[KEY_CUSTOM_AVATAR_PATH] = path
            } else {
                prefs.remove(KEY_CUSTOM_AVATAR_PATH)
            }
        }
    }

    suspend fun resetProfile() {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = "Vortex Explorer"
            prefs[KEY_AVATAR_ID] = "cosmic"
            prefs.remove(KEY_CUSTOM_AVATAR_PATH)
        }
    }
}
