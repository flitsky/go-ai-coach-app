package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 뒤로가기 기권이 대국 기록에 남는지를 **순서로** 못박는 소스 계약(백로그 #96).
 *
 * ⚠️ **이 결함은 순수 함수 테스트로 잡히지 않는다.** `runGameHistoryAppendIfCompleted`의 기권
 * 테스트(`resignationIsRecordedRegardlessOfFinalScoreJudgement`)는 **버그가 살아 있는 동안에도
 * 통과했다** — 함수는 옳았고 깨진 것은 **호출 순서**였다. 그래서 그물을 소스에 친다.
 */
class GameExitRecordingContractTest {

    private val shell = codeOnly(File("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt").readText())

    /**
     * ⚠️ **기록이 `refreshNewGamePreview()`보다 앞서야 한다.**
     *
     * `refreshNewGamePreview()`는 `applyGameSetupPreview`로 **새 미리보기 판을 적용**한다 —
     * 즉 `gameState.moves`가 통째로 갈린다. 뒤로가기 기권은 `dispatch(ResignCurrentGame)`와
     * `exitToHome()`을 **한 핸들러 안에서 연달아** 부르므로, 순서가 뒤집히면 기록 함수가 볼 때
     * `Move.Resign`이 이미 사라지고 없다.
     *
     * ⚠️ 순서를 되돌려도 **다른 테스트는 전부 초록**이다. 이 단언 하나가 유일한 그물이다.
     */
    @Test
    fun theFinishedGameIsRecordedBeforeTheBoardIsRebuilt() {
        val record = shell.indexOf("recordFinishedGameOnExit(")
        val refresh = shell.indexOf("refreshNewGamePreview()")
        assertTrue("나가는 길목의 기록 호출을 찾지 못했다(#96).", record >= 0)
        assertTrue("`refreshNewGamePreview()` 호출을 찾지 못했다 — 이 계약의 전제가 무너졌다.", refresh >= 0)
        assertTrue(
            "끝난 대국을 기록하기 전에 판을 갈아엎는다 — 뒤로가기 기권이 대국 기록에 남지 않는다(#96). " +
                "`recordFinishedGameOnExit(...)`를 `refreshNewGamePreview()` **앞으로** 옮길 것.",
            record < refresh,
        )
    }

    /**
     * ⚠️ **기록은 `exitToHome` 안에 있어야 한다 — 기권 확인 버튼 안이 아니라.**
     * 나가는 길목은 하나가 아니다(뒤로가기·로비 뒤로가기·기권 확인). 특정 버튼에만 붙이면
     * 나머지 경로가 같은 방식으로 조용히 샌다.
     */
    @Test
    fun theRecordingSitsOnTheSharedExitPathNotOneButton() {
        val exitBlock = shell.substringAfter("exitToHome = {").substringBefore("}")
        assertTrue(
            "기록이 `exitToHome` 블록 안에 없다 — 나가는 경로가 여럿이라 한 버튼에만 붙이면 샌다(#96).",
            exitBlock.contains("recordFinishedGameOnExit("),
        )
    }

    /** 주석을 걷어낸 코드만 남긴다 — 처방이 KDoc에도 적혀 있어 걷어내지 않으면 거짓 통과한다. */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
