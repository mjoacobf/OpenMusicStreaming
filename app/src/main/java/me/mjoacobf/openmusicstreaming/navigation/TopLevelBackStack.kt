package me.mjoacobf.openmusicstreaming.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Una pila por pestaña de la barra inferior (receta "Common UI" de Navigation 3).
 * El historial de cada pestaña se conserva al cambiar entre ellas.
 */
class TopLevelBackStack(startKey: NavKey) {
    private val topLevelStacks: LinkedHashMap<NavKey, SnapshotStateList<NavKey>> =
        linkedMapOf(startKey to mutableStateListOf(startKey))

    var topLevelKey by mutableStateOf(startKey)
        private set

    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() {
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }
    }

    fun addTopLevel(key: NavKey) {
        val existing = topLevelStacks.remove(key)
        topLevelStacks[key] = existing ?: mutableStateListOf(key)
        topLevelKey = key
        updateBackStack()
    }

    /** Tocar la pestaña activa vuelve a su pantalla raíz. */
    fun resetTopLevel(key: NavKey) {
        topLevelStacks[key]?.let { stack ->
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        updateBackStack()
    }

    fun add(key: NavKey) {
        val stack = topLevelStacks[topLevelKey] ?: return
        if (stack.lastOrNull() == key) return
        stack.add(key)
        updateBackStack()
    }

    fun removeLast(): Boolean {
        if (backStack.size <= 1) return false
        val stack = topLevelStacks[topLevelKey] ?: return false
        val removed = stack.removeLastOrNull()
        if (stack.isEmpty()) topLevelStacks.remove(removed)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
        return true
    }
}
