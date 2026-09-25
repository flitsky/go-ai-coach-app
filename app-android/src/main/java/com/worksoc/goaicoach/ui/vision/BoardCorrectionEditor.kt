package com.worksoc.goaicoach.ui.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.KomiOptions
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.AnalysisResult
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.ui.GoBoard

enum class BoardEditTool {
    Toggle, // 탭 시 빈칸 -> 흑 -> 백 -> 빈칸 순환
    Black,  // 흑돌 배치
    White,  // 백돌 배치
    Eraser, // 지우개
}

@Composable
internal fun BoardCorrectionEditor(
    initialGameState: GameState,
    isAnalyzing: Boolean,
    analysisResult: AnalysisResult?,
    scoreEstimate: ScoreEstimate?,
    onStartAnalysis: (GameState) -> Unit,
    onPlayFromHere: (GameState) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardSize by remember { mutableStateOf(initialGameState.boardSize) }
    var stones by remember { mutableStateOf(initialGameState.stones) }
    var nextPlayer by remember { mutableStateOf(initialGameState.nextPlayer) }
    var komi by remember { mutableDoubleStateOf(initialGameState.komi) }
    var ruleset by remember { mutableStateOf(initialGameState.ruleset) }
    var selectedTool by remember { mutableStateOf(BoardEditTool.Toggle) }

    val currentGameState = remember(boardSize, stones, nextPlayer, komi, ruleset) {
        GameState(
            boardSize = boardSize,
            ruleset = ruleset,
            nextPlayer = nextPlayer,
            stones = stones,
            moves = emptyList(),
            komi = komi,
        )
    }

    val blackStoneCount = remember(stones) { stones.values.count { it == StoneColor.Black } }
    val whiteStoneCount = remember(stones) { stones.values.count { it == StoneColor.White } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. 헤더 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "국면 확인 및 보정",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row {
                OutlinedButton(
                    onClick = { stones = emptyMap() },
                    enabled = stones.isNotEmpty() && !isAnalyzing,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("초기화", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRetake,
                    enabled = !isAnalyzing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text("재촬영", fontSize = 13.sp)
                }
            }
        }

        // 2. 바둑판 컴포넌트 (GoBoard)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = currentGameState,
                candidateMoves = analysisResult?.candidates ?: emptyList(),
                moveReviews = emptyList(),
                ownershipEstimate = scoreEstimate?.ownership,
                uxOptions = KaTrainUxOptions(
                    showCoordinates = true,
                    showOwnershipOverlay = scoreEstimate?.ownership != null,
                ),
                inputEnabled = !isAnalyzing,
                engineActivityIndicator = null,
                onCoordinateTap = { coord ->
                    if (isAnalyzing) return@GoBoard
                    val currentStone = stones[coord]
                    val updatedStones = stones.toMutableMap()

                    when (selectedTool) {
                        BoardEditTool.Toggle -> {
                            when (currentStone) {
                                null -> updatedStones[coord] = StoneColor.Black
                                StoneColor.Black -> updatedStones[coord] = StoneColor.White
                                StoneColor.White -> updatedStones.remove(coord)
                            }
                        }
                        BoardEditTool.Black -> {
                            if (currentStone == StoneColor.Black) updatedStones.remove(coord)
                            else updatedStones[coord] = StoneColor.Black
                        }
                        BoardEditTool.White -> {
                            if (currentStone == StoneColor.White) updatedStones.remove(coord)
                            else updatedStones[coord] = StoneColor.White
                        }
                        BoardEditTool.Eraser -> {
                            updatedStones.remove(coord)
                        }
                    }
                    stones = updatedStones
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 편집 도구 선택 툴바
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "터치 도구",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "흑 ${blackStoneCount}개  /  백 ${whiteStoneCount}개",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Toggle,
                        onClick = { selectedTool = BoardEditTool.Toggle },
                        label = { Text("자동 순환", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Black,
                        onClick = { selectedTool = BoardEditTool.Black },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("흑돌", fontSize = 12.sp)
                            }
                        },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.White,
                        onClick = { selectedTool = BoardEditTool.White },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Gray, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("백돌", fontSize = 12.sp)
                            }
                        },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Eraser,
                        onClick = { selectedTool = BoardEditTool.Eraser },
                        label = { Text("지우개", fontSize = 12.sp) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. 대국 조건 설정 (차례, 덤, 룰)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 다음 차례 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("다음 착수 차례", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = nextPlayer == StoneColor.Black,
                            onClick = { nextPlayer = StoneColor.Black },
                            label = { Text("흑 차례 (선착)") },
                        )
                        FilterChip(
                            selected = nextPlayer == StoneColor.White,
                            onClick = { nextPlayer = StoneColor.White },
                            label = { Text("백 차례") },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 덤 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("덤 (Komi)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KomiOptions.forEach { komiOption ->
                            FilterChip(
                                selected = komi == komiOption,
                                onClick = { komi = komiOption },
                                label = { Text("${komiOption}집", fontSize = 12.sp) },
                            )
                        }
                    }
                }
            }
        }

        // 5. 분석 결과 영역 (결과가 있을 때 표시)
        if (analysisResult != null || scoreEstimate != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AI 분석 결과",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    scoreEstimate?.let { estimate ->
                        estimate.whiteWinRate?.let { whiteWin ->
                            val blackWin = (1.0 - whiteWin) * 100
                            Text(
                                text = "승률: 흑 %.1f%%  /  백 %.1f%%".format(blackWin, whiteWin * 100),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        estimate.whiteScoreLead?.let { whiteLead ->
                            val leadText = if (whiteLead >= 0) "백 +%.1f집 우세".format(whiteLead) else "흑 +%.1f집 우세".format(-whiteLead)
                            Text(
                                text = "형세 판단: $leadText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    analysisResult?.let { result ->
                        if (result.candidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "AI 추천수 ${result.candidates.size}개 탐색 완료 (판 위의 마커 확인)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. 하단 CTA 액션 버튼들
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onStartAnalysis(currentGameState) },
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("AI가 국면을 분석하고 있습니다...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("AI 추천수 및 형세 분석하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { onPlayFromHere(currentGameState) },
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("이 국면부터 AI와 대국하기", fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
