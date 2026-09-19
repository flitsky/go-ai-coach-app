package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대국 다시보기(백로그 #156)가 지켜야 할 배치. **전부 어겨도 컴파일은 되고 다른 테스트는
 * 초록이다** — 그래서 소스 계약으로 든다.
 */
class GameReplayContractTest {

    /** 주석·import를 걷어낸 본문만 본다 — 이름이 주석에 남아 그물이 헐거워지는 것을 막는다(함정 10-2). */
    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val replay = source("src/main/java/com/worksoc/goaicoach/ui/GameReplayScreen.kt")
    private val history = source("src/main/java/com/worksoc/goaicoach/ui/GameHistoryScreen.kt")
    private val shell = source("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
    private val banner = source("src/main/java/com/worksoc/goaicoach/ui/BannerAdView.kt")

    /**
     * ⚠️ **셸의 상태 훅 예산은 42/42로 여유 0이다**(함정 3). 다시보기를 `ScreenDestination`으로
     * 올리면 목적지 하나당 최소 한 줄의 상태가 셸에 생겨 `LayeringContractTest`가 깨진다 —
     * 그런데 그 실패 메시지는 "셸이 커졌다"고만 말해서, 다음 사람이 원인을 여기서 찾지 못한다.
     */
    @Test
    fun theShellDoesNotKnowAboutTheReplayScreen() {
        listOf("GameReplayScreen", "GameReplayData", "buildGameReplayTimeline").forEach { name ->
            assertFalse(
                "`GoCoachApp.kt`가 `$name`을 안다 — 다시보기 배선이 셸로 올라왔다. " +
                    "재생 상태는 대국 기록 화면이 소유한다(백로그 #156).",
                shell.contains(name),
            )
        }
    }

    /**
     * ⚠️ **셸의 `uxOptions`를 고치면 자동저장이 그대로 받아 적는다**(함정 2) — 다시보기에서
     * 수순 번호를 켰다는 이유로 **다음 대국의 판에 번호가 남는다.** 그래서 이 화면은 자기
     * [KaTrainUxOptions]를 새로 만든다.
     */
    @Test
    fun theReplayScreenBuildsItsOwnUxOptions() {
        assertTrue(
            "다시보기가 자기 `KaTrainUxOptions(`를 만들지 않는다 — 셸 설정을 빌려 쓰면 " +
                "자동저장이 대국 설정을 영구히 바꾼다(함정 2).",
            replay.contains("KaTrainUxOptions("),
        )
        assertFalse(
            "다시보기가 `GameUiEvent.ChangeUxOptions`를 보낸다 — 그 경로는 셸의 설정을 " +
                "영구 저장한다. 지역 상태로만 바꿀 것.",
            replay.contains("ChangeUxOptions"),
        )
    }

    /**
     * ⚠️ **시스템 뒤로가기가 복기를 건너뛰고 홈으로 튄다.** 셸의 `BackHandler`는 목적지가
     * Home이 아니면 무조건 `exitToHome()`을 부른다 — 다시보기는 목적지가 아니라 하위 상태라
     * 셸이 그것을 모른다. 이 저장소의 **중첩 `BackHandler` 첫 사례**다.
     */
    @Test
    fun theReplayScreenCatchesTheSystemBackItself() {
        assertTrue(
            "다시보기에 `BackHandler`가 없다 — 뒤로가기가 목록을 건너뛰고 홈으로 튄다.",
            replay.contains("BackHandler"),
        )
    }

    /**
     * ⚠️ **판 위 착수 평가 색은 대국 화면에서 파는 기능이다**(`GoBoard.kt`가
     * `uxOptions.showMoveReview && premium.isActive`로 가른다). 다시보기가 그 판정을 자기
     * 손으로 다시 하면 **같은 권한에 지급 경로가 둘**이 되고, 쉬운 쪽이 설계를 무력화한다(함정 14).
     * 무료로 주는 것은 실착 **목록**이고, 판 위 색은 `GoBoard`의 판정을 그대로 따른다.
     */
    @Test
    fun theReplayScreenDoesNotReopenThePremiumGate() {
        listOf("LocalPremiumUiState", "FeatureAccessPolicy", "FeatureId.MoveReview").forEach { name ->
            assertFalse(
                "다시보기가 `$name`을 직접 본다 — 프리미엄 판정은 `GoBoard`가 한다. " +
                    "두 번째 판정 경로를 만들지 말 것(함정 14).",
                replay.contains(name),
            )
        }
    }

    /**
     * ⚠️ **판은 읽기 전용이어도 끌기를 삼킨다.** `GoBoard`의 제스처 루프는
     * `awaitFirstDown().consume()`을 `inputEnabled` 판정보다 **먼저** 한다 — 스크롤 부모 안에
     * 넣으면 판 위에서 시작한 끌기로는 화면이 굴러가지 않는다(함정 44). 게다가 스크롤 안에서는
     * `min(가로, 세로)`가 늘 가로폭이라 넓은 화면에서 판이 화면 밖으로 넘친다(함정 45).
     */
    @Test
    fun theReplayScreenHasNoScrollParent() {
        assertFalse(
            "다시보기가 `verticalScroll`을 쓴다 — 판이 끌기를 삼켜 스크롤이 안 되고(함정 44), " +
                "판 높이 제약이 무한해진다(함정 45). 판에 `weight`를 줄 것.",
            replay.contains("verticalScroll"),
        )
        assertTrue(
            "판이 남는 높이를 받지 않는다 — 조작부가 화면 밖으로 밀린다.",
            replay.contains(".weight(1f)"),
        )
    }

    /**
     * ⚠️ **본문 없는 옛 기록은 눌려서는 안 된다.** 2026-09-18 이전 기록에는 수순이 저장된 적이
     * 없어(#151) 다시보기가 영영 열리지 않는다 — 눌러도 아무 일이 없는 행은 고장으로 읽힌다.
     */
    @Test
    fun onlyRowsWithAStoredRecordAreClickable() {
        assertTrue(
            "목록 행의 클릭이 `hasReplay`로 갈리지 않는다 — 열리지 않는 행이 눌린다.",
            history.contains("takeIf { entry.hasReplay }"),
        )
    }

    /**
     * 사용자가 정한 세로 차례(2026-09-19): **광고 → 큰 실수 → 판 → 수순 조작 → 사석·승률.**
     *
     * ⚠️ 차례가 바뀌어도 **컴파일도 되고 화면도 뜬다** — 블록을 옮기는 것은 한 줄 이동이라
     * 다음 사람이 무심코 되돌리기 쉽다. 의도가 있는 배치이므로 소스에서 못박는다:
     * 실착 요약이 판보다 **위**인 것이 이 배치의 핵심이다(복기의 결론을 먼저 보여 준다).
     */
    @Test
    fun theBlocksStayInTheOrderTheUserChose() {
        val order = listOf(
            "SubscriptionAwareBannerAd(",
            "ReplayBlunderSection(",
            "GoBoard(",
            "ReplayControls(",
            "ReplayScoreSection(",
        )
        val positions = order.map { name ->
            val at = replay.indexOf(name)
            assertTrue("다시보기 화면에서 `$name` 호출을 찾지 못했다.", at >= 0)
            name to at
        }
        positions.zipWithNext { (leftName, left), (rightName, right) ->
            assertTrue(
                "세로 차례가 어긋났다 — `$rightName`이 `$leftName`보다 위에 있다. " +
                    "사용자가 정한 차례는 광고 → 큰 실수 → 판 → 수순 조작 → 사석·승률이다(2026-09-19).",
                left < right,
            )
        }
    }

    /**
     * ⚠️ **화면이 `BannerAdView`를 직접 부르면 구독자에게도 광고가 뜬다.** 등재문이
     * *"광고 없이 편하게 쓰고 싶다면 프리미엄 구독"* 이라 적고 있어, 그것은 스토어가 하는 말과
     * 앱이 하는 일을 어긋나게 한다 — 2026-09-16 거부가 가르친 바로 그것이다(함정 51·52).
     */
    @Test
    fun theReplayScreenShowsTheBannerThroughTheSubscriptionGate() {
        assertTrue(
            "다시보기가 `SubscriptionAwareBannerAd`를 부르지 않는다.",
            replay.contains("SubscriptionAwareBannerAd("),
        )
        assertFalse(
            "다시보기가 `BannerAdView`를 직접 부른다 — 구독자에게도 광고가 뜬다. " +
                "`SubscriptionAwareBannerAd`를 쓸 것.",
            Regex("""(?<!Subscription)(?<!AwareBanner)\bBannerAdView\s*\(""").containsMatchIn(replay),
        )
    }

    /**
     * ⚠️ **`isActive`로 바꾸면 광고를 한 번 본 사람에게 한 시간 동안 배너가 사라진다.**
     * `isActive`는 광고 시청으로 얻은 1시간 부여까지 포함하는데, 등재문이 광고를 없애 준다고
     * 약속한 대상은 **구독**뿐이다. 한 글자 차이라 리뷰에서 놓치기 쉬워 그물을 단다.
     */
    @Test
    fun theBannerGateLooksAtTheSubscriptionNotTheAdGrant() {
        assertTrue(
            "배너 게이트가 `isPurchased`를 보지 않는다.",
            banner.contains("LocalPremiumUiState.current.isPurchased"),
        )
        assertFalse(
            "배너 게이트가 `isActive`를 본다 — 광고를 본 사람에게 한 시간 동안 배너가 사라진다. " +
                "광고를 없애 주기로 한 것은 구독(`isPurchased`)뿐이다.",
            banner.contains("LocalPremiumUiState.current.isActive"),
        )
    }
}
