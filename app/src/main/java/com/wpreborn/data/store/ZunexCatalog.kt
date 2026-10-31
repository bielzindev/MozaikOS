package com.wpreborn.data.store

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.wpreborn.R
import com.wpreborn.data.apps.BuiltInApp
import java.io.File

enum class StoreCategory(@StringRes val label: Int) {
    Communication(R.string.store_cat_communication),
    Media(R.string.store_cat_media),
    Productivity(R.string.store_cat_productivity),
    Utilities(R.string.store_cat_utilities),
    System(R.string.store_cat_system),
}

/**
 * One app of the ZUNEX family as the Store lists it. To add an app to the family, add its
 * [BuiltInApp] and an entry here: the Store, Start and the app list pick it up.
 */
data class CatalogApp(
    val app: BuiltInApp,
    val version: String,
    /** Raised when a new APK brings a newer version of this app: the Store offers the update. */
    val versionCode: Int,
    val category: StoreCategory,
    @StringRes val description: Int,
    @StringRes val whatsNew: Int,
    val color: Color,
    /** Android permissions the app uses, as labels. */
    val permissions: List<Int> = emptyList(),
    /** Apps it opens or relies on; installing this one installs them too. */
    val dependsOn: List<BuiltInApp> = emptyList(),
    /** Part of the system: cannot be turned off or uninstalled. */
    val required: Boolean = false,
    val featured: Boolean = false,
    /** What uninstalling removes. The user's own photos, songs, contacts and messages are never deleted. */
    val clearData: (Context) -> Unit = {},
) {
    val id get() = app.id
}

object ZunexCatalog {
    private fun prefs(vararg names: String): (Context) -> Unit = { c -> names.forEach { c.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() } }

    val apps: List<CatalogApp> = listOf(
        CatalogApp(
            BuiltInApp.Phone, "8.1.3", 3, StoreCategory.Communication, R.string.store_desc_phone, R.string.store_new_phone, Color(0xFF0050EF),
            permissions = listOf(R.string.perm_label_calls, R.string.perm_label_contacts), dependsOn = listOf(BuiltInApp.People),
        ),
        CatalogApp(
            BuiltInApp.People, "8.1.2", 2, StoreCategory.Communication, R.string.store_desc_people, R.string.store_new_people, Color(0xFFE51400),
            permissions = listOf(R.string.perm_label_contacts),
        ),
        CatalogApp(
            BuiltInApp.Messaging, "8.1.2", 2, StoreCategory.Communication, R.string.store_desc_messaging, R.string.store_new_messaging, Color(0xFF1BA1E2),
            permissions = listOf(R.string.perm_label_sms, R.string.perm_label_contacts), dependsOn = listOf(BuiltInApp.People),
        ),
        CatalogApp(
            BuiltInApp.Music, "3.0.0", 30, StoreCategory.Media, R.string.store_desc_music, R.string.store_new_music, Color(0xFFFA6800),
            permissions = listOf(R.string.perm_label_audio, R.string.perm_label_notifications), featured = true,
            clearData = { c ->
                prefs("music_queue", "music_favorites", "play_stats", "equalizer")(c)
                File(c.filesDir, "playlists.json").delete()
                com.wpreborn.services.music.MusicController.get(c).stop()
            },
        ),
        CatalogApp(
            BuiltInApp.Photos, "8.1.2", 2, StoreCategory.Media, R.string.store_desc_photos, R.string.store_new_photos, Color(0xFF8E44AD),
            permissions = listOf(R.string.perm_label_photos),
        ),
        CatalogApp(
            BuiltInApp.Camera, "2.0.0", 20, StoreCategory.Media, R.string.store_desc_camera, R.string.store_new_camera, Color(0xFF333333),
            permissions = listOf(R.string.perm_label_camera, R.string.perm_label_microphone), dependsOn = listOf(BuiltInApp.Photos), featured = true,
        ),
        CatalogApp(
            BuiltInApp.Calendar, "8.1.1", 1, StoreCategory.Productivity, R.string.store_desc_calendar, R.string.store_new_calendar, Color(0xFF00ABA9),
            permissions = listOf(R.string.perm_label_calendar),
        ),
        CatalogApp(
            BuiltInApp.Notes, "1.1.0", 11, StoreCategory.Productivity, R.string.store_desc_notes, R.string.store_new_notes, Color(0xFF80397B), featured = true,
            clearData = { c ->
                File(c.filesDir, "notes.json").delete()
                com.wpreborn.data.notes.NotesRepository.get(c).clearAll()
            },
        ),
        CatalogApp(
            BuiltInApp.Calculator, "1.1.0", 11, StoreCategory.Utilities, R.string.store_desc_calculator, R.string.store_new_calculator, Color(0xFF515C6B),
        ),
        CatalogApp(
            BuiltInApp.Weather, "2.0.0", 20, StoreCategory.Utilities, R.string.store_desc_weather, R.string.store_new_weather, Color(0xFF1E88E5),
            permissions = listOf(R.string.perm_label_internet, R.string.perm_label_location), featured = true,
            clearData = { c ->
                File(c.filesDir, "weather").deleteRecursively()
                com.wpreborn.data.weather.WeatherRepository.get(c).clearAll()
            },
        ),
        CatalogApp(
            BuiltInApp.Keyboard, "1.0.0", 10, StoreCategory.Utilities, R.string.store_desc_keyboard, R.string.store_new_keyboard, Color(0xFF6A00FF),
        ),
        CatalogApp(
            BuiltInApp.Alarms, "8.1.0", 1, StoreCategory.Utilities, R.string.store_desc_alarms, R.string.store_new_alarms, Color(0xFFE51400),
            permissions = listOf(R.string.perm_label_alarms, R.string.perm_label_notifications), featured = true,
            clearData = { c -> com.wpreborn.data.alarms.AlarmStore.get(c).let { s -> s.alarms.value.forEach { s.delete(it.id) } } },
        ),
        CatalogApp(
            BuiltInApp.Clock, "1.0.0", 10, StoreCategory.Utilities, R.string.store_desc_clock, R.string.store_new_clock, Color(0xFF00ABA9),
            dependsOn = listOf(BuiltInApp.Alarms),
            clearData = prefs("clock", "stopwatch"),
        ),
        CatalogApp(
            BuiltInApp.Recorder, "1.0.0", 10, StoreCategory.Utilities, R.string.store_desc_recorder, R.string.store_new_recorder, Color(0xFFD80073),
            permissions = listOf(R.string.perm_label_microphone),
        ),
        CatalogApp(
            BuiltInApp.Flashlight, "1.0.0", 10, StoreCategory.Utilities, R.string.store_desc_flashlight, R.string.store_new_flashlight, Color(0xFFF0A30A),
            permissions = listOf(R.string.perm_label_camera),
        ),
        CatalogApp(
            BuiltInApp.Maps, "1.0.0", 10, StoreCategory.Utilities, R.string.store_desc_maps, R.string.store_new_maps, Color(0xFF60A917),
            permissions = listOf(R.string.perm_label_internet, R.string.perm_label_location), featured = true,
            clearData = prefs("osmdroid"),
        ),
        CatalogApp(
            BuiltInApp.Settings, "8.1.0", 1, StoreCategory.System, R.string.store_desc_settings, R.string.store_new_settings, Color(0xFF647687), required = true,
        ),
        CatalogApp(
            BuiltInApp.Store, "1.0.0", 10, StoreCategory.System, R.string.store_desc_store, R.string.store_new_store, Color(0xFF00A300), required = true,
        ),
    )

    fun byId(id: String) = apps.firstOrNull { it.id == id }
    fun of(app: BuiltInApp) = apps.firstOrNull { it.app == app }
}
