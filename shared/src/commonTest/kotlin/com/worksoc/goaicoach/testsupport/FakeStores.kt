package com.worksoc.goaicoach.testsupport

import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.premium.port.PremiumStateStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort

/**
 * [PremiumStateStorePort]의 인메모리 페이크.
 *
 * ## 통합하며 대조한 두 구현
 * `AttendanceRewardGrantTest`와 `ConsumableSpendApplicationTest`가 **같은 이름으로 각자** 들고 있었다.
 * 저장·적재 동작은 완전히 같았고 차이는 **관찰 면뿐**이었다 —
 * 앞은 `stored`(외부 쓰기 금지)만, 뒤는 `state`(외부에서 쓸 수 있는 var)와 `saveCount`를 노출했다.
 * 숨은 가정 불일치는 아니었으므로 **관찰 면을 합집합으로** 둔다. 어느 쪽 단언도 잃지 않는다.
 * 쓰기는 `private set` 쪽(더 좁은 쪽)으로 맞췄다 — 페이크의 상태를 테스트가 몰래 갈아끼우면
 * 그 시점부터 저장소가 아니라 그냥 변수다.
 */
class FakePremiumStore(
    initial: PremiumState = PremiumState(),
) : PremiumStateStorePort {
    var stored: PremiumState = initial
        private set

    /** `ConsumableSpendApplicationTest`가 쓰던 이름. [stored]와 같은 값이다. */
    val state: PremiumState get() = stored

    var saveCount: Int = 0
        private set

    override fun save(state: PremiumState) {
        stored = state
        saveCount += 1
    }

    override fun load(): PremiumState = stored
}

/**
 * [SavedGameStorePort]의 인메모리 페이크.
 *
 * [calls]는 호출 순서까지 남긴다 — 저장/삭제가 **몇 번, 어떤 순서로** 일어났는지가
 * 이어하기 회귀에서 실제로 문제가 되는 지점이다.
 */
class FakeSavedGameStore : SavedGameStorePort {
    val calls: MutableList<String> = mutableListOf()
    var snapshot: SavedGameSnapshot? = null

    override fun save(snapshot: SavedGameSnapshot) {
        this.snapshot = snapshot
        calls += "save:${snapshot.savedAtMillis}"
    }

    override fun load(): SavedGameSnapshot? = snapshot

    override fun clear() {
        snapshot = null
        calls += "clear"
    }
}
