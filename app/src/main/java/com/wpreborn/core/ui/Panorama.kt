package com.wpreborn.core.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wpreborn.core.theme.WpType

data class PanoramaSection(
    val header: String?,
    /** Section width relative to the screen; 8.1 sections were ~88% wide so the next one peeks in. */
    val widthFraction: Float = 0.88f,
    val content: @Composable () -> Unit,
)

/**
 * The Panorama hub: a huge title that scrolls slower than the content (parallax), sections
 * that peek in from the right and a background that scrolls slowest.
 */
@Composable
fun WpPanorama(
    title: String,
    sections: List<PanoramaSection>,
    modifier: Modifier = Modifier,
    background: (@Composable (offsetPx: Float) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(listState)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenPx = with(density) { maxWidth.toPx() }
        val widths: List<Dp> = sections.map { maxWidth * it.widthFraction }
        val scrollPx by remember(sections.size, screenPx) {
            derivedStateOf {
                val before = widths.take(listState.firstVisibleItemIndex).sumOf { with(density) { it.toPx().toDouble() } }
                before.toFloat() + listState.firstVisibleItemScrollOffset
            }
        }
        background?.invoke(scrollPx * 0.2f)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.clipToBounds()) {
                WpText(
                    title.lowercase(),
                    style = WpType.PanoramaTitle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .padding(start = 14.dp, top = 6.dp)
                        .graphicsLayer { translationX = -scrollPx * 0.45f },
                )
            }
            LazyRow(state = listState, flingBehavior = fling, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(sections) { i, section ->
                    Column(Modifier.width(widths[i]).fillMaxHeight().padding(start = 20.dp, end = 4.dp)) {
                        if (section.header != null) {
                            WpText(section.header.lowercase(), style = WpType.Header.copy(fontSize = WpType.PivotHeader.fontSize), maxLines = 1)
                        }
                        section.content()
                    }
                }
            }
        }
    }
}
