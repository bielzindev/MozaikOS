package com.wpreborn.apps.calculator

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import java.text.DecimalFormatSymbols

/**
 * Calculator, after the Windows Phone 8.1 one: a big right-aligned display with the running
 * expression above it, flat rectangular keys and the accent "=" key. The scientific keys
 * (that WP showed in landscape) are a toggle in the app bar, so they also work in portrait.
 */
class CalculatorActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        var expr by rememberSaveable { mutableStateOf("") }
        var shown by rememberSaveable { mutableStateOf<String?>(null) } // result after "="
        var scientific by rememberSaveable { mutableStateOf(false) }
        var degrees by rememberSaveable { mutableStateOf(true) }
        var memory by rememberSaveable { mutableStateOf(0.0) }
        val history = remember { mutableStateListOf<Pair<String, String>>() }
        var showHistory by remember { mutableStateOf(false) }
        val sep = remember { DecimalFormatSymbols.getInstance().decimalSeparator }

        fun preview(): String? = if (expr.isBlank()) null else Calc.eval(expr, degrees)?.let(Calc::format)

        fun input(token: String) {
            if (shown != null) {
                // After "=", digits start over; operators continue from the result.
                expr = if (token.first().isDigit() || token == "." || token.endsWith("(") || token == "π" || token == "e") "" else Calc.toExpr(shown!!)
                shown = null
            }
            expr = Calc.append(expr, token)
        }

        fun equals() {
            val r = Calc.eval(expr, degrees) ?: return
            val text = Calc.format(r)
            history.add(0, Calc.display(expr, sep) to text)
            if (history.size > 30) history.removeAt(history.lastIndex)
            shown = text
        }

        Column(Modifier.fillMaxSize()) {
            WpText(stringResource(R.string.app_calculator).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
            if (showHistory) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    WpText(stringResource(R.string.calc_history), style = WpType.PivotHeader, modifier = Modifier.padding(bottom = 8.dp))
                    if (history.isEmpty()) WpText(stringResource(R.string.calc_history_empty), style = WpType.Large, color = Wp.palette.subtle)
                    history.take(12).forEach { (e, r) ->
                        Column(
                            Modifier.fillMaxWidth().wpClickable { expr = Calc.toExpr(r); shown = null; showHistory = false }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            WpText(e, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1)
                            WpText(Calc.display(r, sep), style = WpType.ExtraLarge, maxLines = 1)
                        }
                    }
                }
            } else {
                // Display
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End,
                ) {
                    if (memory != 0.0) WpText("M", style = WpType.Small, color = Wp.palette.subtle)
                    val top = if (shown != null) Calc.display(expr, sep) + " =" else ""
                    WpText(top, style = WpType.Large, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val main = shown ?: expr.ifEmpty { "0" }
                    val scroll = rememberScrollState()
                    LaunchedEffect(main) { scroll.scrollTo(scroll.maxValue) }
                    val len = main.length
                    WpText(
                        Calc.display(main, sep),
                        style = WpType.Header.copy(fontSize = when { len > 16 -> 36.sp; len > 11 -> 48.sp; else -> 64.sp }, lineHeight = 72.sp),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.horizontalScroll(scroll),
                    )
                    val p = if (shown == null) preview() else null
                    WpText(
                        if (p != null && p != expr) "= " + Calc.display(p, sep) else " ",
                        style = WpType.Large,
                        color = Wp.palette.subtle,
                        maxLines = 1,
                    )
                }
            }
            val keyBg = Wp.palette.raised
            val opBg = if (Wp.palette.isDark) Color(0xFF2B2B2B) else Color(0xFFD6D6D6)
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (scientific) {
                    KeyRow {
                        Key(if (degrees) "deg" else "rad", opBg, small = true) { degrees = !degrees }
                        Key("sin", opBg, small = true) { input("sin(") }
                        Key("cos", opBg, small = true) { input("cos(") }
                        Key("tan", opBg, small = true) { input("tan(") }
                        Key("(", opBg, small = true) { input("(") }
                        Key(")", opBg, small = true) { input(")") }
                    }
                    KeyRow {
                        Key("ln", opBg, small = true) { input("ln(") }
                        Key("log", opBg, small = true) { input("log(") }
                        Key("√", opBg, small = true) { input("√(") }
                        Key("x²", opBg, small = true) { input("^2") }
                        Key("xʸ", opBg, small = true) { input("^") }
                        Key("n!", opBg, small = true) { input("!") }
                    }
                    KeyRow {
                        Key("π", opBg, small = true) { input("π") }
                        Key("e", opBg, small = true) { input("e") }
                        Key("1/x", opBg, small = true) { input("^(-1)") }
                        Key("MC", opBg, small = true) { memory = 0.0 }
                        Key("M+", opBg, small = true) { (if (shown != null) shown?.let { Calc.eval(it, degrees) } else Calc.eval(expr, degrees))?.let { memory += it } }
                        Key("MR", opBg, small = true) { input(Calc.format(memory)) }
                    }
                }
                KeyRow {
                    Key("C", opBg) { expr = ""; shown = null }
                    Key(null, opBg, icon = true, onLongClick = { expr = ""; shown = null }) { if (shown != null) shown = null else expr = Calc.backspace(expr) }
                    Key("%", opBg) { input("%") }
                    Key("÷", opBg) { input("÷") }
                }
                KeyRow {
                    Key("7", keyBg) { input("7") }; Key("8", keyBg) { input("8") }; Key("9", keyBg) { input("9") }
                    Key("×", opBg) { input("×") }
                }
                KeyRow {
                    Key("4", keyBg) { input("4") }; Key("5", keyBg) { input("5") }; Key("6", keyBg) { input("6") }
                    Key("−", opBg) { input("−") }
                }
                KeyRow {
                    Key("1", keyBg) { input("1") }; Key("2", keyBg) { input("2") }; Key("3", keyBg) { input("3") }
                    Key("+", opBg) { input("+") }
                }
                KeyRow {
                    Key("±", keyBg) {
                        if (shown != null) { expr = Calc.toExpr(shown!!); shown = null }
                        expr = Calc.negate(expr)
                    }
                    Key("0", keyBg) { input("0") }
                    Key(sep.toString(), keyBg) { input(".") }
                    Key("=", Wp.accent, textColor = Color.White) { equals() }
                }
            }
            WpAppBar(
                buttons = listOf(
                    AppBarButton(com.wpreborn.core.icons.Glyphs.Calculator, stringResource(if (scientific) R.string.calc_standard else R.string.calc_scientific)) { scientific = !scientific },
                    AppBarButton(WpIcons.History, stringResource(R.string.calc_history)) { showHistory = !showHistory },
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.calc_copy)) {
                        val value = shown ?: preview() ?: return@AppBarMenuItem
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ZUNEX", Calc.display(value, sep)))
                    },
                    AppBarMenuItem(stringResource(R.string.calc_clear_history)) { history.clear() },
                ),
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun RowScope.Key(
    label: String?,
    bg: Color,
    small: Boolean = false,
    icon: Boolean = false,
    textColor: Color = Color.Unspecified,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(if (small) 46.dp else 68.dp)
            .background(bg)
            .then(if (onLongClick != null) Modifier.wpCombinedClickable(onLongClick = onLongClick, onClick = onClick) else Modifier.wpClickable(onClick = onClick))
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            WpIcon(WpIcons.Backspace, size = 26.dp)
        } else if (label != null) {
            WpText(label, style = if (small) WpType.Normal else WpType.Keypad, color = textColor)
        }
    }
}
