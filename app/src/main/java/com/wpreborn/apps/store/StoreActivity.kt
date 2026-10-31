package com.wpreborn.apps.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.PanoramaSection
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpPanorama
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.store.AppManager
import com.wpreborn.data.store.AppState
import com.wpreborn.data.store.CatalogApp
import com.wpreborn.data.store.StoreCategory
import com.wpreborn.data.store.ZunexCatalog
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.WpNavigator
import com.wpreborn.navigation.rememberNavigator

private enum class StoreFilter { Installed, Updates, Available, Disabled }

private sealed interface StorePage {
    data object Hub : StorePage
    data class Filtered(val filter: StoreFilter) : StorePage
    data class Category(val category: StoreCategory) : StorePage
    data class Details(val id: String) : StorePage
    data object Search : StorePage
}

/**
 * ZUNEX Store: the catalogue of the ZUNEX family and the place to manage it. Inspired by the
 * Windows Phone store: a panorama with spotlight apps, quick links, categories and every app;
 * a details pivot with install / open / update / turn off / uninstall that act for real
 * through [AppManager].
 */
class StoreActivity : WpActivity() {

    @Composable
    override fun Content() {
        val nav = rememberNavigator<StorePage>(StorePage.Hub)
        WpNavHost(nav) { page ->
            when (page) {
                StorePage.Hub -> StoreHub(nav)
                is StorePage.Filtered -> AppListPage(filterTitle(page.filter), nav, filter = page.filter)
                is StorePage.Category -> AppListPage(stringResource(page.category.label), nav, category = page.category)
                is StorePage.Details -> ZunexCatalog.byId(page.id)?.let { DetailsPage(it, nav) }
                StorePage.Search -> SearchPage(nav)
            }
        }
    }
}

@Composable
private fun filterTitle(f: StoreFilter) = stringResource(
    when (f) {
        StoreFilter.Installed -> R.string.store_installed
        StoreFilter.Updates -> R.string.store_updates
        StoreFilter.Available -> R.string.store_available
        StoreFilter.Disabled -> R.string.store_disabled
    },
)

private fun matches(f: StoreFilter, c: CatalogApp, s: AppState, manager: AppManager) = when (f) {
    StoreFilter.Installed -> s.installed
    StoreFilter.Updates -> manager.hasUpdate(c)
    StoreFilter.Available -> !s.installed
    StoreFilter.Disabled -> s.installed && !s.enabled
}

@Composable
private fun StoreHub(nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    fun stateOf(c: CatalogApp) = states[c.id] ?: manager.state(c.app)
    val updates = ZunexCatalog.apps.count { manager.hasUpdate(it) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            WpPanorama(
                title = stringResource(R.string.store_short),
                sections = listOf(
                    PanoramaSection(null) {
                        // Spotlight: the featured apps as big tiles.
                        val featured = ZunexCatalog.apps.filter { it.featured }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp)) {
                            featured.chunked(2).forEachIndexed { row, pair ->
                                Row(Modifier.fillMaxWidth().slideInStaggered(row).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { c ->
                                        Column(Modifier.weight(1f).wpClickable { nav.navigate(StorePage.Details(c.id)) }) {
                                            AppTile(c, Modifier.fillMaxWidth().aspectRatio(1f), 64.dp)
                                            WpText(stringResource(c.app.label).uppercase(), style = WpType.Normal.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                                            WpText(stateLabel(c, stateOf(c), manager), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_quick_links)) {
                        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(StoreFilter.Installed, StoreFilter.Updates, StoreFilter.Available, StoreFilter.Disabled).chunked(2).forEachIndexed { row, pair ->
                                Row(Modifier.fillMaxWidth().slideInStaggered(row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { f ->
                                        val count = ZunexCatalog.apps.count { matches(f, it, stateOf(it), manager) }
                                        Box(
                                            Modifier.weight(1f).aspectRatio(1f).background(Wp.accent).wpClickable { nav.navigate(StorePage.Filtered(f)) }.padding(10.dp),
                                        ) {
                                            WpText(filterTitle(f), style = WpType.Large, color = Color.White)
                                            WpText(count.toString(), style = WpType.TileCount, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd))
                                        }
                                    }
                                }
                            }
                            if (updates > 0) {
                                WpButton(stringResource(R.string.store_update_all, updates)) { manager.updateAll() }
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_categories)) {
                        Column(Modifier.fillMaxSize().padding(start = 20.dp)) {
                            StoreCategory.entries.forEachIndexed { i, cat ->
                                WpText(
                                    stringResource(cat.label).lowercase(),
                                    style = WpType.ExtraLarge,
                                    modifier = Modifier.fillMaxWidth().slideInStaggered(i).wpClickable { nav.navigate(StorePage.Category(cat)) }.padding(vertical = 8.dp),
                                )
                            }
                        }
                    },
                    PanoramaSection(stringResource(R.string.store_all_apps)) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 12.dp)) {
                            itemsIndexed(ZunexCatalog.apps, key = { _, c -> c.id }) { i, c ->
                                AppRow(c, stateOf(c), manager, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
                            }
                        }
                    },
                ),
            )
        }
        WpAppBar(buttons = listOf(AppBarButton(WpIcons.Search, stringResource(R.string.search)) { nav.navigate(StorePage.Search) }))
    }
}

@Composable
private fun stateLabel(c: CatalogApp, s: AppState, manager: AppManager): String = stringResource(
    when {
        !s.installed -> R.string.store_state_free
        manager.hasUpdate(c) -> R.string.store_state_update
        !s.enabled -> R.string.store_state_off
        else -> R.string.store_state_installed
    },
)

/** The app's identity: its colour with its glyph. */
@Composable
fun AppTile(c: CatalogApp, modifier: Modifier, glyph: Dp) {
    Box(modifier.background(c.color), contentAlignment = Alignment.Center) {
        WpIcon(c.app.icon, tint = Color.White, size = glyph)
    }
}

@Composable
private fun AppRow(c: CatalogApp, s: AppState, manager: AppManager, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().wpClickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTile(c, Modifier.size(62.dp).then(if (!s.active) Modifier.background(Color.Black.copy(alpha = 0.4f)) else Modifier), 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            WpText(stringResource(c.app.label), style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WpText(
                stateLabel(c, s, manager) + "  ·  " + stringResource(c.category.label),
                style = WpType.Small,
                color = if (manager.hasUpdate(c) || !s.installed) Wp.accent else Wp.palette.subtle,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AppListPage(title: String, nav: WpNavigator<StorePage>, filter: StoreFilter? = null, category: StoreCategory? = null) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    val list = ZunexCatalog.apps.filter { c ->
        val s = states[c.id] ?: manager.state(c.app)
        (filter == null || matches(filter, c, s, manager)) && (category == null || c.category == category)
    }
    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.app_store).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        WpText(title.lowercase(), style = WpType.Header, maxLines = 1, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        if (filter == StoreFilter.Updates && list.isNotEmpty()) {
            WpButton(stringResource(R.string.store_update_all, list.size), Modifier.padding(start = 20.dp, bottom = 8.dp)) { manager.updateAll() }
        }
        if (list.isEmpty()) {
            WpText(stringResource(R.string.store_nothing_here), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(start = 20.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                AppRow(c, states[c.id] ?: manager.state(c.app), manager, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
            }
        }
    }
}

@Composable
private fun SearchPage(nav: WpNavigator<StorePage>) {
    val context = LocalContext.current
    val manager = remember { AppManager.get(context) }
    val states by manager.states.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val results = if (q.isEmpty()) emptyList() else ZunexCatalog.apps.filter { c ->
        context.getString(c.app.label).contains(q, true) || context.getString(c.description).contains(q, true) || context.getString(c.category.label).contains(q, true)
    }
    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.app_store).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        WpText(stringResource(R.string.search), style = WpType.Header, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        WpTextBox(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), placeholder = stringResource(R.string.store_search_hint))
        if (q.isNotEmpty() && results.isEmpty()) {
            WpText(stringResource(R.string.store_no_results), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            itemsIndexed(results, key = { _, c -> c.id }) { i, c ->
                AppRow(c, states[c.id] ?: manager.state(c.app), manager, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(c.id)) }
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
    val s = states[c.id] ?: manager.state(c.app)
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val name = stringResource(c.app.label)

    Box(Modifier.fillMaxSize()) {
        WpPivot(
            title = stringResource(R.string.app_store) + " · " + name,
            headers = listOf(stringResource(R.string.store_details), stringResource(R.string.store_related)),
        ) { index ->
            if (index == 0) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    Row(Modifier.slideInStaggered(0)) {
                        AppTile(c, Modifier.size(120.dp), 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            WpText(name, style = WpType.ExtraLarge, maxLines = 2)
                            WpText("ZUNEX", style = WpType.Normal, color = Wp.palette.subtle)
                            WpText(stringResource(c.category.label), style = WpType.Small, color = Wp.palette.subtle)
                            WpText(
                                stateLabel(c, s, manager),
                                style = WpType.Normal,
                                color = Wp.accent,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Actions for the current state.
                    Row(Modifier.slideInStaggered(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            !s.installed -> WpButton(stringResource(R.string.store_install)) {
                                val added = manager.install(c.app)
                                val extra = added.filter { it != c.app }
                                if (extra.isNotEmpty()) notice = context.getString(R.string.store_installed_with, extra.joinToString { context.getString(it.label) })
                            }
                            !s.enabled -> WpButton(stringResource(R.string.store_enable)) { manager.enable(c.app) }
                            else -> WpButton(stringResource(R.string.store_open)) {
                                runCatching { context.startActivity(Intent(context, c.app.activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        if (manager.hasUpdate(c)) WpButton(stringResource(R.string.store_update)) { manager.update(c.app) }
                    }
                    if (s.installed && !c.required) {
                        Row(Modifier.padding(top = 10.dp).slideInStaggered(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (s.enabled) WpButton(stringResource(R.string.store_disable)) { confirm = Confirm.Disable }
                            WpButton(stringResource(R.string.store_uninstall)) { confirm = Confirm.Uninstall }
                        }
                    }
                    if (c.required) {
                        WpText(stringResource(R.string.store_required), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 10.dp))
                    }

                    Section(stringResource(R.string.store_description), 3)
                    WpText(stringResource(c.description), style = WpType.Normal)
                    Section(stringResource(R.string.store_whats_new), 4)
                    WpText(stringResource(c.whatsNew), style = WpType.Normal)
                    Section(stringResource(R.string.store_information), 5)
                    InfoLine(stringResource(R.string.store_version), c.version)
                    if (s.installed && s.versionCode < c.versionCode) InfoLine(stringResource(R.string.store_installed_version), stringResource(R.string.store_older))
                    InfoLine(stringResource(R.string.store_publisher), "ZUNEX")
                    InfoLine(stringResource(R.string.store_category), stringResource(c.category.label))
                    if (c.permissions.isNotEmpty()) {
                        Section(stringResource(R.string.store_permissions), 6)
                        c.permissions.forEach { WpText("•  " + stringResource(it), style = WpType.Normal) }
                    }
                    if (c.dependsOn.isNotEmpty()) {
                        Section(stringResource(R.string.store_requires), 7)
                        c.dependsOn.forEach { d ->
                            WpText(
                                stringResource(d.label) + if (!manager.isActive(d)) "  (" + stringResource(R.string.store_state_off) + ")" else "",
                                style = WpType.Large,
                                color = Wp.accent,
                                modifier = Modifier.wpClickable { nav.navigate(StorePage.Details(d.id)) }.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            } else {
                val related = ZunexCatalog.apps.filter { it.category == c.category && it.id != c.id }.ifEmpty { ZunexCatalog.apps.filter { it.featured && it.id != c.id } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                    itemsIndexed(related, key = { _, r -> r.id }) { i, r ->
                        AppRow(r, states[r.id] ?: manager.state(r.app), manager, Modifier.slideInStaggered(i)) { nav.navigate(StorePage.Details(r.id)) }
                    }
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

@Composable
private fun Section(title: String, index: Int) {
    WpText(title.uppercase(), style = WpType.AppTitle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).slideInStaggered(index))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        WpText(label, style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.width(140.dp))
        WpText(value, style = WpType.Normal)
    }
}
