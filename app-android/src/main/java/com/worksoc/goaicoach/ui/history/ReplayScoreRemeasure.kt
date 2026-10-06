package com.worksoc.goaicoach.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.gamehistory.hasProvisionalScores
import com.worksoc.goaicoach.application.gamehistory.remeasureScoreRecord
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile

/**
 * 다시보기를 연 판의 형세 기록에 사람 모델의 임시 값이 남아 있으면 **그 자리에서 주 모델로 마저 잰다**(백로그 #215 보강 ①).
 *
 * 대국이 끝난 직후의 재측정(`ScoreRecordRemeasureEffect`)이 끝까지 못 간 판이 여기 온다 — 곧바로 새 대국을 시작했거나
 * 앱을 닫은 판이다. 다시보기가 열려 있는 동안만 돌고, 닫으면 멈춘다(잰 데까지는 기록에 남는다). 재는 대로 [onRemeasured]로
 * 알려 변곡점·형세 그래프·실수 표시가 따라 바뀐다.
 *
 * 형세 보기·추천 수를 누르면 그쪽이 먼저다 — 재측정의 한 조각(평가 1회)만 기다리고 답한다(`LocalEngineSessionClient.backgroundPiece`).
 *
 * @return 지금 재고 있는가 — 화면이 「다시 재는 중」 한 줄을 띄운다.
 */
@Composable
internal fun ReplayScoreRemeasureEffect(
    entry: GameHistoryEntry,
    replay: GameReplayData,
    engine: EngineScoringClient,
    onRemeasured: (GameHistoryEntry, GameReplayData) -> Unit,
): Boolean {
    val context = LocalContext.current
    // 번들 참고 기보는 저장소를 거치지 않는 기록이고, 임시 값도 없다.
    val shouldRemeasure = entry.id != ReferenceGameHistoryId && replay.scoreSnapshots.hasProvisionalScores()
    val latestReplay by rememberUpdatedState(replay)
    val latestOnRemeasured by rememberUpdatedState(onRemeasured)
    LaunchedEffect(entry.id, shouldRemeasure) {
        if (!shouldRemeasure) return@LaunchedEffect
        val store = GameHistoryStore(context)
        remeasureScoreRecord(
            entry = entry,
            replay = latestReplay,
            engineClient = engine,
            profile = ReplayRemeasureProfile,
            save = { record -> runEngineIo { store.updateReplay(entry.id, record) } },
            onProgress = { record -> latestOnRemeasured(entry, record) },
        )
    }
    return shouldRemeasure
}

/** 다시보기는 대국 세션 밖이라 그 판의 엔진 프로필을 모른다 — 기본값으로 잰다. 매 수 형세는 신경망 평가 1회라 프로필에 기대지 않는다. */
private val ReplayRemeasureProfile = EngineProfile()
