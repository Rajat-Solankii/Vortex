# Vortex

Vortex is a native Android movie and TV streaming client built with Kotlin and Jetpack Compose.

## Features
- **120 FPS High Refresh Rate Support**: Ultra-smooth scrolling, GPU zero-copy hardware bitmaps, downsampled image caching.
- **Dynamic Recommendation Carousel**: Featured movie recommendations in an auto-sliding format.
- **Categorized Streaming**: Anime, Bollywood, Cartoons, Drama, Hollywood, Movies, and TV Shows.
- **Multi-Server Playback**: Embed routing across multiple video sources with built-in AdBlocker shield protection.
- **Watch History & Resume**: Local persistence powered by Room database.
- **Telegram Monitoring & Feedback**: Telemetry launch analytics and in-app feedback bot directly to Telegram.
- **Remote Update Checking**: Automated GitHub update verification and blacklist ban security.

## Tech Stack
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose & Material Design 3
- **Image Loading**: Coil with hardware acceleration & memory caching
- **Database**: Room Database
- **Networking**: Retrofit, OkHttp, Moshi
- **Video Engine**: ExoPlayer & Media3 WebView embed integration
