package com.wpreborn.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.settings.SettingsActivity
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.motion.turnstile
import com.wpreborn.core.motion.TurnstileState
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpListItem
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.prefs.SettingsRepository

/**
 * Personalisation hub opened by pressing and holding an empty part of Start: theme, accent,
 * icon pack, tile density, live tiles and the Start background, without leaving the launcher.
 */
@Composable
fun PersonalizeScreen(turnstile: TurnstileState, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings = Wp.settings
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            repo.setStartBackground(uri.toString())
        }
    }
    BackHandler(onBack = onClose)
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)

    Column(
        Modifier
            .fillMaxSize()
            .turnstile(turnstile)
            .background(Wp.palette.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
    ) {
        WpText("ZUNEX", style = WpType.AppTitle)
        WpText(stringResource(R.string.personalize_title), style = WpType.Header, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))

        Section(stringResource(R.string.set_background), 1)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.slideInStaggered(1)) {
            Choice(stringResource(R.string.theme_dark), settings.darkTheme) { repo.setDarkTheme(true) }
            Choice(stringResource(R.string.theme_light), !settings.darkTheme) { repo.setDarkTheme(false) }
        }

        Section(stringResource(R.string.set_accent), 2)
        AccentColor.entries.chunked(5).forEachIndexed { row, colors ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).slideInStaggered(2 + row), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(c.color)
                            .then(if (settings.accent == c) Modifier.border(3.dp, Wp.palette.foreground) else Modifier)
                            .wpClickable { repo.setAccent(c) },
                    )
                }
            }
        }

        Section(stringResource(R.string.icons_title), 7)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.slideInStaggered(7)) {
            IconChoice(stringResource(R.string.icons_metro), settings.metroIcons, metro = true) { repo.setMetroIcons(true) }
            IconChoice(stringResource(R.string.icons_original), !settings.metroIcons, metro = false) { repo.setMetroIcons(false) }
        }

        Spacer(Modifier.height(8.dp))
        if (settings.metroIcons) {
            WpToggle(stringResource(R.string.set_brand_colors), settings.brandColors, onText = on, offText = off) { repo.setBrandColors(it) }
        }
        WpToggle(stringResource(R.string.set_wp_status_bar), settings.wpStatusBar, onText = on, offText = off) { repo.setWpStatusBar(it) }
        WpToggle(stringResource(R.string.set_show_duplicates), settings.showDuplicates, onText = on, offText = off) { repo.setShowDuplicates(it) }
        WpToggle(stringResource(R.string.set_more_tiles), settings.moreTiles, onText = on, offText = off) { repo.setMoreTiles(it) }
        WpToggle(stringResource(R.string.set_live_tiles), settings.liveTiles, onText = on, offText = off) { repo.setLiveTiles(it) }
        if (!settings.metroIcons) {
            WpToggle(stringResource(R.string.set_icons_accent), settings.iconsOnAccent, onText = on, offText = off) { repo.setIconsOnAccent(it) }
        }

        Section(stringResource(R.string.set_start_background), 9)
        WpText(stringResource(R.string.set_start_background_hint), style = WpType.Small, color = Wp.palette.subtle)
        settings.startBackground?.let {
            Thumbnail(android.net.Uri.parse(it), Modifier.padding(vertical = 8.dp).size(110.dp), sizePx = 330)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            WpButton(stringResource(R.string.choose_photo)) {
                pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (settings.startBackground != null) WpButton(stringResource(R.string.remove)) { repo.setStartBackground(null) }
        }

        Spacer(Modifier.height(20.dp))
        WpListItem(stringResource(R.string.set_lock_screen), titleStyle = WpType.Large) {
            context.startActivity(Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, "lock"))
        }
        WpListItem(stringResource(R.string.ac_all_settings), titleStyle = WpType.Large) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }
}

@Composable
private fun Section(title: String, index: Int) {
    WpText(
        title,
        style = WpType.Subtle,
        color = Wp.palette.subtle,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).slideInStaggered(index),
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(130.dp)
            .height(44.dp)
            .border(2.dp, if (selected) Wp.accent else Wp.palette.foreground)
            .background(if (selected) Wp.accent else Color.Transparent)
            .wpClickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        WpText(label, color = if (selected) Color.White else Wp.palette.foreground, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Preview of an icon style: three small tiles drawn with that style. */
@Composable
private fun IconChoice(label: String, selected: Boolean, metro: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .border(2.dp, if (selected) Wp.accent else Wp.palette.divider)
            .wpClickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(WpIcons.Globe, WpIcons.Mail, Glyphs.Chat).forEachIndexed { i, glyph ->
                Box(Modifier.size(38.dp).background(Wp.accent), contentAlignment = Alignment.Center) {
                    if (metro) {
                        WpIcon(glyph, tint = Color.White, size = 22.dp)
                    } else {
                        // Original icons are colourful app artwork; hint at that with colour blocks.
                        val c = listOf(Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))[i]
                        Box(Modifier.size(24.dp).background(Color.White).padding(5.dp).background(c))
                    }
                }
            }
        }
        WpText(label, style = WpType.Small, modifier = Modifier.padding(top = 6.dp))
    }
}
