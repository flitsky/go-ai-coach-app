package com.worksoc.goaicoach.application.guide

/**
 * **첫돌이 가이드**가 "지금 무엇을 보여줄 차례인가"를 정하는 **순수 판정**(백로그 #128).
 *
 * ## 왜 판정이 `shared`에 있는가
 *
 * 이 판정이 틀리면 **가이드가 조용히 사라지거나 조용히 영원히 뜬다** — 둘 다 화면을 보고 있어야만
 * 알아챌 수 있는 종류다. 판정을 안드로이드 밖으로 빼면 `commonTest`가 그 경계를 전부 셀 수 있다.
 * 컴포저블에 남는 것은 *"판정에게 물어보고 그린다"* 뿐이다.
 *
 * ## ⚠️ 표면당 단계가 하나라고 가정하지 않는다
 *
 * 오늘은 표면 다섯과 단계 다섯이 1:1이라 [autoPlayStep]이 그냥 첫 미시청 단계를 고른다. 그런데
 * #18·#26(구매·구독)이나 로그인이 붙으면 **이미 단계가 있는 표면**(대국 설정·마이페이지)에 두 번째
 * 단계가 생긴다. 그때 판정식을 다시 쓰지 않도록 **[GuideStep] 선언 순서가 같은 표면 안에서도
 * 권위를 갖는다** — 그 성질을 `FirstRunGuidePolicyTest`가 케이스로 못박는다. 지금은 공짜이고,
 * 나중에는 판정식 재작성이다.
 */
enum class GuideSurface {
    Landing,
    AttendanceClaim,
    Home,
    MatchSetup,
    InGame,
}

/**
 * 가이드의 다섯 단계. **선언 순서가 재생 순서이고, 한 표면에서 둘이 경쟁할 때의 우선순위다.**
 *
 * ⚠️ [id]는 **저장 포맷**이다(`seen_steps` 집합에 이 문자열이 들어간다). 함정 1번과 같은 성질이라
 * **상수 이름이 아니라 이 문자열을 바꾸면** 이미 본 사용자에게 가이드가 다시 뜬다. 바꾸지 말 것.
 */
enum class GuideStep(val id: String, val surface: GuideSurface) {
    /** ① 랜딩 상단 좌우 첫돌이 — 문구 없는 **정적 장식**이라 판정에 참여하지 않는다(아래 주석). */
    Landing("landing", GuideSurface.Landing),

    /** ② 출석 보상 팝업 안 한 줄. */
    AttendanceClaim("attendance_claim", GuideSurface.AttendanceClaim),

    /** ③ 홈 `대국 하기` 카드 우상단 말풍선. */
    HomeStartMatch("home_start_match", GuideSurface.Home),

    /** ④ 대국 설정 화면. */
    MatchSetup("match_setup", GuideSurface.MatchSetup),

    /** ⑤ 대국 화면의 도구 넷. */
    InGameTools("in_game_tools", GuideSurface.InGame),
}

/**
 * 저장된 진행도. [GuideProgressStore]가 이 값을 싣고 내린다.
 *
 * @param armed 랜딩을 **끝낸** 적이 있는가. ⚠️ *"랜딩을 봤는가"* 가 아니다 — 완료·건너뛰기 두 갈래
 *   모두에서 켜진다. 이 한 값이 **기존 사용자에게 자동 재생이 시작되지 않는 것**을 공짜로 만든다:
 *   이미 랜딩을 지난 사용자는 `armed`가 꺼진 채이므로 자동 재생이 아예 무장되지 않는다.
 * @param dismissed 사용자가 *"그만 보기"* 를 눌렀는가. 사슬 **전체**를 끈다(사용자 확정 1번의 단서).
 * @param seenSteps 이미 보여준 단계의 [GuideStep.id] 집합.
 */
data class GuideProgress(
    val armed: Boolean = false,
    val dismissed: Boolean = false,
    val seenSteps: Set<String> = emptySet(),
) {
    fun hasSeen(step: GuideStep): Boolean = step.id in seenSteps
}

/**
 * 이 표면에서 **지금 그릴 단계**를 고른다. 없으면 `null`.
 *
 * @param blocked 이 표면 위에 **다른 다이얼로그가 떠 있는가.** ⚠️ 이 인자가 이 판정의 핵심이다 —
 *   가려진 채 그려지면 사용자는 못 봤는데 `seen`으로 기록돼 **가이드가 조용히 소진된다.**
 *   그래서 카드 표현형의 앵커에서는 이 인자를 **기본값 없이** 받아 컴파일러가 게이트를 강제한다.
 *
 * ⚠️ **호출부는 이 결과를 `remember`로 기억하지 말 것.** [blocked]와 진행도는 둘 다 변하는 값이라
 * 초기화식에 넣으면 **첫 프레임의 답이 영구히 굳는다** — 실제로 그 형태로 짜면 첫 실행 홈에서
 * 출석 팝업이 떠 있는 동안 ③이 `null`로 고정되고, 팝업이 닫혀도 홈은 dispose되지 않아 다시
 * 계산되지 않는다(설계 심사에서 잡힌 결함이다). 컴포지션 중에 그냥 부를 것.
 */
fun autoPlayStep(
    surface: GuideSurface,
    progress: GuideProgress,
    blocked: Boolean,
): GuideStep? {
    if (!progress.armed || progress.dismissed || blocked) return null
    return GuideStep.entries
        .filter { step -> step.surface == surface && step != GuideStep.Landing }
        .firstOrNull { step -> !progress.hasSeen(step) }
}

/**
 * ④ 문구가 말할 수 있는 **살아 있는 사실**.
 *
 * ## ⚠️ 기력은 여기 없다 — 앱이 그것을 기억하지 않는다
 *
 * 사용자 원문은 *"(기력)을 선택하셨으니 5점 접바둑으로…"* 였는데, `applyLandingSetup`은
 * `ruleset`·`handicapCount`·`playerSetup`·`hasSeenOnboarding`만 남기고 **`SelfRatedSkill`을 버린다.**
 * 저장소 전체에서 그 열거형을 참조하는 곳은 정책 파일·랜딩 화면의 휘발 상태·문구표 셋뿐이라,
 * *"입문을 선택하셨으니"* 는 **오늘의 코드로 말할 수 없다.** 기력을 새로 장부에 올리는 대신
 * **언급을 생략하기로 했다**(2026-09-09 사용자 결정) — 랜딩을 건너뛴 사용자와 나중에 설정을 바꾼
 * 사용자에게도 거짓이 되지 않는 쪽이다.
 *
 * 그래서 ④는 **실제 저장값에서 파생한** 이 셋만 말한다.
 */
data class GuideSetupFacts(
    val handicapCount: Int,
    val humanPlaysBlack: Boolean,
) {
    /** 문구가 갈리는 세 갈래. 호선/흑 N점/백 N점. */
    val shape: Shape = when {
        handicapCount <= 0 -> Shape.Even
        humanPlaysBlack -> Shape.HumanTakesStones
        else -> Shape.HumanGivesStones
    }

    enum class Shape { Even, HumanTakesStones, HumanGivesStones }
}
