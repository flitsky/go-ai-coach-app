package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.l10n.ProvideUiLanguage

/** 카탈로그 「바둑판」(W11) — 누르면 번갈아 둔다(규칙 · 따냄 · 놓을 수 없는 자리의 진동). 좌표 · 수순 번호를 켜서 캔버스 글자를 본다. */
@Composable
internal fun CatalogBoard() {
    var size by remember { mutableStateOf(BoardSize.Nine) }
    var state by remember(size) { mutableStateOf(GameState.empty(boardSize = size)) }
    var coordinates by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    ProvideUiLanguage { _, _ ->
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(BoardSize.Nine, BoardSize.Thirteen, BoardSize.Nineteen).forEach { option ->
                    FilterChip(selected = size == option, onClick = { size = option }, label = { Text("${option.value}줄") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = coordinates, onClick = { coordinates = !coordinates }, label = { Text("좌표") })
                FilterChip(selected = numbers, onClick = { numbers = !numbers }, label = { Text("수순 번호") })
            }
            GoBoard(
                gameState = state,
                candidateMoves = emptyList(),
                moveReviews = emptyList(),
                ownershipEstimate = null,
                uxOptions = KaTrainUxOptions(showCoordinates = coordinates, showMoveNumbers = numbers),
                inputEnabled = true,
                engineActivityIndicator = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                onCoordinateTap = { coordinate ->
                    runCatching { state.play(Move.Play(state.nextPlayer, coordinate)) }.onSuccess { state = it }
                },
            )
            Text("수 ${state.moves.size} · 흑 따냄 ${state.capturedByBlack} · 백 따냄 ${state.capturedByWhite}")
        }
    }
}
