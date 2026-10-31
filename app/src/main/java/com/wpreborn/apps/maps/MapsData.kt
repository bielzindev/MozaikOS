package com.wpreborn.apps.maps

import android.content.Context
import com.wpreborn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Routing profiles of the FOSSGIS OSRM servers (routing.openstreetmap.de). */
enum class TravelMode(val profile: String, val label: Int, val googleMode: String) {
    Car("routed-car", R.string.maps_mode_car, "d"),
    Bike("routed-bike", R.string.maps_mode_bike, "b"),
    Foot("routed-foot", R.string.maps_mode_foot, "w"),
}

data class Step(val type: String, val modifier: String, val street: String, val exit: Int, val distanceM: Double) {
    /** A short instruction, e.g. "Turn right onto Main Street". */
    fun text(context: Context): String {
        val dir = when (modifier) {
            "left" -> context.getString(R.string.maps_dir_left)
            "right" -> context.getString(R.string.maps_dir_right)
            "slight left" -> context.getString(R.string.maps_dir_slight_left)
            "slight right" -> context.getString(R.string.maps_dir_slight_right)
            "sharp left" -> context.getString(R.string.maps_dir_sharp_left)
            "sharp right" -> context.getString(R.string.maps_dir_sharp_right)
            "uturn" -> context.getString(R.string.maps_dir_uturn)
            else -> context.getString(R.string.maps_dir_straight)
        }
        val onto = if (street.isNotBlank()) context.getString(R.string.maps_onto, street) else ""
        return when (type) {
            "depart" -> context.getString(R.string.maps_step_depart, onto).trim()
            "arrive" -> context.getString(R.string.maps_step_arrive)
            "roundabout", "rotary", "exit roundabout", "exit rotary" -> context.getString(R.string.maps_step_roundabout, maxOf(exit, 1), onto).trim()
            "continue", "new name" -> context.getString(R.string.maps_step_continue, dir, onto).trim()
            else -> context.getString(R.string.maps_step_turn, dir, onto).trim()
        }
    }
}

data class Route(val distanceM: Double, val durationS: Double, val points: List<Pair<Double, Double>>, val steps: List<Step>)

private const val AGENT = "ZUNEX launcher (https://github.com/joaogabrielsilvademourap3-spec/zunex)"

private fun get(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", AGENT)
    conn.connectTimeout = 10_000
    conn.readTimeout = 20_000
    return try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun placeFrom(props: JSONObject, lat: Double, lon: Double): Place {
    val street = listOf(props.optString("street"), props.optString("housenumber")).filter { it.isNotBlank() }.joinToString(", ")
    val name = props.optString("name").ifBlank { street.ifBlank { props.optString("city") } }
    val detail = listOf(
        if (props.optString("name").isNotBlank()) street else "",
        props.optString("district"),
        props.optString("city"),
        props.optString("state"),
        props.optString("country"),
    ).filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")
    return Place(name, detail, lat, lon)
}

/** Place search (Photon by Komoot, OpenStreetMap data), biased to the map centre. */
suspend fun search(q: String, lat: Double, lon: Double): List<Place> = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject(get("https://photon.komoot.io/api/?limit=10&lat=$lat&lon=$lon&q=" + URLEncoder.encode(q, "UTF-8")))
        val arr = json.getJSONArray("features")
        List(arr.length()) { i ->
            val f = arr.getJSONObject(i)
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            placeFrom(f.getJSONObject("properties"), c.getDouble(1), c.getDouble(0))
        }
    }.getOrDefault(emptyList())
}

/** The address at a point. */
suspend fun reverse(lat: Double, lon: Double): Place? = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject(get("https://photon.komoot.io/reverse?lat=$lat&lon=$lon"))
        val f = json.getJSONArray("features").getJSONObject(0)
        placeFrom(f.getJSONObject("properties"), lat, lon)
    }.getOrNull()
}

/** A route with its line and steps, or null when none was found. */
suspend fun route(mode: TravelMode, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? = withContext(Dispatchers.IO) {
    runCatching {
        val url = "https://routing.openstreetmap.de/${mode.profile}/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson&steps=true"
        val json = JSONObject(get(url))
        if (json.optString("code") != "Ok") return@runCatching null
        val r = json.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = List(coords.length()) { i -> coords.getJSONArray(i).let { it.getDouble(1) to it.getDouble(0) } }
        val steps = ArrayList<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val s = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until s.length()) {
                val st = s.getJSONObject(i)
                val m = st.getJSONObject("maneuver")
                steps += Step(m.optString("type"), m.optString("modifier"), st.optString("name"), m.optInt("exit", 0), st.optDouble("distance", 0.0))
            }
        }
        Route(r.getDouble("distance"), r.getDouble("duration"), points, steps)
    }.getOrNull()
}

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

fun formatDistance(m: Double): String = when {
    m < 1000 -> "${(m / 10).roundToInt() * 10} m"
    m < 10_000 -> "%.1f km".format(m / 1000)
    else -> "${(m / 1000).roundToInt()} km"
}

fun formatDuration(context: Context, s: Double): String {
    val min = (s / 60).roundToInt().coerceAtLeast(1)
    return if (min < 60) context.getString(R.string.maps_minutes, min) else context.getString(R.string.maps_hours, min / 60, min % 60)
}
