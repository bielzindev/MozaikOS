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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.geometry.Rect
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
import com.wpreborn.data.tiles.folderId
import com.wpreborn.data.tiles.isFolder
import com.wpreborn.data.tiles.TileLayout
import com.wpreborn.data.tiles.TileSize
import com.wpreborn.data.tiles.TileSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    /** The folder open in place (its contents shown under it, the tiles below pushed down). */
    var openFolderId by mutableStateOf<String?>(null)
    /** The open folder's name is being edited (right after creating it, or after a tap on it). */
    var renamingFolder by mutableStateOf(false)
    /** While dragging: the tile held over long enough to become a folder on release. */
    var mergeTargetId by mutableStateOf<String?>(null)
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
    /** The apps of a folder tile, with the size of their tiles. */
    folderContents: (TileSpec) -> List<Pair<AppEntry, TileSize>> = { emptyList() },
    folderName: (TileSpec) -> String = { "" },
    /** A tile dropped and held on another one: make (or fill) a folder; returns its id. */
    onMerge: (dragId: String, targetId: String) -> String? = { _, _ -> null },
    onFolderLaunch: (AppEntry) -> Unit = {},
    onFolderRemove: (folderId: String, appKey: String) -> Unit = { _, _ -> },
    onFolderRename: (folderId: String, name: String) -> Unit = { _, _ -> },
) {
    val settings = Wp.settings
    val density = LocalDensity.current
    val context = LocalContext.current

    BackHandler(enabled = state.editId != null) { state.editId = null }
    BackHandler(enabled = state.editId == null && state.openFolderId != null) { state.openFolderId = null; state.renamingFolder = false }
    // Editing Start closes the open folder (tiles move around while editing).
    LaunchedEffect(state.editId) { if (state.editId != null) { state.openFolderId = null; state.renamingFolder = false } }

    // Start background (8.1): an image seen through the tiles.
    val background by produceState<ImageBitmap?>(null, settings.startBackground) {
        value = settings.startBackground?.let { uri ->
            ImageLoader.full(context, Uri.parse(uri), 2400)?.asImageBitmap()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geo = startGeometry(maxWidth, settings.moreTiles)
        val screenWidth = maxWidth
        val viewportPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val placements = remember(tiles, geo.columns, settings.smallTilesRow) { TileLayout.pack(tiles, geo.columns, settings.smallTilesRow) }
        val rows = TileLayout.rows(tiles, placements)
        val unitPx = with(density) { (geo.unit + geo.gap).toPx() }
        val marginPx = with(density) { geo.margin.toPx() }
        // The tiles start right under the status bar, one gap down, like on the phone.
        val topPad = geo.gap
        val topPadPx = with(density) { topPad.toPx() }

        // An open folder, Windows Phone 8.1 style: its tiles appear in a band right under the
        // folder's row, with its name on top, and every tile below moves down to make room.
        val openIndex = state.openFolderId?.let { fid -> tiles.indexOfFirst { it.isFolder && it.folderId == fid } }?.takeIf { it >= 0 }
        val openTile = openIndex?.let { tiles[it] }
        val items = openTile?.let(folderContents).orEmpty()
        val itemSpecs = remember(items) { items.map { (app, size) -> TileSpec(id = "in:" + app.key, appKey = app.key, size = size) } }
        val itemPlacements = remember(itemSpecs, geo.columns, settings.smallTilesRow) { TileLayout.pack(itemSpecs, geo.columns, settings.smallTilesRow) }
        val itemRows = TileLayout.rows(itemSpecs, itemPlacements)
        val bandRow = openIndex?.let { placements[it].y + tiles[it].size.h }
        val bandHeader = 46.dp
        val bandTop = if (bandRow != null) topPad + geo.y(bandRow) else 0.dp
        val bandHeight = if (bandRow != null) bandHeader + geo.y(itemRows) + geo.gap else 0.dp
        val shift = if (bandRow != null) bandHeight + geo.gap else 0.dp
        val shiftPx = with(density) { shift.toPx() }
        fun rowTop(row: Int): Dp = topPad + geo.y(row) + (if (bandRow != null && row >= bandRow) shift else 0.dp)
        val itemRects = with(density) {
            items.indices.map { i ->
                val pl = itemPlacements[i]
                val s = itemSpecs[i].size
                Rect(
                    Offset(geo.x(pl.x).toPx(), (bandTop + bandHeader + geo.y(pl.y)).toPx()),
                    androidx.compose.ui.geometry.Size(geo.w(s, geo.columns).toPx(), geo.h(s).toPx()),
                )
            }
        }
        val currentBandRow by rememberUpdatedState(bandRow)
        val currentShiftPx by rememberUpdatedState(shiftPx)
        val currentItems by rememberUpdatedState(items)
        val currentItemRects by rememberUpdatedState(itemRects)
        val currentOpenFolder by rememberUpdatedState(openTile)
        val currentOnMerge by rememberUpdatedState(onMerge)
        val currentOnFolderLaunch by rememberUpdatedState(onFolderLaunch)
        val currentOnFolderRemove by rememberUpdatedState(onFolderRemove)
        val bandRect = if (bandRow != null) with(density) { Rect(Offset(0f, bandTop.toPx()), androidx.compose.ui.geometry.Size(widthPx, bandHeight.toPx())) } else null
        val currentBandRect by rememberUpdatedState(bandRect)

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

        val gapPx = with(density) { geo.gap.toPx() }

        /** Where tile [i] is drawn, in content pixels (below an open folder: pushed down). */
        fun rectOf(i: Int): Rect? {
            val t = currentTiles.getOrNull(i) ?: return null
            val pl = currentPlacements.getOrNull(i) ?: return null
            val w = minOf(t.size.w, geo.columns)
            val band = currentBandRow
            val top = topPadPx + pl.y * unitPx + (if (band != null && pl.y >= band) currentShiftPx else 0f)
            return Rect(Offset(marginPx + pl.x * unitPx, top), androidx.compose.ui.geometry.Size(w * unitPx - gapPx, t.size.h * unitPx - gapPx))
        }

        fun tileAt(p: Offset): Int {
            for (i in currentTiles.indices) if (rectOf(i)?.contains(p) == true) return i
            return -1
        }

        /** A tile inside the open folder. */
        fun itemAt(p: Offset): Int = currentItemRects.indexOfFirst { it.contains(p) }

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
        // After the last row only one gap, so the screen ends where the tiles end.
        val contentHeight = maxOf(geo.y(rows) + topPad + geo.margin + shift, maxHeight)

        // Edit mode: every tile floats gently, each on its own path, like tiles lifted off the
        // screen. One clock for all of them, read while drawing, so no tile recomposes per frame.
        val floatClock = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(state.editId != null) {
            if (state.editId == null) return@LaunchedEffect
            val t0 = withFrameNanos { it }
            while (isActive) withFrameNanos { floatClock.floatValue = (it - t0) / 1_000_000_000f }
        }
        val floatAmp = with(density) { 3.dp.toPx() }
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
                                // Inside the open folder: tap opens the app, press and hold
                                // takes it out of the folder (back to Start, after the folder).
                                val item = if (state.editId == null) itemAt(down.position) else -1
                                if (item >= 0) {
                                    var cancelled = false
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                                    }
                                    val entry = currentItems.getOrNull(item)
                                    val folder = currentOpenFolder
                                    if (entry != null && folder != null) {
                                        if (up != null) {
                                            up.consume()
                                            currentOnFolderLaunch(entry.first)
                                        } else if (!cancelled) {
                                            currentOnFolderRemove(folder.folderId, entry.first.key)
                                        }
                                    }
                                    return@awaitEachGesture
                                }
                                // The folder's name bar handles its own taps (rename).
                                if (currentBandRect?.contains(down.position) == true && itemAt(down.position) < 0) return@awaitEachGesture
                                val hit = tileAt(down.position)
                                if (hit < 0) {
                                    // A tap outside closes the open folder.
                                    if (state.openFolderId != null && state.editId == null) {
                                        val up = waitForUpOrCancellation()
                                        if (up != null && itemAt(up.position) < 0) { state.openFolderId = null; state.renamingFolder = false }
                                        return@awaitEachGesture
                                    }
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
                                    // The tile under the finger and how long it has been there. Held
                                    // over its middle: a folder; resting near its edges: the tiles move.
                                    var hoverId: String? = null
                                    var hoverSince = 0L
                                    var inCenter = false
                                    var edgeSince = 0L
                                    var movedHere = false
                                    fun settle(now: Long) {
                                        val id = hoverId ?: return
                                        if (inCenter) {
                                            if (now - hoverSince > 600) state.mergeTargetId = id
                                        } else if (!movedHere && edgeSince > 0 && now - edgeSince > 180) {
                                            val over = currentTiles.indexOfFirst { it.id == id }
                                            if (over >= 0) currentOnMove(tile.id, over, false)
                                            movedHere = true
                                        }
                                    }
                                    while (true) {
                                        val ev = withTimeoutOrNull(60) { awaitPointerEvent() }
                                        if (ev == null) {
                                            if (dragging) settle(System.currentTimeMillis())
                                            continue
                                        }
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
                                            val now = System.currentTimeMillis()
                                            val over = tileAt(ch.position)
                                            val r = if (over >= 0 && currentTiles[over].id != tile.id) rectOf(over) else null
                                            if (r == null) {
                                                hoverId = null
                                                inCenter = false
                                                state.mergeTargetId = null
                                            } else {
                                                val id = currentTiles[over].id
                                                if (hoverId != id) {
                                                    hoverId = id
                                                    hoverSince = now
                                                    edgeSince = 0L
                                                    movedHere = false
                                                    state.mergeTargetId = null
                                                }
                                                val mx = r.width * 0.2f
                                                val my = r.height * 0.2f
                                                val center = !tile.isFolder && Rect(r.left + mx, r.top + my, r.right - mx, r.bottom - my).contains(ch.position)
                                                if (center != inCenter) {
                                                    inCenter = center
                                                    if (center) {
                                                        hoverSince = now
                                                    } else {
                                                        edgeSince = now
                                                        state.mergeTargetId = null
                                                    }
                                                }
                                                if (!center && edgeSince == 0L) edgeSince = now
                                                settle(now)
                                            }
                                        }
                                    }
                                    if (dragging) {
                                        state.dragId = null
                                        val target = state.mergeTargetId
                                        state.mergeTargetId = null
                                        val fid = target?.let { currentOnMerge(tile.id, it) }
                                        if (fid != null) {
                                            // The new folder opens with its name ready to type.
                                            state.editId = null
                                            state.openFolderId = fid
                                            state.renamingFolder = true
                                        } else {
                                            // Let go over a tile without holding: take its place.
                                            hoverId?.let { id -> currentTiles.indexOfFirst { it.id == id }.takeIf { it >= 0 } }?.let { currentOnMove(tile.id, it, false) }
                                            currentOnMove(tile.id, currentTiles.indexOfFirst { it.id == tile.id }, true)
                                        }
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
                                        if (tile.isFolder) {
                                            // A tap opens the folder in place, another closes it.
                                            state.renamingFolder = false
                                            state.openFolderId = if (state.openFolderId == tile.folderId) null else tile.folderId
                                        } else {
                                            currentAppFor(tile)?.let { currentOnLaunch(tile, it) }
                                        }
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
                            IntOffset(geo.x(pl.x).roundToPx(), rowTop(pl.y).roundToPx())
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
                        val mergeTarget = state.mergeTargetId == tile.id
                        val scale by animateFloatAsState(
                            when {
                                mergeTarget -> 1.12f
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
                                    if (editing && !dragging) {
                                        val c = floatClock.floatValue
                                        val phase = (tile.id.hashCode() and 0xFFFF) / 65535f * 6.2832f
                                        val amp = if (selected) floatAmp * 0.6f else floatAmp
                                        translationX = sin(c * 1.7f + phase) * amp
                                        translationY = cos(c * 1.3f + phase * 1.9f) * amp
                                        rotationZ = sin(c * 1.1f + phase * 0.7f) * 0.8f
                                    }
                                    if (selected || dragging) {
                                        shadowElevation = if (dragging) 18f else 10f
                                    }
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
                                androidx.compose.runtime.CompositionLocalProvider(LocalTileColor provides tileColor) {
                                    TileFace(tile, app, inputsFor(tile, app), w, seed = tile.id.hashCode())
                                }
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
                    if (openTile != null && openIndex != null && bandRow != null) {
                        // The open folder's area: a lighter band across the screen (like the
                        // phone's), the folder's tiles on it at their own sizes.
                        val bandColor = androidx.compose.ui.graphics.lerp(Wp.palette.background, Color.White, if (Wp.palette.isDark) 0.12f else 0f)
                            .let { if (Wp.palette.isDark) it else androidx.compose.ui.graphics.lerp(it, Color.Black, 0.08f) }
                        Box(Modifier.offset(0.dp, bandTop).fillMaxWidth().height(bandHeight).background(bandColor))
                        // The notch pointing up at the folder's tile.
                        val fpl = placements[openIndex]
                        val notchX = geo.x(fpl.x) + geo.w(openTile.size, geo.columns) / 2
                        androidx.compose.foundation.Canvas(Modifier.offset(notchX - 9.dp, bandTop - geo.gap).size(18.dp, geo.gap)) {
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, size.height); lineTo(size.width / 2f, 0f); lineTo(size.width, size.height); close()
                            }
                            drawPath(p, bandColor)
                        }
                        Box(
                            Modifier.offset(geo.margin, bandTop).width(screenWidth - geo.margin * 2).height(bandHeader),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.renamingFolder) {
                                val fid = openTile.folderId
                                var draft by remember(fid) { mutableStateOf(folderName(openTile)) }
                                val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                                LaunchedEffect(fid) { runCatching { fr.requestFocus() } }
                                // Saved when done, and also when the folder closes.
                                androidx.compose.runtime.DisposableEffect(fid) { onDispose { if (draft.isNotBlank()) onFolderRename(fid, draft) } }
                                com.wpreborn.core.ui.WpTextBox(
                                    draft, { draft = it }, Modifier.fillMaxWidth(),
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                    focusRequester = fr,
                                    onImeAction = { state.renamingFolder = false },
                                )
                            } else {
                                com.wpreborn.core.ui.WpText(
                                    folderName(openTile), style = com.wpreborn.core.theme.WpType.Large, color = Wp.palette.foreground, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.wpClickable { state.renamingFolder = true },
                                )
                            }
                        }
                        items.forEachIndexed { i, (itemApp, itemSize) ->
                            key("in:" + itemApp.key) {
                                val ipl = itemPlacements.getOrNull(i) ?: Placement(0, 0)
                                val spec = itemSpecs[i]
                                val iw = geo.w(itemSize, geo.columns)
                                val ih = geo.h(itemSize)
                                val color = rememberBrandColor(itemApp) ?: Wp.accent
                                Box(
                                    Modifier
                                        .offset(geo.x(ipl.x), bandTop + bandHeader + geo.y(ipl.y))
                                        .size(iw, ih)
                                        .tilt()
                                        .background(color)
                                        .clipToBounds(),
                                ) {
                                    androidx.compose.runtime.CompositionLocalProvider(LocalTileColor provides color) {
                                        TileFace(spec, itemApp, inputsFor(spec, itemApp), iw, seed = spec.id.hashCode())
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
        drawRect(Color.Black)
        val vw = viewport.width.toFloat().coerceAtLeast(1f)
        val vh = viewport.height.toFloat().coerceAtLeast(1f)
        // The picture covers the screen plus a little extra height for the parallax, cropped
        // in the middle (never smaller than the screen, so nothing is stretched).
        val extra = vh * 0.15f
        val s = minOf(image.width / vw, image.height / (vh + extra))
        val offX = (image.width - vw * s) / 2f
        val offY = (image.height - (vh + extra) * s) / 2f
        // Scrolling moves the picture by the extra height over the whole list, slower than the tiles.
        val progress = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f
        val y = pos.y - scroll.value + extra * progress
        val sx = offX + pos.x * s
        val sy = offY + y * s
        val sw = size.width * s
        val sh = size.height * s
        // Only the part that is inside the picture (a tile above the screen is cut, not stretched).
        val left = maxOf(0f, sx)
        val top = maxOf(0f, sy)
        val right = minOf(image.width.toFloat(), sx + sw)
        val bottom = minOf(image.height.toFloat(), sy + sh)
        if (right - left < 1f || bottom - top < 1f) return@drawBehind
        drawImage(
            image,
            srcOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            srcSize = IntSize((right - left).roundToInt().coerceAtLeast(1), (bottom - top).roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset(((left - sx) / s).roundToInt(), ((top - sy) / s).roundToInt()),
            dstSize = IntSize(((right - left) / s).roundToInt().coerceAtLeast(1), ((bottom - top) / s).roundToInt().coerceAtLeast(1)),
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
