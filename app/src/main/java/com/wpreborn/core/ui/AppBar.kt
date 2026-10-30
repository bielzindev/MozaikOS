package com.wpreborn.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.WpEasing
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType

data class AppBarButton(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class AppBarMenuItem(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Hoisted so that the page can draw a full-screen scrim that collapses the bar. */
class AppBarState {
    var expanded by mutableStateOf(false)
}

/**
 * The Windows Phone ApplicationBar: up to four round icon buttons centred on a chrome strip,
 * with the "..." expander on the right. Expanding it slides the bar up to reveal the button
 * labels and the lowercase menu items. Tapping outside collapses it.
 */
@Composable
fun WpAppBar(
    buttons: List<AppBarButton>,
    menuItems: List<AppBarMenuItem> = emptyList(),
    modifier: Modifier = Modifier,
    background: Color = Color.Unspecified,
    state: AppBarState = remember { AppBarState() },
) {
    val chrome = if (background == Color.Unspecified) Wp.palette.chrome else background
    val mini = buttons.isEmpty()
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(chrome)
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (mini) 0.dp else 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    buttons.take(4).forEach { b ->
                        Column(
                            Modifier.width(78.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RoundButton(b.icon, enabled = b.enabled, size = 42.dp, contentDescription = b.label) {
                                state.expanded = false
                                b.onClick()
                            }
                            AnimatedVisibility(
                                state.expanded,
                                enter = expandVertically(tween(200, easing = WpEasing.ExpOut)),
                                exit = shrinkVertically(tween(150)),
                            ) {
                                WpText(
                                    b.label.lowercase(),
                                    style = WpType.Small,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (menuItems.isNotEmpty() || buttons.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(52.dp)
                            .height(30.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { state.expanded = !state.expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        WpIcon(WpIcons.More, size = 26.dp)
                    }
                }
            }
            if (!mini) Box(Modifier.height(10.dp))
            AnimatedVisibility(
                state.expanded && menuItems.isNotEmpty(),
                enter = expandVertically(tween(250, easing = WpEasing.ExpOut)),
                exit = shrinkVertically(tween(150)),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp)) {
                    menuItems.forEach { item ->
                        WpText(
                            item.label.lowercase(),
                            style = WpType.Large,
                            color = if (item.enabled) Wp.palette.foreground else Wp.palette.disabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wpClickable(enabled = item.enabled, tilt = false) {
                                    state.expanded = false
                                    item.onClick()
                                }
                                .padding(vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}
