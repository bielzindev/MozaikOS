package com.wpreborn.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType

/**
 * The 8.1 TextBox: a light gray box with dark text when idle, white with an accent border
 * while editing.
 */
@Composable
fun WpTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = Wp.accent
    val bg = if (focused) Wp.palette.textBoxFocused else Wp.palette.textBox
    val textColor = if (focused || !Wp.palette.isDark) Color.Black else Color.Black.copy(alpha = 0.85f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = WpType.Normal.copy(color = textColor, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .border(2.dp, if (focused) accent else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    WpText(placeholder, style = WpType.Normal, color = Color(0xFF666666))
                }
                inner()
            }
        },
    )
}

/** 8.1 Slider: thin track, accent fill and a narrow rectangular thumb. */
@Composable
fun WpSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, onValueChangeFinished: (() -> Unit)? = null) {
    val update by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val v = value.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(widthPx) {
                    detectTapGestures { update((it.x / widthPx).coerceIn(0f, 1f)); finished?.invoke() }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(onDragEnd = { finished?.invoke() }) { change, _ ->
                        change.consume()
                        update((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Wp.palette.raised))
            Box(Modifier.fillMaxWidth(v.coerceAtLeast(0.001f)).height(6.dp).background(Wp.accent))
            Box(
                Modifier
                    .offset(x = maxWidth * v - 6.dp)
                    .width(12.dp)
                    .height(26.dp)
                    .background(Wp.palette.foreground),
            )
        }
    }
}
