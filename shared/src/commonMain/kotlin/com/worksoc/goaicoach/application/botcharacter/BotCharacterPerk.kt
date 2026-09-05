package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController

/**
 * 6계층(Session & Continuity) — **유료로 산 캐릭터와 두는 동안** 인게임 프리미엄 기능을 열어
 * 주는 구매 특전이 지금 성립하는지(백로그 #18, `FEATURE_ACCESS_PRINCIPLES.md` 8장).
 *
 * 특전은 **구매한 캐릭터에만** 붙는다. 광고 조각이나 출석으로 얻은 캐릭터는 아무리 상위
 * 단계라도 특전이 없다 — 특전의 목적이 구매 유도이기 때문이다(2026-08-29 사용자 확정).
 *
 * 별도 저장 필드를 두지 않는다: "샀다"는 사실은 이미 두 값의 조합으로 결정된다 — 카탈로그가
 * 그 캐릭터를 [BotUnlockSource.Purchase]로 정의하고([BotCharacter.unlockSource]), 컬렉션이
 * 그것을 획득했다고 기록한 것([BotCollectionState.isClaimed]). 새 필드를 만들면 이 둘과
 * 어긋날 수 있는 세 번째 진실이 생긴다.
 *
 * @param opponent 지금 두고 있는 상대. AI가 아니거나 캐릭터가 대응되지 않으면 `null`을 넘긴다.
 */
fun isBotCharacterPerkActive(
    opponent: BotCharacter?,
    collection: BotCollectionState,
): Boolean {
    val character = opponent ?: return false
    if (character.unlockSource !is BotUnlockSource.Purchase) return false
    return collection.isClaimed(character.id)
}

/**
 * 지금 대국의 **상대 캐릭터**. 특전 판정([isBotCharacterPerkActive])의 입력을 만든다.
 *
 * AI 좌석의 레벨에서 캐릭터를 되찾는다 — 사람끼리 두는 대국이면 상대가 캐릭터가 아니므로
 * `null`이다. 양쪽이 다 AI인 관전 구성에서는 백을 상대로 본다: 기본 배치가 흑=사람/백=AI라
 * (`match/MatchPolicy.kt`) "상대"라는 말이 가리키는 자리와 일치한다.
 */
fun matchOpponentCharacter(setup: PlayerSetup): BotCharacter? {
    val aiSide = when {
        setup.white.controller == SeatController.Ai -> setup.white
        setup.black.controller == SeatController.Ai -> setup.black
        else -> return null
    }
    return BotCharacterCatalog.forPlayLevel(aiSide.playLevel)
}
