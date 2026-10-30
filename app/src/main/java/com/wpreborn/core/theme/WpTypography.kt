package com.wpreborn.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wpreborn.R

/**
 * Selawik is Microsoft's open-source (SIL OFL) metric-compatible fallback for Segoe UI.
 * It stands in for Segoe WP, which cannot be redistributed.
 */
val SegoeFamily = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_semilight, FontWeight.ExtraLight),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
)

/** Segoe WP SemiLight is mapped onto ExtraLight so that both light cuts stay addressable. */
val SemiLight = FontWeight.ExtraLight

/**
 * Text styles of the Windows Phone 8.1 type ramp. Sizes are converted from the 480-wide
 * "effective pixel" grid (1 epx ≈ 0.8 dp on a 400 dp wide phone) and rounded.
 */
object WpType {
    private val base = TextStyle(fontFamily = SegoeFamily, letterSpacing = 0.em)

    /** Panorama title (PhoneFontSizeHuge+). */
    val PanoramaTitle = base.copy(fontSize = 96.sp, fontWeight = FontWeight.Light, lineHeight = 100.sp)

    /** Page title inside a pivot / page header (PhoneTextTitle1Style, 72px). */
    val Header = base.copy(fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 58.sp, letterSpacing = (-0.01).em)

    /** Pivot item headers (Segoe WP Light ~ 60px). */
    val PivotHeader = base.copy(fontSize = 46.sp, fontWeight = FontWeight.Light, lineHeight = 52.sp, letterSpacing = (-0.01).em)

    /** Application title shown above pivots: "MESSAGING", "SETTINGS". */
    val AppTitle = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, letterSpacing = 0.02.em)

    /** PhoneTextExtraLargeStyle (42px), used for list titles in settings and hubs. */
    val ExtraLarge = base.copy(fontSize = 30.sp, fontWeight = SemiLight, lineHeight = 36.sp)

    /** PhoneTextLargeStyle (32px). */
    val Large = base.copy(fontSize = 24.sp, fontWeight = SemiLight, lineHeight = 30.sp)

    /** PhoneTextGroupHeaderStyle / Subtle titles. */
    val GroupHeader = base.copy(fontSize = 22.sp, fontWeight = SemiLight, lineHeight = 28.sp)

    /** PhoneTextNormalStyle (20px). */
    val Normal = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)

    /** PhoneTextSubtleStyle (20px). */
    val Subtle = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)

    /** PhoneTextSmallStyle (18px). */
    val Small = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp)

    /** Labels inside tiles (bottom-left, PhoneFontSizeNormal semibold 15px). */
    val TileLabel = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)

    /** Live tile counters. */
    val TileCount = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp)

    /** Lock screen clock. */
    val LockClock = base.copy(fontSize = 96.sp, fontWeight = FontWeight.Light, lineHeight = 100.sp, letterSpacing = (-0.02).em)

    /** Keypad digits and the dial number. */
    val Keypad = base.copy(fontSize = 34.sp, fontWeight = SemiLight, lineHeight = 38.sp)
}
