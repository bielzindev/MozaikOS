package com.wpreborn.core.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wpreborn.R

/**
 * The 20 accent colors shipped with Windows Phone 8 / 8.1, with their original hex values.
 */
enum class AccentColor(val color: Color, @StringRes val label: Int) {
    Lime(Color(0xFFA4C400), R.string.accent_lime),
    Green(Color(0xFF60A917), R.string.accent_green),
    Emerald(Color(0xFF008A00), R.string.accent_emerald),
    Teal(Color(0xFF00ABA9), R.string.accent_teal),
    Cyan(Color(0xFF1BA1E2), R.string.accent_cyan),
    Cobalt(Color(0xFF0050EF), R.string.accent_cobalt),
    Indigo(Color(0xFF6A00FF), R.string.accent_indigo),
    Violet(Color(0xFFAA00FF), R.string.accent_violet),
    Pink(Color(0xFFF472D0), R.string.accent_pink),
    Magenta(Color(0xFFD80073), R.string.accent_magenta),
    Crimson(Color(0xFFA20025), R.string.accent_crimson),
    Red(Color(0xFFE51400), R.string.accent_red),
    Orange(Color(0xFFFA6800), R.string.accent_orange),
    Amber(Color(0xFFF0A30A), R.string.accent_amber),
    Yellow(Color(0xFFE3C800), R.string.accent_yellow),
    Brown(Color(0xFF825A2C), R.string.accent_brown),
    Olive(Color(0xFF6D8764), R.string.accent_olive),
    Steel(Color(0xFF647687), R.string.accent_steel),
    Mauve(Color(0xFF76608A), R.string.accent_mauve),
    Taupe(Color(0xFF87794E), R.string.accent_taupe);

    companion object {
        fun fromName(name: String?): AccentColor = entries.firstOrNull { it.name == name } ?: Cobalt
    }
}

/**
 * Theme brushes, named after the PhoneXxxBrush resources of the Windows Phone SDK.
 */
data class WpPalette(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    /** PhoneSubtleBrush: secondary text. */
    val subtle: Color,
    /** PhoneChromeBrush: application bar, context menus, message boxes. */
    val chrome: Color,
    /** PhoneDisabledBrush. */
    val disabled: Color,
    /** Inactive pivot headers. */
    val inactive: Color,
    /** PhoneTextBoxBrush (unfocused text box). */
    val textBox: Color,
    /** PhoneTextBoxEditBackgroundBrush (focused text box). */
    val textBoxFocused: Color,
    /** Hairline separators. */
    val divider: Color,
    /** Background of keypad keys and similar raised controls. */
    val raised: Color,
) {
    companion object {
        val Dark = WpPalette(
            isDark = true,
            background = Color.Black,
            foreground = Color.White,
            subtle = Color(0x99FFFFFF),
            chrome = Color(0xFF1F1F1F),
            disabled = Color(0x66FFFFFF),
            inactive = Color(0x59FFFFFF),
            textBox = Color(0xBFFFFFFF),
            textBoxFocused = Color.White,
            divider = Color(0x33FFFFFF),
            raised = Color(0xFF262626),
        )
        val Light = WpPalette(
            isDark = false,
            background = Color.White,
            foreground = Color.Black,
            subtle = Color(0x66000000),
            chrome = Color(0xFFDDDDDD),
            disabled = Color(0x4D000000),
            inactive = Color(0x4D000000),
            textBox = Color(0x26000000),
            textBoxFocused = Color.White,
            divider = Color(0x26000000),
            raised = Color(0xFFE5E5E5),
        )
    }
}

/** A darker shade of a color, used e.g. for sent SMS bubbles. */
fun Color.darker(fraction: Float = 0.3f): Color = lerp(this, Color.Black, fraction)

fun Color.lighter(fraction: Float = 0.3f): Color = lerp(this, Color.White, fraction)
