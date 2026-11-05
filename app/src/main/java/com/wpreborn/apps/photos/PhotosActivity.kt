package com.wpreborn.apps.photos

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.wpCombinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.Photo
import com.wpreborn.data.media.PhotoAlbum
import com.wpreborn.data.media.PhotoLibrary
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.rememberPermissionState
import com.wpreborn.permissions.hasPermissions
import java.util.Calendar
import android.view.TextureView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.wpreborn.core.ui.WpSlider
import kotlinx.coroutines.delay

private sealed interface PhotosPage {
    data object Hub : PhotosPage
    data class AlbumPage(val id: Long) : PhotosPage
    data class Viewer(val albumId: Long?, val index: Int) : PhotosPage
    data class Edit(val photoId: Long) : PhotosPage
    data object Trash : PhotosPage
    /** A picture or video another app (or Android) asked to show. */
    data class External(val uri: String, val video: Boolean) : PhotosPage
}

/** Photos hub: "collection" by month, "albums", an album page and the full-screen viewer. */
class PhotosActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val perm = rememberPermissionState(Perms.IMAGES)
        val videoPerm = rememberPermissionState(Perms.VIDEOS)
        val library = remember { PhotoLibrary(context) }
        var photos by remember { mutableStateOf<List<Photo>?>(null) }
        var reload by remember { mutableStateOf(0) }
        // Videos need their own permission on Android 13+: asked once, after the pictures.
        LaunchedEffect(perm.granted) {
            val prefs = context.getSharedPreferences("photos", android.content.Context.MODE_PRIVATE)
            if (perm.granted && !videoPerm.granted && !prefs.getBoolean("askedVideo", false)) {
                prefs.edit().putBoolean("askedVideo", true).apply()
                videoPerm.request()
            }
        }
        LaunchedEffect(perm.granted, videoPerm.granted, reload) {
            if (perm.granted) photos = library.load(videos = context.hasPermissions(Perms.VIDEOS))
        }
        val albums = remember(photos) { photos?.let { library.albums(it) }.orEmpty() }
        val trash by androidx.compose.runtime.produceState<List<Photo>>(emptyList(), perm.granted, reload) {
            if (perm.granted) value = library.trash(videos = context.hasPermissions(Perms.VIDEOS))
        }
        // Opened to show a picture or video (Mozaik Photos as the phone's gallery).
        val start = remember {
            val i = intent
            val data = i?.data
            if (data != null && (i.action == Intent.ACTION_VIEW || i.action == "com.android.camera.action.REVIEW")) {
                val type = i.type ?: runCatching { context.contentResolver.getType(data) }.getOrNull().orEmpty()
                PhotosPage.External(data.toString(), type.startsWith("video") || data.toString().contains("/video/"))
            } else {
                PhotosPage.Hub
            }
        }
        val nav = rememberNavigator<PhotosPage>(start)
        WpNavHost(nav) { page ->
            when (page) {
                PhotosPage.Hub -> {
                    val selection = rememberPhotoSelection()
                    Column(Modifier.fillMaxSize()) {
                        WpPivot(
                            title = stringResource(R.string.app_photos),
                            headers = listOf(stringResource(R.string.photos_collection), stringResource(R.string.photos_albums)),
                            modifier = Modifier.weight(1f),
                        ) { index ->
                            PermissionGate(perm, stringResource(R.string.perm_photos), Modifier.padding(start = 20.dp)) {
                                val list = photos
                                when {
                                    list == null -> LoadingDots(Modifier.padding(top = 24.dp))
                                    list.isEmpty() -> CenteredMessage(stringResource(R.string.photos_empty), Modifier.padding(start = 20.dp))
                                    index == 0 -> Collection(list, selection) { nav.navigate(PhotosPage.Viewer(null, it)) }
                                    else -> Albums(albums, trash, onTrash = { nav.navigate(PhotosPage.Trash) }) { nav.navigate(PhotosPage.AlbumPage(it.id)) }
                                }
                            }
                        }
                        if (perm.granted && !photos.isNullOrEmpty()) SelectionAppBar(selection, photos.orEmpty()) { reload++ }
                    }
                }
                is PhotosPage.AlbumPage -> albums.firstOrNull { it.id == page.id }?.let { album ->
                    val selection = rememberPhotoSelection()
                    Column(Modifier.fillMaxSize()) {
                        WpPageHeader(stringResource(R.string.app_photos), album.name)
                        Box(Modifier.weight(1f)) {
                            PhotoGrid(album.photos, selection) { nav.navigate(PhotosPage.Viewer(album.id, it)) }
                        }
                        SelectionAppBar(selection, album.photos) { reload++ }
                    }
                } ?: GoneBack(photos != null) { nav.pop() }
                is PhotosPage.Viewer -> {
                    val list = if (page.albumId == null) photos.orEmpty() else albums.firstOrNull { it.id == page.albumId }?.photos.orEmpty()
                    if (list.isEmpty()) GoneBack(photos != null) { nav.pop() }
                    else Viewer(list, page.index, onDeleted = { reload++; nav.pop() }, onEdit = { nav.navigate(PhotosPage.Edit(it.id)) })
                }
                PhotosPage.Trash -> TrashPage(trash) { reload++ }
                is PhotosPage.External -> {
                    val uri = remember(page.uri) { android.net.Uri.parse(page.uri) }
                    // A picture of the library: shown among the others, so you can swipe on.
                    val mediaId = remember(page.uri) { if (uri.authority == "media") runCatching { android.content.ContentUris.parseId(uri) }.getOrNull() else null }
                    val list = photos
                    val at = if (mediaId != null) list?.indexOfFirst { it.id == mediaId } ?: -1 else -1
                    when {
                        at >= 0 && list != null -> Viewer(list, at, onDeleted = { reload++ }, onEdit = { nav.navigate(PhotosPage.Edit(it.id)) })
                        mediaId != null && perm.granted && list == null -> LoadingDots(Modifier.padding(top = 24.dp))
                        else -> Viewer(
                            listOf(Photo(-1, System.currentTimeMillis(), 0, "", isVideo = page.video, source = uri)),
                            0, onDeleted = { (context as? android.app.Activity)?.finish() }, onEdit = {},
                        )
                    }
                }
                is PhotosPage.Edit -> photos?.firstOrNull { it.id == page.photoId }?.let { photo ->
                    PhotoEditorPage(photo, onSaved = { reload++ })
                } ?: GoneBack(photos != null) { nav.pop() }
            }
        }
    }
}

@Composable
private fun Collection(photos: List<Photo>, selection: PhotoSelection, onOpen: (Int) -> Unit) {
    val groups = remember(photos) {
        photos.withIndex().groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.value.dateTaken }
            c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { (key, list) ->
            item(key = "m$key", span = { GridItemSpan(maxLineSpan) }) {
                val c = Calendar.getInstance().apply { set(Calendar.YEAR, key / 100); set(Calendar.MONTH, key % 100); set(Calendar.DAY_OF_MONTH, 1) }
                WpText(
                    DateFormat.format("MMMM yyyy", c).toString().lowercase(),
                    style = WpType.GroupHeader,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
            }
            items(list, key = { it.value.id }) { (index, photo) ->
                SelectableThumb(photo, selection) { onOpen(index) }
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<Photo>, selection: PhotoSelection, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(photos.size, key = { photos[it].id }) { i ->
            SelectableThumb(photos[i], selection) { onOpen(i) }
        }
    }
}

@Composable
private fun Albums(albums: List<PhotoAlbum>, trash: List<Photo>, onTrash: () -> Unit, onOpen: (PhotoAlbum) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { a ->
            Column(Modifier.slideInStaggered(albums.indexOf(a) / 2).wpClickable { onOpen(a) }) {
                Thumbnail(a.photos.first().uri, Modifier.fillMaxWidth().aspectRatio(1f).background(Wp.palette.raised), sizePx = 512)
                WpText(a.name.lowercase(), style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                WpText(a.photos.size.toString(), style = WpType.Small, color = Wp.palette.subtle)
            }
        }
        // The trash, last: what was deleted in the past 30 days.
        if (Build.VERSION.SDK_INT >= 30) {
            item(key = "trash") {
                Column(Modifier.wpClickable(onClick = onTrash)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Wp.palette.raised), contentAlignment = Alignment.Center) {
                        trash.firstOrNull()?.let { Thumbnail(it.uri, Modifier.fillMaxSize(), sizePx = 512) }
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (trash.isEmpty()) 0f else 0.45f)))
                        WpIcon(WpIcons.Delete, tint = Color.White, size = 48.dp)
                    }
                    WpText(stringResource(R.string.photos_trash), style = WpType.Large, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    WpText(trash.size.toString(), style = WpType.Small, color = Wp.palette.subtle)
                }
            }
        }
    }
}

/**
 * The trash: deleted pictures and videos with the days left before Android deletes them for
 * good; restore or delete for good the chosen ones, or everything.
 */
@Composable
private fun TrashPage(trashed: List<Photo>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val selection = rememberPhotoSelection()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            selection.clear()
            onChanged()
        }
    }
    fun restore(list: List<Photo>) {
        if (list.isEmpty() || Build.VERSION.SDK_INT < 30) return
        runCatching {
            val pi = MediaStore.createTrashRequest(context.contentResolver, list.map { it.uri }, false)
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }
    fun deleteForGood(list: List<Photo>) {
        if (list.isEmpty() || Build.VERSION.SDK_INT < 30) return
        runCatching {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, list.map { it.uri })
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_photos), stringResource(R.string.photos_trash))
        WpText(stringResource(R.string.photos_trash_info), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        Box(Modifier.weight(1f)) {
            if (trashed.isEmpty()) {
                CenteredMessage(stringResource(R.string.photos_trash_empty), Modifier.padding(start = 20.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(trashed.size, key = { trashed[it].id }) { i ->
                        val p = trashed[i]
                        Box {
                            // A tap here selects (a deleted item is not opened).
                            SelectableThumb(p, selection) { selection.active = true; selection.toggle(p.id) }
                            val days = ((p.expiresAt - System.currentTimeMillis()) / 86_400_000L + 1).coerceAtLeast(0)
                            WpText(
                                stringResource(R.string.photos_days_left, days.toInt()), style = WpType.Small, color = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        val chosen = trashed.filter { it.id in selection.ids }
        if (selection.active) {
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Sync, stringResource(R.string.photos_restore), enabled = chosen.isNotEmpty()) { restore(chosen) },
                    AppBarButton(WpIcons.Delete, stringResource(R.string.photos_delete_forever), enabled = chosen.isNotEmpty()) { deleteForGood(chosen) },
                    AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select_all)) {
                        selection.ids = if (selection.ids.size == trashed.size) emptySet() else trashed.map { it.id }.toSet()
                    },
                    AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { selection.clear() },
                ),
            )
        } else {
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select), enabled = trashed.isNotEmpty()) { selection.active = true },
                    AppBarButton(WpIcons.Sync, stringResource(R.string.photos_restore_all), enabled = trashed.isNotEmpty()) { restore(trashed) },
                    AppBarButton(WpIcons.Delete, stringResource(R.string.photos_empty_trash), enabled = trashed.isNotEmpty()) { deleteForGood(trashed) },
                ),
            )
        }
    }
}

@Composable
private fun Viewer(photos: List<Photo>, start: Int, onDeleted: () -> Unit, onEdit: (Photo) -> Unit) {
    val context = LocalContext.current
    if (photos.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(0, photos.lastIndex)) { photos.size }
    var chrome by remember { mutableStateOf(true) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onDeleted()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { photos[it].id }) { page ->
            val item = photos[page]
            if (item.isVideo) VideoPage(item, active = pager.currentPage == page, chrome = chrome) { chrome = !chrome }
            else ZoomableImage(item) { chrome = !chrome }
        }
        if (chrome) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                val p = photos[pager.currentPage]
                WpText(
                    DateFormat.getDateFormat(context).format(java.util.Date(p.dateTaken)) + "  " + DateFormat.getTimeFormat(context).format(java.util.Date(p.dateTaken)),
                    style = WpType.Small,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
                WpAppBar(
                    buttons = buildList {
                        add(AppBarButton(WpIcons.Share, stringResource(R.string.share)) {
                            runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType(if (p.isVideo) "video/*" else "image/*").putExtra(Intent.EXTRA_STREAM, p.uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    null,
                                ),
                            )
                            }
                        })
                        if (Build.VERSION.SDK_INT >= 30 && p.source == null) {
                            add(AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) {
                                // To the trash (30 days), not deleted for good.
                                val pi = MediaStore.createTrashRequest(context.contentResolver, listOf(p.uri), true)
                                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            })
                        }
                        if (!p.isVideo && p.source == null) add(AppBarButton(WpIcons.Edit, stringResource(R.string.edit)) { onEdit(p) })
                    },
                    background = Color.Black.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(photo: Photo, onTap: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, photo.id) {
        value = ImageLoader.full(context, photo.uri, 2560)?.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Zoomed in: the picture at (up to) its full resolution, so detail stays sharp.
    val zoomed = scale > 1.2f
    val sharp by produceState<ImageBitmap?>(null, photo.id, zoomed) {
        value = if (zoomed) ImageLoader.large(context, photo.uri, 4096)?.asImageBitmap() else null
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .then(
                if (scale > 1f) {
                    Modifier.pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                } else {
                    Modifier.pointerInput(photo.id) {
                        // Pinch to zoom from fit; single-finger drags stay with the pager.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val a = event.changes[0]
                                    val b = event.changes[1]
                                    val prev = (a.previousPosition - b.previousPosition).getDistance()
                                    val now = (a.position - b.position).getDistance()
                                    if (prev > 0f) scale = (scale * now / prev).coerceIn(1f, 5f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val img = sharp ?: image
        if (img == null) {
            Thumbnail(photo.uri, Modifier.fillMaxSize(), sizePx = 512, contentScale = ContentScale.Fit)
        } else {
            Image(
                img,
                null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

/** Multi-selection in the photo grids, like the Windows Phone 8.1 Photos app. */
class PhotoSelection {
    var active by mutableStateOf(false)
    var ids by mutableStateOf(emptySet<Long>())
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun clear() { active = false; ids = emptySet() }
}

@Composable
private fun rememberPhotoSelection(): PhotoSelection {
    val s = remember { PhotoSelection() }
    androidx.activity.compose.BackHandler(enabled = s.active) { s.clear() }
    return s
}

/** A thumbnail; in selection mode a tap ticks it (accent check box in the corner). */
@Composable
private fun SelectableThumb(photo: Photo, selection: PhotoSelection, onOpen: () -> Unit) {
    val selected = photo.id in selection.ids
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Wp.palette.raised)
            .wpCombinedClickable(onLongClick = {
                selection.active = true
                selection.toggle(photo.id)
            }) { if (selection.active) selection.toggle(photo.id) else onOpen() },
    ) {
        Thumbnail(photo.uri, Modifier.fillMaxSize(), sizePx = 256)
        if (photo.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpIcon(WpIcons.Play, tint = Color.White, size = 12.dp)
                WpText(formatTime(photo.durationMs), style = WpType.Small, color = Color.White, modifier = Modifier.padding(start = 3.dp))
            }
        }
        if (selection.active) {
            if (selected) Box(Modifier.fillMaxSize().border(3.dp, Wp.accent))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (selected) Wp.accent else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                    .border(2.dp, androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) WpIcon(WpIcons.Check, tint = androidx.compose.ui.graphics.Color.White, size = 16.dp)
            }
        }
    }
}

/** select / share / delete / select all / cancel, depending on the selection mode. */
@Composable
private fun SelectionAppBar(selection: PhotoSelection, photos: List<Photo>, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val chosen = photos.filter { it.id in selection.ids }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            selection.clear()
            onDeleted()
        }
    }
    if (!selection.active) {
        WpAppBar(buttons = listOf(AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select)) { selection.active = true }))
        return
    }
    WpAppBar(
        buttons = listOf(
            AppBarButton(WpIcons.Share, stringResource(R.string.share), enabled = chosen.isNotEmpty()) {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
                        .setType(
                            when {
                                chosen.all { !it.isVideo } -> "image/*"
                                chosen.all { it.isVideo } -> "video/*"
                                else -> "*/*"
                            },
                        )
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(chosen.map { it.uri }))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = chosen.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val pi = MediaStore.createTrashRequest(context.contentResolver, chosen.map { it.uri }, true)
                    deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    chosen.forEach { runCatching { context.contentResolver.delete(it.uri, null, null) } }
                    selection.clear()
                    onDeleted()
                }
            },
            AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select_all)) {
                selection.ids = if (selection.ids.size == photos.size) emptySet() else photos.map { it.id }.toSet()
            },
            AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { selection.clear() },
        ),
    )
}

private fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

/**
 * A video in the viewer, played in place (ExoPlayer): the poster with a round play button,
 * then the picture with play/pause and a seek bar while the app bar is shown. Only the
 * page on screen plays; it pauses when swiped away.
 */
@Composable
private fun VideoPage(photo: Photo, active: Boolean, chrome: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(photo.durationMs) }
    var aspect by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    val player = remember(photo.id) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setMediaItem(MediaItem.fromUri(photo.uri))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration > 0) duration = player.duration
                if (state == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(active) { if (!active) player.pause() }
    LaunchedEffect(playing) {
        do {
            position = player.currentPosition
            delay(250)
        } while (playing)
    }
    Box(
        Modifier.fillMaxSize().pointerInput(photo.id) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { TextureView(it).also { tv -> player.setVideoTextureView(tv) } },
            modifier = if (aspect > 0f) Modifier.fillMaxWidth().aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f) else Modifier.fillMaxSize(),
        )
        if (!rendered || (!playing && position == 0L)) {
            Thumbnail(photo.uri, Modifier.fillMaxSize(), sizePx = 1024, contentScale = ContentScale.Fit)
        }
        if (!playing || chrome) {
            Box(
                Modifier
                    .size(72.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .wpClickable { if (playing) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                WpIcon(if (playing) WpIcons.Pause else WpIcons.Play, tint = Color.White, size = 34.dp)
            }
        }
        if (chrome) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 104.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = seeking?.let { (it * duration).toLong() } ?: position
                WpText(formatTime(shown), style = WpType.Small, color = Color.White)
                WpSlider(
                    seeking ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    { seeking = it },
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    onValueChangeFinished = {
                        seeking?.let {
                            position = (it * duration).toLong()
                            player.seekTo(position)
                        }
                        seeking = null
                    },
                )
                WpText(formatTime(duration), style = WpType.Small, color = Color.White)
            }
        }
    }
}

/**
 * A page whose album or photo no longer exists (everything in it was deleted): go back
 * instead of staying on a blank screen (once the library has loaded).
 */
@Composable
private fun GoneBack(loaded: Boolean, back: () -> Unit) {
    if (loaded) LaunchedEffect(Unit) { back() }
}
