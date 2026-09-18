package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
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
    private val wipe = source("src/main/java/com/worksoc/goaicoach/DeveloperModeResetCoordinator.kt")

    /**
     * ⚠️ **기록 경로는 둘이다** — 평소의 `LaunchedEffect`와, 뒤로가기 기권 전용
     * `recordFinishedGameOnExit`(#96). 한쪽만 실으면 **기권으로 끝난 판만 수순이 없는** 기록이 된다.
     */
    @Test
    fun bothRecordingPathsCarryTheReplayPayload() {
        assertTrue(
            "GoCoachApp의 기록 효과가 scoreSnapshots를 안 넘긴다 — 형세 타임라인이 조용히 버려진다.",
            goCoachApp.contains("scoreSnapshots = scoreState.scoreSnapshots"),
        )
        assertTrue(
            "GoCoachApp의 기록 효과가 moveEvaluations를 안 넘긴다 — 손실집수가 조용히 버려진다.",
            goCoachApp.contains("moveEvaluations = moveReviewState.moveReviews"),
        )
        assertTrue(
            "뒤로가기 기권 경로가 리플레이를 안 넘긴다 — 기권 판만 수순 없는 기록이 된다(#96·#151).",
            exitRecording.contains("scoreSnapshots = scoreSnapshots") &&
                exitRecording.contains("moveEvaluations = moveEvaluations"),
        )
    }

    /**
     * ⚠️ **U-35가 사는 곳은 이 한 줄이다**(2026-09-18 사용자: *"무료 대국에서도 손실집수를 계산하고 저장"*).
     *
     * `topMovesEnabled`가 거짓이면 사전 분석을 **아예 요청하지 않아** `reviewAnalysis`가 비고,
     * 착수 평가 마커가 만들어지지 않는다. 누가 "추천 수 꺼져 있으면 분석도 끄자"며 이 OR를
     * 지우면 **다시보기의 실착 지표가 조용히 구독자 전용이 된다.**
     */
    @Test
    fun preMoveAnalysisIsNotGatedAwayByTheTopMovesToggleAlone() {
        assertTrue(
            "U-35가 사라졌다 — 추천 수가 꺼지면 손실집수가 다시 계산되지 않는다.",
            topMoves.contains("ReplayRecordingPolicy.RecordMoveEvaluations"),
        )
        assertEquals(
            "`shouldRequestTopMoveAnalysis`에 넘기는 topMovesEnabled가 정책과 OR로 묶여 있지 않다.",
            1,
            Regex("""topMovesEnabled = request\.controllerState\.settings\.topMovesEnabled \|\|""")
                .findAll(topMoves).count(),
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
