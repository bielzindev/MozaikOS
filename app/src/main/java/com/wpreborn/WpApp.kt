package com.wpreborn

import android.app.Application
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.launcher.lock.LockActivity

class WpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read the theme synchronously so the very first frame already has the right colors.
        SettingsRepository.get(this).warmUp()
        LockActivity.register(this)
        com.wpreborn.services.music.AudioEffects.load(this)
        // A saved queue: connect to the player so it is restored (paused) and shows everywhere.
        if (com.wpreborn.services.music.QueueStore(this).hasQueue) com.wpreborn.services.music.MusicController.get(this).connect()
    }
}
