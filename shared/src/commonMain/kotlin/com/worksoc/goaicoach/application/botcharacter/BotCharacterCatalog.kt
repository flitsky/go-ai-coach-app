package com.worksoc.goaicoach.application.botcharacter

import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting

/**
 * 봇 캐릭터 카탈로그 — "어떤 캐릭터가 존재하고 각각이 어느 AI 레벨에 대응하는가"의 단일 출처.
 * 사용자가 무엇을 수집했는지는 [BotCollectionState]가 따로 들고 있고, 이 객체는 상태가 없다.
 *
 * Phase 1 범위는 [PlayLevelGroup.FastBeginner]의 5단계(초보~초고수)뿐이다 — 7.1절대로 이 5단계가
 * 곧 캐릭터 5종이 된다. `초급`/`중급`/`고급` 그룹은 현재 대국 셋업 UI에서 숨겨져 있어(대국장
 * 로드맵 예정) 캐릭터화 대상이 아니며, 그래서 [forPlayLevel]은 그 그룹들에 대해 `null`을 준다.
 *
 * ⚠️ [BotCharacter.name]/[BotCharacter.description]은 **전부 플레이스홀더**다 — 실제 캐릭터 이름과 설명은 백로그 #9에서
 * 사용자가 확정한다. 그때는 아래 [placeholderName]/[placeholderDescription] 호출만 확정된 문자열
 * 리터럴로 갈아끼우면 되고, [BotCharacterId]와 티어 매핑은 건드리지 않는다.
 */
object BotCharacterCatalog {

    /**
     * [PlayLevelGroup.FastBeginner] 5단계에 1:1로 대응하는 캐릭터 5종(티어 오름차순).
     *
     * **모든 캐릭터가 잠겨 있고 획득해야 쓸 수 있다** — 지금까지 조건 없이 고를 수 있던 난이도
     * 5단계가 좁아지는 것은 의도된 방향이다(2026-08-24 사용자 확정). 1·2번째 캐릭터의 지급
     * 일차는 4.2절 보상 정책표에서 왔고, 3~5번째의 획득 경로는 아직 확정 전이라 임시로
     * [BotUnlockSource.AdWatch]를 달아 뒀다.
     */
    val fastBeginnerRoster: List<BotCharacter> = listOf(
        // 첫 번째 캐릭터 — 출석 1일차 보상(첫 실행 즉시 획득).
        fastBeginnerCharacter(tier = 1, unlockSource = BotUnlockSource.Attendance(tier = 1)),
        // 두 번째 캐릭터 — 출석 5일차 보상.
        fastBeginnerCharacter(tier = 2, unlockSource = BotUnlockSource.Attendance(tier = 5)),
        // 3~5번째 — 획득 경로 미확정(6일차 이후/14·21·28일차 보상 vs 광고). 임시로 광고 획득.
        fastBeginnerCharacter(tier = 3, unlockSource = BotUnlockSource.AdWatch),
        fastBeginnerCharacter(tier = 4, unlockSource = BotUnlockSource.AdWatch),
        fastBeginnerCharacter(tier = 5, unlockSource = BotUnlockSource.AdWatch),
    )

    /** 현재 존재하는 모든 캐릭터. 다른 그룹이 캐릭터화되면 여기에 합친다. */
    val all: List<BotCharacter> = fastBeginnerRoster

    fun byId(id: BotCharacterId): BotCharacter? = all.firstOrNull { character -> character.id == id }

    /**
     * 이 AI 레벨 설정에 대응하는 캐릭터를 돌려준다. 범위를 벗어난 `level`은
     * [PlayLevelSetting.safeLevel]로 이미 보정돼 있으므로 그 값을 기준으로 찾는다.
     */
    fun forPlayLevel(setting: PlayLevelSetting): BotCharacter? =
        all.firstOrNull { character ->
            character.linkedPlayLevel == setting.group && character.tierWithinGroup == setting.safeLevel
        }

    /** 저장된 id 문자열을 카탈로그 정의로 되돌린다. 모르는 id면 `null`(다운그레이드 등). */
    fun byRawId(raw: String): BotCharacter? = byId(BotCharacterId(raw))
}

/** `FastBeginner` 캐릭터 한 종을 만든다. */
private fun fastBeginnerCharacter(tier: Int, unlockSource: BotUnlockSource): BotCharacter {
    val group = PlayLevelGroup.FastBeginner
    return BotCharacter(
        id = BotCharacterId("fast_beginner_$tier"),
        name = placeholderName(group, tier),
        description = placeholderDescription(group, tier),
        avatarRef = null,
        linkedPlayLevel = group,
        tierWithinGroup = tier,
        unlockSource = unlockSource,
    )
}

/** #9 확정 전까지 쓰는 임시 이름 — 기존 난이도 라벨("빠른 초급 · 초보")을 그대로 빌려 쓴다. */
private fun placeholderName(group: PlayLevelGroup, tier: Int): String =
    PlayLevelSetting(group = group, level = tier).displayLabel

/** #9 확정 전까지 쓰는 임시 설명 — 해당 단계의 착수 정책 설명을 그대로 빌려 쓴다. */
private fun placeholderDescription(group: PlayLevelGroup, tier: Int): String =
    PlayLevelSetting(group = group, level = tier).selectionPolicy.description
