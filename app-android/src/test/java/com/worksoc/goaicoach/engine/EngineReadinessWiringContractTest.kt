package com.worksoc.goaicoach.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 엔진 준비를 화면에서 떼어낸 배선의 계약(백로그 #101 ③단계).
 *
 * ⚠️ **여기 있는 것들은 단위 테스트로 잡히지 않는다.** 전부 *"어느 순서로, 무엇을 키로"* 의
 * 문제라 순수 함수에는 흔적이 남지 않는다 — #96·#101 0단계와 같은 모양이라 소스 계약으로 잰다.
 */
class EngineReadinessWiringContractTest {

    private fun codeOnly(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val mainActivity = codeOnly("src/main/java/com/worksoc/goaicoach/MainActivity.kt")
    private val goCoachApp = codeOnly("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
    private val failureNotice =
        codeOnly("src/main/java/com/worksoc/goaicoach/ui/EngineUnavailableNoticeDialog.kt")
    private val badge = codeOnly("src/main/java/com/worksoc/goaicoach/ui/EngineUnavailableBadge.kt")
    private val gamePlay = codeOnly("src/main/java/com/worksoc/goaicoach/ui/GamePlaySection.kt")

    @Test
    fun nothingBlocksTheFirstFrameWhileTheEngineGetsReady() {
        // 최초 실행에는 약 100MB 모델 복사가 돈다. 그 사이를 화면으로 막으면 사용자는 몇 초 동안
        // 아무것도 못 한다 — #101의 출발점이 이것이다.
        assertTrue(
            "준비 화면이 되살아났다 — 앱 첫 프레임이 다시 엔진을 기다린다(#101).",
            "PreparingEngineScreen" !in mainActivity,
        )
    }

    @Test
    fun theEngineClientIsBuiltOnceAndDoesNotRekeyWhenTheBootstrapLands() {
        val start = mainActivity.indexOf("val engineClient = remember(")
        assertTrue("`val engineClient = remember(`를 찾지 못했다 — 이 계약의 전제가 무너졌다.", start >= 0)
        val keys = mainActivity.substring(start, mainActivity.indexOf(") {", start))

        // ⚠️ 부트스트랩을 키로 넣으면 도착하는 순간 클라이언트가 새로 만들어지고,
        // `GoCoachApp`의 `LaunchedEffect(engineClient)`가 **엔진 기동을 처음부터 다시** 돌린다.
        assertTrue(
            "engineClient가 engineBootstrap을 키로 쓴다 — 부트스트랩 도착 시 엔진 기동이 재실행된다(#101).",
            "engineBootstrap" !in keys,
        )
    }

    @Test
    fun theEngineIsUnlockedBeforeItsIdentityIsAnnounced() {
        val unlock = mainActivity.indexOf("coreApiDeferred.complete(")
        val announce = mainActivity.indexOf("engineBootstrap = ready")

        assertTrue("부트스트랩 완료 배선을 찾지 못했다 — 전제가 무너졌다.", unlock >= 0 && announce >= 0)
        // ⚠️ 반대로 하면, 정체를 보고 재구성된 화면이 *"엔진 있음"* 으로 보이는 찰나에 엔진은
        // 아직 잠겨 있다. 둘 사이에 중단점이 없어 창이 아주 좁지만, 순서를 뒤집을 이유도 없다.
        assertTrue(
            "엔진을 풀어주기 전에 정체부터 알린다 — 화면이 엔진보다 먼저 준비된 것처럼 보인다(#101).",
            unlock < announce,
        )
    }

    @Test
    fun theEngineIdentityIsAskedForRatherThanHandedOver() {
        assertTrue(
            "GoCoachApp이 엔진 정체를 값으로 받는다 — 준비 전 값이 LaunchedEffect 안에서 굳는다(#101).",
            "engineIdentity: () -> EngineIdentity" in goCoachApp,
        )
        // ⚠️ `remember`로 감싸면 공급자로 받은 의미가 사라진다 — 첫 답이 그 자리에서 굳는다.
        assertTrue(
            "engineIdentity()를 remember로 감쌌다 — 준비 전 답이 굳는다(#101).",
            Regex("""remember\s*[({][^\n]*engineIdentity\(\)""").containsMatchIn(goCoachApp).not(),
        )
    }

    @Test
    fun theResolvedIdentityIsStampedOntoTheProfileAfterStartup() {
        val startup = goCoachApp.indexOf("runEngineStartupApplication(")
        val stamp = goCoachApp.indexOf("engineProfile = runtimeState.engineProfile.copy(")

        assertTrue("`runEngineStartupApplication(`을 찾지 못했다 — 전제가 무너졌다.", startup >= 0)
        // ⚠️ 이 도장이 없으면 씨앗에 박힌 Unresolved가 **영원히** 남는다. `initialPlan`을 받은
        // `sessionHolder`는 `remember`에 키가 없어 두 번 다시 만들어지지 않기 때문이다.
        assertTrue(
            "엔진 기동 뒤 진짜 정체를 프로필에 찍지 않는다 — 진단 리포트가 계속 AI/Unknown이라고 말한다(#101).",
            stamp >= 0,
        )
        assertTrue(
            "정체 도장이 엔진 기동보다 **앞에** 있다 — 그 시점에는 아직 아무것도 부팅되지 않았다(#101).",
            stamp > startup,
        )
    }

    @Test
    fun theFailureNoticeDoesNotDecideForItselfWhatCountsAsFailure() {
        // ⚠️ 세 상태를 가르는 판단은 shared의 정책이 소유한다(기조 1ⓒ). 화면에서 `mode`를 직접
        // 비교하기 시작하면 *"아직"* 과 *"안 된다"* 의 경계가 화면마다 갈라진다.
        assertTrue(
            "실패 팝업이 엔진 상태를 직접 판정한다 — 판단이 shared 정책 밖으로 샜다(기조 1ⓒ).",
            "engineAvailabilityFor(mode)" in failureNotice,
        )
    }

    @Test
    fun theFailureNoticeIsNeverSilencedAcrossLaunches() {
        // 다음 실행에도 여전히 스텁이면 **다시 알려야 한다.** "다시 보지 않기"를 저장하면
        // 고쳐지지 않은 고장을 영구히 숨기게 된다.
        assertTrue(
            "실패 팝업이 닫힘을 저장소에 남긴다 — 고쳐지지 않은 고장이 영구히 숨는다(#101 ④).",
            "Store" !in failureNotice,
        )
    }

    @Test
    fun theFailureNoticeOutranksTheOtherPopups() {
        val failure = goCoachApp.indexOf("EngineUnavailableNoticeDialog(")
        val releaseReset = goCoachApp.indexOf("ReleaseResetNoticeDialog(")

        assertTrue("실패 팝업이 배선돼 있지 않다 — 스텁 폴백이 다시 조용해진다(#101 ④).", failure >= 0)
        // ⚠️ Compose 다이얼로그는 각자 별도 윈도우라 **선언 순서로 위아래가 정해지지 않는다.**
        // 게이트 체인의 앞자리를 차지하는 것으로만 보장된다(ReleaseResetNoticeDialog의 KDoc).
        assertTrue(
            "실패 팝업이 다른 팝업 뒤에 있다 — 앱이 정상 동작하지 않는다는 사실을 나중에야 읽는다.",
            failure < releaseReset,
        )
    }

    @Test
    fun theInGameBadgeIsDrivenByAvailabilityAndNotByReadiness() {
        // ⚠️ `isReady`로 바꾸면 배지가 **정확히 거꾸로** 뜬다 — 스텁은 isReady=true라 안 뜨고,
        // 준비 중에는 isReady=false라 매번 뜬다. 두 신호는 다른 것을 잰다(기조 1ⓒ).
        assertTrue(
            "대국 화면 배지가 availability를 보지 않는다 — 스텁 표시가 거꾸로 뜬다(#105).",
            "screenState.engine.availability" in gamePlay,
        )
        assertTrue(
            "배지가 Unavailable 이외에도 뜬다 — 준비 중에 경고가 번쩍인다(기조 1ⓒ).",
            "availability != EngineAvailability.Unavailable) return" in badge,
        )
    }

    @Test
    fun theStartupFailureMessageAsksForTheDiagnosticWhenItActuallyFails() {
        // 실패 문구는 부트스트랩 진단을 담는다. 값으로 붙잡으면 하필 그것이 필요한 스텁 폴백에서
        // "준비 중"만 남는다.
        assertTrue(
            "startup 요청이 진단을 값으로 붙잡는다 — 스텁 폴백 사유가 실패 문구에서 사라진다(#101).",
            "engineDiagnostic = { engineIdentity().diagnostic }" in goCoachApp,
        )
    }
}
