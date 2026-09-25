package com.worksoc.goaicoach.engine

/**
 * 엔진 세션 클라이언트가 **세션 세대**를 물어보는 자리(refactor backlog #18).
 *
 * ## 왜 필요한가 — 만드는 순서가 거꾸로다
 * `LocalEngineSessionClient`는 `MainActivity`가 만든다(#101 이후 엔진보다도 먼저). 세대의 원천인
 * `GameSessionStateHolder`는 그 아래 `GoCoachApp`이 만든다. 클라이언트를 만드는 순간에는 읽을
 * 대상이 아직 없다. 그래서 클라이언트에는 **이 중계기를 읽는 람다**를 주고, 홀더가 생기는 자리에서
 * 중계기를 그 홀더에 잇는다. [DeferredEngineCoreApi]가 *"아직 없는 엔진"* 을 기다리는 것과 같은
 * 모양이고, 기다리지 않는다는 것만 다르다.
 *
 * ## 잇기 전의 답은 0이다
 * 예전에 3계층이 박아 넣던 값과 같다 — 잇기 전에 나간 요청의 로그는 #18 이전과 똑같이 찍힌다.
 * 이 값은 진단 로그(`operationId`의 `g` 번호와 `sessionGeneration` 문맥)에만 쓰이고 결과 폐기
 * 판정에는 쓰이지 않으므로(`LocalEngineSessionClient`의 해당 매개변수 KDoc), 어느 쪽이든 사용자에게
 * 보이는 동작은 같다.
 */
internal class SessionGenerationRelay {

    /**
     * ⚠️ **`@Volatile`이다** — [bind]는 메인 스레드(컴포지션)에서, [current]는 엔진 IO 스레드에서 불린다.
     */
    @Volatile
    private var reader: () -> Long = { 0L }

    /**
     * 세대를 읽을 곳을 잇는다. 다시 부르면 **나중 것이 이긴다** — 화면이 다시 만들어져 홀더가 새로
     * 생기면 새 홀더를 가리켜야 한다. [reader]는 [current]와 같은 조건을 지켜야 한다 — 싸고, 막히지
     * 않고, 아무 스레드에서나 안전할 것.
     */
    fun bind(reader: () -> Long) {
        this.reader = reader
    }

    fun current(): Long = reader()
}
