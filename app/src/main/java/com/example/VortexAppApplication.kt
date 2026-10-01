package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger

class VortexAppApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28) // 28% of available heap for instant cached image rendering
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("vortex_image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            .allowHardware(true) // Ultra-fast hardware bitmap rendering directly on GPU
            .crossfade(false) // Disable heavy crossfade animations on scroll for smooth 120Hz flings
            .respectCacheHeaders(false)
            .build()
    }
}
