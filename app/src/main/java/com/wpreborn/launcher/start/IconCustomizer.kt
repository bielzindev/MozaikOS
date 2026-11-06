package com.wpreborn.launcher.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.IconPack
import com.wpreborn.data.apps.IconPrefs
import com.wpreborn.data.apps.IconTint

/**
 * Per-app icon settings, opened from a tile in Start's edit mode: the icon pack (Metro or the
 * original Android icon), independent for each app. The tile's colour stays on the palette
 * button. Changes apply at once and are saved for that app.
 */
@Composable
fun IconCustomizer(app: AppEntry, tileColor: Color?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IconPrefs.get(context) }
    val custom = rememberIconCustom(app)
    BackHandler(onBack = onDismiss)
    Column(
        Modifier
            .fillMaxSize()
            .background(Wp.palette.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        WpText(stringResource(R.string.icon_title, app.label).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))

        // Live preview on the tile's colour.
        Box(
            Modifier.size(120.dp).background(tileColor ?: rememberBrandColor(app) ?: Wp.accent),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(app, if (custom.usesMetro()) 56.dp else 72.dp)
        }

        Section(stringResource(R.string.icon_pack))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.icon_pack_default), custom.pack == IconPack.Default, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Default)) }
            Choice(stringResource(R.string.icons_metro), custom.pack == IconPack.Metro, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Metro)) }
            Choice(stringResource(R.string.icons_original), custom.pack == IconPack.Original, Modifier.weight(1f)) { prefs.set(app.key, custom.copy(pack = IconPack.Original)) }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WpButton(stringResource(R.string.icon_done), onClick = onDismiss)
            WpButton(stringResource(R.string.icon_reset)) { prefs.set(app.key, com.wpreborn.data.apps.IconCustom()) }
        }
    }
}

@Composable
private fun Section(title: String) {
    WpText(title.uppercase(), style = WpType.AppTitle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .border(2.dp, if (selected) Wp.accent else Wp.palette.foreground)
            .background(if (selected) Wp.accent else Color.Transparent)
            .wpClickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        WpText(label, style = WpType.Small, color = if (selected) Color.White else Wp.palette.foreground, maxLines = 2)
    }
}
