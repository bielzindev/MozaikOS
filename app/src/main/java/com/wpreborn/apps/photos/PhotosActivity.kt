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
import java.util.Calendar

private sealed interface PhotosPage {
    data object Hub : PhotosPage
    data class AlbumPage(val id: Long) : PhotosPage
    data class Viewer(val albumId: Long?, val index: Int) : PhotosPage
    data class Edit(val photoId: Long) : PhotosPage
}

/** Photos hub: "collection" by month, "albums", an album page and the full-screen viewer. */
class PhotosActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val perm = rememberPermissionState(Perms.IMAGES)
        val library = remember { PhotoLibrary(context) }
        var photos by remember { mutableStateOf<List<Photo>?>(null) }
        var reload by remember { mutableStateOf(0) }
        LaunchedEffect(perm.granted, reload) { if (perm.granted) photos = library.load() }
        val albums = remember(photos) { photos?.let { library.albums(it) }.orEmpty() }
        val nav = rememberNavigator<PhotosPage>(PhotosPage.Hub)
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
                                    else -> Albums(albums) { nav.navigate(PhotosPage.AlbumPage(it.id)) }
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
                }
                is PhotosPage.Viewer -> {
                    val list = if (page.albumId == null) photos.orEmpty() else albums.firstOrNull { it.id == page.albumId }?.photos.orEmpty()
                    Viewer(list, page.index, onDeleted = { reload++; nav.pop() }, onEdit = { nav.navigate(PhotosPage.Edit(it.id)) })
                }
                is PhotosPage.Edit -> photos?.firstOrNull { it.id == page.photoId }?.let { photo ->
                    PhotoEditorPage(photo, onSaved = { reload++ })
                }
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
private fun Albums(albums: List<PhotoAlbum>, onOpen: (PhotoAlbum) -> Unit) {
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
            ZoomableImage(photos[page]) { chrome = !chrome }
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
                                    Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, p.uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    null,
                                ),
                            )
                            }
                        })
                        if (Build.VERSION.SDK_INT >= 30) {
                            add(AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) {
                                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(p.uri))
                                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            })
                        }
                        add(AppBarButton(WpIcons.Edit, stringResource(R.string.edit)) { onEdit(p) })
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
        value = ImageLoader.full(context, photo.uri, 2048)?.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
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
        val img = image
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
                        .setType("image/*")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(chosen.map { it.uri }))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = chosen.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, chosen.map { it.uri })
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
