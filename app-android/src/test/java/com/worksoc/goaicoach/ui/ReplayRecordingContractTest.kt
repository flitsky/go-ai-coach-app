package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 리플레이 적재(백로그 #151)의 **배선**을 소스에서 못박는다.
 *
 * ## ⚠️ 왜 계약 테스트인가 — 빠뜨려도 전부 초록이다
 * 여기서 지키는 것들은 하나같이 **컴파일도 되고 다른 테스트도 초록인 채** 조용히 죽는다.
 * 인자를 안 넘기면 기본값 `emptyList()`가 조용히 들어가고, 기록은 **수순 없는 껍데기**로 남는다.
 * 그리고 그 손실은 **되돌릴 수 없다** — 그 대국의 순간은 다시 오지 않는다.
 */
class ReplayRecordingContractTest {

    private fun source(path: String): String = File(path).readText()

    private val goCoachApp = source("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
    private val exitRecording = source("src/main/java/com/worksoc/goaicoach/ui/GameExitRecording.kt")
    private val topMoves = source(
        "../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/topmoves/TopMovesApplication.kt",
    )
    private val gameHistoryAppend = source(
        "../shared/src/commonMain/kotlin/com/worksoc/goaicoach/application/gamehistory/GameHistoryAppendApplication.kt",
    )
    private val wipe = source("src/main/java/com/worksoc/goaicoach/DeveloperModeResetCoordinator.kt")

    /**
     * ⚠️ **기록 경로는 둘이다** — 평소의 `LaunchedEffect`와, 뒤로가기 기권 전용
     * `recordFinishedGameOnExit`(#96). 한쪽만 실으면 **기권으로 끝난 판만 형세가 없는** 기록이 된다.
     *
     * ⚠️ **손실집수(`moveEvaluations`)는 더 이상 호출부가 넘기지 않는다**(2026-09-20 개정) —
     * `runGameHistoryAppendIfCompleted`가 `scoreSnapshots`로부터 직접 계산한다. 그래서 여기서
     * 지킬 것은 `scoreSnapshots` 하나뿐이다 — 그것만 있으면 손실집수는 저절로 따라온다.
     */
    @Test
    fun bothRecordingPathsCarryTheReplayPayload() {
        assertTrue(
            "GoCoachApp의 기록 효과가 scoreSnapshots를 안 넘긴다 — 형세 타임라인이 조용히 버려진다.",
            goCoachApp.contains("scoreSnapshots = scoreState.scoreSnapshots"),
        )
        assertTrue(
            "뒤로가기 기권 경로가 형세를 안 넘긴다 — 기권 판만 형세 없는 기록이 된다(#96·#151).",
            exitRecording.contains("scoreSnapshots = scoreSnapshots"),
        )
    }

    /**
     * ⚠️ **U-35(2026-09-18)의 강제 상시 탐색은 2026-09-20에 걷어냈다** — "추천 수 보기"를 꺼도
     * 매 턴 사전 분석이 돌아 대국 진행을 방해한다는 제보 때문이다(백로그 #151 개정).
     *
     * 다만 **"착수 평가"(구독자 전용 실시간 코칭)는 여전히 같은 탐색 결과를 쓴다** — 그 토글을
     * 켠 사용자에게는 매 턴 탐색이 돌아야 색 링이 그려진다. 누가 이 OR를 지우면 **그 유료
     * 기능이 조용히 깨진다.**
     */
    @Test
    fun preMoveAnalysisStaysGatedByExplicitUserOptInsOnly() {
        assertFalse(
            "U-35가 되살아났다 — 추천 수·착수 평가 둘 다 꺼도 매 턴 탐색이 다시 강제로 돈다.",
            topMoves.contains("RecordMoveEvaluations"),
        )
        assertEquals(
            "`shouldRequestTopMoveAnalysis`에 넘기는 topMovesEnabled가 showMoveReviewEnabled와 OR로 묶여 있지 않다" +
                " — \"착수 평가\"가 대국 진행 방해 없이 동작할 유일한 경로다.",
            1,
            Regex("""topMovesEnabled = request\.controllerState\.settings\.topMovesEnabled \|\|\s*request\.showMoveReviewEnabled""")
                .findAll(topMoves).count(),
        )
    }

    /**
     * ⚠️ **다시보기의 큰 실수 표시는 엔진 재호출 없이 만들어져야 한다**(2026-09-20 개정) — 대국이
     * 끝나 기록되는 시점에 이미 공짜로 쌓인 형세 스냅샷의 앞뒤 차이만 본다. 누가 다시 "사전
     * 분석 캐시를 넘겨받는" 방식으로 되돌리면, 대국 진행을 방해하던 그 탐색도 함께 돌아온다.
     */
    @Test
    fun replayBlunderMarkersAreDerivedFromScoreSnapshotsAtRecordTime() {
        assertTrue(
            "moveEvaluations가 더 이상 deriveMoveReviewMarkersFromScoreSwing에서 오지 않는다.",
            gameHistoryAppend.contains("moveEvaluations = deriveMoveReviewMarkersFromScoreSwing("),
        )
    }

    /**
     * ⚠️ **대국 기록은 이제 `filesDir`에 있다** — `shared_prefs` 훑기만으로는 안 지워진다.
     * 빠지면 *"최초 설치 상태로 되돌리기"* 가 기록을 남긴 채 끝난다(백로그 #151 회귀).
     */
    @Test
    fun theFreshInstallWipeAlsoClearsTheFileBackedGameHistory() {
        assertTrue(
            "wipeToFreshInstall이 game_history 디렉터리를 안 지운다 — 기록이 초기화를 살아남는다.",
            wipe.contains("File(app.filesDir, GameHistoryDirName).deleteRecursively()"),
        )
    }
}
