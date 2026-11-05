package com.wpreborn.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wpreborn.core.theme.AccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.wpDataStore: DataStore<Preferences> by preferencesDataStore(name = "wp_settings")

/** Everything the user can personalise. Persisted with DataStore. */
data class WpSettings(
    val darkTheme: Boolean = true,
    val accent: AccentColor = AccentColor.Cobalt,
    /** "Show more tiles": 6 small-tile columns instead of 4. */
    val moreTiles: Boolean = true,
    /** Start background image (content uri), drawn through the tiles like on 8.1. */
    val startBackground: String? = null,
    val lockWallpaper: String? = null,
    /** Show the Windows Phone lock screen in front of the system keyguard. */
    val lockEnabled: Boolean = false,
    val lockShowNotifications: Boolean = true,
    /** Without a chosen picture, cycle the latest photos on the lock screen. */
    val lockPhotos: Boolean = true,
    /** Replace the Android status bar by the Windows Phone one inside our screens. */
    val wpStatusBar: Boolean = true,
    /** Draw third-party icons on accent-colored squares in the app list. */
    val iconsOnAccent: Boolean = true,
    /** Metro icon pack (white glyphs) instead of the original Android icons. */
    val metroIcons: Boolean = true,
    /** Tiles of apps without a custom colour take the app's brand colour (Metro icon style). */
    val brandColors: Boolean = true,
    /** Show system apps that duplicate a ZUNEX app (dialer, messages, music...) in the app list. */
    val showDuplicates: Boolean = false,
    val liveTiles: Boolean = true,
    /** Small tiles fill a row across the screen instead of 2x2 groups. */
    val smallTilesRow: Boolean = false,
    /** Lumia glance screen (always on display) while the screen is off. */
    val glance: Boolean = false,
    /** Mozaik apps open with their splash screen (colour and glyph), as on Windows Phone. */
    val splash: Boolean = true,
    val firstRunDone: Boolean = false,
)

class SettingsRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store get() = context.wpDataStore

    @Volatile
    var cached: WpSettings = WpSettings()
        private set

    val settings: Flow<WpSettings> = store.data.map { it.toSettings() }.onEach { cached = it }

    /** Loads the settings synchronously once at process start so the first frame is correct. */
    fun warmUp() {
        cached = runBlocking { store.data.first().toSettings() }
    }

    fun update(block: (MutablePreferences) -> Unit) {
        scope.launch { store.edit { block(it) } }
    }

    fun setDarkTheme(value: Boolean) = update { it[DARK] = value }
    fun setAccent(value: AccentColor) = update { it[ACCENT] = value.name }
    fun setMoreTiles(value: Boolean) = update { it[MORE_TILES] = value }
    fun setStartBackground(uri: String?) = update { if (uri == null) it.remove(START_BG) else it[START_BG] = uri }
    fun setLockWallpaper(uri: String?) = update { if (uri == null) it.remove(LOCK_BG) else it[LOCK_BG] = uri }
    fun setLockEnabled(value: Boolean) = update { it[LOCK_ENABLED] = value }
    fun setLockShowNotifications(value: Boolean) = update { it[LOCK_NOTIF] = value }
    fun setLockPhotos(value: Boolean) = update { it[LOCK_PHOTOS] = value }
    fun setWpStatusBar(value: Boolean) = update { it[WP_STATUS] = value }
    fun setIconsOnAccent(value: Boolean) = update { it[ICONS_ACCENT] = value }
    fun setMetroIcons(value: Boolean) = update { it[METRO_ICONS] = value }
    fun setBrandColors(value: Boolean) = update { it[BRAND_COLORS] = value }
    fun setShowDuplicates(value: Boolean) = update { it[SHOW_DUPLICATES] = value }
    fun setLiveTiles(value: Boolean) = update { it[LIVE_TILES] = value }
    fun setSmallTilesRow(value: Boolean) = update { it[SMALL_ROW] = value }
    fun setGlance(value: Boolean) = update { it[GLANCE] = value }
    fun setSplash(value: Boolean) = update { it[SPLASH] = value }
    fun setFirstRunDone() = update { it[FIRST_RUN] = true }

    private fun Preferences.toSettings() = WpSettings(
        darkTheme = this[DARK] ?: true,
        accent = AccentColor.fromName(this[ACCENT]),
        moreTiles = this[MORE_TILES] ?: true,
        startBackground = this[START_BG],
        lockWallpaper = this[LOCK_BG],
        lockEnabled = this[LOCK_ENABLED] ?: false,
        lockShowNotifications = this[LOCK_NOTIF] ?: true,
        lockPhotos = this[LOCK_PHOTOS] ?: true,
        wpStatusBar = this[WP_STATUS] ?: true,
        iconsOnAccent = this[ICONS_ACCENT] ?: true,
        metroIcons = this[METRO_ICONS] ?: true,
        brandColors = this[BRAND_COLORS] ?: true,
        showDuplicates = this[SHOW_DUPLICATES] ?: false,
        liveTiles = this[LIVE_TILES] ?: true,
        smallTilesRow = this[SMALL_ROW] ?: false,
        glance = this[GLANCE] ?: false,
        splash = this[SPLASH] ?: true,
        firstRunDone = this[FIRST_RUN] ?: false,
    )

    companion object {
        private val DARK = booleanPreferencesKey("dark_theme")
        private val ACCENT = stringPreferencesKey("accent")
        private val MORE_TILES = booleanPreferencesKey("more_tiles")
        private val START_BG = stringPreferencesKey("start_background")
        private val LOCK_BG = stringPreferencesKey("lock_wallpaper")
        private val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        private val LOCK_NOTIF = booleanPreferencesKey("lock_notifications")
        private val LOCK_PHOTOS = booleanPreferencesKey("lock_photos")
        private val WP_STATUS = booleanPreferencesKey("wp_status_bar")
        private val ICONS_ACCENT = booleanPreferencesKey("icons_on_accent")
        private val METRO_ICONS = booleanPreferencesKey("metro_icons")
        private val BRAND_COLORS = booleanPreferencesKey("brand_colors")
        private val SHOW_DUPLICATES = booleanPreferencesKey("show_duplicates")
        private val LIVE_TILES = booleanPreferencesKey("live_tiles")
        private val SMALL_ROW = booleanPreferencesKey("small_tiles_row")
        private val GLANCE = booleanPreferencesKey("glance")
        private val SPLASH = booleanPreferencesKey("splash")
        private val FIRST_RUN = booleanPreferencesKey("first_run_done")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
