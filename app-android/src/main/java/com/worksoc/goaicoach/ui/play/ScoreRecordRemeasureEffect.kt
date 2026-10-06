package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.gamehistory.findRecordedGame
import com.worksoc.goaicoach.application.gamehistory.hasProvisionalScores
import com.worksoc.goaicoach.application.gamehistory.remeasureScoreRecord
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import kotlinx.coroutines.delay

/**
 * 대국이 끝나면 **그 판의 형세 기록을 주 모델로 다시 잰다**(백로그 #215 보강 ①) — 급수 캐릭터와 둔 판은 수마다 사람 모델의
 * 임시 값이 남아 있다. 무엇을 어떻게 재는지는 [remeasureScoreRecord]가 정하고, 여기는 **언제 돌고 언제 멈추는지**만 정한다.
 *
 * - **끝난 판에서만 돈다**([isActive]) — 새 대국을 시작하면 이 효과가 취소되고, 잰 데까지는 기록에 남는다.
 *   다시보기를 연 동안에도 멈춘다: 그 화면이 같은 기록을 제 손으로 잰다(`ReplayScoreRemeasureEffect`). 돌아오면 저장소에서 이어받는다.
 * - **기록은 저장소의 것을 고친다.** 살아 있는 형세 기록([scoreSnapshots])에는 [onRemeasured]로 받아들이게 한다 —
 *   판정 결과의 「5집 이상 실착」 개수와 형세 그래프가 따라 바뀐다.
 * - 대국을 막지 않는다: 엔진 오퍼레이션으로 올리지 않아(바쁨 표시·버튼 잠금 없음) 재는 동안에도 재대국·대국 설정이 그대로 눌린다.
 *
 * `GoCoachApp.kt`의 상태 훅 예산이 꽉 차 있어 셸에는 호출 한 줄만 둔다(`OneShotAnalysisAutoClear`와 같은 이유).
 */
@Composable
internal fun ScoreRecordRemeasureEffect(
    isActive: Boolean,
    sessionGeneration: Long,
    gameState: GameState,
    scoreSnapshots: List<ScoreSnapshot>,
    engineClient: EngineScoringClient,
    profile: EngineProfile,
    onRemeasured: (List<ScoreSnapshot>) -> Unit,
) {
    val context = LocalContext.current
    val hasProvisionalScores = scoreSnapshots.hasProvisionalScores()
    val latestGameState by rememberUpdatedState(gameState)
    val latestGeneration by rememberUpdatedState(sessionGeneration)
    val latestOnRemeasured by rememberUpdatedState(onRemeasured)
    LaunchedEffect(isActive, hasProvisionalScores, sessionGeneration, gameState.moves.size) {
        if (!isActive || !hasProvisionalScores) return@LaunchedEffect
        val moves = gameState.moves
        val store = GameHistoryStore(context)
        // 기록 붙이기는 같은 프레임의 다른 효과가 한다 — 아직이면 잠깐 기다렸다 다시 본다.
        var recorded = runEngineIo { store.findRecordedGame(moves) }
        repeat(RecordLookupRetries) {
            if (recorded != null) return@repeat
            delay(RecordLookupRetryMillis)
            recorded = runEngineIo { store.findRecordedGame(moves) }
        }
        val (entry, replay) = recorded ?: return@LaunchedEffect
        // ⚠️ 받아들이기 전에 **아직 그 판인지** 본다 — 취소돼도 잰 데까지는 알림이 오는데, 그때는 이미 새 대국일 수 있다.
        val adopt: (List<ScoreSnapshot>) -> Unit = { remeasured ->
            if (latestGeneration == sessionGeneration && latestGameState.moves == moves) latestOnRemeasured(remeasured)
        }
        val measured = remeasureScoreRecord(
            entry = entry,
            replay = replay,
            engineClient = engineClient,
            profile = profile,
            save = { record -> runEngineIo { store.updateReplay(entry.id, record) } },
            onProgress = { record -> adopt(record.scoreSnapshots) },
        )
        // 저장소에는 이미 다 재어져 있던 판(다시보기에서 먼저 잰 판)도 여기서 받아들인다.
        adopt(measured.scoreSnapshots)
    }
}

private const val RecordLookupRetries = 3
private const val RecordLookupRetryMillis = 400L
