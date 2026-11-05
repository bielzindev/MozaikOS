package com.wpreborn.apps.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.prefs.GlanceOptions
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.launcher.lock.Glance
import com.wpreborn.permissions.canDrawOverlays

/**
 * The Lumia "Glance screen" settings page: mode (off / peek / always on), the 15-minute
 * time-out, the colour, night mode (hours and colour, or hidden), the lock screen picture as
 * background, and what it shows: the detailed status (weather, calendar, music), notification
 * icons and the battery.
 */
@Composable
fun GlancePage() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val s = Wp.settings
    val o = s.glanceOptions
    fun set(n: GlanceOptions) = repo.setGlanceOptions(n)
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_settings), stringResource(R.string.glance_page))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            GlancePreview(o)
            Spacer(Modifier.height(12.dp))
            val mode = when {
                !s.glance -> 0
                o.peek -> 1
                else -> 2
            }
            WpListPicker(
                stringResource(R.string.glance_mode),
                listOf(stringResource(R.string.glance_mode_off), stringResource(R.string.glance_mode_peek), stringResource(R.string.glance_mode_always)),
                mode,
            ) { i ->
                repo.setGlance(i != 0)
                if (i != 0) set(o.copy(peek = i == 1))
                if (i != 0 && !context.canDrawOverlays()) {
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
                    }
                }
            }
            WpText(
                stringResource(if (mode == 1) R.string.glance_peek_info else R.string.glance_info),
                style = WpType.Small,
                color = Wp.palette.subtle,
            )
            if (s.glance && !context.canDrawOverlays()) {
                WpText(stringResource(R.string.lock_needs_overlay), style = WpType.Small, color = Wp.accent)
            }
            if (mode == 2) {
                Spacer(Modifier.height(8.dp))
                WpListPicker(
                    stringResource(R.string.glance_timeout),
                    listOf(stringResource(R.string.glance_timeout_15), stringResource(R.string.glance_timeout_never)),
                    if (o.timeout) 0 else 1,
                ) { set(o.copy(timeout = it == 0)) }
            }

            Section(R.string.glance_appearance)
            val colors = listOf("accent", "white", "red", "green", "blue")
            val colorNames = listOf(R.string.glance_color_accent, R.string.glance_color_white, R.string.glance_color_red, R.string.glance_color_green, R.string.glance_color_blue)
            WpListPicker(
                stringResource(R.string.glance_color),
                colorNames.map { stringResource(it) },
                colors.indexOf(o.color).coerceAtLeast(0),
                swatches = colors.map { Glance.color(it, Wp.accent) },
            ) { set(o.copy(color = colors[it])) }
            WpToggle(stringResource(R.string.glance_background), o.background, onText = stringResource(R.string.glance_background_on), offText = off) {
                set(o.copy(background = it))
            }
            if (o.background && s.lockWallpaper == null) {
                WpText(stringResource(R.string.glance_background_none), style = WpType.Small, color = Wp.palette.subtle)
            }

            Section(R.string.glance_night)
            WpToggle(stringResource(R.string.glance_night), o.night, onText = on, offText = off) { set(o.copy(night = it)) }
            if (o.night) {
                val hours = (0 until 48).map { it * 30 }
                val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
                fun label(m: Int): String = timeFormat.format(
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, m / 60)
                        set(java.util.Calendar.MINUTE, m % 60)
                    }.time,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        WpListPicker(stringResource(R.string.glance_night_from), hours.map(::label), hours.indexOf(o.nightFrom).coerceAtLeast(0)) {
                            set(o.copy(nightFrom = hours[it]))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        WpListPicker(stringResource(R.string.glance_night_to), hours.map(::label), hours.indexOf(o.nightTo).coerceAtLeast(0)) {
                            set(o.copy(nightTo = hours[it]))
                        }
                    }
                }
                val nightColors = listOf("red", "green", "blue", "hidden")
                WpListPicker(
                    stringResource(R.string.glance_night_color),
                    listOf(R.string.glance_color_red, R.string.glance_color_green, R.string.glance_color_blue, R.string.glance_night_hidden).map { stringResource(it) },
                    nightColors.indexOf(o.nightColor).coerceAtLeast(0),
                    swatches = nightColors.map { if (it == "hidden") Color.Black else Glance.color(it, Wp.accent) },
                ) { set(o.copy(nightColor = nightColors[it])) }
                WpText(stringResource(R.string.glance_night_info), style = WpType.Small, color = Wp.palette.subtle)
            }

            Section(R.string.glance_content)
            val details = listOf("weather", "calendar", "music", "none")
            WpListPicker(
                stringResource(R.string.glance_detail),
                listOf(R.string.app_weather, R.string.app_calendar, R.string.glance_detail_music, R.string.glance_detail_none).map { stringResource(it) },
                details.indexOf(o.detail).coerceAtLeast(0),
            ) { set(o.copy(detail = details[it])) }
            WpToggle(stringResource(R.string.glance_icons), o.icons, onText = on, offText = off) { set(o.copy(icons = it)) }
            WpToggle(stringResource(R.string.glance_battery), o.battery, onText = stringResource(R.string.glance_battery_always), offText = stringResource(R.string.glance_battery_charging)) {
                set(o.copy(battery = it))
            }
            Spacer(Modifier.height(16.dp))
            WpButton(stringResource(R.string.glance_try)) {
                runCatching {
                    context.startActivity(Intent(context, com.wpreborn.launcher.lock.GlanceActivity::class.java).putExtra(com.wpreborn.launcher.lock.GlanceActivity.EXTRA_TRY, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    WpText(
        stringResource(title).lowercase(),
        style = WpType.Large.copy(fontWeight = FontWeight.SemiBold),
        color = Wp.accent,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

/** A small black phone screen with the clock in the chosen colour. */
@Composable
private fun GlancePreview(o: GlanceOptions) {
    val color = Glance.color(o.color, Wp.accent)
    Box(
        Modifier
            .padding(top = 8.dp)
            .size(width = 120.dp, height = 200.dp)
            .border(2.dp, Wp.palette.subtle)
            .background(Color.Black)
            .padding(10.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            WpText("10:24", style = WpType.Header.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light), color = color)
            WpText(android.text.format.DateFormat.format("EEEE", System.currentTimeMillis()).toString(), style = WpType.Small, color = color)
        }
    }
}

/**
 * The Windows Phone 8.1 ListPicker: the header, then a box with the chosen value; a tap
 * opens the list in place (chosen item in the accent colour), a tap on an item closes it.
 */
@Composable
fun WpListPicker(
    header: String,
    items: List<String>,
    selected: Int,
    swatches: List<Color>? = null,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        WpText(header, style = WpType.Subtle, color = Wp.palette.subtle)
        Column(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .border(2.dp, if (open) Wp.accent else Wp.palette.foreground)
                .background(if (open) Wp.palette.background else Color.Transparent)
                .animateContentSize(),
        ) {
            if (!open) {
                PickerRow(items.getOrElse(selected) { "" }, swatches?.getOrNull(selected), Wp.palette.foreground) { open = true }
            } else {
                items.forEachIndexed { i, label ->
                    PickerRow(label, swatches?.getOrNull(i), if (i == selected) Wp.accent else Wp.palette.foreground) {
                        open = false
                        if (i != selected) onSelect(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, swatch: Color?, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().wpClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(20.dp).background(swatch).border(1.dp, Wp.palette.subtle))
            Spacer(Modifier.width(12.dp))
        }
        WpText(label, style = WpType.Normal, color = color)
    }
}
