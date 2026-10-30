package com.wpreborn.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wpreborn.core.motion.TurnstileHost

/**
 * A minimal page stack, like the Windows Phone NavigationService: Navigate() pushes a page,
 * the hardware back button pops it. Every transition is a turnstile.
 */
@Stable
class WpNavigator<T : Any>(start: T) {
    private val stack = mutableStateListOf(Entry(start, 0))
    private var nextId = 1
    var lastWasBack by mutableStateOf(false)
        private set

    val current: Entry<T> get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun navigate(page: T) {
        lastWasBack = false
        stack.add(Entry(page, nextId++))
    }

    /** Replaces the current page (no back entry). */
    fun replace(page: T) {
        lastWasBack = false
        stack[stack.lastIndex] = Entry(page, nextId++)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        lastWasBack = true
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popToRoot() {
        if (stack.size <= 1) return
        lastWasBack = true
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    data class Entry<T>(val page: T, val id: Int)
}

@Composable
fun <T : Any> rememberNavigator(start: T): WpNavigator<T> = remember { WpNavigator(start) }

/** Hosts the navigator's current page with turnstile transitions and back handling. */
@Composable
fun <T : Any> WpNavHost(
    navigator: WpNavigator<T>,
    modifier: Modifier = Modifier,
    animateFirst: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    BackHandler(enabled = navigator.canPop) { navigator.pop() }
    TurnstileHost(
        target = navigator.current,
        isBack = navigator.lastWasBack,
        modifier = modifier,
        animateFirst = animateFirst,
    ) { entry -> content(entry.page) }
}
