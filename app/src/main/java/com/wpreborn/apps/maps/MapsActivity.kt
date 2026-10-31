package com.wpreborn.apps.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Map styles from OpenFreeMap (free, no key; © OpenMapTiles © OpenStreetMap contributors). */
private enum class MapStyle(val url: String, val label: Int) {
    Streets("https://tiles.openfreemap.org/styles/liberty", R.string.maps_style_streets),
    Light("https://tiles.openfreemap.org/styles/positron", R.string.maps_style_light),
    Bright("https://tiles.openfreemap.org/styles/bright", R.string.maps_style_bright),
    Dark("https://tiles.openfreemap.org/styles/dark", R.string.maps_style_dark),
}

/**
 * Maps, in the spirit of HERE Maps on Windows Phone 8.1 with what Google Maps users expect:
 * sharp vector maps that rotate and tilt (3D buildings), your location with heading,
 * search with suggestions near you, press and hold anywhere for the address, place cards,
 * routes by car, bike or on foot with time, distance and turn-by-turn steps drawn on the
 * map, map styles (dark in the dark theme), and handing the route to a navigation app.
 */
class MapsActivity : WpActivity() {

    @SuppressLint("MissingPermission")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val dark = Wp.palette.isDark
        val accent = Wp.accent
        val location = rememberPermissionState(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        var style by remember { mutableStateOf(if (dark) MapStyle.Dark else MapStyle.Streets) }
        var map by remember { mutableStateOf<MapLibreMap?>(null) }
        var styleReady by remember { mutableStateOf(0) }
        var tilted by remember { mutableStateOf(false) }

        var searching by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Place>?>(null) }
        var loading by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Place?>(null) }

        var routing by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(TravelMode.Car) }
        var route by remember { mutableStateOf<Route?>(null) }
        var routeError by remember { mutableStateOf(false) }
        var showSteps by remember { mutableStateOf(false) }

        val mapView = remember {
            MapLibre.getInstance(context)
            MapView(context).apply { onCreate(null) }
        }

        fun myLocation(): LatLng? = runCatching {
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
        }.getOrNull()

        fun center(): LatLng = map?.cameraPosition?.target ?: LatLng(-14.2, -51.9)

        // Map + style.
        LaunchedEffect(Unit) {
            mapView.getMapAsync { m ->
                m.uiSettings.isCompassEnabled = true
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = true
                m.cameraPosition = CameraPosition.Builder().target(LatLng(-14.2, -51.9)).zoom(3.0).build()
                m.addOnMapLongClickListener { p ->
                    // Press and hold: drop a pin and look the address up.
                    searching = false
                    routing = false
                    route = null
                    selected = Place(context.getString(R.string.maps_dropped_pin), "", p.latitude, p.longitude)
                    scope.launch { reverse(p.latitude, p.longitude)?.let { found -> if (selected?.lat == p.latitude) selected = found.copy(lat = p.latitude, lon = p.longitude) } }
                    true
                }
                map = m
            }
            mapView.addOnDidFailLoadingMapListener {
                // A style that can't load falls back to the standard streets.
                if (style != MapStyle.Streets) style = MapStyle.Streets
            }
        }
        LaunchedEffect(map, style) {
            val m = map ?: return@LaunchedEffect
            m.setStyle(Style.Builder().fromUri(style.url)) { s ->
                s.addSource(GeoJsonSource(SRC_ROUTE))
                s.addLayer(
                    LineLayer(LAYER_ROUTE_CASING, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor(android.graphics.Color.WHITE),
                        PropertyFactory.lineWidth(9f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor(accent.toArgb()),
                        PropertyFactory.lineWidth(6f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addSource(GeoJsonSource(SRC_PIN))
                s.addLayer(
                    CircleLayer(LAYER_PIN, SRC_PIN).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor(accent.toArgb()),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                if (location.granted) {
                    runCatching {
                        m.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(context, s).build())
                        m.locationComponent.isLocationComponentEnabled = true
                        m.locationComponent.renderMode = RenderMode.COMPASS
                    }
                }
                styleReady++
            }
        }
        // First fix: go to the user's location once.
        LaunchedEffect(styleReady, location.granted) {
            val m = map ?: return@LaunchedEffect
            if (!location.granted || styleReady == 0) return@LaunchedEffect
            runCatching {
                if (!m.locationComponent.isLocationComponentActivated) {
                    m.style?.let { s ->
                        m.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(context, s).build())
                        m.locationComponent.isLocationComponentEnabled = true
                        m.locationComponent.renderMode = RenderMode.COMPASS
                    }
                }
            }
            repeat(20) {
                myLocation()?.let { me ->
                    if (m.cameraPosition.zoom < 6) m.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15.0), 900)
                    return@LaunchedEffect
                }
                delay(500)
            }
        }
        // Pin and route on the map.
        LaunchedEffect(selected, styleReady) {
            val s = map?.style ?: return@LaunchedEffect
            val p = selected
            s.getSourceAs<GeoJsonSource>(SRC_PIN)?.setGeoJson(
                if (p == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))),
            )
            if (p != null && !routing) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), maxOf(map?.cameraPosition?.zoom ?: 15.0, 15.0)), 700)
        }
        LaunchedEffect(route, styleReady) {
            val s = map?.style ?: return@LaunchedEffect
            val r = route
            s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
                if (r == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.second, it.first) }))),
            )
            if (r != null && r.points.size > 1) {
                val b = LatLngBounds.Builder().apply { r.points.forEach { include(LatLng(it.first, it.second)) } }.build()
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 120), 900)
            }
        }
        // Route whenever the destination or the travel mode changes.
        LaunchedEffect(routing, mode, selected) {
            val dest = selected
            if (!routing || dest == null) return@LaunchedEffect
            val from = myLocation()
            if (from == null) {
                if (!location.granted) location.request()
                routeError = true
                return@LaunchedEffect
            }
            loading = true
            routeError = false
            route = route(mode, from.latitude, from.longitude, dest.lat, dest.lon)
            routeError = route == null
            loading = false
        }
        // Search as you type, near the map centre (debounced).
        LaunchedEffect(query) {
            val q = query.trim()
            if (q.length < 3) { results = null; return@LaunchedEffect }
            delay(450)
            loading = true
            val c = center()
            results = search(q, c.latitude, c.longitude)
            loading = false
        }
        // MapView lifecycle.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                mapView.onDestroy()
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView({ mapView }, Modifier.fillMaxSize())

                // Search panel.
                if (searching) {
                    Column(Modifier.fillMaxWidth().background(Wp.palette.background).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        WpText(stringResource(R.string.app_maps).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(bottom = 8.dp))
                        WpTextBox(query, { query = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.maps_search_hint), imeAction = ImeAction.Search)
                        if (loading) LoadingDots(Modifier.padding(top = 8.dp))
                        val list = results
                        if (list != null && list.isEmpty() && !loading) {
                            WpText(stringResource(R.string.maps_no_results), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
                        }
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(list.orEmpty()) { p ->
                                Column(
                                    Modifier.fillMaxWidth().wpClickable {
                                        selected = p
                                        searching = false
                                        route = null
                                        routing = false
                                    }.padding(vertical = 7.dp),
                                ) {
                                    WpText(p.name, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val dist = myLocation()?.let { formatDistance(distanceM(it.latitude, it.longitude, p.lat, p.lon)) }
                                    WpText(listOfNotNull(dist, p.detail.ifBlank { null }).joinToString("  ·  "), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Directions panel.
                if (routing && selected != null) {
                    Column(Modifier.fillMaxWidth().background(Wp.palette.background).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        WpText(stringResource(R.string.maps_directions).uppercase(), style = WpType.AppTitle)
                        WpText(stringResource(R.string.maps_from_here_to, selected!!.name), style = WpType.Large, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TravelMode.entries.forEach { m ->
                                Box(
                                    Modifier
                                        .border(2.dp, if (mode == m) Wp.accent else Wp.palette.foreground)
                                        .background(if (mode == m) Wp.accent else Color.Transparent)
                                        .wpClickable { mode = m }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    WpText(stringResource(m.label), style = WpType.Normal, color = if (mode == m) Color.White else Wp.palette.foreground)
                                }
                            }
                        }
                        val r = route
                        when {
                            loading -> LoadingDots(Modifier.padding(top = 10.dp))
                            routeError -> WpText(stringResource(R.string.maps_route_error), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 10.dp))
                            r != null -> {
                                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                                    WpText(formatDuration(context, r.durationS), style = WpType.ExtraLarge.copy(fontWeight = FontWeight.SemiBold), color = Wp.accent)
                                    Spacer(Modifier.width(12.dp))
                                    WpText(formatDistance(r.distanceM), style = WpType.Large, color = Wp.palette.subtle)
                                }
                                WpText(
                                    stringResource(if (showSteps) R.string.maps_hide_steps else R.string.maps_show_steps, r.steps.size),
                                    style = WpType.Normal,
                                    color = Wp.accent,
                                    modifier = Modifier.wpClickable { showSteps = !showSteps }.padding(vertical = 6.dp),
                                )
                                if (showSteps) {
                                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                        items(r.steps) { st ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                                WpText(st.text(context), style = WpType.Normal, modifier = Modifier.weight(1f))
                                                if (st.distanceM > 0) WpText(formatDistance(st.distanceM), style = WpType.Small, color = Wp.palette.subtle)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Place card.
                selected?.let { p ->
                    if (!searching && !routing) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Wp.palette.chrome).padding(16.dp)) {
                            WpText(p.name, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.detail.isNotBlank()) WpText(p.detail, style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            myLocation()?.let { me ->
                                WpText(formatDistance(distanceM(me.latitude, me.longitude, p.lat, p.lon)), style = WpType.Small, color = Wp.accent)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WpButton(stringResource(R.string.maps_directions)) { routing = true; showSteps = false }
                                WpButton(stringResource(R.string.share)) { share(context, p) }
                            }
                        }
                    }
                }
            }
            WpAppBar(
                buttons = listOfNotNull(
                    AppBarButton(Glyphs.MapPin, stringResource(R.string.maps_me)) {
                        if (!location.granted) {
                            location.request()
                        } else {
                            myLocation()?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.0), 800) }
                            runCatching { map?.locationComponent?.cameraMode = CameraMode.TRACKING }
                        }
                    },
                    AppBarButton(WpIcons.Search, stringResource(R.string.search)) { searching = !searching; routing = false },
                    selected?.let { AppBarButton(Glyphs.Car, stringResource(R.string.maps_directions)) { routing = !routing; searching = false } },
                    if (routing && selected != null) AppBarButton(WpIcons.ArrowRightCircle, stringResource(R.string.maps_navigate)) { navigate(context, selected!!, mode) } else null,
                ),
                menuItems = MapStyle.entries.map { st ->
                    AppBarMenuItem(stringResource(st.label) + if (st == style) "  ✓" else "") { style = st }
                } + listOf(
                    AppBarMenuItem(stringResource(if (tilted) R.string.maps_2d else R.string.maps_3d)) {
                        tilted = !tilted
                        map?.let { m -> m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).tilt(if (tilted) 60.0 else 0.0).build()), 600) }
                    },
                    AppBarMenuItem(stringResource(R.string.maps_clear)) {
                        selected = null
                        route = null
                        routing = false
                    },
                ),
            )
        }
    }

    private companion object {
        const val SRC_ROUTE = "zunex-route"
        const val SRC_PIN = "zunex-pin"
        const val LAYER_ROUTE = "zunex-route-line"
        const val LAYER_ROUTE_CASING = "zunex-route-casing"
        const val LAYER_PIN = "zunex-pin-dot"
    }
}

private fun share(context: Context, p: Place) {
    val text = listOf(p.name, p.detail).filter { it.isNotBlank() }.joinToString("\n") + "\nhttps://www.openstreetmap.org/?mlat=${p.lat}&mlon=${p.lon}#map=17/${p.lat}/${p.lon}"
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Turn-by-turn in the phone's navigation app (Google Maps, Waze, HERE...). */
private fun navigate(context: Context, p: Place, mode: TravelMode) {
    val google = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${p.lat},${p.lon}&mode=${mode.googleMode}"))
    val any = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(" + Uri.encode(p.name) + ")"))
    val intent = if (google.resolveActivity(context.packageManager) != null) google else Intent.createChooser(any, null)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
