package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.shared.EngineMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineIdentityTest {

    /** 준비되지 않은 코어. 이 테스트들은 엔진을 부르지 않으므로 영원히 미완성이어도 된다. */
    private fun idleCoreApi() = DeferredEngineCoreApi(CompletableDeferred())

    @Test
    fun theUnresolvedAnswerDoesNotGuessWhichBackendCameUp() {
        // ⚠️ 여기가 `LocalProcess`나 `Stub`이면 **진단 리포트가 거짓말을 한다** — 아직 아무것도
        // 부팅되지 않았는데 부팅됐다고 말하는 셈이다(2026-09-05 사용자 결정: 예측하지 않는다).
        assertEquals(EngineMode.Unknown, EngineIdentity.Unresolved.mode)
    }

    @Test
    fun theUnresolvedNameIsSomethingASeatLabelCanShow() {
        // AI 좌석 라벨이 이 값을 **그대로** 쓴다 — 비어 있으면 좌석이 이름 없이 그려진다.
        // ⚠️ 백로그 #109 전까지는 그렇지 않았다(죽은 폴백이 가로막고 있었다).
        // 그 회귀를 막는 것은 `SeatLabelEngineNameTest`다.
        assertTrue(
            "준비 전 엔진 이름이 비어 있다 — AI 좌석이 이름 없이 그려진다.",
            EngineIdentity.Unresolved.name.isNotBlank(),
        )
    }

    @Test
    fun aFinishedBootstrapReportsItsOwnModeNameAndDiagnostic() {
        val bootstrap = EngineBootstrap(
            coreApi = idleCoreApi(),
            mode = EngineMode.Stub,
            displayName = "stub AI",
            diagnostic = "Stub fallback: missing native lib.",
        )

        val identity = bootstrap.identity()

        assertEquals(EngineMode.Stub, identity.mode)
        // ⚠️ 이름과 진단이 뒤바뀌면 좌석 라벨에 진단문이 통째로 뜬다 — 둘 다 String이라
        // 컴파일러가 잡아주지 않는다.
        assertEquals("stub AI", identity.name)
        assertEquals("Stub fallback: missing native lib.", identity.diagnostic)
    }
}
