package me.mjoacobf.openmusicstreaming.core.designsystem

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado para reordenar una `LazyColumn` manteniendo el dedo presionado y arrastrando.
 *
 * La lista que se dibuja ([items]) es una copia local que se reordena al instante mientras dura
 * el gesto; al soltar se llama una sola vez a [onCommit] con la posición inicial y la final, y
 * cuando la fuente real ([rememberReorderableState] `source`) cambia, la copia se vuelve a sincronizar.
 * Sin librerías externas (objetivo de eficiencia en equipos de gama baja).
 */
@Stable
class ReorderableState<T> internal constructor(
    val listState: LazyListState,
    private val key: (T) -> Any,
    private val headerCount: Int,
    private val scope: CoroutineScope,
    private val onCommit: (from: Int, to: Int) -> Unit,
) {
    /** Lista mostrada; usarla en `items(...)` de la LazyColumn. */
    val items = mutableStateListOf<T>()

    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var startIndex = -1
    private var initialOffset = 0
    private var delta by mutableFloatStateOf(0f)

    internal fun sync(source: List<T>) {
        if (draggingKey != null) return
        if (items.size != source.size || items.indices.any { items[it] != source[it] }) {
            items.clear()
            items.addAll(source)
        }
    }

    private fun info(key: Any) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Desplazamiento vertical (px) que sigue al dedo para el elemento arrastrado. */
    internal fun translationFor(key: Any): Float {
        if (key != draggingKey) return 0f
        val current = info(key) ?: return delta
        return initialOffset + delta - current.offset
    }

    internal fun start(key: Any) {
        startIndex = items.indexOfFirst { this.key(it) == key }
        if (startIndex < 0) return
        initialOffset = info(key)?.offset ?: 0
        delta = 0f
        draggingKey = key
    }

    internal fun drag(dy: Float) {
        val dragged = draggingKey ?: return
        delta += dy
        val current = info(dragged) ?: return
        val top = initialOffset + delta
        val center = top + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != dragged && center >= it.offset && center <= it.offset + it.size &&
                it.index - headerCount in items.indices
        }
        if (target != null) {
            val from = current.index - headerCount
            val to = target.index - headerCount
            if (from in items.indices) items.add(to, items.removeAt(from))
        }
        // Desplazamiento automático al acercarse a los bordes
        val layout = listState.layoutInfo
        val edge = 72f
        val step = when {
            top < layout.viewportStartOffset + edge -> -24f
            top + current.size > layout.viewportEndOffset - edge -> 24f
            else -> 0f
        }
        if (step != 0f) scope.launch { listState.scrollBy(step) }
    }

    internal fun end() {
        val dragged = draggingKey ?: return
        val finalIndex = items.indexOfFirst { key(it) == dragged }
        draggingKey = null
        delta = 0f
        if (startIndex >= 0 && finalIndex >= 0 && finalIndex != startIndex) onCommit(startIndex, finalIndex)
        startIndex = -1
    }
}

/**
 * @param source lista real (p. ej. la cola del reproductor); [key] debe dar una clave única y estable.
 * @param headerCount elementos fijos de la lista antes de los reordenables (cabeceras).
 * @param onCommit se invoca al soltar con (posición inicial, posición final) dentro de [source].
 */
@Composable
fun <T> rememberReorderableState(
    source: List<T>,
    key: (T) -> Any,
    listState: LazyListState = rememberLazyListState(),
    headerCount: Int = 0,
    onCommit: (from: Int, to: Int) -> Unit,
): ReorderableState<T> {
    val scope = rememberCoroutineScope()
    val latestKey by rememberUpdatedState(key)
    val latestCommit by rememberUpdatedState(onCommit)
    val state = remember(listState, headerCount) {
        ReorderableState<T>(listState, { latestKey(it) }, headerCount, scope) { f, t -> latestCommit(f, t) }
    }
    LaunchedEffect(source, state.draggingKey) { state.sync(source) }
    return state
}

/**
 * Modificador de cada elemento: mantener presionado y arrastrar lo reordena; los demás se
 * desplazan con animación. Un toque normal sigue llegando al `onClick` del elemento.
 */
fun <T> LazyItemScope.reorderable(state: ReorderableState<T>, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return Modifier
        .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
        .graphicsLayer {
            translationY = state.translationFor(key)
            if (dragging) {
                scaleX = 1.03f
                scaleY = 1.03f
            }
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = state::end,
                onDragCancel = state::end,
            )
        }
}
