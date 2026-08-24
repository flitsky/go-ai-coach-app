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
 * 캐릭터 이름/설명은 "바둑 도장" 콘셉트로 확정됐다(백로그 #9, 2026-08-24) — 입문생에서 관장까지
 * 이름 자체가 서열이라, 출석으로 한 명씩 열리는 획득 순서(약한 상대 → 강한 상대)가 그대로 드러난다.
 * 이 이름만으로는 어느 쪽이 센지 모호하지 않도록, 픽커는 [PlayLevelSetting.tierLabel]("초보"/"하수"/...)을
 * 함께 병기한다(#10 몫). 아바타는 아직 플레이스홀더다([BotCharacter.avatarRef]가 전부 `null`).
 *
 * ⚠️ 이름/설명은 콘텐츠라 자유롭게 고쳐도 되지만 [BotCharacterId]와 티어 매핑은 저장 스키마의
 * 일부이므로 건드리지 않는다.
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
        fastBeginnerCharacter(
            tier = 1,
            name = "첫돌이",
            description = "오늘 처음 돌을 잡은 입문생. 두는 곳마다 실수예요.",
            unlockSource = BotUnlockSource.Attendance(tier = 1),
        ),
        // 두 번째 캐릭터 — 출석 5일차 보상.
        fastBeginnerCharacter(
            tier = 2,
            name = "연습생 돌뫼",
            description = "기본기를 익히는 중. 절반쯤은 제대로 둡니다.",
            unlockSource = BotUnlockSource.Attendance(tier = 5),
        ),
        // 3~5번째 — 획득 경로 미확정(6일차 이후/14·21·28일차 보상 vs 광고). 임시로 광고 획득.
        fastBeginnerCharacter(
            tier = 3,
            name = "도장생 반상",
            description = "웬만한 수는 받아칩니다. 방심하면 한 방 먹어요.",
            unlockSource = BotUnlockSource.AdWatch,
        ),
        fastBeginnerCharacter(
            tier = 4,
            name = "사범 묘수",
            description = "수를 읽고 빈틈을 파고듭니다. 실수는 놓치지 않아요.",
            unlockSource = BotUnlockSource.AdWatch,
        ),
        fastBeginnerCharacter(
            tier = 5,
            name = "관장 천원",
            description = "도장 최강. 언제나 최선의 수만 둡니다.",
            unlockSource = BotUnlockSource.AdWatch,
        ),
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

    /**
     * 출석 [tier]일차에 열리는 캐릭터들. "몇 일차에 주는가"는 카탈로그의
     * [BotUnlockSource.Attendance]가 단일 출처이므로, 출석 보상 정책표
     * (`AttendanceRewardPolicy`)는 이 함수를 통해 그 정보를 읽어 간다 — 같은 사실을 두 군데
     * 적어 두고 어긋나게 두지 않기 위함이다.
     */
    fun forAttendanceTier(tier: Int): List<BotCharacter> =
        all.filter { character ->
            (character.unlockSource as? BotUnlockSource.Attendance)?.tier == tier
        }
}

/** `FastBeginner` 캐릭터 한 종을 만든다. */
private fun fastBeginnerCharacter(
    tier: Int,
    name: String,
    description: String,
    unlockSource: BotUnlockSource,
): BotCharacter =
    BotCharacter(
        id = BotCharacterId("fast_beginner_$tier"),
        name = name,
        description = description,
        avatarRef = null,
        linkedPlayLevel = PlayLevelGroup.FastBeginner,
        tierWithinGroup = tier,
        unlockSource = unlockSource,
    )
