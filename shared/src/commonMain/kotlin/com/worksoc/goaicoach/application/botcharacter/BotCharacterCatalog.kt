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
 * 이 이름만으로는 어느 쪽이 센지 모호하지 않도록, 픽커는 티어명("초보"/"하수"/...)을 함께
 * 병기한다(#10 몫). 아바타는 아직 플레이스홀더다([BotCharacter.avatarRef]가 전부 `null`).
 *
 * ⚠️ **그 이름과 설명은 이 파일에 없다**(백로그 #32) — 네 언어 표가 UI 계층의
 * `ui/UiStringsBotCharacters.kt`에 있다. 여기 한국어 리터럴로 두었더니 다른 언어 화면에도 한글이
 * 그대로 나갔다. 이 카탈로그는 **id와 획득 경로**만 정한다.
 *
 * ⚠️ [BotCharacterId]와 티어 매핑은 저장 스키마의 일부이므로 건드리지 않는다. 그 id는 위 문구
 * 표의 키이기도 해서, 바꾸면 이름이 통째로 id 문자열로 보인다.
 */
object BotCharacterCatalog {

    /**
     * [PlayLevelGroup.FastBeginner] 5단계에 1:1로 대응하는 캐릭터 5종(티어 오름차순).
     *
     * **획득 경로는 2026-08-24 재확정본(7장 표)을 따르며, 티어 오름차순이 아니다 — 의도된 배치다.**
     * 무료 사용자는 1단계(기본)와 3단계(출석 4일차)를 갖게 되고 그 사이의 2단계가 비어 있는데,
     * **기본으로 쥔 3단계가 버거우면 광고를 봐서라도 2단계를 데려오라**는 유인이다. 그래서
     * 경로가 `기본·출석 = 1 → 3`, `광고 조각 = 2 → 4`, `유료 = 5`로 갈라진다.
     *
     * 이 배치는 #8의 "전 종 잠금"을 되돌린 것이다(백로그 #16) — 전부 잠그면 아무것도 획득하지
     * 않은 사용자에게 고를 캐릭터가 하나도 없는 빈 상태가 생겼다.
     *
     * ⚠️ 여기 적힌 것은 **획득 경로 표기까지**다. 조각 누적 진행도(#11)와 결제 배선(#18)은 각
     * 항목이 가져간다 — 지금 이 카탈로그만으로는 2·4·5단계를 실제로 열 방법이 아직 없다.
     */
    val fastBeginnerRoster: List<BotCharacter> = listOf(
        // 1단계 — 기본 제공. 설치 즉시 잠금 없이 쓸 수 있는 유일한 캐릭터다.
        fastBeginnerCharacter(
            tier = 1,
            unlockSource = BotUnlockSource.Default,
        ),
        // 2단계 — 광고 5회 조각 누적(#11).
        fastBeginnerCharacter(
            tier = 2,
            unlockSource = BotUnlockSource.AdShards(required = 5),
        ),
        // 3단계 — 출석 4일차 보상. 무료 사용자가 얻는 두 번째 캐릭터다(5단계도 28일차 출석이다).
        fastBeginnerCharacter(
            tier = 3,
            unlockSource = BotUnlockSource.Attendance(tier = 4),
        ),
        // 4단계 — 광고 10회 조각 누적(#11).
        fastBeginnerCharacter(
            tier = 4,
            unlockSource = BotUnlockSource.AdShards(required = 10),
        ),
        // 5단계 — 개별 구매 전용(#18). 광고로도 출석으로도 열리지 않는다.
        fastBeginnerCharacter(
            tier = 5,
            unlockSource = BotUnlockSource.Attendance(tier = TopCharacterAttendanceTier),
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
     *
     * 출석으로 열리는 캐릭터는 **3단계(4일차)와 5단계(28일차)** 둘이다(2026-08-29). 1일차에는
     * 캐릭터가 걸리지 않는다 — #16이 1단계를 기본 제공으로 돌리면서 1일차 중복 지급이 사라졌다.
     *
     * ⚠️ **정책표는 이 함수를 접지 않은 실제 회차로 부른다.** 8일차 이후 소모품은 7일차 내용을
     * 반복하지만(`AttendanceRewardPolicy`), 캐릭터는 **한 번뿐인 영구 획득**이라 반복 축과 성질이
     * 다르다 — 접힌 회차(항상 7)로 조회하면 28일차 캐릭터에 영영 닿지 못한다.
     */
    fun forAttendanceTier(tier: Int): List<BotCharacter> =
        all.filter { character ->
            (character.unlockSource as? BotUnlockSource.Attendance)?.tier == tier
        }

    /**
     * 조각을 모아서 여는 캐릭터들([BotUnlockSource.AdShards]).
     *
     * [forAttendanceTier]와 같은 이유로 카탈로그가 단일 출처다 — 출석 정책표가 "누구에게 조각을
     * 주는가"를 따로 적어 두면, 캐릭터의 획득 경로를 바꿀 때 한쪽만 고쳐져 어긋난다.
     *
     * **이 경로가 광고 전용이 아니라는 점이 중요하다.** 조각은 캐릭터에 붙은 성질이고, 그것을
     * 어디서 얻는지는 별개 축이다 — 광고 시청(#11)과 출석 장기 보상이 같은 조각 저장소에 쌓인다.
     */
    fun shardPathCharacters(): List<BotCharacter> =
        all.filter { character -> character.unlockSource is BotUnlockSource.AdShards }
}

/**
 * 최상위 캐릭터(5단계 관장 천원)를 여는 출석 회차.
 *
 * 원래는 유료 구매 전용이었다(백로그 #18). 2026-08-29에 Play Console의 "수익 창출"이 앱 설정
 * 대시보드 미완료로 막혀 상품 등록 자체가 불가능해지자, **결제를 미루고 장기 출석 보상으로
 * 돌렸다**(사용자 결정) — 최상위 상대에 닿는 길이 아예 없는 것보다 낫다.
 *
 * 4주(7의 배수 회차 중 네 번째)라 기존 반복 주기와 어긋나지 않는다. 결제를 다시 열 때는 이
 * 캐릭터를 `Purchase`로 되돌리거나, 유료 전용 캐릭터를 새로 추가하면 된다 — #18이 넣은 결제·특전
 * 배선은 지우지 않고 그대로 잠들어 있다.
 */
const val TopCharacterAttendanceTier: Int = 28

/** `FastBeginner` 캐릭터 한 종을 만든다. */
private fun fastBeginnerCharacter(
    tier: Int,
    unlockSource: BotUnlockSource,
): BotCharacter =
    BotCharacter(
        id = BotCharacterId("fast_beginner_$tier"),
        avatarRef = null,
        linkedPlayLevel = PlayLevelGroup.FastBeginner,
        tierWithinGroup = tier,
        unlockSource = unlockSource,
    )
