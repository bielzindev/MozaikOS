package com.wpreborn.data.tiles

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiles other screens (the Store) ask Start to pin, by app key ("builtin:music"). Start takes
 * them as soon as it is running, so a request made while it is closed isn't lost.
 */
object PinRequests {
    val pending = MutableStateFlow<List<String>>(emptyList())

    fun pin(appKey: String) {
        if (appKey !in pending.value) pending.value = pending.value + appKey
    }
}
