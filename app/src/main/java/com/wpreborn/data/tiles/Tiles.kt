package com.wpreborn.data.tiles

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.BuiltInApp
import com.wpreborn.data.prefs.wpDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tile sizes in small-tile units. Small (1x1), Medium (2x2), Wide (4x2), Large (4x4). */
enum class TileSize(val w: Int, val h: Int) {
    Small(1, 1),
    Medium(2, 2),
    Wide(4, 2),
    Large(4, 4);

    /** The resize button cycles medium -> small -> wide -> (large) -> medium, like 8.1. */
    fun next(allowLarge: Boolean): TileSize = when (this) {
        Medium -> Small
        Small -> Wide
        Wide -> if (allowLarge) Large else Medium
        Large -> Medium
    }
}

data class TileSpec(
    val id: String = UUID.randomUUID().toString(),
    /** [AppEntry.key] of the app this tile launches. */
    val appKey: String,
    val size: TileSize,
    /** Null means "use the accent color". */
    val color: AccentColor? = null,
)

data class Placement(val x: Int, val y: Int)

object TileLayout {
    /**
     * Places tiles in order, in the first free slot, scanning 2x2 blocks the way the Start
     * screen does: four small tiles fill a medium block before the next block is used.
     */
    fun pack(tiles: List<TileSpec>, columns: Int): List<Placement> {
        val occupied = HashSet<Long>()
        fun key(x: Int, y: Int) = (y.toLong() shl 16) or x.toLong()
        fun fits(x: Int, y: Int, w: Int, h: Int): Boolean {
            if (x + w > columns) return false
            for (dy in 0 until h) for (dx in 0 until w) if (key(x + dx, y + dy) in occupied) return false
            return true
        }
        val offsets = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
        return tiles.map { t ->
            val w = minOf(t.size.w, columns)
            val h = t.size.h
            var placed: Placement? = null
            var by = 0
            while (placed == null) {
                var bx = 0
                while (bx < columns && placed == null) {
                    for ((dx, dy) in offsets) {
                        if (w > 1 && (dx != 0 || dy != 0)) continue
                        val x = bx + dx
                        val y = by + dy
                        if (fits(x, y, w, h)) {
                            placed = Placement(x, y)
                            break
                        }
                    }
                    bx += 2
                }
                by += 2
            }
            val p = placed!!
            for (dy in 0 until h) for (dx in 0 until w) occupied += key(p.x + dx, p.y + dy)
            p
        }
    }

    fun rows(tiles: List<TileSpec>, placements: List<Placement>): Int =
        tiles.indices.maxOfOrNull { placements[it].y + tiles[it].size.h } ?: 0
}

/** Persists the Start screen layout as JSON inside the settings DataStore. */
class TileRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = stringPreferencesKey("start_tiles")

    /** Null until the first layout has been created. */
    val tiles: Flow<List<TileSpec>?> = context.wpDataStore.data.map { prefs -> prefs[key]?.let(::decode) }

    fun save(list: List<TileSpec>) {
        scope.launch { context.wpDataStore.edit { it[key] = encode(list) } }
    }

    /** The layout of a freshly flashed Lumia, using the closest Android equivalents. */
    fun defaultLayout(apps: List<AppEntry>): List<TileSpec> {
        val pm = context.packageManager
        fun resolvePackage(intent: Intent): String? =
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
        fun appFor(pkg: String?): AppEntry? = pkg?.let { p -> apps.firstOrNull { it.packageName == p && it.builtIn == null } }
        fun builtin(b: BuiltInApp) = "builtin:${b.id}"

        val browser = appFor(resolvePackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)))
        val email = appFor(resolvePackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)))
        val camera = appFor(resolvePackage(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
        val maps = appFor(resolvePackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)))
        val store = appFor("com.android.vending")

        return buildList {
            add(TileSpec(appKey = builtin(BuiltInApp.Phone), size = TileSize.Medium))
            add(TileSpec(appKey = builtin(BuiltInApp.Messaging), size = TileSize.Medium))
            add(TileSpec(appKey = builtin(BuiltInApp.People), size = TileSize.Medium))
            add(TileSpec(appKey = builtin(BuiltInApp.Calendar), size = TileSize.Wide))
            browser?.let { add(TileSpec(appKey = it.key, size = TileSize.Medium)) }
            email?.let { add(TileSpec(appKey = it.key, size = TileSize.Small)) }
            camera?.let { add(TileSpec(appKey = it.key, size = TileSize.Small)) }
            add(TileSpec(appKey = builtin(BuiltInApp.Settings), size = TileSize.Small))
            maps?.let { add(TileSpec(appKey = it.key, size = TileSize.Small)) }
            add(TileSpec(appKey = builtin(BuiltInApp.Photos), size = TileSize.Wide))
            add(TileSpec(appKey = builtin(BuiltInApp.Music), size = TileSize.Wide))
            store?.let { add(TileSpec(appKey = it.key, size = TileSize.Medium)) }
        }
    }

    private fun encode(list: List<TileSpec>): String = JSONArray().also { arr ->
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("app", t.appKey)
                    .put("size", t.size.name)
                    .put("color", t.color?.name ?: JSONObject.NULL),
            )
        }
    }.toString()

    private fun decode(json: String): List<TileSpec> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TileSpec(
                id = o.getString("id"),
                appKey = o.getString("app"),
                size = runCatching { TileSize.valueOf(o.getString("size")) }.getOrDefault(TileSize.Medium),
                color = if (o.isNull("color")) null else runCatching { AccentColor.valueOf(o.getString("color")) }.getOrNull(),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        @Volatile
        private var instance: TileRepository? = null

        fun get(context: Context): TileRepository =
            instance ?: synchronized(this) {
                instance ?: TileRepository(context.applicationContext).also { instance = it }
            }
    }
}
