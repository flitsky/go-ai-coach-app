package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.PlayLevelSetting

/**
 * 저장된 AI 레벨이 획득하지 않은 캐릭터를 가리켜 낮춰야 한다는 판정 결과(#22).
 *
 * [from]은 원래 가리키던(=잠긴) 캐릭터, [to]는 실제로 쓸 수 있는 대체 상대다. 호출부는 이
 * 두 이름을 그대로 사용자에게 보여줘, 무엇이 왜 바뀌었는지 알린다 — 조용히 바꾸면 "내 설정이
 * 왜 바뀌었지"가 된다(2026-08-29 사용자 결정).
 */
data class BotLevelClamp(
    val from: BotCharacter,
    val to: BotCharacter,
    val playLevel: PlayLevelSetting,
)

/**
 * 5계층(App Service) — 저장된 [playLevel]이 아직 획득하지 않은 캐릭터를 가리키면, **획득한 것
 * 중 그 단계 이하에서 가장 높은 단계**로 낮춘다(백로그 #22, 2026-08-29 사용자 결정).
 *
 * **왜 필요한가**: 상대 캐릭터는 저장된 AI 레벨에서 파생되는데, 획득 여부는 픽커에서 **새로
 * 고를 때만** 강제됐다. 그래서 저장된 레벨이 잠긴 캐릭터를 가리키면 획득 시스템이 통째로
 * 우회됐다. 엣지 케이스가 아니다 — #10 이전의 단계 드롭다운은 5단계를 아무 게이트 없이
 * 제공했으므로(2026-08-18 5단계 개편 ~ 2026-08-29), 그 사이 2~5단계를 골라 둔 사용자가 전부
 * 해당된다.
 *
 * **"획득한 최고 단계"가 아니라 "요청 단계 이하에서 가장 높은 단계"인 이유**: 획득 집합은
 * 연속이 아닐 수 있다(출석으로 3단계만 먼저 얻고 2단계는 아직인 경우). 그냥 최고 단계를 쓰면
 * 요청보다 **더 센 상대**로 올라가 버려 "낮춘다"는 계약이 깨진다.
 *
 * 1단계는 [BotUnlockSource.Default]라 항상 쓸 수 있으므로, 조각/출석을 하나도 안 모은
 * 사용자에게도 대체 상대는 반드시 존재한다.
 *
 * @return 낮춰야 하면 그 판정, 그대로 둬도 되면 `null`. 캐릭터가 대응되지 않는 레벨
 *   (초급/중급/고급 그룹 — 카탈로그에 없다)도 `null`이라 건드리지 않는다.
 */
fun clampToOwnedBotCharacter(
    playLevel: PlayLevelSetting,
    collection: BotCollectionState,
): BotLevelClamp? {
    val current = BotCharacterCatalog.forPlayLevel(playLevel) ?: return null
    if (collection.isAvailable(current)) return null
    val currentTier = current.tierWithinGroup ?: return null

    val target = BotCharacterCatalog.all
        .filter { candidate ->
            candidate.linkedPlayLevel == current.linkedPlayLevel &&
                (candidate.tierWithinGroup ?: return@filter false) <= currentTier &&
                collection.isAvailable(candidate)
        }
        .maxByOrNull { candidate -> candidate.tierWithinGroup ?: 0 }
        ?: return null

    val level = target.toPlayLevelSetting() ?: return null
    return BotLevelClamp(from = current, to = target, playLevel = level)
}
