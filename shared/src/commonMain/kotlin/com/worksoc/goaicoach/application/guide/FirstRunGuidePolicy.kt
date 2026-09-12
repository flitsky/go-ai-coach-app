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
 * 한때는 표면과 단계가 1:1이라 [autoPlayStep]이 그냥 첫 미시청 단계를 고르면 됐다. 그런데
 * #18·#26(구매·구독)이나 로그인이 붙으면 **이미 단계가 있는 표면**(대국 설정·마이페이지)에 두 번째
 * 단계가 생긴다. 그때 판정식을 다시 쓰지 않도록 **[GuideStep] 선언 순서가 같은 표면 안에서도
 * 권위를 갖는다** — 그 성질을 `FirstRunGuidePolicyTest`가 케이스로 못박는다. 지금은 공짜이고,
 * 나중에는 판정식 재작성이다.
 */
enum class GuideSurface {
    AttendanceClaim,
    Home,
    MatchSetup,
    InGame,
}

/**
 * 가이드의 단계. **선언 순서가 재생 순서이고, 한 표면에서 둘이 경쟁할 때의 우선순위다.**
 *
 * ⚠️ [id]는 **저장 포맷**이다(`seen_steps` 집합에 이 문자열이 들어간다). 함정 1번과 같은 성질이라
 * **상수 이름이 아니라 이 문자열을 바꾸면** 이미 본 사용자에게 가이드가 다시 뜬다. 바꾸지 말 것.
 *
 * ⚠️ 번호 ②~⑤는 #128이 붙인 그대로다. **①(랜딩 상단 좌우 첫돌이)은 #140이 랜딩과 함께 없앴다** —
 * 문구 없는 정적 장식이라 판정에 참여한 적이 없어 `landing`이 `seen_steps`에 쓰인 적도 없다.
 */
enum class GuideStep(
    val id: String,
    val surface: GuideSurface,
    val target: GuideTarget? = null,
) {
    /** ② 출석 보상 팝업 안 한 줄. */
    AttendanceClaim("attendance_claim", GuideSurface.AttendanceClaim),

    /** ③ 홈 `대국 하기` 카드 우상단 말풍선. */
    HomeStartMatch("home_start_match", GuideSurface.Home),

    /** ④ 대국 설정 화면. */
    MatchSetup("match_setup", GuideSurface.MatchSetup),

    // ⑤ 대국 화면 — **버튼마다 하나씩** 넷으로 쪼갰다(2026-09-09 사용자 지시).
    //
    // ⚠️ 처음에는 한 장에 넷을 몰아 설명했는데(`in_game_tools`), 실기에서 두 가지가 드러났다:
    // ⓐ 카드가 **판을 덮는다** — 접바둑 첫 대국은 AI가 먼저 두므로 그 순간을 가린다.
    // ⓑ 넷을 한꺼번에 읽어야 해서 **어느 글자가 어느 버튼인지** 눈으로 잇기 어렵다.
    // 그래서 각 단계가 **자기 버튼 옆에서** 말하고, 그 버튼에 동그라미를 쳐서 가리킨다.
    //
    // ⚠️ **이 넷이 한 표면(InGame)에 사는 첫 사례다.** 판정이 *"선언 순서상 첫 미시청 단계"* 를
    // 고르므로 이 순서가 곧 재생 순서다 — `FirstRunGuidePolicyTest`가 그 성질을 이미 못박아 두었고,
    // 그것을 미리 세워 둔 덕분에 이 쪼개기가 판정식 수정 없이 끝났다.
    // ⚠️ 돋보기·판 크기 단계는 **#143이 지웠다** — 그 두 버튼이 메뉴로 들어가 대국 화면에 없다.
    //   가리킬 것이 없는 코치마크는 조용히 안 뜨는 것이 아니라 **아예 두지 않는다**(저장된 id는 무시된다).
    InGameEval("in_game_eval", GuideSurface.InGame, GuideTarget.Eval),
    InGameTopMoves("in_game_top_moves", GuideSurface.InGame, GuideTarget.TopMoves),
}

/**
 * 코치마크가 **동그라미를 칠 대상**. 화면이 자기 컨트롤의 자리를 이 키로 알려 준다.
 *
 * ⚠️ 좌표를 화면 밖으로 흘리는 것은 이 저장소에 선례가 없다(`onGloballyPositioned` 사용처 0건).
 * ③ 말풍선에서는 그래서 좌표 없이 푸는 쪽을 택했는데, *"버튼을 가리지 않게 그 옆에서, 버튼에
 * 동그라미"* 라는 요구는 **대상의 자리를 알아야만** 성립한다 — 그 대가를 여기서 치른다.
 */
enum class GuideTarget { Eval, TopMoves }

/**
 * 저장된 진행도. [GuideProgressStore]가 이 값을 싣고 내린다.
 *
 * @param armed **첫 실행 처리**(`FirstRunGate`)를 이 설치에서 거쳤는가. 이 한 값이 **기존 사용자에게
 *   자동 재생이 시작되지 않는 것**을 공짜로 만든다: 이미 첫 실행을 지난 사용자는 그 처리가 다시 돌지
 *   않으므로 `armed`가 꺼진 채이고, 자동 재생이 아예 무장되지 않는다(#140 전에는 랜딩을 끝낼 때 켜졌다).
 *
 * ⚠️ **`dismissed`("그만 보기") 플래그는 두었다가 없앴다**(2026-09-09 사용자 판정). 사슬이 짧아서
 * (대국 화면 넷 + 앞의 둘) *"알겠어요"* 를 연타하면 곧 끝나는데, **사정거리가 다른 버튼 둘**을
 * 나란히 두면 사용자가 무엇을 껐는지 알 수 없다는 지적이었다 — 그래서 동작은 하나로 줄였다.
 * 나중에 *"가이드 다시 보지 않기"* 를 정말 두게 되면 **설정 화면의 항목**으로 붙일 것이고,
 * 그때 이 플래그를 되살리면 된다. 쓰는 사람이 없는 플래그를 미리 남겨 두지는 않는다.
 * @param seenSteps 이미 보여준 단계의 [GuideStep.id] 집합.
 */
data class GuideProgress(
    val armed: Boolean = false,
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
    if (!progress.armed || blocked) return null
    return GuideStep.entries
        .filter { step -> step.surface == surface }
        .firstOrNull { step -> !progress.hasSeen(step) }
}
