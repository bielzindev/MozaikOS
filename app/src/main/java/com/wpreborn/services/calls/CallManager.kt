package com.wpreborn.services.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One call as the call screen shows it. */
data class CallInfo(
    val call: Call,
    val state: Int,
    val number: String,
    /** When the call was answered (0 before). */
    val connectedAt: Long,
    val canHold: Boolean,
) {
    val ringing get() = state == Call.STATE_RINGING
    val dialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_NEW || state == Call.STATE_SELECT_PHONE_ACCOUNT
    val active get() = state == Call.STATE_ACTIVE
    val holding get() = state == Call.STATE_HOLDING
    val ended get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
}

/**
 * The calls of the phone while Mozaik is the default Phone app: Android hands them to
 * [WpInCallService], this keeps them and their state for the call screen and notification,
 * and carries out answer, hang up, hold, mute, speaker and keypad tones.
 */
object CallManager {
    internal var service: WpInCallService? = null
    private val calls = mutableListOf<Call>()
    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val state: StateFlow<List<CallInfo>> = _calls.asStateFlow()
    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    /** The call the screen is about: the ringing one first, else the one in progress. */
    val primary: CallInfo? get() = _calls.value.let { l -> l.firstOrNull { it.ringing } ?: l.firstOrNull { !it.holding && !it.ended } ?: l.firstOrNull() }

    internal fun add(call: Call) {
        if (call !in calls) {
            calls += call
            call.registerCallback(callback)
        }
        publish()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls -= call
        publish()
    }

    internal fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    @Suppress("DEPRECATION")
    private fun stateOf(c: Call): Int = if (Build.VERSION.SDK_INT >= 31) c.details.state else c.state

    private fun publish() {
        _calls.value = calls.map { c ->
            val d = c.details
            CallInfo(
                call = c,
                state = stateOf(c),
                number = d?.handle?.schemeSpecificPart.orEmpty(),
                connectedAt = d?.connectTimeMillis ?: 0L,
                canHold = d?.can(Call.Details.CAPABILITY_HOLD) == true,
            )
        }
        service?.let { CallNotifications.update(it) }
    }

    fun answer(c: Call) = c.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(c: Call) = c.reject(false, null)
    fun hangUp(c: Call) = c.disconnect()

    fun toggleHold(info: CallInfo) {
        if (info.holding) info.call.unhold() else if (info.canHold) info.call.hold()
    }

    val muted get() = _audio.value?.isMuted == true
    val speaker get() = _audio.value?.route == CallAudioState.ROUTE_SPEAKER
    val bluetooth get() = _audio.value?.route == CallAudioState.ROUTE_BLUETOOTH

    @Suppress("DEPRECATION")
    fun toggleMute() { service?.setMuted(!muted) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    val supportsBluetooth get() = (_audio.value?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0

    @Suppress("DEPRECATION")
    fun toggleBluetooth() {
        service?.setAudioRoute(if (bluetooth) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH)
    }

    fun tone(c: Call, digit: Char) {
        c.playDtmfTone(digit)
        c.stopDtmfTone()
    }
}
