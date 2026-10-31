package com.wpreborn.apps.alarms

import android.app.KeyguardManager
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.alarms.AlarmReceiver
import com.wpreborn.data.alarms.AlarmStore
import java.util.Date

/**
 * The ringing alarm, as on Windows Phone: shown over the lock screen with the screen turned
 * on, the alarm sound looping and the phone vibrating, with "snooze" (tap the duration to
 * change it) and "dismiss". It snoozes by itself after ten minutes.
 */
class AlarmRingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var alarmId = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoSnooze = Runnable { snooze(10) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        alarmId = intent.getIntExtra(AlarmStore.EXTRA_ID, 0)
        val alarm = AlarmStore.get(this).get(alarmId)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        startRinging()
        handler.postDelayed(autoSnooze, 10 * 60_000L)
        val label = alarm?.label?.takeIf { it.isNotBlank() }
        val isTimer = alarm?.timer == true || alarmId == AlarmStore.TIMER_ID
        setContent {
            WpTheme {
                val snoozeOptions = listOf(5, 10, 15, 20, 30)
                var snoozeIndex by remember { mutableIntStateOf(1) }
                Column(Modifier.fillMaxSize().background(Wp.accent).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
                    WpText(stringResource(if (isTimer) R.string.clock_timer else R.string.app_alarms).uppercase(), style = WpType.AppTitle, color = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.height(24.dp))
                    WpText(DateFormat.getTimeFormat(this@AlarmRingActivity).format(Date()), style = WpType.LockClock, color = androidx.compose.ui.graphics.Color.White)
                    WpText(
                        label ?: stringResource(if (isTimer) R.string.clock_timer_done else R.string.alarm_default_name),
                        style = WpType.ExtraLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!isTimer) {
                        WpText(stringResource(R.string.alarm_snooze_for), style = WpType.Subtle, color = androidx.compose.ui.graphics.Color.White)
                        WpText(
                            stringResource(R.string.alarm_minutes, snoozeOptions[snoozeIndex]),
                            style = WpType.Large,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.wpClickable { snoozeIndex = (snoozeIndex + 1) % snoozeOptions.size }.padding(vertical = 8.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isTimer) WpButton(stringResource(R.string.alarm_snooze), Modifier.weight(1f)) { snooze(snoozeOptions[snoozeIndex]) }
                        WpButton(stringResource(R.string.alarm_dismiss), Modifier.weight(1f)) { dismiss() }
                    }
                }
            }
        }
    }

    private fun startRinging() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator()?.let { v ->
            val pattern = longArrayOf(0, 800, 600)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoSnooze)
        runCatching { player?.stop(); player?.release() }
        player = null
        vibrator()?.cancel()
        getSystemService(NotificationManager::class.java)?.cancel(AlarmReceiver.NOTIFICATION_BASE + alarmId)
    }

    private fun snooze(minutes: Int) {
        stopRinging()
        AlarmStore.get(this).snooze(alarmId, minutes)
        finish()
    }

    private fun dismiss() {
        stopRinging()
        if (alarmId == AlarmStore.TIMER_ID) AlarmStore.get(this).cancelTimer()
        finish()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    @Suppress("unused")
    private fun keyguard() = getSystemService(KeyguardManager::class.java)
}
