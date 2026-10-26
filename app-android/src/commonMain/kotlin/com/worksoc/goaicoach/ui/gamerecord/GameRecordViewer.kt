package com.worksoc.goaicoach.ui.gamerecord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.application.gamehistory.canMeasureScoreSwings
import com.worksoc.goaicoach.application.gamehistory.deriveScoreSwingHighlights
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.gamerecord.GameRecord
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.designsystem.ActionButton
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.foundation.PlatformBackHandler
import com.worksoc.goaicoach.ui.history.ReplayAnalysisEngine
import com.worksoc.goaicoach.ui.history.ReplayControls
import com.worksoc.goaicoach.ui.history.ReplayHeader
import com.worksoc.goaicoach.ui.history.ReplayScoreSection
import com.worksoc.goaicoach.ui.history.ReplayScoreSwingSection
import com.worksoc.goaicoach.ui.history.ReplaySectionGap
import com.worksoc.goaicoach.ui.history.rememberReplayAnalysis
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.gameRecordAutoPlayFor
import com.worksoc.goaicoach.ui.l10n.gameRecordPauseFor
import com.worksoc.goaicoach.ui.l10n.gameRecordPlayersFor
import com.worksoc.goaicoach.ui.l10n.gameRecordResultFor
import com.worksoc.goaicoach.ui.l10n.gameRecordSecondsFor
import com.worksoc.goaicoach.ui.l10n.gameReplayTruncatedFor
import com.worksoc.goaicoach.ui.play.KeepScreenOnWhile
import kotlinx.coroutines.delay

/**
 * 보기 화면에 여는 기보 한 판 — [key]는 이 판의 분석 기록(1회권 장부)을 가르는 열쇠다.
 * [evaluation]은 앱에 실은 기보의 미리 잰 형세(#269) — 있으면 판 전체 형세 그래프·변곡점이 보이고 형세 보기가 엔진 없이 그려진다. 사용자가 연 파일은 없다.
 */
internal data class OpenedGameRecord(val key: String, val title: String, val record: GameRecord, val evaluation: GameRecordEvaluation? = null)

/**
 * 기보 한 판을 한 수씩 넘겨 보는 화면(백로그 #269) — 다시보기(`GameReplayScreen`)의 부품을 그대로 쓴다: 머리줄 · 판 · 이동 버튼 ·
 * 「형세 보기」·「추천 수」(누를 때만 엔진을 부르고, 게이트는 `ReplayAnalysis.kt` — 다시보기와 같은 게이트, 사용자 2026-10-18).
 *
 * 다시보기와 다른 것: 내 대국이 아니라서 판 위 착수 평가 색·변곡점·형세 그래프가 없다(기보에는 형세 기록이 없다) · **첫 수 전부터 연다**
 * (놓아 보기는 처음부터다 — 스레드가 정한 시작점) · 배너를 붙이지 않는다(광고 자리를 늘리는 것은 사용자가 정할 일이다).
 * ⚠️ 시스템 뒤로 가기를 여기서 잡는다 — 셸의 `BackHandler`는 홈으로 간다. 이 화면은 목록 위에 그려져 나중에 등록되므로 먼저 받는다.
 */
@Composable
internal fun GameRecordViewer(
    opened: OpenedGameRecord,
    analysisEngine: ReplayAnalysisEngine,
    onBackClick: () -> Unit,
    /** 자동 진행의 착수 간격(초) — 목록 화면이 들고 있어 다른 판을 열어도 고른 간격이 남는다. */
    autoPlaySeconds: Int,
    onAutoPlaySecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    PlatformBackHandler { onBackClick() }

    val record = opened.record
    val timeline = remember(opened.key) { buildGameReplayTimeline(setup = record.setup, moves = record.moves) }
    var moveNumber by remember(opened.key) { mutableIntStateOf(0) }
    var showMoveNumbers by remember { mutableStateOf(false) }
    // 「자동 진행」(사용자 2026-10-19) — 정한 간격마다 한 수씩. 마지막 수에 닿으면 멈추고, 이동 버튼을 누르면 멈춘다(스레드가 정한 규칙).
    // 간격을 바꾸면 효과가 새로 돌아 진행 중에도 새 간격으로 이어 간다. 진행 중에는 화면을 켜 둔다(관전과 같은 도구).
    var isAutoPlaying by remember(opened.key) { mutableStateOf(false) }
    LaunchedEffect(opened.key, isAutoPlaying, autoPlaySeconds) {
        while (isAutoPlaying && moveNumber < timeline.lastMoveNumber) {
            delay(autoPlaySeconds * 1_000L)
            moveNumber = (moveNumber + 1).coerceAtMost(timeline.lastMoveNumber)
        }
        isAutoPlaying = false
    }
    KeepScreenOnWhile(active = isAutoPlaying)
    // 미리 잰 형세(#269, 사용자 2026-10-19) — 판 전체 그래프·변곡점은 다시보기처럼 누구에게나, 판 위 형세는 다시보기와 같은 게이트로.
    val evaluation = opened.evaluation
    val replayData = remember(opened.key) { evaluation?.let { GameReplayData(moves = record.moves, scoreSnapshots = it.snapshots) } }
    val scoreSwings = remember(opened.key) { replayData?.let { deriveScoreSwingHighlights(it.scoreSnapshots) }.orEmpty() }
    val canMeasureSwings = remember(opened.key) { replayData?.let { canMeasureScoreSwings(it.scoreSnapshots) } ?: false }
    var isScoreExpanded by remember(opened.key) { mutableStateOf(false) }
    fun jumpTo(target: Int) {
        isAutoPlaying = false
        moveNumber = target.coerceIn(0, timeline.lastMoveNumber)
    }
    val state = timeline.stateAt(moveNumber)
    val analysis = rememberReplayAnalysis(
        engine = analysisEngine,
        replayKey = opened.key,
        moveNumber = moveNumber,
        positionAt = timeline::stateAt,
        precomputedEstimate = evaluation?.let { it::estimateAt },
    )
    val uxOptions = remember(showMoveNumbers) {
        KaTrainUxOptions(
            showMoveNumbers = showMoveNumbers,
            showMoveReview = false,
            showOwnershipOverlay = false,
            isDirectPlayEnabled = false,
            isPlayHapticEnabled = false,
            isPlayEffectEnabled = false,
        )
    }
    val subtitle = listOfNotNull(
        gameRecordPlayersFor(language, record.info.blackName, record.info.whiteName),
        gameRecordResultFor(language, record.info.result) ?: record.info.result,
        record.info.date,
    ).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(ReplaySectionGap),
    ) {
        ReplayHeader(title = opened.title, subtitle = subtitle, closeLabel = strings.close, onBackClick = onBackClick)

        if (timeline.isTruncated) {
            Text(
                text = gameReplayTruncatedFor(language, timeline.truncatedAtMoveNumber!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppSpacing.Space16),
            )
        }

        // 다시보기와 같은 차례 — 변곡점 → 형세(요약 + 판 전체 그래프, 누르면 큰 그래프) → 판. 큰 그래프가 열린 동안은 변곡점을 접는다(판 높이, #226).
        if (replayData != null) {
            if (!isScoreExpanded) {
                ReplayScoreSwingSection(
                    canMeasureSwings = canMeasureSwings,
                    swings = scoreSwings,
                    isRemeasuringScores = false,
                    currentMoveNumber = moveNumber,
                    strings = strings,
                    onJumpTo = ::jumpTo,
                )
            }
            ReplayScoreSection(
                replay = replayData,
                moveNumber = moveNumber,
                lastMoveNumber = timeline.lastMoveNumber,
                capturedByBlack = state.capturedBy(StoneColor.Black),
                capturedByWhite = state.capturedBy(StoneColor.White),
                isExpanded = isScoreExpanded,
                onExpandedChange = { isScoreExpanded = it },
                onSeek = ::jumpTo,
                strings = strings,
            )
        }

        // 다시보기와 같다 — 스크롤 부모를 두지 않는다(판이 끌기를 삼킨다, 함정 44). 판이 남는 높이를 받는다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = AppSpacing.Space12),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = state,
                candidateMoves = analysis.candidateMoves,
                moveReviews = emptyList(),
                ownershipEstimate = analysis.ownership,
                uxOptions = uxOptions,
                inputEnabled = false,
                engineActivityIndicator = analysis.activity,
                modifier = Modifier.fillMaxSize(),
                onCoordinateTap = {},
                isGameEnded = moveNumber == timeline.lastMoveNumber,
            )
        }

        ReplayControls(
            moveNumber = moveNumber,
            lastMoveNumber = timeline.lastMoveNumber,
            strings = strings,
            showMoveNumbers = showMoveNumbers,
            analysis = analysis,
            onMoveNumberChange = { next ->
                isAutoPlaying = false
                moveNumber = next.coerceIn(0, timeline.lastMoveNumber)
            },
            onToggleMoveNumbers = { showMoveNumbers = !showMoveNumbers },
            extraRow = {
                GameRecordAutoPlayRow(
                    isPlaying = isAutoPlaying,
                    canPlay = moveNumber < timeline.lastMoveNumber,
                    seconds = autoPlaySeconds,
                    onToggle = { isAutoPlaying = !isAutoPlaying },
                    onSecondsChange = onAutoPlaySecondsChange,
                )
            },
        )
    }
}

/** 자동 진행의 착수 간격으로 고를 수 있는 초 — 기본 1초(사용자 2026-10-19). */
internal val GameRecordAutoPlaySeconds: IntRange = 1..5
internal const val DefaultGameRecordAutoPlaySeconds = 1

/** 「▶ 자동 진행」/「❚❚ 멈춤」 + 착수 간격(누르면 1~5초 드롭다운). */
@Composable
private fun GameRecordAutoPlayRow(
    isPlaying: Boolean,
    canPlay: Boolean,
    seconds: Int,
    onToggle: () -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val language = LocalUiStrings.current.language
    var isMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space6),
    ) {
        ActionButton(
            label = if (isPlaying) gameRecordPauseFor(language) else gameRecordAutoPlayFor(language),
            enabled = isPlaying || canPlay,
            onClick = onToggle,
            modifier = Modifier.weight(2f),
        )
        Box(modifier = Modifier.weight(1f)) {
            ActionButton(
                label = "${gameRecordSecondsFor(language, seconds)} ▾",
                enabled = true,
                onClick = { isMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                GameRecordAutoPlaySeconds.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(gameRecordSecondsFor(language, choice)) },
                        onClick = {
                            onSecondsChange(choice)
                            isMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}
