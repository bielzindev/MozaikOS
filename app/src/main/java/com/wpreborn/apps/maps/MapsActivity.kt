package com.wpreborn.apps.maps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class PlaceResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/**
 * Maps in the spirit of HERE / Bing Maps on Windows Phone 8.1: the full-screen map
 * (OpenStreetMap, dark in the dark theme), your location, place search, and directions handed
 * to the phone's navigation app. Map data © OpenStreetMap contributors.
 */
class MapsActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val dark = Wp.palette.isDark
        val location = rememberPermissionState(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        var searching by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<PlaceResult>?>(null) }
        var loading by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<PlaceResult?>(null) }
        var darkMap by remember { mutableStateOf(dark) }

        val map = remember {
            Configuration.getInstance().apply {
                load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                userAgentValue = "ZUNEX/" + com.wpreborn.BuildConfig.VERSION_NAME + " (" + context.packageName + ")"
            }
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(-14.2, -51.9))
                overlays.add(CopyrightOverlay(context))
            }
        }
        val me = remember { MyLocationNewOverlay(GpsMyLocationProvider(context), map) }
        LaunchedEffect(darkMap) {
            map.overlayManager.tilesOverlay.setColorFilter(if (darkMap) TilesOverlay.INVERT_COLORS else null)
            map.invalidate()
        }
        LaunchedEffect(location.granted) {
            if (location.granted && me !in map.overlays) {
                me.enableMyLocation()
                map.overlays.add(me)
                me.runOnFirstFix {
                    map.post {
                        map.controller.setZoom(15.0)
                        me.myLocation?.let { map.controller.animateTo(it) }
                    }
                }
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_RESUME -> { map.onResume(); if (location.granted) me.enableMyLocation() }
                    Lifecycle.Event.ON_PAUSE -> { map.onPause(); me.disableMyLocation() }
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                map.onDetach()
            }
        }
        // Search as you type (Nominatim asks for at most one request per second).
        LaunchedEffect(query) {
            val q = query.trim()
            if (q.length < 3) { results = null; return@LaunchedEffect }
            delay(900)
            loading = true
            results = search(q, map.mapCenter.latitude, map.mapCenter.longitude)
            loading = false
        }

        fun show(p: PlaceResult) {
            selected = p
            searching = false
            map.overlays.removeAll { it is Marker }
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(p.lat, p.lon)
                title = p.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
            map.controller.setZoom(16.0)
            map.controller.animateTo(GeoPoint(p.lat, p.lon))
            map.invalidate()
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView({ map }, Modifier.fillMaxSize())
                if (searching) {
                    Column(Modifier.fillMaxWidth().background(Wp.palette.background).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        WpText(stringResource(R.string.app_maps).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(bottom = 8.dp))
                        WpTextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.maps_search_hint), imeAction = ImeAction.Search)
                        if (loading) LoadingDots(Modifier.padding(top = 8.dp))
                        val list = results
                        if (list != null && list.isEmpty() && !loading) {
                            WpText(stringResource(R.string.maps_no_results), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
                        }
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(list.orEmpty()) { p ->
                                Column(Modifier.fillMaxWidth().wpClickable { show(p) }.padding(vertical = 6.dp)) {
                                    WpText(p.name, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    WpText(p.detail, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                selected?.let { p ->
                    if (!searching) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Wp.palette.chrome).padding(16.dp)) {
                            WpText(p.name, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WpText(p.detail, style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            WpAppBar(
                buttons = listOfNotNull(
                    AppBarButton(Glyphs.MapPin, stringResource(R.string.maps_me)) {
                        if (!location.granted) location.request()
                        else me.myLocation?.let { map.controller.setZoom(16.0); map.controller.animateTo(it) }
                    },
                    AppBarButton(WpIcons.Search, stringResource(R.string.search)) { searching = !searching },
                    selected?.let { p ->
                        AppBarButton(Glyphs.Car, stringResource(R.string.maps_directions)) { directions(context, p) }
                    },
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(if (darkMap) R.string.maps_light else R.string.maps_dark)) { darkMap = !darkMap },
                    AppBarMenuItem(stringResource(R.string.maps_clear)) {
                        selected = null
                        map.overlays.removeAll { it is Marker }
                        map.invalidate()
                    },
                ),
            )
        }
    }
}

/** Hands the destination to the phone's navigation app (Google Maps, Waze, HERE...). */
private fun directions(context: Context, p: PlaceResult) {
    val uri = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(" + Uri.encode(p.name) + ")")
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private suspend fun search(q: String, lat: Double, lon: Double): List<PlaceResult> = withContext(Dispatchers.IO) {
    runCatching {
        val lang = java.util.Locale.getDefault().toLanguageTag()
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=10&accept-language=$lang&q=" + URLEncoder.encode(q, "UTF-8") +
            "&viewbox=${lon - 1},${lat + 1},${lon + 1},${lat - 1}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "ZUNEX launcher (https://github.com/joaogabrielsilvademourap3-spec/zunex)")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val arr = JSONArray(text)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val display = o.optString("display_name")
            PlaceResult(o.optString("name").ifBlank { display.substringBefore(',') }, display, o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
    }.getOrDefault(emptyList())
}
