package com.wpreborn.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LOOPS = 1000

/** Pivot state. The pager is virtually infinite so the pivot wraps around like the original. */
class PivotState(val count: Int, val pager: PagerState) {
    val selected: Int get() = Math.floorMod(pager.currentPage, count)
    suspend fun select(index: Int) {
        val base = pager.currentPage - selected
        pager.animateScrollToPage(base + index)
    }
}

@Composable
fun rememberPivotState(count: Int, initial: Int = 0): PivotState {
    val start = if (count > 1) count * (LOOPS / 2) + initial else initial
    val pager = rememberPagerState(initialPage = start) { if (count > 1) count * LOOPS else count }
    return remember(count, pager) { PivotState(count, pager) }
}

/**
 * The Windows Phone Pivot control: small uppercase application title, a row of large
 * lowercase headers that scroll with the content, and swipeable pages. The selected header
 * is white, the others are dimmed; headers wrap around.
 */
@Composable
fun WpPivot(
    title: String?,
    headers: List<String>,
    modifier: Modifier = Modifier,
    state: PivotState = rememberPivotState(headers.size),
    titleAccessory: (@Composable () -> Unit)? = null,
    page: @Composable (index: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                WpText(title.uppercase(), style = WpType.AppTitle, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                titleAccessory?.invoke()
            }
        }
        PivotHeaders(headers, state) { scope.launch { state.select(it) } }
        HorizontalPager(
            state = state.pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { it },
        ) { virtual ->
            val index = Math.floorMod(virtual, headers.size)
            Box(Modifier.fillMaxSize().slideInStaggered(0, key = state.selected == index, distanceDp = 40f)) {
                page(index)
            }
        }
    }
}

@Composable
private fun PivotHeaders(headers: List<String>, state: PivotState, onSelect: (Int) -> Unit) {
    val widths = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    val count = headers.size
    val current = state.selected
    val frac = state.pager.currentPageOffsetFraction
    // Draw the previous header too, so it can slide back in when swiping right.
    val order = (-1 until count).map { Math.floorMod(current + it, count) }.let { if (count == 1) listOf(0) else it }
    // Measured widths already include each header's trailing 22 dp gap.
    val prevWidth = if (count > 1) (widths[order.first()] ?: 0) else 0
    val curWidth = widths[current] ?: 0
    val shift = if (frac >= 0f) frac * curWidth else frac * prevWidth
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(top = 2.dp),
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset((with(density) { 18.dp.toPx() } - prevWidth - shift).roundToInt(), 0) },
        ) {
            order.forEachIndexed { pos, index ->
                val selected = index == current && pos == (if (count == 1) 0 else 1)
                val color by animateColorAsState(
                    if (selected) Wp.palette.foreground else Wp.palette.inactive,
                    tween(200),
                    label = "pivotHeader",
                )
                WpText(
                    headers[index].lowercase(),
                    style = WpType.PivotHeader,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .onSizeChanged { widths[index] = it.width }
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(index) }
                        .padding(end = 22.dp),
                )
            }
        }
    }
}
