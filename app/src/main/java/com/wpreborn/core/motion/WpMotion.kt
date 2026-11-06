package com.wpreborn.core.motion

/**
 * Which animations the app plays: the classic Mozaik ones (the default), or the ones taken from
 * Windows Phone's own code (the 8.1 animation library and the Windows Phone Toolkit). Set from
 * Settings > start + theme; read by every animation when it runs.
 */
object WpMotion {
    @Volatile var classic: Boolean = true
}
