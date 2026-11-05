package com.wpreborn.services.calls

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Android's in-call service: with Mozaik as the default Phone app, every call (incoming and
 * outgoing) is shown on Mozaik's own call screen, in the Windows Phone style.
 */
class WpInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        CallManager.service = this
        CallManager.add(call)
        // Incoming calls also come up through the full-screen notification (screen off or
        // another app in front); opening the screen directly works when Mozaik is in front.
        runCatching {
            startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    @Deprecated("Still the way to follow the audio route before API 34")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.setAudio(audioState)
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifications.cancel(this)
        super.onDestroy()
    }
}
