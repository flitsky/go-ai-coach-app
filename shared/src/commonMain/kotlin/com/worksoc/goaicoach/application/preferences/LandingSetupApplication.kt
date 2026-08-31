package com.worksoc.goaicoach.application.preferences

import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.Ruleset

/**
 * 첫 실행 랜딩에서 사용자가 고르는 **자기 실력**(백로그 #51).
 *
 * ⚠️ **봇 5단계의 이름(초보/하수/중수/고수/초고수)과 일부러 다른 어휘를 쓴다.** 같은 낱말을
 * 쓰면 "내 실력"과 "상대 이름"이 한 화면 안에서 섞여, 최상급이라고 답한 사람이 "초보와
 * 두세요"라는 안내를 받는 꼴이 된다(2026-08-31 사용자 확정).
 *
 * ⚠️ **낮은 등급이라고 약한 상대를 붙이는 것이 아니다.** 1단계 봇도 실제 기력은 일반 중급자를
 * 상회한다(사용자 확인) — 그래서 다섯 답 모두 같은 1단계를 상대로 두되, **접바둑 돌 수와
 * 좌석으로만** 균형을 맞춘다. 문구도 "쉬운 상대를 붙였다"고 말하면 안 된다.
 */
enum class SelfRatedSkill {
    Entry,
    Beginner,
    Intermediate,
    Advanced,
    Expert,
}

/**
 * [SelfRatedSkill]이 정하는 것은 **접바둑 돌 수와 누가 흑을 잡는가** 둘뿐이다.
 *
 * [humanPlaysBlack]이 `false`면 사람이 백을 잡고 **AI가 접바둑 돌을 받는다**(이른바 "후수").
 * 접바둑 돌은 규칙상 항상 흑이 놓으므로, 좌석을 뒤집는 것이 곧 "내가 돌을 접어 준다"가 된다.
 */
data class LandingSetupPlan(
    val handicapCount: Int,
    val humanPlaysBlack: Boolean,
)

/** 자기 실력 → 접바둑 계획(2026-08-31 사용자 확정 표). */
fun landingSetupPlan(skill: SelfRatedSkill): LandingSetupPlan =
    when (skill) {
        SelfRatedSkill.Entry -> LandingSetupPlan(handicapCount = 5, humanPlaysBlack = true)
        SelfRatedSkill.Beginner -> LandingSetupPlan(handicapCount = 3, humanPlaysBlack = true)
        SelfRatedSkill.Intermediate -> LandingSetupPlan(handicapCount = 0, humanPlaysBlack = true)
        SelfRatedSkill.Advanced -> LandingSetupPlan(handicapCount = 2, humanPlaysBlack = false)
        SelfRatedSkill.Expert -> LandingSetupPlan(handicapCount = 3, humanPlaysBlack = false)
    }

/**
 * 랜딩의 답을 초기 설정으로 옮긴다. **순수 함수다** — 저장은 호출부가 한다.
 *
 * ⚠️ **[current]에서 출발해 필요한 축만 덮어쓴다.** 기본값으로 새 스냅샷을 만들면 판 크기·덤·
 * 표시 옵션 같은 나머지가 조용히 초기화된다(`UserPreferencesAutosaveApplication`이 경고하는
 * 그 사고와 같은 모양이다).
 *
 * ⚠️ 접바둑 돌 수는 **판 크기 상한으로 자른다.** 13x13·9x9는 5개까지라 표의 값이 그대로
 * 들어가지만, 사용자가 이미 9x9를 쓰고 있다가 랜딩을 다시 보는 경우까지 안전하게 둔다.
 */
fun applyLandingSetup(
    current: UserPreferencesSnapshot,
    skill: SelfRatedSkill,
    ruleset: Ruleset,
): UserPreferencesSnapshot {
    val plan = landingSetupPlan(skill)
    val human = if (plan.humanPlaysBlack) SeatController.Human else SeatController.Ai
    val ai = if (plan.humanPlaysBlack) SeatController.Ai else SeatController.Human
    return current.copy(
        ruleset = ruleset,
        handicapCount = plan.handicapCount.coerceIn(0, current.boardSize.maxHandicapCount),
        playerSetup = current.playerSetup.copy(
            // 좌석의 나머지 설정(엔진 선택 등)은 건드리지 않고 controller/레벨만 바꾼다.
            black = current.playerSetup.black.copy(controller = human, playLevel = FirstTierOpponent),
            white = current.playerSetup.white.copy(controller = ai, playLevel = FirstTierOpponent),
        ),
        hasSeenOnboarding = true,
    )
}

/**
 * 다섯 답 모두 **1단계를 상대로** 시작한다 — 신규 설치 시 획득해 둔 캐릭터가 그것뿐이라
 * 다른 선택지가 애초에 없고, 상위 캐릭터는 모아서 여는 것이 이 앱의 구조이기 때문이다.
 */
private val FirstTierOpponent = PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = 1)
