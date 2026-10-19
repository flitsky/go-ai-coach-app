package com.worksoc.goaicoach.ui.gamerecord

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.gamerecord.GameRecord
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.history.ReplayAnalysisEngine
import com.worksoc.goaicoach.ui.history.ReplayControls
import com.worksoc.goaicoach.ui.history.ReplayHeader
import com.worksoc.goaicoach.ui.history.ReplaySectionGap
import com.worksoc.goaicoach.ui.history.rememberReplayAnalysis
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.gameRecordPlayersFor
import com.worksoc.goaicoach.ui.l10n.gameRecordResultFor
import com.worksoc.goaicoach.ui.l10n.gameReplayTruncatedFor

/** 보기 화면에 여는 기보 한 판 — [key]는 이 판의 분석 기록(1회권 장부)을 가르는 열쇠다. */
internal data class OpenedGameRecord(val key: String, val title: String, val record: GameRecord)

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
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    BackHandler { onBackClick() }

    val record = opened.record
    val timeline = remember(opened.key) { buildGameReplayTimeline(setup = record.setup, moves = record.moves) }
    var moveNumber by remember(opened.key) { mutableIntStateOf(0) }
    var showMoveNumbers by remember { mutableStateOf(false) }
    val state = timeline.stateAt(moveNumber)
    val analysis = rememberReplayAnalysis(
        engine = analysisEngine,
        replayKey = opened.key,
        moveNumber = moveNumber,
        positionAt = timeline::stateAt,
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
            onMoveNumberChange = { next -> moveNumber = next.coerceIn(0, timeline.lastMoveNumber) },
            onToggleMoveNumbers = { showMoveNumbers = !showMoveNumbers },
        )
    }
}
