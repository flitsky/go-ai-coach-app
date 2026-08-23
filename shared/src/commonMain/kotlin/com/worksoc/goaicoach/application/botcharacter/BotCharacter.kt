package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting

/**
 * 봇 캐릭터의 영구 식별자. 저장소(`persistence/BotCollectionStore.kt`)에 그대로 문자열로
 * 기록되므로 **한 번 정한 [raw] 값은 바꾸지 않는다** — 값을 바꾸면 이미 수집한 사용자의
 * 컬렉션이 통째로 유실된다. 표시 이름([BotCharacter.name])은 콘텐츠라 자유롭게 바꿔도 되지만
 * 이 id는 스키마의 일부다.
 */
data class BotCharacterId(val raw: String)

/**
 * 봇 캐릭터의 획득 경로(카탈로그 메타데이터). "이 캐릭터를 어떻게 얻는가"를 나타내며,
 * "이 사용자가 지금 갖고 있는가"는 [BotCollectionState]가 따로 관리한다 — 두 축은 분리돼 있다.
 *
 * `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 7장에 따라 sealed class로 열어둔다.
 * 지금 범위 밖인 개별 구매/월 구독 상품이 나중에 추가되면 `Purchase`/`Subscription` 하위 타입을
 * 여기에 더하면 되고, 그때 `when` 분기가 컴파일 에러로 드러나 빠뜨린 처리를 잡아준다.
 */
sealed class BotUnlockSource {
    /** 출석 [tier]일차 보상으로 지급되는 캐릭터(4.2절 보상 정책표). */
    data class Attendance(val tier: Int) : BotUnlockSource()

    /** 리워드 광고 시청으로 획득. 기존 프리미엄의 1시간 임시 활성화와 달리 **영구 획득**이다(#11). */
    data object AdWatch : BotUnlockSource()
}

/**
 * 6계층(Session & Continuity) — AI 봇 캐릭터 한 종의 카탈로그 정의.
 *
 * ⚠️ `match/MatchPolicy.kt`의 [com.worksoc.goaicoach.match.AiCharacterProfile]과 **다른 개념이다.**
 * 그쪽은 "엔진 + 난이도"를 한 줄 라벨로 묶어 보여주는 표시용 값이고(수집 개념 없음), 이 타입은
 * 수집 대상이 되는 캐릭터 자체다. 7장 원칙대로 [PlayLevelGroup]을 대체하지 않고 그 **위에
 * 프레젠테이션 레이어를 씌우는** 방식이라, 난이도/AI 강도 로직은 [linkedPlayLevel]로 그대로
 * 위임한다.
 *
 * [avatarRef]는 Android 드로어블 리소스 id가 아니라 플랫폼 비종속 문자열 키다(`shared`는 플랫폼
 * 독립이어야 하므로) — 실제 이미지 해석은 UI 계층의 몫이다. Phase 1에서는 항상 `null`(플레이스홀더).
 *
 * [tierWithinGroup]이 `null`이면 그룹 전체에 대응할 뿐 특정 단계에 묶이지 않는다는 뜻이다 —
 * Phase 1 카탈로그는 전부 특정 티어에 1:1로 묶이므로 실제로는 항상 값이 있다.
 */
data class BotCharacter(
    val id: BotCharacterId,
    val name: String,
    val description: String,
    val avatarRef: String? = null,
    val linkedPlayLevel: PlayLevelGroup,
    val tierWithinGroup: Int?,
    val unlockSource: BotUnlockSource,
) {
    /**
     * 이 캐릭터를 고른다는 것이 곧 어떤 AI 레벨을 고르는 것인지 돌려준다(7.1절 — 캐릭터 선택이
     * 곧 레벨 선택). [tierWithinGroup]이 없으면 특정 단계로 환원할 수 없으므로 `null`.
     */
    fun toPlayLevelSetting(): PlayLevelSetting? =
        tierWithinGroup?.let { tier -> PlayLevelSetting(group = linkedPlayLevel, level = tier).normalized() }
}
