package com.wpreborn.launcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.AppRepository
import com.wpreborn.data.calendar.CalendarEvent
import com.wpreborn.data.calendar.CalendarRepository
import com.wpreborn.data.calls.CallLogRepository
import com.wpreborn.data.contacts.ContactsRepository
import com.wpreborn.data.media.PhotoLibrary
import com.wpreborn.data.sms.SmsRepository
import com.wpreborn.data.tiles.TileRepository
import com.wpreborn.data.tiles.TileSize
import com.wpreborn.data.tiles.TileSpec
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.hasPermissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Data for the live tiles, refreshed from the Android providers. */
data class LiveData(
    val missedCalls: Int = 0,
    val unreadSms: Int = 0,
    val events: List<CalendarEvent> = emptyList(),
    val photos: List<Uri> = emptyList(),
    val contactPhotos: List<Uri> = emptyList(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    val appRepo = AppRepository.get(app)
    private val tileRepo = TileRepository.get(app)

    val apps: StateFlow<List<AppEntry>> = appRepo.apps

    private val _tiles = MutableStateFlow<List<TileSpec>>(emptyList())
    val tiles: StateFlow<List<TileSpec>> = _tiles.asStateFlow()

    private val _live = MutableStateFlow(LiveData())
    val live: StateFlow<LiveData> = _live.asStateFlow()

    private var observers = ArrayList<Job>()

    /** Start tiles of ZUNEX apps that are turned off in the Store, waiting for them to return. */
    private val parkedTiles = HashMap<String, List<TileSpec>>()

    init {
        viewModelScope.launch {
            val saved = tileRepo.tiles.first()
            if (saved != null) {
                _tiles.value = saved
            } else {
                // First run: wait for the app list, then build the default Lumia layout.
                val list = apps.first { it.isNotEmpty() }
                val layout = tileRepo.defaultLayout(list)
                _tiles.value = layout
                tileRepo.save(layout)
            }
        }
        // Drop tiles of uninstalled apps.
        viewModelScope.launch {
            apps.collect { list ->
                if (list.isEmpty()) return@collect
                val keys = list.mapTo(HashSet()) { it.key }
                var current = _tiles.value
                // A ZUNEX app turned back on in the Store gets its Start tiles back.
                val restored = list.mapNotNull { it.builtIn }.flatMap { parkedTiles.remove(it.id).orEmpty() }
                if (restored.isNotEmpty()) current = current + restored.filter { r -> current.none { it.id == r.id } }
                val filtered = current.filter { it.appKey in keys }
                // Tiles of ZUNEX apps that were only turned off are kept aside, not lost.
                current.filter { it.appKey !in keys && it.appKey.startsWith("builtin:") }.groupBy { it.appKey.removePrefix("builtin:") }
                    .forEach { (id, specs) -> parkedTiles[id] = parkedTiles[id].orEmpty() + specs }
                if ((filtered.size != _tiles.value.size || restored.isNotEmpty()) && current.isNotEmpty()) update(filtered)
            }
        }
        refreshLive()
    }

    fun appFor(tile: TileSpec): AppEntry? = apps.value.firstOrNull { it.key == tile.appKey }

    fun isPinned(app: AppEntry) = _tiles.value.any { it.appKey == app.key }

    private fun update(list: List<TileSpec>) {
        _tiles.value = list
        tileRepo.save(list)
    }

    fun pin(app: AppEntry) {
        if (isPinned(app)) return
        val size = app.builtIn?.defaultSize?.let { if (it == TileSize.Wide) TileSize.Medium else it } ?: TileSize.Medium
        update(_tiles.value + TileSpec(appKey = app.key, size = size))
    }

    fun unpin(id: String) = update(_tiles.value.filterNot { it.id == id })

    fun resize(id: String, allowLarge: Boolean) =
        update(_tiles.value.map { if (it.id == id) it.copy(size = it.size.next(allowLarge)) else it })

    fun setColor(id: String, color: AccentColor?) =
        update(_tiles.value.map { if (it.id == id) it.copy(color = color) else it })

    /** Moves a tile to another position in the flow (used while dragging). */
    fun move(id: String, toIndex: Int, persist: Boolean) {
        val list = _tiles.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = toIndex.coerceIn(0, list.lastIndex)
        if (from == target) return
        val t = list.removeAt(from)
        list.add(target, t)
        if (persist) update(list) else _tiles.value = list
    }

    fun persist() = tileRepo.save(_tiles.value)

    /** Re-reads the providers; called on resume and when content observers fire. */
    fun refreshLive() {
        viewModelScope.launch {
            val ctx = context
            val missed = if (ctx.hasPermissions(Perms.CALL_LOG)) CallLogRepository(ctx).missedCount() else 0
            val sms = if (ctx.hasPermissions(arrayOf(android.Manifest.permission.READ_SMS))) SmsRepository(ctx).unreadCount() else 0
            val events = if (ctx.hasPermissions(Perms.CALENDAR)) CalendarRepository(ctx).upcoming(days = 14, max = 3) else emptyList()
            val photos = if (ctx.hasPermissions(Perms.IMAGES)) PhotoLibrary(ctx).load(limit = 24).map { it.uri } else emptyList()
            val faces = if (ctx.hasPermissions(Perms.CONTACTS)) {
                ContactsRepository(ctx).all().mapNotNull { it.thumbUri }.shuffled().take(18)
            } else {
                emptyList()
            }
            _live.value = LiveData(missed, sms, events, photos, faces)
        }
        startObservers()
    }

    private fun startObservers() {
        if (observers.isNotEmpty()) return
        val ctx = context
        if (ctx.hasPermissions(Perms.CALL_LOG)) {
            observers += viewModelScope.launch {
                var first = true
                CallLogRepository(ctx).changes().collect {
                    if (first) first = false else _live.value = _live.value.copy(missedCalls = CallLogRepository(ctx).missedCount())
                }
            }
        }
        if (ctx.hasPermissions(arrayOf(android.Manifest.permission.READ_SMS))) {
            observers += viewModelScope.launch {
                var first = true
                SmsRepository(ctx).changes().collect {
                    if (first) first = false else _live.value = _live.value.copy(unreadSms = SmsRepository(ctx).unreadCount())
                }
            }
        }
    }

    fun restartObservers() {
        observers.forEach { it.cancel() }
        observers.clear()
        refreshLive()
    }
}
