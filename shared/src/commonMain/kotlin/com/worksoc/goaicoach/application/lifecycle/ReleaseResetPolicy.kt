package com.worksoc.goaicoach.application.lifecycle

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.premium.PremiumState

/**
 * 6계층(Session & Continuity) — **정식 릴리즈 초기화**를 이번 실행에서 해야 하는지 판정한다
 * (백로그 #63, `FEATURE_ACCESS_PRINCIPLES.md` 8.3-2).
 *
 * 비공개 테스트 기간에 쌓인 권한(캐릭터·출석·1회권·프리미엄)은 **테스트용이었으므로 정식
 * 릴리즈에서 민다**(2026-09-01 사용자 확정). #26이 *"'초기화를 고려한다'와 '초기화한다'는
 * 다르다"* 며 미결로 남겼던 질문의 답이다.
 *
 * ⚠️ **버전 코드가 아니라 "세대(generation)"를 기준으로 삼는다.** versionCode를 쓰면 개발 중
 * 빌드가 올라갈 때마다 경계가 흔들리고(빌드마다 `bump-version`이 1씩 올린다), 나중에 또 밀어야
 * 할 때 "그때 버전이 몇이었지"를 되짚어야 한다. 세대는 **[CurrentResetGeneration]을 1 올리는
 * 것만으로** 다음 초기화를 예약할 수 있고, 빌드 번호와 무관하게 의미가 고정된다.
 */
object ReleaseResetPolicy {

    /**
     * 지금까지 발행된 초기화 세대. **이 값을 올리면 그 다음 실행에서 한 번 더 초기화된다.**
     *
     * - `1` — 정식 릴리즈 초기화(2026-09-01, #63). 비공개 테스트 기간의 권한을 민다.
     *
     * ⚠️ **올릴 때는 반드시 위 목록에 한 줄을 남길 것.** 숫자만 올리면 다음 사람이 "이 세대는
     * 무엇을 왜 밀었는가"에 닿지 못한다.
     */
    const val CurrentResetGeneration: Int = 1

    /**
     * 마커가 아직 없는 저장 상태가 뜻하는 값. 초기화 세대 개념이 없던 빌드에서 올라온 경우이고,
     * 그쪽은 정의상 **아무 세대도 적용되지 않았다.**
     */
    const val NoResetApplied: Int = 0

    /**
     * [lastAppliedGeneration]까지 적용된 기기에서 [currentGeneration]을 적용해야 하는가.
     *
     * ⚠️ **신규 설치도 참을 돌려준다** — 마커가 없으니 [NoResetApplied]로 읽히기 때문이다.
     * 그래도 안전하다: 지울 것이 없는 기기에서 초기화는 **아무 일도 하지 않는 무해한 연산**이고,
     * 반대로 "신규 설치인지 업그레이드인지"를 저장소 내용으로 추측하려 들면 판정이 데이터에
     * 의존해 흔들린다. 판정을 마커 하나에만 걸어 두는 편이 단순하고 틀릴 여지가 없다.
     */
    fun shouldReset(
        lastAppliedGeneration: Int,
        currentGeneration: Int = CurrentResetGeneration,
    ): Boolean = lastAppliedGeneration < currentGeneration

    /**
     * 지울 것이 **실제로 있었는가**. 초기화 안내를 띄울지가 이 값에 걸린다.
     *
     * ⚠️ **[shouldReset]과 헷갈리지 말 것 — 묻는 것이 다르다.** 초기화는 신규 설치에서도 돌지만
     * (지울 것이 없어 무해하다), 안내는 **잃은 사람에게만** 가야 한다. 둘을 같은 값으로 묶으면
     * **처음 설치한 사람이 "테스트 기간 기록이 초기화됐다"는 안내를 받는다.**
     *
     * ⚠️ **반드시 지우기 전에 물어야 한다.** 지운 뒤에는 넷 다 기본값이라 구분이 사라진다.
     */
    fun hasEntitlementData(
        attendance: AttendanceState,
        collection: BotCollectionState,
        consumables: ConsumableInventory,
        premium: PremiumState,
    ): Boolean =
        attendance != AttendanceState() ||
            collection != BotCollectionState() ||
            consumables != ConsumableInventory() ||
            premium != PremiumState()
}
