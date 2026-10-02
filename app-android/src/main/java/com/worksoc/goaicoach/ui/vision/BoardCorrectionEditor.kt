package com.worksoc.goaicoach.ui.vision

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.StonePalette
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.boardScanStringsFor
import com.worksoc.goaicoach.vision.stonesWithoutLiberties

enum class BoardEditTool {
    Toggle, // 탭 시 빈칸 -> 흑 -> 백 -> 빈칸 순환
    Black,  // 흑돌 배치
    White,  // 백돌 배치
    Eraser, // 지우개
}

/**
 * 인식한 국면을 확인·보정한다.
 *
 * ⚠️ **[boardPhoto](반듯하게 편 판 사진)와 맞바꿔 보게 한다**(백로그 #210) — 옛 화면에는 사진이 없어서, 인식이 틀린 돌을
 * 찾으려면 기억에 기대야 했다. 같은 자리·같은 크기로 바꿔 보는 것이 폰 화면에서 비교하기 가장 쉽다.
 * 숨 없는 돌 무리([stonesWithoutLiberties])가 있으면 "인식이 틀렸을 수 있다"고 알린다 — 지우지는 않는다.
 */
@Composable
internal fun BoardCorrectionEditor(
    initialGameState: GameState,
    boardPhoto: Bitmap?,
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
    var showPhoto by remember { mutableStateOf(false) }
    val text = boardScanStringsFor(LocalUiStrings.current.language)
    val photoBitmap = remember(boardPhoto) { boardPhoto?.asImageBitmap() }

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
    val stonesWithoutLiberty = remember(stones, boardSize) { stonesWithoutLiberties(stones, boardSize).size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // targetSdk 36부터 앱이 시스템 바 영역까지 그린다(#25) — 제목·재촬영 버튼이 상태 표시줄에 깔리지 않게.
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. 헤더 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text.editorTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row {
                OutlinedButton(
                    onClick = { stones = emptyMap() },
                    enabled = stones.isNotEmpty() && !isAnalyzing,
                    shape = RoundedCornerShape(AppRadius.Corner8),
                ) {
                    Text(text.reset, fontSize = AppTextSize.Text13)
                }
                Spacer(modifier = Modifier.width(AppSpacing.Space8))
                Button(
                    onClick = onRetake,
                    enabled = !isAnalyzing,
                    shape = RoundedCornerShape(AppRadius.Corner8),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text(text.adjustPins, fontSize = AppTextSize.Text13)
                }
            }
        }

        // 보정한 판 ↔ 원본 사진
        if (photoBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Space16),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
            ) {
                FilterChip(selected = !showPhoto, onClick = { showPhoto = false }, label = { Text(text.viewBoard, fontSize = AppTextSize.Text12) })
                FilterChip(selected = showPhoto, onClick = { showPhoto = true }, label = { Text(text.viewPhoto, fontSize = AppTextSize.Text12) })
            }
        }
        if (stonesWithoutLiberty > 0) {
            Text(
                text = text.noLibertyWarning(stonesWithoutLiberty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space4),
            )
        }

        // 2. 바둑판 컴포넌트 (GoBoard)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space8),
            contentAlignment = Alignment.Center,
        ) {
            if (showPhoto && photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = text.photoDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
            } else GoBoard(
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

        Spacer(modifier = Modifier.height(AppSpacing.Space8))

        // 3. 편집 도구 선택 툴바
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space16),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(AppRadius.Corner12),
        ) {
            Column(modifier = Modifier.padding(AppSpacing.Space12)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text.toolsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = text.stoneCounts(blackStoneCount, whiteStoneCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(AppSpacing.Space8))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6),
                ) {
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Toggle,
                        onClick = { selectedTool = BoardEditTool.Toggle },
                        label = { Text(text.toolToggle, fontSize = AppTextSize.Text12) },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Black,
                        onClick = { selectedTool = BoardEditTool.Black },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StonePalette.Black))
                                Spacer(modifier = Modifier.width(AppSpacing.Space4))
                                Text(text.toolBlack, fontSize = AppTextSize.Text12)
                            }
                        },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.White,
                        onClick = { selectedTool = BoardEditTool.White },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StonePalette.White).border(AppBorderWidth.Hairline, StonePalette.WhiteOutline, CircleShape))
                                Spacer(modifier = Modifier.width(AppSpacing.Space4))
                                Text(text.toolWhite, fontSize = AppTextSize.Text12)
                            }
                        },
                    )
                    FilterChip(
                        selected = selectedTool == BoardEditTool.Eraser,
                        onClick = { selectedTool = BoardEditTool.Eraser },
                        label = { Text(text.toolEraser, fontSize = AppTextSize.Text12) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space8))

        // 4. 대국 조건 설정 (차례, 덤, 룰)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space16),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(AppRadius.Corner12),
        ) {
            Column(modifier = Modifier.padding(AppSpacing.Space12)) {
                // 다음 차례 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text.nextTurnLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                        FilterChip(
                            selected = nextPlayer == StoneColor.Black,
                            onClick = { nextPlayer = StoneColor.Black },
                            label = { Text(text.blackTurn) },
                        )
                        FilterChip(
                            selected = nextPlayer == StoneColor.White,
                            onClick = { nextPlayer = StoneColor.White },
                            label = { Text(text.whiteTurn) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.Space8))

                // 덤 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text.komiLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                        KomiOptions.forEach { komiOption ->
                            FilterChip(
                                selected = komi == komiOption,
                                onClick = { komi = komiOption },
                                label = { Text(text.komiChip(komiOption), fontSize = AppTextSize.Text12) },
                            )
                        }
                    }
                }
            }
        }

        // 5. 분석 결과 영역 (결과가 있을 때 표시)
        if (analysisResult != null || scoreEstimate != null) {
            Spacer(modifier = Modifier.height(AppSpacing.Space12))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.Space16),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(AppRadius.Corner12),
            ) {
                Column(modifier = Modifier.padding(AppSpacing.Space14)) {
                    Text(
                        text = text.analysisTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.Space6))

                    scoreEstimate?.let { estimate ->
                        estimate.whiteWinRate?.let { whiteWin ->
                            val blackWin = (1.0 - whiteWin) * 100
                            Text(
                                text = text.winRate(blackWin, whiteWin * 100),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        estimate.whiteScoreLead?.let { whiteLead ->
                            Text(
                                text = text.scoreLead(whiteLead >= 0, kotlin.math.abs(whiteLead)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    analysisResult?.let { result ->
                        if (result.candidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AppSpacing.Space4))
                            Text(
                                text = text.candidatesFound(result.candidates.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space16))

        // 6. 하단 CTA 액션 버튼들
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space16),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            Button(
                onClick = { onStartAnalysis(currentGameState) },
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(AppRadius.Corner12),
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.Space10))
                    Text(text.analyzing, fontSize = AppTextSize.Text16, fontWeight = FontWeight.Bold)
                } else {
                    Text(text.analyze, fontSize = AppTextSize.Text16, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { onPlayFromHere(currentGameState) },
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(AppRadius.Corner12),
            ) {
                Text(text.playFromHere, fontSize = AppTextSize.Text15)
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space24))
    }
}
