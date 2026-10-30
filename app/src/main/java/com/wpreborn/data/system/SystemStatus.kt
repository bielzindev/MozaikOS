package com.wpreborn.data.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class BatteryState(val level: Int = 100, val charging: Boolean = false)

data class NetworkState(
    val wifiConnected: Boolean = false,
    /** 0..4 */
    val wifiLevel: Int = 0,
    /** 0..4, -1 when there is no SIM/radio information. */
    val cellLevel: Int = -1,
    val carrier: String = "",
    val mobileData: Boolean = false,
)

data class RadioState(
    val airplane: Boolean = false,
    val bluetooth: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
)

/**
 * Live system information for the Windows Phone status bar and Action Center.
 * Every flow registers its receivers only while somebody is collecting it.
 */
class SystemStatus private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val battery: StateFlow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(intent.toBattery())
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { trySend(it.toBattery()) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), BatteryState())

    val network: StateFlow<NetworkState> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun emit() {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val rssi = caps?.signalStrength ?: Int.MIN_VALUE
            val wifiLevel = if (wifi && rssi != Int.MIN_VALUE) {
                @Suppress("DEPRECATION")
                WifiManager.calculateSignalLevel(rssi, 5)
            } else if (wifi) 4 else 0
            trySend(NetworkState(wifi, wifiLevel.coerceIn(0, 4), cellLevel(), carrier(), cell))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = emit()
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        emit()
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), NetworkState())

    val radios: StateFlow<RadioState> = callbackFlow {
        fun emit() = trySend(readRadios())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                emit()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        emit()
        awaitClose { context.unregisterReceiver(receiver) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), RadioState())

    /** Current time, re-emitted at every minute boundary. */
    val clock: StateFlow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000 + 20)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun readRadios(): RadioState {
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val bt = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        val ringer = context.getSystemService(AudioManager::class.java).ringerMode
        return RadioState(airplane, bt, ringer)
    }

    @SuppressLint("MissingPermission")
    private fun cellLevel(): Int = try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        if (tm == null || tm.simState != TelephonyManager.SIM_STATE_READY) -1 else tm.signalStrength?.level ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun carrier(): String = try {
        context.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun Intent.toBattery(): BatteryState {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryState(level * 100 / scale, charging)
    }

    companion object {
        @Volatile
        private var instance: SystemStatus? = null

        fun get(context: Context): SystemStatus =
            instance ?: synchronized(this) {
                instance ?: SystemStatus(context.applicationContext).also { instance = it }
            }
    }
}
