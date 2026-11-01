package com.wpreborn.data.apps

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.wpreborn.R
import com.wpreborn.apps.calendar.CalendarActivity
import com.wpreborn.apps.messages.MessagingActivity
import com.wpreborn.apps.music.MusicActivity
import com.wpreborn.apps.people.PeopleActivity
import com.wpreborn.apps.phone.PhoneActivity
import com.wpreborn.apps.photos.PhotosActivity
import com.wpreborn.apps.settings.SettingsActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.data.tiles.TileSize

/** The Windows Phone apps that ship inside this package. */
enum class BuiltInApp(
    val id: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val activity: Class<out Activity>,
    val defaultSize: TileSize,
) {
    Phone("phone", R.string.app_phone, WpIcons.Phone, PhoneActivity::class.java, TileSize.Medium),
    People("people", R.string.app_people, WpIcons.People, PeopleActivity::class.java, TileSize.Wide),
    Messaging("messaging", R.string.app_messaging, WpIcons.Messaging, MessagingActivity::class.java, TileSize.Medium),
    Music("music", R.string.app_music, WpIcons.Music, MusicActivity::class.java, TileSize.Wide),
    Photos("photos", R.string.app_photos, WpIcons.Photos, PhotosActivity::class.java, TileSize.Wide),
    Calendar("calendar", R.string.app_calendar, WpIcons.Calendar, CalendarActivity::class.java, TileSize.Wide),
    Settings("settings", R.string.app_settings, WpIcons.Settings, SettingsActivity::class.java, TileSize.Small),
    Keyboard("keyboard", R.string.app_keyboard, WpIcons.Keypad, com.wpreborn.keyboard.KeyboardSettingsActivity::class.java, TileSize.Small),
    Camera("camera", R.string.app_camera, WpIcons.Camera, com.wpreborn.apps.camera.CameraActivity::class.java, TileSize.Small),
    Calculator("calculator", R.string.app_calculator, com.wpreborn.core.icons.Glyphs.Calculator, com.wpreborn.apps.calculator.CalculatorActivity::class.java, TileSize.Small),
    Notes("notes", R.string.app_notes, com.wpreborn.core.icons.Glyphs.Note, com.wpreborn.apps.notes.NotesActivity::class.java, TileSize.Medium),
    Weather("weather", R.string.app_weather, com.wpreborn.core.icons.Glyphs.Weather, com.wpreborn.apps.weather.WeatherActivity::class.java, TileSize.Wide),
    Store("store", R.string.app_store, com.wpreborn.core.icons.Glyphs.Bag, com.wpreborn.apps.store.StoreActivity::class.java, TileSize.Medium),
    Alarms("alarms", R.string.app_alarms, WpIcons.Alarm, com.wpreborn.apps.alarms.AlarmsActivity::class.java, TileSize.Medium),
    Clock("clock", R.string.app_clock, WpIcons.Clock, com.wpreborn.apps.alarms.ClockActivity::class.java, TileSize.Small),
    Recorder("recorder", R.string.app_recorder, com.wpreborn.core.icons.Glyphs.Microphone, com.wpreborn.apps.recorder.RecorderActivity::class.java, TileSize.Small),
    Flashlight("flashlight", R.string.app_flashlight, WpIcons.Flashlight, com.wpreborn.apps.flashlight.FlashlightActivity::class.java, TileSize.Small),
    Maps("maps", R.string.app_maps, com.wpreborn.core.icons.Glyphs.MapPin, com.wpreborn.apps.maps.MapsActivity::class.java, TileSize.Medium),
    News("news", R.string.app_news, com.wpreborn.core.icons.Glyphs.News, com.wpreborn.apps.news.NewsActivity::class.java, TileSize.Wide),
    Browser("browser", R.string.app_browser, WpIcons.Globe, com.wpreborn.apps.browser.BrowserActivity::class.java, TileSize.Medium);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}
