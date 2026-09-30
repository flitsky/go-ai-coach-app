package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「대국 → 복기 → 다시 두기」 흐름의 계약(백로그 #185).
 *
 * ⚠️ **전부 어겨도 컴파일은 되고 화면도 뜬다** — 그래서 소스 계약으로 든다. 구조는
 * `StudyLessonContractTest`·`GameReplayContractTest`와 같다.
 */
class FinishedGameFlowContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun source(path: String): String =
        File(path).readContractSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val play = source(RepoPaths.uiFile("GamePlaySection.kt").path)
    private val content = source(RepoPaths.uiFile("GoCoachContent.kt").path)
    private val history = source(RepoPaths.uiFile("GameHistoryScreen.kt").path)
    private val dialog = source(RepoPaths.uiFile("FinalJudgementDialog.kt").path)

    /**
     * ⚠️ **같은 다섯 칸을 세 배치가 그린다**(한 줄 다섯 · 좌우 기둥 · 두 줄) — 그중 좌우 기둥은
     * **순서가 뒤집혀 있어** *"통과 오른쪽"* 이라는 말조차 성립하지 않는다. 종국 갈래를 배치에
     * 적으면 **한쪽 배치만 고쳐진다**(`GameActionSlots`의 KDoc이 #44·#66을 근거로 이미 경고한다).
     *
     * 갈래는 칸을 **만드는** 곳(`GameActionButtonHost`)에만 있어야 한다.
     */
    @Test
    fun theEndedGameActionsBranchWhereTheSlotsAreBuiltNotInTheArrangements() {
        val host = play.substringAfter("private fun GameActionButtonHost(")
        assertTrue(
            "`GameActionButtonHost`를 찾지 못했다 — 그물이 파일 전체를 재고 있다.",
            play.contains("private fun GameActionButtonHost("),
        )
        listOf("onReviewFinishedGame", "onOpenGameSetup").forEach { name ->
            assertTrue(
                "`$name`을 쓰는 곳이 칸을 만드는 자리(`GameActionButtonHost`)가 아니다.",
                host.contains("onClick = $name"),
            )
        }

        // 배치 셋은 **통로일 뿐**이다 — 넘겨 주기만 하고 `isGameEnded`로 갈라서는 안 된다.
        listOf("WidePlayArrangement", "WideColumnsArrangement").forEach { arrangement ->
            val body = play.substringAfter("private fun $arrangement(").substringBefore("\nprivate fun ")
            assertFalse(
                "`$arrangement`가 `onClick = onReviewFinishedGame`을 직접 들고 있다 — " +
                    "배치마다 칸을 다시 적으면 한쪽만 고쳐진다(`GameActionSlots` KDoc).",
                body.contains("onClick = onReviewFinishedGame"),
            )
        }
    }

    /**
     * ⚠️ **「복기 하기」는 대국 화면을 컴포지션에서 뺀다.** 닫았다는 기억이 `remember`에만
     * 있으면 복기에서 돌아오는 순간 **판정 결과가 다시 뜬다**(2026-09-22 실기에서 잡혔다 —
     * 사용자는 방금 스스로 닫은 팝업을 또 닫아야 했다).
     */
    @Test
    fun theDismissedJudgementIsRememberedOutsideTheScreen() {
        assertTrue(
            "닫은 계가 팝업의 열쇠를 화면 밖(`FinishedGameFlow`)에서 읽지 않는다 — " +
                "복기에서 돌아오면 판정 결과가 다시 뜬다.",
            content.contains("mutableStateOf(FinishedGameFlow.dismissedJudgementKey)"),
        )
        assertTrue(
            "닫을 때 화면 밖에 적지 않는다 — 읽기만 해서는 아무것도 막지 못한다.",
            content.contains("FinishedGameFlow.markJudgementDismissed("),
        )
        assertTrue(
            "새 대국이 시작될 때 비우지 않는다 — 다음 판의 결과가 조용히 삼켜질 수 있다.",
            content.contains("FinishedGameFlow.clearDismissedJudgement()"),
        )
    }

    /**
     * ⚠️ **요청한 판이 맞는지 확인하지 않으면 남의 판이 열린다.** 대국 화면은 자기 기록의 id를
     * 모르므로 *"가장 최근 기록"* 으로 찾는데, 기록 붙이기가 아직 안 돌았거나 실패했으면 그것은
     * **직전 대국**이다 — 화면은 아무 말 없이 그것을 보여 주고, 사용자는 자기 판이라고 믿는다.
     * 빈 화면보다 나쁘다.
     */
    @Test
    fun theAutoOpenedReplayIsCheckedAgainstTheRequestedGame() {
        assertTrue(
            "자동으로 여는 다시보기가 수순 개수를 확인하지 않는다 — 엉뚱한 판이 열린다.",
            history.contains("newest.moveCount != requestedMoveCount"),
        )
        assertTrue(
            "참고 기보를 후보에서 빼지 않는다 — 그것이 항상 맨 앞이라 먼저 잡힌다.",
            history.contains("it.id != ReferenceGameHistoryId"),
        )
    }

    /**
     * ⚠️ **나가는 문이 둘이다**(2026-09-22 사용자 결정). 목록에서 연 다시보기는 **목록**으로,
     * 대국 화면에서 온 것만 **그 대국 화면**으로 돌아간다 — 하나로 합치면 둘 중 하나는 반드시
     * 엉뚱한 곳으로 간다(사용자는 목록에 들른 적이 없다).
     */
    @Test
    fun theReplayRemembersWhichDoorItCameThrough() {
        assertTrue(
            "다시보기가 들어온 문을 기억하지 않는다 — 나가는 곳이 하나뿐이다.",
            history.contains("cameFromGame"),
        )
        assertTrue(
            "대국 화면에서 온 경우에 돌려보내지 않는다.",
            history.contains("onReturnToGame()"),
        )
    }

    /**
     * ⚠️ **방금 끝난 그 판에서 갈라질 때는 경고하지 않는다**(2026-09-22 사용자 결정).
     * 저장 슬롯에 남은 것이 **자기 자신**이라, 「진행 중인 다른 대국을 덮어씁니다」가 사실과
     * 다른 말이 된다 — 거짓인 안내는 없느니만 못하다(함정 39).
     */
    @Test
    fun branchingFromTheJustFinishedGameDoesNotWarnAboutOverwritingItself() {
        assertTrue(
            "덮어쓰기 경고가 들어온 문을 보지 않는다 — 자기 자신을 덮어쓴다는 경고가 뜬다.",
            history.contains("hasResumableSession && !cameFromGame"),
        )
    }

    /**
     * 대국 뒤 「복기 하기」 추천(백로그 #200)의 배선 — 전부 어겨도 컴파일되고 화면도 뜬다.
     *
     * - 팝업의 배지와 말풍선이 **같은 개수**를 본다(한쪽만 다른 계산을 쓰면 배지 3·말풍선 5가 된다).
     * - 말풍선 기억은 **화면 안**(`remember`)에만 있고 새 대국이 비운다 — 화면 밖(`FinishedGameFlow`)에 적으면
     *   복기에서 돌아올 때 **또 뜬다**(「한 판에 한 번」이 깨진다).
     * - 오버레이는 본문 **뒤**에 있어야 위에 그려진다(함정 38 — 앞에 두면 판·상태판이 말풍선을 덮는다).
     */
    @Test
    fun theReviewRecommendationIsWiredOncePerFinishedGame() {
        assertTrue(
            "판정 결과 팝업에 실착 수를 넘기지 않는다 — 배지가 영영 안 뜬다.",
            content.contains("reviewMistakeCount = reviewMistakeCount"),
        )
        assertEquals(
            "실착 수를 두 번 이상 계산한다 — 배지와 말풍선이 다른 값을 볼 수 있다.",
            1,
            Regex("""countReviewRecommendationMistakes\(""").findAll(content).count(),
        )
        val restartEffect = content.substringAfter("LaunchedEffect(screenState.isGameEnded) {").substringBefore("\n    }\n")
        assertTrue(
            "`LaunchedEffect(screenState.isGameEnded)`를 찾지 못했다 — 그물이 파일 전체를 재고 있다.",
            content.contains("LaunchedEffect(screenState.isGameEnded) {"),
        )
        assertTrue("새 대국이 말풍선 기억을 비우지 않는다.", restartEffect.contains("reviewRecommendationKey = null"))
        assertFalse(
            "말풍선 기억을 화면 밖(`FinishedGameFlow`)에 적는다 — 복기에서 돌아오면 또 뜬다.",
            content.contains("FinishedGameFlow.markReviewRecommendation") || content.contains("FinishedGameFlow.reviewRecommendation"),
        )
        assertTrue(
            "말풍선 오버레이가 본문(`BoxWithConstraints`)보다 앞에 있다 — 판·상태판이 그것을 덮는다(함정 38).",
            content.indexOf("ReviewRecommendationOverlay(") > content.indexOf("BoxWithConstraints("),
        )
        assertTrue(
            "판 자리를 알리지 않는 배치가 있다 — 그 배치에서 말풍선이 설 곳을 모른다.",
            Regex("""onBoardSlotPositioned = """).findAll(content).count() == 2 &&
                play.contains("onBoardSlotPositioned(Rect(coordinates.positionInRoot(), coordinates.size.toSize()))"),
        )
    }

    /**
     * ⚠️ **계가 팝업의 버튼은 둘로 유지한다**(2026-09-18 사용자 결정). Material3 `AlertDialog`는
     * 버튼 슬롯이 둘뿐이라 셋째를 넣으려면 커스텀 배치가 필요하고, 4개 언어 폭이 걸린다.
     */
    @Test
    fun theFinalJudgementDialogKeepsExactlyTwoButtons() {
        assertEquals(
            "계가 팝업의 `TextButton`이 둘이 아니다 — 2026-09-18 「둘로 유지」 결정을 뒤집었다.",
            2,
            Regex("""TextButton\(""").findAll(dialog).count(),
        )
        assertTrue(
            "팝업에서 복기로 가는 길이 없다 — 이 팝업을 닫으면 복기를 찾기 어렵다.",
            dialog.contains("onClick = onReplay"),
        )
    }
}
