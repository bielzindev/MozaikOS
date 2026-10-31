package com.wpreborn.apps.weather

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Background photos for the weather conditions: public domain / CC0 pictures from Wikimedia
 * Commons (credits in third_party/weather-photos), packaged as assets/weather/<key>.jpg.
 * A condition without its photo draws the animated sky instead.
 */
object WeatherPhotos {
    private val cache = HashMap<String, ImageBitmap?>()

    fun keyFor(sky: Sky, phase: SkyPhase): String = when {
        phase == SkyPhase.Night && (sky == Sky.Clear || sky == Sky.PartlyCloudy) -> "night"
        phase == SkyPhase.Golden && (sky == Sky.Clear || sky == Sky.PartlyCloudy) -> "sunset"
        else -> when (sky) {
            Sky.Clear -> "sunny"
            Sky.PartlyCloudy -> "partly"
            Sky.Cloudy -> "cloudy"
            Sky.Fog -> "fog"
            Sky.Drizzle, Sky.Rain, Sky.Showers -> "rain"
            Sky.Snow -> "snow"
            Sky.Storm -> "storm"
        }
    }

    /** The decoded photo, or null when the APK has none for [key]. */
    fun load(context: Context, key: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(key) {
            runCatching {
                context.assets.open("weather/$key.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
}
