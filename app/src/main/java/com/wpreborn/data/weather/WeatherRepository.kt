package com.wpreborn.data.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.wpreborn.permissions.hasPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val region: String, val lat: Double, val lon: Double, val current: Boolean = false) {
    val id get() = if (current) "current" else "%.3f,%.3f".format(Locale.US, lat, lon)
    fun toJson(): JSONObject = JSONObject().put("name", name).put("region", region).put("lat", lat).put("lon", lon).put("current", current)

    companion object {
        fun fromJson(o: JSONObject) = Place(o.getString("name"), o.optString("region"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("current"))
    }
}

data class Hour(val time: Long, val temp: Double, val code: Int, val precipChance: Int, val isDay: Boolean)
data class Day(val date: Long, val max: Double, val min: Double, val code: Int, val precipChance: Int, val sunrise: Long, val sunset: Long, val uv: Double)

data class Weather(
    val place: Place,
    val updated: Long,
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    val windDir: Int,
    val pressure: Double,
    val precipitation: Double,
    val hours: List<Hour>,
    val days: List<Day>,
)

/**
 * Weather from Open-Meteo (free, no account or key), for the current location or saved
 * places. The last forecasts are cached so the app and the live tile work offline.
 */
class WeatherRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "weather").apply { mkdirs() }

    private val _places = MutableStateFlow(loadPlaces())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    private val _forecasts = MutableStateFlow<Map<String, Weather>>(emptyMap())
    val forecasts: StateFlow<Map<String, Weather>> = _forecasts.asStateFlow()

    private val _fahrenheit = MutableStateFlow(prefs.getBoolean("fahrenheit", false))
    val fahrenheit: StateFlow<Boolean> = _fahrenheit.asStateFlow()

    init {
        // Cached forecasts first, so the first frame already has numbers.
        _forecasts.value = _places.value.mapNotNull { p -> readCache(p)?.let { p.id to it } }.toMap()
    }

    /** The place shown on the live tile: the first in the list. */
    val primary: Place? get() = _places.value.firstOrNull()

    /** Forgets places, forecasts and preferences (the Weather app was uninstalled). */
    fun clearAll() {
        prefs.edit().clear().apply()
        cacheDir.mkdirs()
        _places.value = emptyList()
        _forecasts.value = emptyMap()
        _fahrenheit.value = false
    }

    fun setFahrenheit(value: Boolean) {
        prefs.edit().putBoolean("fahrenheit", value).apply()
        _fahrenheit.value = value
    }

    fun addPlace(place: Place) {
        if (_places.value.any { it.id == place.id }) return
        savePlaces(_places.value + place)
        scope.launch { refresh(place) }
    }

    fun removePlace(place: Place) {
        savePlaces(_places.value.filterNot { it.id == place.id })
        File(cacheDir, place.cacheName).delete()
        _forecasts.value = _forecasts.value - place.id
    }

    fun makePrimary(place: Place) {
        savePlaces(listOf(place) + _places.value.filterNot { it.id == place.id })
    }

    /** Refreshes every place whose forecast is older than [maxAgeMs]. */
    fun refreshIfStale(maxAgeMs: Long = 30 * 60_000L) {
        scope.launch {
            val now = System.currentTimeMillis()
            _places.value.forEach { p ->
                val w = _forecasts.value[p.id]
                if (w == null || now - w.updated > maxAgeMs) refresh(p)
            }
        }
    }

    /** Downloads the forecast; for the current location, finds the location first. Returns false when offline. */
    suspend fun refresh(place: Place): Boolean {
        val target = if (place.current) locate() ?: place.takeIf { it.lat != 0.0 || it.lon != 0.0 } ?: return false else place
        if (place.current && target != place) {
            savePlaces(_places.value.map { if (it.current) target else it })
        }
        val w = withContext(Dispatchers.IO) { runCatching { download(target) }.getOrNull() } ?: return false
        _forecasts.value = _forecasts.value + (target.id to w)
        return true
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val lang = Locale.getDefault().language
            val json = get("https://geocoding-api.open-meteo.com/v1/search?count=10&language=$lang&name=" + URLEncoder.encode(query, "UTF-8"))
            val arr = JSONObject(json).optJSONArray("results") ?: JSONArray()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Place(
                    o.getString("name"),
                    listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                    o.getDouble("latitude"),
                    o.getDouble("longitude"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Adds "current location" as the first place (needs the location permission). */
    fun addCurrentLocation() {
        if (_places.value.any { it.current }) return
        val p = Place(context.getString(com.wpreborn.R.string.weather_current_location), "", 0.0, 0.0, current = true)
        savePlaces(listOf(p) + _places.value)
        scope.launch { refresh(p) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Place? {
        if (!context.hasPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION))) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        var loc: Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if ((loc == null || System.currentTimeMillis() - loc.time > 30 * 60_000L) && Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            loc = withTimeoutOrNull(8000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    runCatching {
                        lm.getCurrentLocation(providers.first(), null, context.mainExecutor) { l -> if (cont.isActive) cont.resumeWith(Result.success(l)) }
                    }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                }
            } ?: loc
        }
        val found = loc ?: return null
        val name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                android.location.Geocoder(context).getFromLocation(found.latitude, found.longitude, 1)?.firstOrNull()?.let { a ->
                    (a.locality ?: a.subAdminArea ?: a.adminArea) to listOfNotNull(a.adminArea, a.countryName).joinToString(", ")
                }
            }.getOrNull()
        }
        return Place(name?.first ?: context.getString(com.wpreborn.R.string.weather_current_location), name?.second.orEmpty(), found.latitude, found.longitude, current = true)
    }

    private fun download(place: Place): Weather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(Locale.US, place.lat)}&longitude=${"%.4f".format(Locale.US, place.lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,pressure_msl,precipitation" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&timezone=auto&timeformat=unixtime&forecast_days=10"
        val json = get(url)
        File(cacheDir, place.cacheName).writeText(JSONObject().put("place", place.toJson()).put("updated", System.currentTimeMillis()).put("data", JSONObject(json)).toString())
        return parse(place, System.currentTimeMillis(), JSONObject(json))
    }

    private fun readCache(place: Place): Weather? = runCatching {
        val o = JSONObject(File(cacheDir, place.cacheName).readText())
        parse(place, o.getLong("updated"), o.getJSONObject("data"))
    }.getOrNull()

    private fun parse(place: Place, updated: Long, o: JSONObject): Weather {
        val c = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val d = o.getJSONObject("daily")
        val now = System.currentTimeMillis() / 1000
        val hTimes = h.getJSONArray("time")
        val hours = (0 until hTimes.length()).map { i ->
            Hour(
                hTimes.getLong(i) * 1000,
                h.getJSONArray("temperature_2m").optDouble(i),
                h.getJSONArray("weather_code").optInt(i),
                h.getJSONArray("precipitation_probability").optInt(i),
                h.getJSONArray("is_day").optInt(i, 1) == 1,
            )
        }.filter { it.time / 1000 >= now - 3600 }.take(24)
        val dTimes = d.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            Day(
                dTimes.getLong(i) * 1000,
                d.getJSONArray("temperature_2m_max").optDouble(i),
                d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("precipitation_probability_max").optInt(i),
                d.getJSONArray("sunrise").optLong(i) * 1000,
                d.getJSONArray("sunset").optLong(i) * 1000,
                d.getJSONArray("uv_index_max").optDouble(i),
            )
        }
        return Weather(
            place = place,
            updated = updated,
            temp = c.optDouble("temperature_2m"),
            feelsLike = c.optDouble("apparent_temperature"),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            humidity = c.optInt("relative_humidity_2m"),
            windKmh = c.optDouble("wind_speed_10m"),
            windDir = c.optInt("wind_direction_10m"),
            pressure = c.optDouble("pressure_msl"),
            precipitation = c.optDouble("precipitation"),
            hours = hours,
            days = days,
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Mozaik")
        return try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private val Place.cacheName get() = id.replace(Regex("[^0-9a-z.,-]"), "_") + ".json"

    private fun loadPlaces(): List<Place> = runCatching {
        val arr = JSONArray(prefs.getString("places", "[]"))
        List(arr.length()) { Place.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun savePlaces(list: List<Place>) {
        prefs.edit().putString("places", JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()).apply()
        _places.value = list
    }

    companion object {
        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) { instance ?: WeatherRepository(context.applicationContext).also { instance = it } }

        fun toUnit(celsius: Double, fahrenheit: Boolean) = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
    }
}
