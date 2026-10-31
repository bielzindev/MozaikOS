package com.wpreborn.launcher.start

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LocalTurnstile
import com.wpreborn.core.motion.TurnstileState
import com.wpreborn.core.motion.tilt
import com.wpreborn.core.motion.turnstileItem
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.tiles.Placement
import com.wpreborn.data.tiles.TileLayout
import com.wpreborn.data.tiles.TileSize
import com.wpreborn.data.tiles.TileSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Geometry of the Start grid, derived from the screen width like the 8.1 layout. */
data class StartGeometry(val columns: Int, val unit: Dp, val gap: Dp, val margin: Dp) {
    fun x(col: Int): Dp = margin + (unit + gap) * col
    fun y(row: Int): Dp = (unit + gap) * row
    fun w(size: TileSize, columns: Int): Dp = unit * minOf(size.w, columns) + gap * (minOf(size.w, columns) - 1)
    fun h(size: TileSize): Dp = unit * size.h + gap * (size.h - 1)
}

fun startGeometry(width: Dp, moreTiles: Boolean): StartGeometry {
    val columns = if (moreTiles) 6 else 4
    val margin = width * 0.035f
    val gap = width * 0.017f
    val unit = (width - margin * 2 - gap * (columns - 1)) / columns
    return StartGeometry(columns, unit, gap, margin)
}

class StartScreenState {
    var editId by mutableStateOf<String?>(null)

    /** Tile whose app icon is being customised (pack and colour). */
    var iconEditorFor by mutableStateOf<String?>(null)
    var dragId by mutableStateOf<String?>(null)
    var colorPickerFor by mutableStateOf<String?>(null)
    /** Tile being launched; it leaves last during the feather-out. */
    var launchingId by mutableStateOf<String?>(null)
    val scroll = ScrollState(0)
    val turnstile = TurnstileState()
}

/**
 * The Start screen: live tiles in a vertically scrolling grid. Tap launches with the
 * turnstile "feather" (tiles swing away from top to bottom, the tapped one last). Press and
 * hold enters edit mode: the other tiles recede, the selected one shows unpin, resize and
 * color buttons, and can be dragged around.
 */
@Composable
fun StartScreen(
    state: StartScreenState,
    tiles: List<TileSpec>,
    appFor: (TileSpec) -> AppEntry?,
    inputsFor: (TileSpec, AppEntry?) -> TileInputs,
    onLaunch: (TileSpec, AppEntry) -> Unit,
    onUnpin: (TileSpec) -> Unit,
    onResize: (TileSpec) -> Unit,
    onColor: (TileSpec, AccentColor?) -> Unit,
    onCustomColor: (TileSpec, Int) -> Unit,
    onMove: (String, Int, Boolean) -> Unit,
    onPersonalize: () -> Unit = {},
) {
    val settings = Wp.settings
    val density = LocalDensity.current
    val context = LocalContext.current

    BackHandler(enabled = state.editId != null) { state.editId = null }

    // Start background (8.1): an image seen through the tiles.
    val background by produceState<ImageBitmap?>(null, settings.startBackground) {
        value = settings.startBackground?.let { uri ->
            ImageLoader.full(context, Uri.parse(uri), 1600)?.asImageBitmap()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geo = startGeometry(maxWidth, settings.moreTiles)
        val viewportPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val placements = remember(tiles, geo.columns) { TileLayout.pack(tiles, geo.columns) }
        val rows = TileLayout.rows(tiles, placements)
        val unitPx = with(density) { (geo.unit + geo.gap).toPx() }
        val marginPx = with(density) { geo.margin.toPx() }
        val topPad = 36.dp
        val topPadPx = with(density) { topPad.toPx() }

        // Drag bookkeeping, in content coordinates.
        var dragStartTopLeft by remember { mutableStateOf(Offset.Zero) }
        var dragDown by remember { mutableStateOf(Offset.Zero) }
        var dragPointerViewport by remember { mutableStateOf(Offset.Zero) }
        val currentTiles by rememberUpdatedState(tiles)
        // The gesture handler outlives recompositions: always call the latest callbacks.
        val currentAppFor by rememberUpdatedState(appFor)
        val currentOnLaunch by rememberUpdatedState(onLaunch)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnPersonalize by rememberUpdatedState(onPersonalize)
        val currentPlacements by rememberUpdatedState(placements)

        fun tileAt(p: Offset): Int {
            val col = ((p.x - marginPx) / unitPx).toInt()
            val row = ((p.y - topPadPx) / unitPx).toInt()
            if (p.x < marginPx || p.y < topPadPx) return -1
            currentTiles.forEachIndexed { i, t ->
                val pl = currentPlacements.getOrNull(i) ?: return@forEachIndexed
                if (col >= pl.x && col < pl.x + minOf(t.size.w, geo.columns) && row >= pl.y && row < pl.y + t.size.h) return i
            }
            return -1
        }

        // Auto-scroll while dragging near the edges.
        LaunchedEffect(state.dragId) {
            if (state.dragId == null) return@LaunchedEffect
            val edge = with(density) { 80.dp.toPx() }
            while (isActive && state.dragId != null) {
                val y = dragPointerViewport.y
                val delta = when {
                    y < edge -> -(edge - y) / 6f
                    y > viewportPx - edge -> (y - (viewportPx - edge)) / 6f
                    else -> 0f
                }
                if (delta != 0f) state.scroll.scrollBy(delta)
                delay(16)
            }
        }

        // At least the screen height, so pressing and holding below the last tile is caught too.
        val contentHeight = maxOf(geo.y(rows) + topPad + 140.dp, maxHeight)
        androidx.compose.runtime.CompositionLocalProvider(LocalTurnstile provides state.turnstile) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(state.scroll, enabled = state.dragId == null),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(contentHeight)
                        .pointerInput(geo) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val hit = tileAt(down.position)
                                if (hit < 0) {
                                    if (state.editId != null) {
                                        val up = waitForUpOrCancellation()
                                        if (up != null) state.editId = null
                                    } else {
                                        // Press and hold on empty space: personalise Start.
                                        var cancelled = false
                                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            waitForUpOrCancellation().also { if (it == null) cancelled = true }
                                        }
                                        if (up == null && !cancelled) currentOnPersonalize()
                                    }
                                    return@awaitEachGesture
                                }
                                val tile = currentTiles[hit]
                                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragLoop() {
                                    var dragging = false
                                    var total = Offset.Zero
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch: PointerInputChange = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        total += ch.positionChange()
                                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) {
                                            dragging = true
                                            val idx = currentTiles.indexOfFirst { it.id == tile.id }
                                            val pl = currentPlacements.getOrNull(idx) ?: break
                                            dragStartTopLeft = Offset(marginPx + pl.x * unitPx, topPadPx + pl.y * unitPx)
                                            dragDown = down.position
                                            state.dragId = tile.id
                                        }
                                        if (dragging) {
                                            ch.consume()
                                            dragPointerViewport = Offset(ch.position.x, ch.position.y - state.scroll.value)
                                            val over = tileAt(ch.position)
                                            if (over >= 0 && currentTiles[over].id != tile.id) currentOnMove(tile.id, over, false)
                                        }
                                    }
                                    if (dragging) {
                                        state.dragId = null
                                        currentOnMove(tile.id, currentTiles.indexOfFirst { it.id == tile.id }, true)
                                    }
                                }
                                if (state.editId != null) {
                                    state.editId = tile.id
                                    dragLoop()
                                } else {
                                    var cancelled = false
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                                    }
                                    if (up != null) {
                                        up.consume()
                                        currentAppFor(tile)?.let { currentOnLaunch(tile, it) }
                                    } else if (!cancelled) {
                                        state.editId = tile.id
                                        dragLoop()
                                    }
                                }
                            }
                        },
                ) {
                    tiles.forEachIndexed { index, tile ->
                      // Each tile keeps its own state (icon, colours, animations) by its
                      // permanent id, not by its position, so moving tiles never mixes them up.
                      key(tile.id) {
                        val pl = placements.getOrNull(index) ?: Placement(0, 0)
                        val app = appFor(tile)
                        val dragging = state.dragId == tile.id
                        val target = with(density) {
                            IntOffset(geo.x(pl.x).roundToPx(), (geo.y(pl.y) + topPad).roundToPx())
                        }
                        val animated by animateIntOffsetAsState(target, tween(250), label = "tilePos")
                        val pos = if (dragging) {
                            val pointerContent = dragPointerViewport + Offset(0f, state.scroll.value.toFloat())
                            val o = dragStartTopLeft + (pointerContent - dragDown)
                            IntOffset(o.x.roundToInt(), o.y.roundToInt())
                        } else {
                            animated
                        }
                        val editing = state.editId != null
                        val selected = state.editId == tile.id
                        val scale by animateFloatAsState(
                            when {
                                !editing -> 1f
                                selected -> 1.04f
                                else -> 0.88f
                            },
                            tween(200),
                            label = "editScale",
                        )
                        val w = geo.w(tile.size, geo.columns)
                        val h = geo.h(tile.size)
                        // Tiles fly out/in from the top-left corner towards the bottom-right.
                        val feather = ((pl.y / 2) + (pl.x / 2)).coerceAtMost(7)
                        val tileColor = tile.fixedColor ?: rememberBrandColor(app) ?: Wp.accent
                        Box(
                            Modifier
                                .offset { pos }
                                .size(w, h)
                                .zIndex(if (dragging) 2f else if (selected) 1f else 0f)
                                .turnstileItem(if (state.launchingId == tile.id) 6 else feather)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = if (editing && !selected) 0.75f else 1f
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .tilt(enabled = !editing)
                                    .then(
                                        if (background != null && tile.fixedColor == null) {
                                            Modifier.drawBackgroundSlice(background!!, pos, IntSize(widthPx.roundToInt(), viewportPx.roundToInt()), state.scroll)
                                        } else {
                                            Modifier.background(tileColor)
                                        },
                                    )
                                    .clipToBounds(),
                            ) {
                                TileFace(tile, app, inputsFor(tile, app), w, seed = tile.id.hashCode())
                            }
                            if (selected && state.dragId == null) {
                                EditButton(WpIcons.Unpin, Modifier.align(Alignment.TopEnd).offset(12.dp, (-12).dp)) {
                                    state.editId = null
                                    onUnpin(tile)
                                }
                                EditButton(WpIcons.Resize, Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp)) {
                                    onResize(tile)
                                }
                                EditButton(WpIcons.Palette, Modifier.align(Alignment.TopStart).offset((-12).dp, (-12).dp)) {
                                    state.colorPickerFor = tile.id
                                }
                                if (app != null) {
                                    EditButton(WpIcons.Edit, Modifier.align(Alignment.BottomStart).offset((-12).dp, 12.dp)) {
                                        state.iconEditorFor = tile.id
                                    }
                                }
                            }
                        }
                      }
                    }
                }
            }
        }

        state.iconEditorFor?.let { id ->
            tiles.firstOrNull { it.id == id }?.let(appFor)?.let { app ->
                IconCustomizer(app, tileColor = tiles.first { it.id == id }.fixedColor, onDismiss = { state.iconEditorFor = null })
            } ?: run { state.iconEditorFor = null }
        }

        state.colorPickerFor?.let { id ->
            val current = tiles.firstOrNull { it.id == id }
            TileColorPicker(
                initial = current?.fixedColor ?: Wp.accent,
                onPick = { color ->
                    current?.let { onColor(it, color) }
                    state.colorPickerFor = null
                },
                onCustom = { argb ->
                    current?.let { onCustomColor(it, argb) }
                    state.colorPickerFor = null
                },
                onDismiss = { state.colorPickerFor = null },
            )
        }
    }
}

@Composable
private fun EditButton(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .background(Color.White, CircleShape)
            .border(2.dp, Color.Black, CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = Color.Black, size = 22.dp)
    }
}

/**
 * Draws the part of the Start background that lies under this tile, so the image appears
 * to be behind transparent tiles. The image scrolls a little (parallax) with the grid.
 */
private fun Modifier.drawBackgroundSlice(image: ImageBitmap, pos: IntOffset, viewport: IntSize, scroll: ScrollState): Modifier =
    this.drawBehind {
        val scale = maxOf(image.width / viewport.width.toFloat(), image.height / (viewport.height * 1.15f))
        val parallax = scroll.value * 0.15f
        val viewportY = pos.y - scroll.value + parallax
        val srcX = (pos.x * scale).roundToInt().coerceIn(0, image.width - 1)
        val srcY = (viewportY * scale).roundToInt().coerceIn(0, image.height - 1)
        val srcW = (size.width * scale).roundToInt().coerceIn(1, image.width - srcX)
        val srcH = (size.height * scale).roundToInt().coerceIn(1, image.height - srcY)
        drawRect(Color.Black)
        drawImage(
            image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }

/**
 * Full-screen tile colour page: "default" (the theme accent, or the app's own colour), the
 * Windows Phone accent palette, and a colour of your own (hue, saturation, brightness).
 */
@Composable
fun TileColorPicker(initial: Color, onPick: (AccentColor?) -> Unit, onCustom: (Int) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0] / 360f) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val custom = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, sat, value)))
    Column(
        Modifier
            .fillMaxSize()
            .background(Wp.palette.background)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        com.wpreborn.core.ui.WpText(
            LocalContext.current.getString(com.wpreborn.R.string.tile_color).uppercase(),
            style = com.wpreborn.core.theme.WpType.AppTitle,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Start) {
            Box(
                Modifier
                    .size(width = 160.dp, height = 44.dp)
                    .border(2.dp, Wp.palette.foreground)
                    .wpClickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) {
                com.wpreborn.core.ui.WpText(LocalContext.current.getString(com.wpreborn.R.string.tile_color_default))
            }
        }
        AccentColor.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(c.color)
                            .wpClickable { onPick(c) },
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // A colour of your own.
        com.wpreborn.core.ui.WpText(
            LocalContext.current.getString(com.wpreborn.R.string.tile_color_custom).uppercase(),
            style = com.wpreborn.core.theme.WpType.AppTitle,
            color = Wp.palette.subtle,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp).background(custom))
            Spacer(Modifier.width(14.dp))
            com.wpreborn.core.ui.WpText(
                "#%06X".format(custom.toArgb() and 0xFFFFFF),
                style = com.wpreborn.core.theme.WpType.Large,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Hue strip above its slider, like a colour wheel unrolled.
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val steps = 36
            val w = size.width / steps
            for (i in 0 until steps) {
                drawRect(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 360f / steps, 1f, 1f))),
                    topLeft = androidx.compose.ui.geometry.Offset(i * w, 0f),
                    size = androidx.compose.ui.geometry.Size(w + 1f, size.height),
                )
            }
        }
        com.wpreborn.core.ui.WpText(LocalContext.current.getString(com.wpreborn.R.string.tile_color_hue), style = com.wpreborn.core.theme.WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 6.dp))
        com.wpreborn.core.ui.WpSlider(hue, { hue = it })
        com.wpreborn.core.ui.WpText(LocalContext.current.getString(com.wpreborn.R.string.tile_color_saturation), style = com.wpreborn.core.theme.WpType.Small, color = Wp.palette.subtle)
        com.wpreborn.core.ui.WpSlider(sat, { sat = it })
        com.wpreborn.core.ui.WpText(LocalContext.current.getString(com.wpreborn.R.string.tile_color_brightness), style = com.wpreborn.core.theme.WpType.Small, color = Wp.palette.subtle)
        com.wpreborn.core.ui.WpSlider(value, { value = it })
        Spacer(Modifier.height(12.dp))
        com.wpreborn.core.ui.WpButton(LocalContext.current.getString(com.wpreborn.R.string.tile_color_use)) { onCustom(custom.toArgb()) }
    }
}
