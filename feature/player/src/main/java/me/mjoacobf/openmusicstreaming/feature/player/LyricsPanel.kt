package me.mjoacobf.openmusicstreaming.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.model.Lyrics

/**
 * Panel de letra que sustituye a la carátula. Con letra sincronizada resalta la línea actual y
 * la mantiene a la vista; tocar una línea salta a ese momento. Función experimental: las letras
 * vienen de LRCLIB y no todas las canciones las tienen.
 */
@Composable
internal fun LyricsPanel(
    state: PlayerViewModel.LyricsUiState,
    viewModel: PlayerViewModel,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Letra", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AssistChip(onClick = {}, label = { Text("Experimental") })
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                PlayerViewModel.LyricsUiState.Idle, PlayerViewModel.LyricsUiState.Loading -> CircularProgressIndicator()
                PlayerViewModel.LyricsUiState.NotFound -> Message("No hay letra disponible para esta canción.", null)
                is PlayerViewModel.LyricsUiState.Error -> Message(state.message, onRetry)
                is PlayerViewModel.LyricsUiState.Ready -> LyricsLines(state.lyrics, viewModel)
            }
        }
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, viewModel: PlayerViewModel) {
    val listState = rememberLazyListState()
    // La posición cambia varias veces por segundo: solo se recompone cuando cambia la línea activa
    val position = viewModel.position.collectAsStateWithLifecycle()
    val active by remember(lyrics) { derivedStateOf { lyrics.activeIndex(position.value) } }

    LaunchedEffect(active) {
        if (lyrics.synced && active >= 0) {
            val offset = -(listState.layoutInfo.viewportSize.height / 3)
            listState.animateScrollToItem(active, offset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines, key = { index, _ -> index }, contentType = { _, _ -> "line" }) { index, line ->
            val isActive = lyrics.synced && index == active
            Text(
                line.text.ifBlank { "♪" },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (lyrics.synced) Modifier.clickable { viewModel.seekTo(line.timeMs) } else Modifier),
                style = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}
