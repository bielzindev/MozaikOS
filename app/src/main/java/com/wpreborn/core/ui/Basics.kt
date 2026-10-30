@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.wpreborn.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wpreborn.core.motion.tilt
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType

/** Text drawn with the Windows Phone type ramp. Defaults to the foreground brush. */
@Composable
fun WpText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = WpType.Normal,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
) {
    val c = if (color == Color.Unspecified) Wp.palette.foreground else color
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(color = c, textAlign = textAlign) else style.copy(color = c),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

/** A tinted glyph from [com.wpreborn.core.icons.WpIcons]. */
@Composable
fun WpIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, size: Dp = 24.dp) {
    val c = if (tint == Color.Unspecified) Wp.palette.foreground else tint
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(c))
}

/** Click handling without Material ripples; Windows Phone used the tilt effect instead. */
fun Modifier.wpClickable(enabled: Boolean = true, tilt: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(if (tilt) Modifier.tilt(enabled) else Modifier)
        .then(Modifier.clickableNoIndication(enabled, onClick))

private fun Modifier.clickableNoIndication(enabled: Boolean, onClick: () -> Unit): Modifier =
    composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }

/** Click + long press (for context menus) without ripples, with tilt. */
fun Modifier.wpCombinedClickable(onLongClick: (() -> Unit)?, tilt: Boolean = true, onClick: () -> Unit): Modifier =
    composed {
        this.then(if (tilt) Modifier.tilt() else Modifier).combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }

/**
 * The round application bar button: a glyph inside a 2 px circle. When pressed, the circle
 * fills with the foreground color and the glyph inverts.
 */
@Composable
fun RoundButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val fg = if (color == Color.Unspecified) Wp.palette.foreground else color
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (!enabled) Wp.palette.disabled else fg
    Box(
        modifier
            .size(size)
            .border(2.dp, tint, CircleShape)
            .background(if (pressed) tint else Color.Transparent, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = if (pressed) Wp.palette.background else tint, size = size * 0.55f)
    }
}

/**
 * Rectangular button with a 3 px border (PhoneBorderThickness). Pressed: fills with accent.
 */
@Composable
fun WpButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = Wp.palette.foreground
    val accent = Wp.accent
    val border = if (enabled) fg else Wp.palette.disabled
    val bg = when {
        pressed -> accent
        filled -> accent
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> Wp.palette.disabled
        pressed || filled -> Color.White
        else -> fg
    }
    Box(
        modifier
            .tilt(enabled)
            .border(2.5.dp, if (filled || pressed) bg else border)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        WpText(text.lowercase(), style = WpType.Normal.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = textColor)
    }
}

/**
 * Windows Phone 8.1 ToggleSwitch: header above, then the On/Off label, then the switch
 * itself (a rectangular track that fills with the accent color and a solid thumb).
 */
@Composable
fun WpToggle(
    header: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onText: String = "On",
    offText: String = "Off",
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val fg = if (enabled) Wp.palette.foreground else Wp.palette.disabled
    Column(
        modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        WpText(header, style = WpType.Subtle, color = Wp.palette.subtle)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            WpText(if (checked) onText else offText, style = WpType.Large, color = fg, modifier = Modifier.weight(1f))
            ToggleTrack(checked, enabled)
        }
    }
}

@Composable
fun ToggleTrack(checked: Boolean, enabled: Boolean = true) {
    val fg = if (enabled) Wp.palette.foreground else Wp.palette.disabled
    val accent = Wp.accent
    val pos by androidx.compose.animation.core.animateFloatAsState(
        if (checked) 1f else 0f,
        androidx.compose.animation.core.tween(150),
        label = "toggle",
    )
    Box(
        Modifier
            .width(56.dp)
            .height(22.dp)
            .border(2.dp, fg)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.05f + 0.75f * pos)
                .height(14.dp)
                .background(if (checked && enabled) accent else Color.Transparent),
        )
        Box(
            Modifier
                .padding(start = (pos * 36).dp)
                .width(12.dp)
                .height(14.dp)
                .background(fg),
        )
    }
}

/** Square check box with 2 px border, accent fill when checked (WP 8.1). */
@Composable
fun WpCheckBox(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier
            .clickable(remember { MutableInteractionSource() }, null) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .border(2.dp, Wp.palette.foreground)
                .background(if (checked) Wp.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) WpIcon(com.wpreborn.core.icons.WpIcons.Check, tint = Color.White, size = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        WpText(label, style = WpType.Normal)
    }
}

/** Two-line list item: a light title and a subtle (or accent) subtitle. */
@Composable
fun WpListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleAccent: Boolean = false,
    titleStyle: TextStyle = WpType.ExtraLarge,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val base = if (onClick != null || onLongClick != null) modifier.wpCombinedClickable(onLongClick) { onClick?.invoke() } else modifier
    Row(base.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            WpText(title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                WpText(
                    subtitle,
                    style = WpType.Subtle,
                    color = if (subtitleAccent) Wp.accent else Wp.palette.subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A thin horizontal rule used sparingly, e.g. between message dialog sections. */
@Composable
fun WpDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Wp.palette.divider))
}

/** Square accent "letter tile" used as group header in app lists and People. */
@Composable
fun LetterTile(letter: String, modifier: Modifier = Modifier, size: Dp = 48.dp, filled: Boolean = false, enabled: Boolean = true) {
    val accent = Wp.accent
    val color = if (enabled) accent else Wp.palette.raised
    Box(
        modifier
            .size(size)
            .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color)),
        contentAlignment = Alignment.BottomStart,
    ) {
        WpText(
            letter,
            style = WpType.Large.copy(fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.55f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = when {
                !enabled -> Wp.palette.disabled
                filled -> Color.White
                else -> Wp.palette.foreground
            },
            modifier = Modifier.padding(start = size * 0.1f, bottom = size * 0.02f),
        )
    }
}
