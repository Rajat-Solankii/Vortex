package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

class VortexAppApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true
        // Calm queue: decodes max 2 images at once to prevent main UI starvation
        val imageDispatcher = Dispatchers.IO.limitedParallelism(2)
        return ImageLoader.Builder(this)
            .dispatcher(imageDispatcher)
            .allowHardware(!isLowRam)
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.08 else 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 50L * 1024 * 1024 else 150L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
