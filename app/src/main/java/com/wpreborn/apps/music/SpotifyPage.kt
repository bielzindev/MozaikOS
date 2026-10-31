package com.wpreborn.apps.music

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpListItem
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.media.ExternalMediaWatcher
import com.wpreborn.data.media.NowPlaying
import com.wpreborn.data.notifications.NotificationCenter

private val SpotifyGreen = Color(0xFF1DB954)

/**
 * Spotify inside Music. Spotify does not let other apps stream its catalogue, so ZUNEX works
 * with the Spotify app: it follows and controls what Spotify plays (any account, through
 * notification access), asks Spotify to play a search (Premium: Spotify only plays a chosen
 * song on demand for Premium accounts) and opens its library pages directly.
 */
@Composable
fun SpotifyPage(external: NowPlaying?, onOpenNowPlaying: () -> Unit) {
    val context = LocalContext.current
    val installed = remember { context.packageManager.getLaunchIntentForPackage(NowPlaying.SPOTIFY) != null }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        if (!installed) {
            WpText(stringResource(R.string.spotify_not_installed), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.slideInStaggered(0))
            Spacer(Modifier.height(16.dp))
            WpButton(stringResource(R.string.spotify_get)) {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${NowPlaying.SPOTIFY}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(market) }.onFailure {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            return@Column
        }

        // What Spotify is playing, with controls.
        val spotify = external?.takeIf { it.isSpotify }
        if (spotify != null) {
            Row(
                Modifier.fillMaxWidth().slideInStaggered(0).wpClickable(onClick = onOpenNowPlaying).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(96.dp).background(SpotifyGreen), contentAlignment = Alignment.Center) {
                    val art = spotify.art
                    if (art != null) {
                        val img = remember(art) { art.asImageBitmap() }
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        WpIcon(WpIcons.Music, tint = Color.White, size = 40.dp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    WpText(stringResource(if (spotify.playing) R.string.spotify_playing else R.string.spotify_paused), style = WpType.Small, color = SpotifyGreen)
                    WpText(spotify.title, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    WpText(spotify.artist, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp)) {
                        RoundButton(WpIcons.Previous, size = 40.dp) { ExternalMediaWatcher.previous() }
                        Spacer(Modifier.width(10.dp))
                        RoundButton(if (spotify.playing) WpIcons.Pause else WpIcons.Play, size = 40.dp) { ExternalMediaWatcher.playPause() }
                        Spacer(Modifier.width(10.dp))
                        RoundButton(WpIcons.Next, size = 40.dp) { ExternalMediaWatcher.next() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        } else if (!NotificationCenter.hasAccess(context)) {
            WpText(stringResource(R.string.spotify_need_access), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.slideInStaggered(0))
            Spacer(Modifier.height(10.dp))
            WpButton(stringResource(R.string.allow)) {
                runCatching { context.startActivity(NotificationCenter.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            WpText(stringResource(R.string.spotify_idle), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.slideInStaggered(0).padding(bottom = 12.dp))
        }

        // Play anything by name (Spotify Premium).
        var query by remember { mutableStateOf("") }
        WpText(stringResource(R.string.spotify_play_search), style = WpType.GroupHeader, modifier = Modifier.slideInStaggered(1))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.slideInStaggered(1)) {
            Box(Modifier.weight(1f)) {
                WpTextBox(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.spotify_search_hint),
                    imeAction = ImeAction.Go,
                    onImeAction = { if (query.isNotBlank()) playFromSearch(context, query.trim()) },
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(WpIcons.Play, size = 44.dp, enabled = query.isNotBlank()) { playFromSearch(context, query.trim()) }
        }
        WpText(stringResource(R.string.spotify_premium_note), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))

        // Straight into Spotify's own pages.
        listOf(
            R.string.spotify_open to null,
            R.string.spotify_liked to "spotify:collection:tracks",
            R.string.spotify_library to "spotify:collection",
            R.string.spotify_search to "spotify:search",
        ).forEachIndexed { i, (label, uri) ->
            WpListItem(stringResource(label), titleStyle = WpType.ExtraLarge, modifier = Modifier.slideInStaggered(2 + i)) {
                if (uri == null) openPackage(context, NowPlaying.SPOTIFY) else openSpotifyUri(context, uri)
            }
        }
    }
}

private fun openSpotifyUri(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(NowPlaying.SPOTIFY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openPackage(context, NowPlaying.SPOTIFY) }
}

/** Android's "play from search": Spotify looks the query up and starts playing it. */
fun playFromSearch(context: Context, query: String) {
    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .setPackage(NowPlaying.SPOTIFY)
        .putExtra(SearchManager.QUERY, query)
        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        openSpotifyUri(context, "spotify:search:" + Uri.encode(query))
    }
}
