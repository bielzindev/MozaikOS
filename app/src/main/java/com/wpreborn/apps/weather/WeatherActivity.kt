package com.wpreborn.apps.weather

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.LocalPalette
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpPalette
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.PanoramaSection
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpPanorama
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.weather.Place
import com.wpreborn.data.weather.Weather
import com.wpreborn.data.weather.WeatherRepository
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

private sealed interface WeatherPage {
    data class Hub(val placeId: String?) : WeatherPage
    data object Places : WeatherPage
}

/**
 * Weather, after MSN Weather on Windows Phone 8.1: a panorama over a sky-coloured background
 * with the current conditions, hourly and 10-day forecasts and details, plus a list of
 * places. Forecasts come from Open-Meteo; the first place feeds the live tile.
 */
class WeatherActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val repo = remember { WeatherRepository.get(context) }
        val places by repo.places.collectAsState()
        val nav = rememberNavigator<WeatherPage>(if (places.isEmpty()) WeatherPage.Places else WeatherPage.Hub(null))
        LaunchedEffect(Unit) { repo.refreshIfStale(15 * 60_000L) }
        WpNavHost(nav) { page ->
            when (page) {
                is WeatherPage.Hub -> WeatherHub(repo, page.placeId, onPlaces = { nav.navigate(WeatherPage.Places) })
                WeatherPage.Places -> PlacesPage(repo, onOpen = { p ->
                    nav.pop()
                    nav.navigate(WeatherPage.Hub(p.id))
                })
            }
        }
    }
}

private fun deg(c: Double, f: Boolean) = WeatherRepository.toUnit(c, f).roundToInt().toString() + "°"

@Composable
private fun WeatherHub(repo: WeatherRepository, placeId: String?, onPlaces: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by repo.places.collectAsState()
    val forecasts by repo.forecasts.collectAsState()
    val f by repo.fahrenheit.collectAsState()
    val place = places.firstOrNull { it.id == placeId } ?: places.firstOrNull()
    val w = place?.let { forecasts[it.id] }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun reload() {
        val p = place ?: return
        scope.launch {
            loading = true
            failed = !repo.refresh(p)
            loading = false
        }
    }
    LaunchedEffect(place?.id) { if (place != null && w == null) reload() }

    val colors = w?.let { skyColors(it.code, it.isDay) } ?: listOf(Color(0xFF1E88E5), Color(0xFF0B4F9C))
    // Weather is always light text on the sky, whatever the theme.
    CompositionLocalProvider(LocalPalette provides WpPalette.Dark) {
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        // The living sky: follows the current conditions and the time of day.
        if (w != null) {
            val today = w.days.firstOrNull()
            WeatherScene(w.code, w.isDay, today?.sunrise, today?.sunset, Modifier.fillMaxSize())
            // Keeps the text readable over bright skies.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (w == null) {
                    Column(Modifier.padding(20.dp)) {
                        WpText((place?.name ?: stringResource(R.string.app_weather)).lowercase(), style = WpType.Header, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        if (loading || !failed) LoadingDots() else WpText(stringResource(R.string.weather_offline), style = WpType.Large, color = Color.White)
                    }
                } else {
                    WpPanorama(
                        title = w.place.name.lowercase(),
                        sections = listOf(
                            PanoramaSection(null) { NowSection(w, f, loading, failed) },
                            PanoramaSection(stringResource(R.string.weather_hourly)) { HourlySection(w, f) },
                            PanoramaSection(stringResource(R.string.weather_daily)) { DailySection(w, f) },
                            PanoramaSection(stringResource(R.string.weather_details)) { DetailsSection(w, f) },
                        ),
                    )
                }
            }
            WpAppBar(
                background = Color.Black.copy(alpha = 0.35f),
                buttons = listOf(
                    AppBarButton(WpIcons.Refresh, stringResource(R.string.refresh)) { reload() },
                    AppBarButton(WpIcons.Globe, stringResource(R.string.weather_places), onClick = onPlaces),
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(if (f) R.string.weather_use_celsius else R.string.weather_use_fahrenheit)) { repo.setFahrenheit(!f) },
                ),
            )
        }
      }
    }
}

@Composable
private fun NowSection(w: Weather, f: Boolean, loading: Boolean, failed: Boolean) {
    val context = LocalContext.current
    val today = w.days.firstOrNull()
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.slideInStaggered(0)) {
            WpText(deg(w.temp, f), style = WpType.PanoramaTitle.copy(fontSize = 110.sp, lineHeight = 116.sp), color = Color.White)
            Spacer(Modifier.width(12.dp))
            WeatherIcon(w.code, w.isDay, 84.dp)
        }
        WpText(stringResource(Sky.of(w.code).label), style = WpType.ExtraLarge, color = Color.White, modifier = Modifier.slideInStaggered(1))
        if (today != null) {
            WpText(
                stringResource(R.string.weather_high_low, deg(today.max, f), deg(today.min, f)),
                style = WpType.Large,
                color = Color.White,
                modifier = Modifier.slideInStaggered(2).padding(top = 4.dp),
            )
        }
        WpText(stringResource(R.string.weather_feels_like, deg(w.feelsLike, f)), style = WpType.Large, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.slideInStaggered(3))
        Spacer(Modifier.height(18.dp))
        // The next few hours at a glance.
        Row(Modifier.fillMaxWidth().slideInStaggered(4), horizontalArrangement = Arrangement.SpaceBetween) {
            w.hours.drop(1).filterIndexed { i, _ -> i % 3 == 0 }.take(4).forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WpText(DateFormat.getTimeFormat(context).format(Date(h.time)).replace(":00", "h"), style = WpType.Small, color = Color.White.copy(alpha = 0.8f))
                    WeatherIcon(h.code, h.isDay, 30.dp, Modifier.padding(vertical = 4.dp))
                    WpText(deg(h.temp, f), style = WpType.Normal, color = Color.White)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        WpText(
            when {
                loading -> stringResource(R.string.weather_updating)
                failed -> stringResource(R.string.weather_offline_cached, DateFormat.getTimeFormat(context).format(Date(w.updated)))
                else -> stringResource(R.string.weather_updated, DateFormat.getTimeFormat(context).format(Date(w.updated)))
            },
            style = WpType.Small,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun HourlySection(w: Weather, f: Boolean) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 12.dp)) {
        itemsIndexed(w.hours) { i, h ->
            Row(Modifier.fillMaxWidth().slideInStaggered(i).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                WpText(DateFormat.getTimeFormat(context).format(Date(h.time)), style = WpType.Large, color = Color.White, maxLines = 1, softWrap = false, modifier = Modifier.width(124.dp))
                WeatherIcon(h.code, h.isDay, 32.dp)
                Spacer(Modifier.width(14.dp))
                WpText(deg(h.temp, f), style = WpType.Large, color = Color.White, modifier = Modifier.weight(1f))
                if (h.precipChance > 0) WpText("${h.precipChance}%", style = WpType.Normal, color = Color(0xFF9FD3FF))
            }
        }
    }
}

@Composable
private fun DailySection(w: Weather, f: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 12.dp)) {
        itemsIndexed(w.days) { i, d ->
            Row(Modifier.fillMaxWidth().slideInStaggered(i).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(110.dp)) {
                    WpText(
                        if (i == 0) stringResource(R.string.weather_today) else DateFormat.format("EEEE", d.date).toString().lowercase(),
                        style = WpType.Large,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WpText(DateFormat.format("d MMM", d.date).toString(), style = WpType.Small, color = Color.White.copy(alpha = 0.7f))
                }
                WeatherIcon(d.code, true, 36.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        WpText(deg(d.max, f), style = WpType.Large.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        WpText(deg(d.min, f), style = WpType.Large, color = Color.White.copy(alpha = 0.65f))
                    }
                    if (d.precipChance > 0) WpText(stringResource(R.string.weather_precip_chance, d.precipChance), style = WpType.Small, color = Color(0xFF9FD3FF))
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(w: Weather, f: Boolean) {
    val context = LocalContext.current
    val today = w.days.firstOrNull()
    val time = { t: Long -> DateFormat.getTimeFormat(context).format(Date(t)) }
    val windDirs = stringResource(R.string.weather_wind_dirs).split(",")
    val windDir = windDirs.getOrElse(((w.windDir % 360) / 45.0).roundToInt() % 8) { "" }
    val items = buildList {
        add(stringResource(R.string.weather_wind) to "${if (f) (w.windKmh / 1.609).roundToInt().toString() + " mph" else w.windKmh.roundToInt().toString() + " km/h"} $windDir")
        add(stringResource(R.string.weather_humidity) to "${w.humidity}%")
        add(stringResource(R.string.weather_pressure) to "${w.pressure.roundToInt()} hPa")
        add(stringResource(R.string.weather_precipitation) to "%.1f mm".format(w.precipitation))
        if (today != null) {
            add(stringResource(R.string.weather_uv) to today.uv.roundToInt().toString())
            add(stringResource(R.string.weather_sunrise) to time(today.sunrise))
            add(stringResource(R.string.weather_sunset) to time(today.sunset))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 12.dp, bottom = 12.dp)) {
        items.forEachIndexed { i, (label, value) ->
            Column(Modifier.slideInStaggered(i).padding(vertical = 6.dp)) {
                WpText(label, style = WpType.Subtle, color = Color.White.copy(alpha = 0.7f))
                WpText(value, style = WpType.ExtraLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlacesPage(repo: WeatherRepository, onOpen: (Place) -> Unit) {
    val scope = rememberCoroutineScope()
    val places by repo.places.collectAsState()
    val forecasts by repo.forecasts.collectAsState()
    val f by repo.fahrenheit.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Place?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    val location = rememberPermissionState(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    var wantLocation by remember { mutableStateOf(false) }
    LaunchedEffect(location.granted, wantLocation) {
        if (wantLocation && location.granted) {
            repo.addCurrentLocation()
            wantLocation = false
        }
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        results = repo.search(query.trim())
        searching = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WpText(stringResource(R.string.app_weather).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
            WpText(stringResource(R.string.weather_places), style = WpType.Header, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            WpTextBox(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = stringResource(R.string.weather_search_hint),
                imeAction = ImeAction.Search,
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                val res = results
                if (res != null) {
                    if (searching) item { LoadingDots(Modifier.padding(vertical = 8.dp)) }
                    if (!searching && res.isEmpty()) item { WpText(stringResource(R.string.weather_no_results), style = WpType.Large, color = Wp.palette.subtle) }
                    itemsIndexed(res, key = { _, p -> "r" + p.id }) { i, p ->
                        Column(
                            Modifier.fillMaxWidth().slideInStaggered(i).wpClickable {
                                repo.addPlace(p)
                                query = ""
                                onOpen(p)
                            }.padding(vertical = 6.dp),
                        ) {
                            WpText(p.name, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WpText(p.region, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    if (places.none { it.current }) {
                        item {
                            WpText(
                                stringResource(R.string.weather_use_location),
                                style = WpType.Large,
                                color = Wp.accent,
                                modifier = Modifier.wpClickable {
                                    wantLocation = true
                                    if (!location.granted) location.request()
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                    if (places.isEmpty()) item {
                        WpText(stringResource(R.string.weather_places_empty), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
                    }
                    itemsIndexed(places, key = { _, p -> p.id }) { i, p ->
                        val w = forecasts[p.id]
                        var bottom by remember { mutableFloatStateOf(0f) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .slideInStaggered(i)
                                .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                                .wpCombinedClickable(onLongClick = { menuY = bottom; menu = p }) { onOpen(p) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                WpText(p.name.lowercase(), style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                WpText(
                                    listOfNotNull(
                                        if (i == 0) stringResource(R.string.weather_on_tile) else null,
                                        if (p.current) stringResource(R.string.weather_current_location) else p.region.ifBlank { null },
                                    ).joinToString(" · "),
                                    style = WpType.Subtle,
                                    color = if (i == 0) Wp.accent else Wp.palette.subtle,
                                    maxLines = 1,
                                )
                            }
                            if (w != null) {
                                Box(Modifier.background(Wp.accent).padding(6.dp)) { WeatherIcon(w.code, w.isDay, 28.dp) }
                                Spacer(Modifier.width(10.dp))
                                WpText(deg(w.temp, f), style = WpType.ExtraLarge)
                            }
                        }
                    }
                }
            }
        }
        menu?.let { p ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.weather_set_tile)) { repo.makePrimary(p) },
                    ContextMenuItem(stringResource(R.string.delete)) { repo.removePlace(p) },
                ),
                anchorY = menuY,
                onDismiss = { menu = null },
            )
        }
    }
}
