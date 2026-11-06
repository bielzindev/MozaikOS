package com.wpreborn.apps.store

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.PanoramaSection
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpPanorama
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.apps.BuiltInApp
import com.wpreborn.data.store.AppManager
import com.wpreborn.data.store.AppState
import com.wpreborn.data.store.CatalogApp
import com.wpreborn.data.store.GameApp
import com.wpreborn.data.store.GameGenre
import com.wpreborn.data.store.GamesCatalog
import com.wpreborn.data.apps.AppRepository
import com.wpreborn.data.store.StoreCategory
import com.wpreborn.data.store.StoreReview
import com.wpreborn.data.store.StoreReviews
import com.wpreborn.data.store.ZunexCatalog
import com.wpreborn.data.tiles.PinRequests
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.WpNavigator
import com.wpreborn.navigation.rememberNavigator
import java.util.Calendar

/** The quick links of the 8.1 Store, for the Mozaik family. */
/** Which games the Games page lists. */
private enum class GameMode { All, Featured, Free }

private enum class StoreFilter { Featured, New, Updates, Installed, Available, Disabled }

private sealed interface StorePage {
    data object Hub : StorePage
    data class Filtered(val filter: StoreFilter) : StorePage
    /** Null: the whole "Mozaik collection". */
    data class Category(val category: StoreCategory?) : StorePage
    data class Details(val id: String) : StorePage
    data class Review(val id: String) : StorePage
    /** The Games section; null: all of them. */
    data class Games(val genre: GameGenre?, val mode: GameMode = GameMode.All) : StorePage
    data class Game(val id: String) : StorePage
    data object Search : StorePage
    data object Settings : StorePage
}

/** Apps whose Start tile is live (the Store says "with Live Tile", like 8.1). */
private val LiveTileApps = setOf(
    BuiltInApp.People, BuiltInApp.Messaging, BuiltInApp.Music, BuiltInApp.Photos, BuiltInApp.Calendar, BuiltInApp.Weather,
    BuiltInApp.Store, BuiltInApp.Alarms, BuiltInApp.Mail, BuiltInApp.News, BuiltInApp.Phone, BuiltInApp.Clock,
)

private const val SHARE_URL = "https://github.com/bielzindev/MozaikOS/releases/latest"

/**
 * The Mozaik Store, after the Windows Phone 8.1 Store: a panorama with the spotlight (a hero
 * app and a grid of featured apps), quick links, categories and your apps; search; an app page
 * with overview / reviews / details / related pivots and the install and share buttons. It lists
 * only the Mozaik family, and installing, updating, turning off and uninstalling act for real
 * through [AppManager] (the app appears on, or leaves, the phone, Start and the app list).
 */
class StoreActivity : WpActivity() {

    @Composable
    override fun Content() {
        val nav = rememberNavigator<StorePage>(StorePage.Hub)
        WpNavHost(nav) { page ->
            when (page) {
                StorePage.Hub -> StoreHub(nav)
                is StorePage.Filtered -> AppListPage(filterTitle(page.filter), nav, filter = page.filter)
                is StorePage.Category -> AppListPage(page.category?.let { stringResource(it.label) } ?: stringResource(R.string.store_collection), nav, category = page.category)
                is StorePage.Details -> ZunexCatalog.byId(page.id)?.let { DetailsPage(it, nav) }
                is StorePage.Review -> ZunexCatalog.byId(page.id)?.let { ReviewPage(it) { nav.pop() } }
                is StorePage.Games -> GamesPage(nav, page.genre, page.mode)
                is StorePage.Game -> GamesCatalog.byId(page.id)?.let { GameDetailsPage(it, nav) }
                StorePage.Search -> SearchPage(nav)
                StorePage.Settings -> StoreSettingsPage()
            }
        }
    }
}

@Composable
private fun filterTitle(f: StoreFilter) = stringResource(
    when (f) {
        StoreFilter.Featured -> R.string.store_featured
        StoreFilter.New -> R.string.store_new
        StoreFilter.Installed -> R.string.store_installed
        StoreFilter.Updates -> R.string.store_updates
        StoreFilter.Available -> R.string.store_available
        StoreFilter.Disabled -> R.string.store_disabled
    },
)

private fun filtered(f: StoreFilter, manager: AppManager, states: Map<String, AppState>): List<CatalogApp> {
    fun s(c: CatalogApp) = states[c.id] ?: manager.state(c.app)
    return when (f) {
        StoreFilter.Featured -> ZunexCatalog.apps.filter { it.featured }
        // The newest versions first.
        StoreFilter.New -> ZunexCatalog.apps.sortedByDescending { manager.updatedAt(it.id) * 1000 + it.versionCode }
        StoreFilter.Installed -> ZunexCatalog.apps.filter { s(it).installed }
        StoreFilter.Updates -> ZunexCatalog.apps.filter { manager.hasUpdate(it) }
        StoreFilter.Available -> ZunexCatalog.apps.filter { !s(it).installed }
        StoreFilter.Disabled -> ZunexCatalog.apps.filter { s(it).installed && !s(it).enabled }
    }
}

@Composable
private fun StoreHub(nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    val reviews by remember { StoreReviews.get(context) }.reviews.collectAsState()
    val progress by manager.progress.collectAsState()
    fun stateOf(c: CatalogApp) = states[c.id] ?: manager.state(c.app)
    val updates = ZunexCatalog.apps.count { manager.hasUpdate(it) }
    val featured = ZunexCatalog.apps.filter { it.featured }
    val installedGames = rememberInstalledGames()
    // The spotlight: featured apps and featured games, side by side, like the 8.1 Store's grid.
    val spotlight: List<Any> = remember {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val apps = featured.shuffled(kotlin.random.Random(day))
        val games = GamesCatalog.featured().shuffled(kotlin.random.Random(day))
        buildList {
            for (i in 0 until maxOf(apps.size, games.size)) {
                apps.getOrNull(i)?.let(::add)
                games.getOrNull(i)?.let(::add)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            WpPanorama(
                title = stringResource(R.string.store_short),
                sections = listOf(
                    PanoramaSection(null) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, bottom = 16.dp)) {
                            // Two by two: the big icon, NAME, price and stars.
                            spotlight.chunked(2).forEachIndexed { row, pair ->
                                Row(Modifier.fillMaxWidth().slideInStaggered(row).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { item ->
                                        when (item) {
                                            is CatalogApp -> Column(Modifier.weight(1f).wpClickable { nav.navigate(StorePage.Details(item.id)) }) {
                                                AppTile(item, Modifier.fillMaxWidth().aspectRatio(1f), 64.dp, progress[item.id])
                                                WpText(stringResource(item.app.label).uppercase(), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    WpText(priceLabel(item, stateOf(item), manager), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, modifier = Modifier.weight(1f))
                                                    Stars(reviews[item.id]?.stars ?: 0, 10.dp)
                                                }
                                            }
                                            is GameApp -> Column(Modifier.weight(1f).wpClickable { nav.navigate(StorePage.Game(item.id)) }) {
                                                GameTile(item, item.id in installedGames, Modifier.fillMaxWidth().aspectRatio(1f), 44.sp)
                                                WpText(item.name.uppercase(), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                                WpText(gameStateLabel(item, item.id in installedGames), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_quick_links)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Like the 8.1 Store: featured apps and games, new, free games, and what is yours.
                            val links: List<Triple<Int, Int?, StorePage>> = listOf(
                                Triple(R.string.store_ql_featured, null, StorePage.Filtered(StoreFilter.Featured)),
                                Triple(R.string.store_ql_featured_games, null, StorePage.Games(null, GameMode.Featured)),
                                Triple(R.string.store_ql_new, null, StorePage.Filtered(StoreFilter.New)),
                                Triple(R.string.store_ql_free_games, null, StorePage.Games(null, GameMode.Free)),
                                Triple(R.string.store_updates, filtered(StoreFilter.Updates, manager, states).size, StorePage.Filtered(StoreFilter.Updates)),
                                Triple(R.string.store_installed, filtered(StoreFilter.Installed, manager, states).size, StorePage.Filtered(StoreFilter.Installed)),
                            )
                            links.chunked(2).forEachIndexed { row, pair ->
                                Row(Modifier.fillMaxWidth().slideInStaggered(row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { (label, count, target) ->
                                        Box(
                                            Modifier.weight(1f).aspectRatio(1.15f).background(Wp.accent).wpClickable { nav.navigate(target) }.padding(10.dp),
                                        ) {
                                            // A smaller type, so no word breaks in the middle.
                                            WpText(
                                                stringResource(label).replaceFirstChar { it.uppercase() },
                                                style = WpType.Normal,
                                                color = Color.White,
                                                maxLines = 3,
                                                modifier = Modifier.align(Alignment.TopStart),
                                            )
                                            if (count != null) {
                                                WpText(count.toString(), style = WpType.TileCount, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd))
                                            }
                                        }
                                    }
                                }
                            }
                            if (updates > 0) {
                                WpButton(stringResource(R.string.store_update_all, updates)) { ZunexCatalog.apps.filter { manager.hasUpdate(it) }.forEach { manager.installWithProgress(it.app, update = true) } }
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_categories)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp)) {
                            WpText(
                                stringResource(R.string.store_collection),
                                style = WpType.ExtraLarge,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().wpClickable { nav.navigate(StorePage.Category(null)) }.padding(vertical = 6.dp),
                            )
                            WpText(
                                stringResource(R.string.store_games),
                                style = WpType.ExtraLarge,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().slideInStaggered(1).wpClickable { nav.navigate(StorePage.Games(null)) }.padding(vertical = 6.dp),
                            )
                            StoreCategory.entries.forEachIndexed { i, cat ->
                                WpText(
                                    stringResource(cat.label).lowercase(),
                                    style = WpType.ExtraLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().slideInStaggered(i + 2).wpClickable { nav.navigate(StorePage.Category(cat)) }.padding(vertical = 6.dp),
                                )
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_my_apps)) {
                        val mine = ZunexCatalog.apps.filter { stateOf(it).installed }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 12.dp)) {
                            itemsIndexed(mine, key = { _, c -> c.id }) { i, c ->
                                AppRow(c, stateOf(c), manager, reviews[c.id], Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
                            }
                        }
                    },
                ),
            )
        }
        WpAppBar(
            buttons = listOf(AppBarButton(WpIcons.Search, stringResource(R.string.search)) { nav.navigate(StorePage.Search) }),
            menuItems = listOf(
                AppBarMenuItem(stringResource(R.string.store_my_apps)) { nav.navigate(StorePage.Filtered(StoreFilter.Installed)) },
                AppBarMenuItem(stringResource(R.string.store_available)) { nav.navigate(StorePage.Filtered(StoreFilter.Available)) },
                AppBarMenuItem(stringResource(R.string.store_disabled)) { nav.navigate(StorePage.Filtered(StoreFilter.Disabled)) },
                AppBarMenuItem(stringResource(R.string.settings_lower)) { nav.navigate(StorePage.Settings) },
            ),
        )
    }
}

/** "free", "installed", "update", "off" — what the 8.1 Store wrote under an app. */
@Composable
private fun priceLabel(c: CatalogApp, s: AppState, manager: AppManager): String = stringResource(
    when {
        !s.installed -> R.string.store_free
        manager.hasUpdate(c) -> R.string.store_state_update
        !s.enabled -> R.string.store_state_off
        else -> R.string.store_state_installed
    },
)

/** The app's icon: its colour with its glyph, and the install progress across the bottom. */
@Composable
fun AppTile(c: CatalogApp, modifier: Modifier, glyph: Dp, progress: Float? = null) {
    Box(modifier.background(c.color), contentAlignment = Alignment.Center) {
        WpIcon(c.app.icon, tint = Color.White, size = glyph)
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.35f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(Color.White))
        }
    }
}

/** Five stars, the first [filled] of them solid, as in the Store's lists. */
@Composable
fun Stars(filled: Int, size: Dp, color: Color = Wp.palette.foreground, onPick: ((Int) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(size / 6)) {
        for (i in 1..5) {
            Canvas(Modifier.size(size).then(if (onPick != null) Modifier.wpClickable { onPick(i) } else Modifier)) {
                val cx = this.size.width / 2
                val cy = this.size.height / 2
                val r = this.size.minDimension / 2
                val path = Path()
                for (k in 0 until 10) {
                    val rr = if (k % 2 == 0) r else r * 0.42f
                    val a = Math.toRadians((-90 + k * 36).toDouble())
                    val p = Offset(cx + (rr * Math.cos(a)).toFloat(), cy + (rr * Math.sin(a)).toFloat())
                    if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                if (i <= filled) drawPath(path, color) else drawPath(path, color.copy(alpha = 0.45f), style = Stroke(r * 0.12f))
            }
        }
    }
}

@Composable
private fun AppRow(c: CatalogApp, s: AppState, manager: AppManager, review: StoreReview?, modifier: Modifier, onClick: () -> Unit) {
    val progress by manager.progress.collectAsState()
    Row(modifier.fillMaxWidth().wpClickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTile(c, Modifier.size(62.dp).then(if (!s.active) Modifier.background(Color.Black.copy(alpha = 0.4f)) else Modifier), 34.dp, progress[c.id])
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            WpText(stringResource(c.app.label), style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText("Mozaik", style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WpText(
                    if (progress[c.id] != null) stringResource(R.string.store_installing) else priceLabel(c, s, manager),
                    style = WpType.Small,
                    color = if (manager.hasUpdate(c) || progress[c.id] != null) Wp.accent else Wp.palette.foreground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Stars(review?.stars ?: 0, 11.dp)
            }
        }
    }
}

@Composable
private fun AppListPage(title: String, nav: WpNavigator<StorePage>, filter: StoreFilter? = null, category: StoreCategory? = null) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    val reviews by remember { StoreReviews.get(context) }.reviews.collectAsState()
    val list = (if (filter != null) filtered(filter, manager, states) else ZunexCatalog.apps).filter { c -> category == null || c.category == category }
    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.app_store).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        WpText(title.lowercase(), style = WpType.Header, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        if (filter == StoreFilter.Updates && list.isNotEmpty()) {
            WpButton(stringResource(R.string.store_update_all, list.size), Modifier.padding(start = 20.dp, bottom = 8.dp)) {
                list.forEach { manager.installWithProgress(it.app, update = true) }
            }
        }
        if (list.isEmpty()) {
            WpText(
                stringResource(if (filter == StoreFilter.Updates) R.string.store_no_updates else R.string.store_nothing_here),
                style = WpType.Large,
                color = Wp.palette.subtle,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                AppRow(c, states[c.id] ?: manager.state(c.app), manager, reviews[c.id], Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
            }
        }
    }
}

@Composable
private fun SearchPage(nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    val reviews by remember { StoreReviews.get(context) }.reviews.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val results = if (q.isEmpty()) emptyList() else ZunexCatalog.apps.filter { c ->
        context.getString(c.app.label).contains(q, true) || context.getString(c.description).contains(q, true) || context.getString(c.category.label).contains(q, true)
    }
    val installedGames = rememberInstalledGames()
    val gameResults = if (q.isEmpty()) emptyList() else GamesCatalog.games.filter { it.name.contains(q, true) || it.developer.contains(q, true) }
    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.store_search_title).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 10.dp))
        WpTextBox(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), placeholder = stringResource(R.string.store_search_hint), focusRequester = focus)
        if (q.isNotEmpty()) {
            WpText(stringResource(R.string.store_apps_header), style = WpType.Header, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
        }
        if (q.isNotEmpty() && results.isEmpty() && gameResults.isEmpty()) {
            WpText(stringResource(R.string.store_no_results), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            itemsIndexed(results, key = { _, c -> c.id }) { i, c ->
                AppRow(c, states[c.id] ?: manager.state(c.app), manager, reviews[c.id], Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
            }
            itemsIndexed(gameResults, key = { _, g -> "game:" + g.id }) { i, g ->
                GameRow(g, g.id in installedGames, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Game(g.id)) }
            }
        }
    }
}

private enum class Confirm { Disable, Uninstall }

@Composable
private fun DetailsPage(c: CatalogApp, nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    val progress by manager.progress.collectAsState()
    val reviews by remember { StoreReviews.get(context) }.reviews.collectAsState()
    val review = reviews[c.id]
    val s = states[c.id] ?: manager.state(c.app)
    val installing = progress[c.id]
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val name = stringResource(c.app.label)

    fun install() = manager.installWithProgress(c.app) { added ->
        val extra = added.filter { it != c.app }
        if (extra.isNotEmpty()) notice = context.getString(R.string.store_installed_with, extra.joinToString { context.getString(it.label) })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                WpPivot(
                    title = name.uppercase(),
                    headers = listOf(stringResource(R.string.store_overview), stringResource(R.string.store_reviews), stringResource(R.string.store_details), stringResource(R.string.store_related)),
                ) { index ->
                    when (index) {
                        0 -> Overview(c, s, manager, review, installing)
                        1 -> ReviewsPivot(review) { nav.navigate(StorePage.Review(c.id)) }
                        2 -> DetailsPivot(c, s, manager, nav)
                        else -> {
                            val related = ZunexCatalog.apps.filter { it.category == c.category && it.id != c.id }.ifEmpty { ZunexCatalog.apps.filter { it.featured && it.id != c.id } }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                                itemsIndexed(related, key = { _, r -> r.id }) { i, r ->
                                    AppRow(r, states[r.id] ?: manager.state(r.app), manager, reviews[r.id], Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(r.id)) }
                                }
                            }
                        }
                    }
                }
            }
            // The 8.1 Store's bottom bar: two wide buttons and "...".
            Column(Modifier.fillMaxWidth().background(Wp.palette.chrome).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val primary: Pair<String, () -> Unit> = when {
                        installing != null -> stringResource(R.string.store_installing) to {}
                        !s.installed -> stringResource(R.string.store_install) to { install() }
                        manager.hasUpdate(c) -> stringResource(R.string.store_update) to { manager.installWithProgress(c.app, update = true) }
                        !s.enabled -> stringResource(R.string.store_enable) to { manager.enable(c.app) }
                        else -> stringResource(R.string.store_open) to {
                            runCatching { context.startActivity(Intent(context, c.app.activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            Unit
                        }
                    }
                    WpButton(primary.first, Modifier.weight(1f), enabled = installing == null, onClick = primary.second)
                    Spacer(Modifier.width(12.dp))
                    WpButton(stringResource(R.string.share), Modifier.weight(1f)) {
                        val text = context.getString(R.string.store_share_text, name, SHARE_URL)
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    WpText("•••", style = WpType.Large, modifier = Modifier.wpClickable { menu = !menu }.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                if (menu) {
                    val items = buildList {
                        if (s.active) add(stringResource(R.string.pin_to_start) to { PinRequests.pin("builtin:${c.id}") })
                        add(stringResource(R.string.store_rate) to { nav.navigate(StorePage.Review(c.id)) })
                        if (s.installed && !c.required && s.enabled) add(stringResource(R.string.store_disable) to { confirm = Confirm.Disable })
                        if (s.installed && !c.required) add(stringResource(R.string.store_uninstall) to { confirm = Confirm.Uninstall })
                    }
                    BackHandler { menu = false }
                    items.forEach { (label, action) ->
                        WpText(
                            label.lowercase(),
                            style = WpType.Large,
                            modifier = Modifier.fillMaxWidth().wpClickable {
                                menu = false
                                action()
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        when (confirm) {
            Confirm.Disable -> {
                val dependents = manager.dependents(c.app).joinToString { context.getString(it.app.label) }
                WpMessageBox(
                    title = stringResource(R.string.store_disable_title, name),
                    message = stringResource(R.string.store_disable_message) + if (dependents.isNotEmpty()) "\n\n" + stringResource(R.string.store_used_by, dependents) else "",
                    confirm = stringResource(R.string.store_disable),
                    onConfirm = { confirm = null; manager.disable(c.app) },
                    dismiss = stringResource(R.string.cancel),
                    onDismiss = { confirm = null },
                )
            }
            Confirm.Uninstall -> {
                val dependents = manager.dependents(c.app).joinToString { context.getString(it.app.label) }
                WpMessageBox(
                    title = stringResource(R.string.store_uninstall_title, name),
                    message = stringResource(R.string.store_uninstall_message) + if (dependents.isNotEmpty()) "\n\n" + stringResource(R.string.store_used_by, dependents) else "",
                    confirm = stringResource(R.string.store_uninstall),
                    onConfirm = { confirm = null; manager.uninstall(c.app) },
                    dismiss = stringResource(R.string.cancel),
                    onDismiss = { confirm = null },
                )
            }
            null -> Unit
        }
        notice?.let {
            WpMessageBox(title = stringResource(R.string.store_install), message = it, confirm = stringResource(R.string.ok), onConfirm = { notice = null }, onDismiss = { notice = null })
        }
    }
}

/** Overview: the icon, name, publisher, price, stars, description with "more", the tiles. */
@Composable
private fun Overview(c: CatalogApp, s: AppState, manager: AppManager, review: StoreReview?, installing: Float?) {
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Row(Modifier.slideInStaggered(0)) {
            AppTile(c, Modifier.size(130.dp), 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                WpText(stringResource(c.app.label), style = WpType.ExtraLarge, maxLines = 2)
                WpText("Mozaik", style = WpType.Normal, color = Wp.palette.subtle)
                WpText(priceLabel(c, s, manager), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Stars(review?.stars ?: 0, 18.dp)
                }
                WpText(
                    LocalContext.current.resources.getQuantityString(R.plurals.store_ratings, if (review != null) 1 else 0, if (review != null) 1 else 0),
                    style = WpType.Small,
                    color = Wp.palette.subtle,
                )
            }
        }
        if (c.app in LiveTileApps) {
            WpText(stringResource(R.string.store_live_tile), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 4.dp))
        }
        if (installing != null) {
            Column(Modifier.padding(top = 14.dp)) {
                WpText(stringResource(R.string.store_installing), style = WpType.Normal, color = Wp.accent)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(Wp.palette.subtle.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(installing).fillMaxHeight().background(Wp.accent))
                }
            }
        }
        WpText(
            stringResource(c.description),
            style = WpType.Normal,
            maxLines = if (more) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp).slideInStaggered(1),
        )
        if (!more) {
            WpText(stringResource(R.string.store_read_more), style = WpType.Normal, color = Wp.accent, modifier = Modifier.wpClickable { more = true }.padding(vertical = 4.dp))
        }
        if (!s.installed) {
            WpText(stringResource(R.string.store_terms), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp))
        }
        // In place of screenshots: the app's tiles on Start, small, medium and wide.
        Row(Modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()).slideInStaggered(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            TilePreview(c, 150.dp, 150.dp, 56.dp, true)
            TilePreview(c, 306.dp, 150.dp, 56.dp, true)
            TilePreview(c, 72.dp, 72.dp, 32.dp, false)
        }
        WpText(stringResource(R.string.store_whats_new).uppercase(), style = WpType.AppTitle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        WpText(stringResource(c.whatsNew), style = WpType.Normal)
    }
}

@Composable
private fun TilePreview(c: CatalogApp, w: Dp, h: Dp, glyph: Dp, label: Boolean) {
    Box(Modifier.size(w, h).background(c.color)) {
        WpIcon(c.app.icon, Modifier.align(Alignment.Center), tint = Color.White, size = glyph)
        if (label) WpText(stringResource(c.app.label), style = WpType.Small, color = Color.White, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Composable
private fun ReviewsPivot(review: StoreReview?, onRate: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        if (review == null) {
            WpText(stringResource(R.string.store_first_review), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 16.dp))
        } else {
            Stars(review.stars, 16.dp)
            WpText(
                stringResource(R.string.store_you) + "  ·  " + android.text.format.DateFormat.getDateFormat(LocalContext.current).format(java.util.Date(review.at)),
                style = WpType.Small,
                color = Wp.palette.subtle,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (review.text.isNotEmpty()) WpText(review.text, style = WpType.Normal, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        }
        WpButton(stringResource(if (review == null) R.string.store_rate else R.string.store_edit_review), onClick = onRate)
    }
}

/** Details: information (updated, version, size, category), permissions, what it works with, publisher. */
@Composable
private fun DetailsPivot(c: CatalogApp, s: AppState, manager: AppManager, nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val pkg = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val updated = manager.updatedAt(c.id).takeIf { it > 0 } ?: pkg?.lastUpdateTime ?: 0L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Section(stringResource(R.string.store_information))
        InfoLine(stringResource(R.string.store_last_updated), if (updated > 0) android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(updated)) else "—")
        InfoLine(stringResource(R.string.store_version), c.version)
        if (s.installed && s.versionCode < c.versionCode) InfoLine(stringResource(R.string.store_installed_version), stringResource(R.string.store_older))
        InfoLine(stringResource(R.string.store_size), stringResource(R.string.store_size_included))
        InfoLine(stringResource(R.string.store_category), stringResource(c.category.label).lowercase())
        if (c.required) WpText(stringResource(R.string.store_required), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
        if (c.permissions.isNotEmpty()) {
            Section(stringResource(R.string.store_permissions))
            c.permissions.forEach { WpText(stringResource(it), style = WpType.Normal, modifier = Modifier.padding(vertical = 1.dp)) }
        }
        if (c.dependsOn.isNotEmpty()) {
            Section(stringResource(R.string.store_requires))
            c.dependsOn.forEach { d ->
                WpText(
                    stringResource(d.label) + if (!manager.isActive(d)) "  (" + stringResource(R.string.store_state_off) + ")" else "",
                    style = WpType.Large,
                    color = Wp.accent,
                    modifier = Modifier.wpClickable { nav.navigate(StorePage.Details(d.id)) }.padding(vertical = 2.dp),
                )
            }
        }
        Section(stringResource(R.string.store_publisher))
        InfoLine(stringResource(R.string.store_name), "Mozaik (João Gabriel)")
        WpText(
            stringResource(R.string.store_more_from),
            style = WpType.Normal,
            color = Wp.accent,
            modifier = Modifier.padding(top = 8.dp).wpClickable { nav.navigate(StorePage.Category(null)) }.padding(vertical = 4.dp),
        )
    }
}

/** Rate and review: tap the stars, write a few words, submit. */
@Composable
private fun ReviewPage(c: CatalogApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { StoreReviews.get(context) }
    val old = remember { store.reviews.value[c.id] }
    var stars by remember { mutableIntStateOf(old?.stars ?: 0) }
    var text by remember { mutableStateOf(old?.text ?: "") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            WpText(stringResource(c.app.label).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(top = 14.dp))
            WpText(stringResource(R.string.store_rate_title), style = WpType.Header, modifier = Modifier.padding(bottom = 12.dp))
            Stars(stars, 40.dp, color = Wp.accent) { stars = it }
            Spacer(Modifier.height(20.dp))
            WpText(stringResource(R.string.store_review_hint), style = WpType.Subtle, color = Wp.palette.subtle)
            WpTextBox(text, { text = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = false, maxLines = 6)
            if (old != null) {
                WpText(
                    stringResource(R.string.store_delete_review),
                    style = WpType.Normal,
                    color = Wp.accent,
                    modifier = Modifier.padding(top = 16.dp).wpClickable {
                        store.delete(c.id)
                        onDone()
                    }.padding(vertical = 4.dp),
                )
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.store_submit), enabled = stars > 0) {
                    store.save(c.id, stars, text)
                    onDone()
                },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { onDone() },
            ),
        )
    }
}

@Composable
private fun StoreSettingsPage() {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    var auto by remember { mutableStateOf(manager.autoUpdate) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        WpText(stringResource(R.string.app_store).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(top = 14.dp))
        WpText(stringResource(R.string.settings_lower), style = WpType.Header, modifier = Modifier.padding(bottom = 12.dp))
        WpToggle(stringResource(R.string.store_auto_update), auto, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            auto = it
            manager.autoUpdate = it
        }
        WpText(stringResource(R.string.store_auto_update_info), style = WpType.Small, color = Wp.palette.subtle)
    }
}

@Composable
private fun Section(title: String) {
    WpText(title, style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        WpText("$label: ", style = WpType.Normal, color = Wp.palette.subtle)
        WpText(value, style = WpType.Normal)
    }
}


/** The packages of the Games section that are installed on the phone right now. */
@Composable
private fun rememberInstalledGames(): Set<String> {
    val context = LocalContext.current
    val apps by remember { AppRepository.get(context) }.apps.collectAsState()
    return remember(apps) {
        val packages = apps.mapTo(HashSet()) { it.packageName }
        GamesCatalog.games.mapNotNullTo(HashSet()) { g -> g.id.takeIf { it in packages } }
    }
}

/** The game's tile: its real icon once it is installed, before that its colour and initials. */
@Composable
private fun GameTile(g: GameApp, installed: Boolean, modifier: Modifier, textSize: androidx.compose.ui.unit.TextUnit) {
    val context = LocalContext.current
    val icon = remember(g.id, installed) {
        if (!installed) null else runCatching {
            context.packageManager.getApplicationIcon(g.id).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }
    Box(modifier.background(g.color), contentAlignment = Alignment.Center) {
        if (icon != null) {
            androidx.compose.foundation.Image(icon, null, Modifier.fillMaxSize())
        } else {
            WpText(g.initials, style = WpType.Header.copy(fontSize = textSize, lineHeight = textSize * 1.1f, fontWeight = FontWeight.Light), color = Color.White)
        }
    }
}

@Composable
private fun gameStateLabel(g: GameApp, installed: Boolean) = stringResource(
    when {
        installed -> R.string.store_state_installed
        g.paid -> R.string.store_on_play_paid
        else -> R.string.store_on_play
    },
)

@Composable
private fun GameRow(g: GameApp, installed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().wpClickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GameTile(g, installed, Modifier.size(62.dp), 24.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            WpText(g.name, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(g.developer, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
            WpText(gameStateLabel(g, installed), style = WpType.Small, color = if (installed) Wp.accent else Wp.palette.foreground, maxLines = 1)
        }
    }
}

@Composable
private fun GamesPage(nav: WpNavigator<StorePage>, genre: GameGenre?, mode: GameMode) {
    val installed = rememberInstalledGames()
    val list = GamesCatalog.games.filter { (genre == null || it.genre == genre) && (mode != GameMode.Featured || it.id in GamesCatalog.featuredIds) && (mode != GameMode.Free || !it.paid) }.sortedWith(compareBy({ it.genre.ordinal }, { it.name }))
    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.app_store).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        WpText(
            (genre?.let { stringResource(it.label) } ?: stringResource(when (mode) { GameMode.Featured -> R.string.store_ql_featured_games; GameMode.Free -> R.string.store_ql_free_games; GameMode.All -> R.string.store_games_all })).lowercase(),
            style = WpType.Header,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            var last: GameGenre? = null
            list.forEachIndexed { i, g ->
                if (genre == null && g.genre != last) {
                    last = g.genre
                    item(key = "h_" + g.genre.name) {
                        WpText(stringResource(g.genre.label).lowercase(), style = WpType.Large, color = Wp.accent, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                    }
                }
                item(key = g.id) { GameRow(g, g.id in installed, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Game(g.id)) } }
            }
        }
    }
}

/** A game's page: overview and details, with install (Google Play), open, share and uninstall. */
@Composable
private fun GameDetailsPage(g: GameApp, nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val apps by remember { AppRepository.get(context) }.apps.collectAsState()
    val entry = apps.firstOrNull { it.packageName == g.id }
    val installed = entry != null
    var menu by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val portuguese = remember { java.util.Locale.getDefault().language == "pt" }
    fun openPlay() {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + g.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess || runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + g.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) notice = context.getString(R.string.store_game_no_play)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                WpPivot(
                    title = g.name.uppercase(),
                    headers = listOf(stringResource(R.string.store_overview), stringResource(R.string.store_details)),
                ) { index ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                        if (index == 0) {
                            Row(Modifier.slideInStaggered(0)) {
                                GameTile(g, installed, Modifier.size(130.dp), 52.sp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    WpText(g.name, style = WpType.ExtraLarge, maxLines = 2)
                                    WpText(g.developer, style = WpType.Normal, color = Wp.palette.subtle)
                                    WpText(gameStateLabel(g, installed), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), color = if (installed) Wp.accent else Wp.palette.foreground, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            WpText(g.description(portuguese), style = WpType.Normal, modifier = Modifier.padding(top = 16.dp).slideInStaggered(1))
                            WpText(stringResource(R.string.store_game_note), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 14.dp))
                        } else {
                            Section(stringResource(R.string.store_information))
                            InfoLine(stringResource(R.string.store_name), g.name)
                            InfoLine(stringResource(R.string.store_category), stringResource(g.genre.label).lowercase())
                            InfoLine(stringResource(R.string.store_games), "Google Play")
                            Section(stringResource(R.string.store_publisher))
                            InfoLine(stringResource(R.string.store_name), g.developer)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Wp.palette.chrome).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpButton(stringResource(if (installed) R.string.store_open else R.string.store_install), Modifier.weight(1f)) {
                        if (entry != null) AppRepository.get(context).launch(entry) else openPlay()
                    }
                    Spacer(Modifier.width(12.dp))
                    WpButton(stringResource(R.string.share), Modifier.weight(1f)) {
                        val text = context.getString(R.string.store_share_text, g.name, "https://play.google.com/store/apps/details?id=" + g.id)
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    if (installed) WpText("•••", style = WpType.Large, modifier = Modifier.wpClickable { menu = !menu }.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                if (menu && entry != null) {
                    BackHandler { menu = false }
                    listOf<Pair<String, () -> Unit>>(
                        stringResource(R.string.pin_to_start) to { PinRequests.pin(entry.key) },
                        stringResource(R.string.store_uninstall) to {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + g.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            Unit
                        },
                    ).forEach { (label, action) ->
                        WpText(
                            label.lowercase(),
                            style = WpType.Large,
                            modifier = Modifier.fillMaxWidth().wpClickable {
                                menu = false
                                action()
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        notice?.let {
            WpMessageBox(title = stringResource(R.string.store_install), message = it, confirm = stringResource(R.string.ok), onConfirm = { notice = null }, onDismiss = { notice = null })
        }
    }
}
