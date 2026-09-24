package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.domain.allCoordinates
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import com.worksoc.goaicoach.shared.enginecontract.EngineState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공통 계약 스위트 — **[EngineCoreApi] 구현체라면 무엇이든** 정적 국면 동기화에 대해
 * 이 시나리오를 만족해야 한다(refactor backlog #20).
 *
 * ## 왜 이것이 이 일감의 본체인가
 * `syncStaticPosition`에는 "동기화했다"고 대답만 하는 **기본 구현이 있었다.** 그래서
 * [StubEngineAdapter]와 [RemoteEngineCoreApiAdapter]는 **둘 다 이 메서드를 아예 빠뜨린 채로
 * 컴파일됐고**, 동기화 직후의 `genMove`가 이미 돌이 놓인 자리를 골라 돌려줬다.
 * 기본 구현을 지우는 것만으로는 **지금 있는 구현체**만 고쳐질 뿐, 다음 구현체가 시그니처만
 * 채우고 본문을 비워 두는 것은 막지 못한다. 막는 것은 이 스위트다:
 * 새 구현체는 여기에 하위 클래스 한 줄만 추가하면 같은 그물에 걸린다.
 *
 * ## 무엇을 고정하는가
 * 동기화 뒤에는 **넘긴 국면이 엔진의 현재 국면**이다 — 놓인 돌도, 둘 차례도. 그리고 이후의
 * 착수는 그 국면 위에 쌓인다. 어떻게 저장하는지(프로세스에 명령을 보내든, 다음 원격 호출에
 * 실어 보내든)는 구현체 자유다.
 *
 * ## 여기에 둔 이유 / 빠진 구현체
 * 계약은 `:shared`(commonMain)에 있지만 구현체는 `engine-android`에 있고, `:shared`의
 * commonTest는 다른 모듈에서 소비할 수 없다(테스트 픽스처 아티팩트를 따로 내보내는 gradle
 * 배선이 없다). 그래서 스위트를 구현체 쪽에 뒀다.
 * - [StubEngineAdapter] → [StubEngineAdapterStaticPositionContractTest]
 * - [RemoteEngineCoreApiAdapter] → [RemoteEngineCoreApiAdapterStaticPositionContractTest]
 * - `KataGoProcessEngineAdapter`는 **여기서 못 돈다** — 실제 KataGo 프로세스를 띄워야 한다.
 *   실기에서 확인할 것(이 파도의 needsDevice 참고).
 * - `app-android`의 `DeferredEngineCoreApi`도 모듈이 달라 여기 못 붙는다. 그쪽은 단순 위임이라
 *   `DeferredEngineCoreApiTest`가 따로 본다.
 */
abstract class EngineCoreApiStaticPositionContract {
    /** 계약을 만족해야 하는 구현체를 새로(초기화 전 상태로) 만든다. */
    protected abstract fun createEngine(): EngineCoreApi

    private suspend fun readyEngine(): EngineCoreApi =
        createEngine().also { engine -> engine.initialize(EngineProfile()) }

    @Test
    fun syncStaticPositionReportsReady() = runBlocking {
        val engine = readyEngine()

        val status = engine.syncStaticPosition(StaticPosition)

        assertEquals(EngineState.Ready, status.state)
    }

    @Test
    fun genMoveAfterSyncAsksTheSyncedSideToPlay() = runBlocking {
        val engine = readyEngine()
        engine.syncStaticPosition(StaticPosition)

        val result = engine.genMove(StaticPosition.nextPlayer)

        assertEquals(StaticPosition.nextPlayer, result.move.player)
    }

    @Test
    fun genMoveAfterSyncNeverPicksAPointThatAlreadyHasAStone() = runBlocking {
        val engine = readyEngine()
        engine.syncStaticPosition(StaticPosition)

        val move = engine.genMove(StaticPosition.nextPlayer).move

        val play = move as? Move.Play
            ?: error("Expected a play on a synced position with ${StaticPosition.stones.size} stone(s), got $move")
        assertTrue(
            "${play.coordinate.label(StaticPosition.boardSize)} already holds a stone in the synced position — " +
                "the engine is still playing on a board it never received",
            play.coordinate !in StaticPosition.stones,
        )
    }

    @Test
    fun movesPlayedAfterSyncStackOnTopOfTheSyncedPosition() = runBlocking {
        val engine = readyEngine()
        engine.syncStaticPosition(StaticPosition)
        engine.playMove(Move.Play(StaticPosition.nextPlayer, FreePointPlayedAfterSync))

        val move = engine.genMove(StaticPosition.nextPlayer.opponent).move

        val play = move as? Move.Play ?: error("Expected a play, got $move")
        assertTrue(
            "${play.coordinate.label(StaticPosition.boardSize)} is occupied after the sync plus one move",
            play.coordinate !in StaticPosition.stones && play.coordinate != FreePointPlayedAfterSync,
        )
    }

    protected companion object {
        /**
         * 수순 없이 돌만 놓인 9x9 국면 — 카메라 인식/수동 배치가 만드는 모양이다.
         *
         * ⚠️ 돌 자리는 **아무 데나가 아니다.** [StubEngineAdapter]가 Beginner 프로필에서
         * 가장 먼저 고르는 자리(네 귀 → 네 변 중앙 → 천원)를 **전부 덮었다.** 동기화가 무동작이면
         * 스텁은 A9(0,0)을 돌려주고, 원격 어댑터는 빈 판을 실어 보내 같은 자리를 받아 온다 —
         * 둘 다 이 스위트에서 즉시 빨개진다.
         */
        val StaticPosition: GameState = GameState(
            boardSize = BoardSize.Nine,
            ruleset = Ruleset.Japanese,
            nextPlayer = StoneColor.White,
            stones = mapOf(
                BoardCoordinate(0, 0) to StoneColor.Black,
                BoardCoordinate(0, 4) to StoneColor.White,
                BoardCoordinate(0, 8) to StoneColor.Black,
                BoardCoordinate(4, 0) to StoneColor.White,
                BoardCoordinate(4, 4) to StoneColor.Black,
                BoardCoordinate(4, 8) to StoneColor.White,
                BoardCoordinate(8, 0) to StoneColor.Black,
                BoardCoordinate(8, 4) to StoneColor.White,
                BoardCoordinate(8, 8) to StoneColor.Black,
            ),
            moves = emptyList(),
        )

        /** 위 국면에서 비어 있고 이웃도 모두 비어 있는 자리 — 착수 합법성 논쟁이 끼어들지 않는다. */
        val FreePointPlayedAfterSync: BoardCoordinate = BoardCoordinate(2, 2)

        init {
            require(FreePointPlayedAfterSync !in StaticPosition.stones)
            require(StaticPosition.boardSize.allCoordinates().any { it !in StaticPosition.stones })
        }
    }
}

class StubEngineAdapterStaticPositionContractTest : EngineCoreApiStaticPositionContract() {
    override fun createEngine(): EngineCoreApi = StubEngineAdapter()
}

class RemoteEngineCoreApiAdapterStaticPositionContractTest : EngineCoreApiStaticPositionContract() {
    override fun createEngine(): EngineCoreApi = RemoteEngineCoreApiAdapter(FirstEmptyPointTransport())
}

/**
 * 원격 서버를 대신하는 **아주 작은 엔진** — 요청에 실려 온 국면에서 비어 있는 첫 자리를 둔다.
 *
 * 이 스위트가 원격 어댑터에서 실제로 검사하는 것은 "어댑터가 동기화한 국면을 원격 호출에
 * 실어 보내는가"이다. 그래서 페이크가 고정된 수를 돌려주면 안 된다 — 받은 국면을 **보고**
 * 답해야, 어댑터가 빈 판을 보냈을 때 틀린 수가 돌아온다.
 */
private class FirstEmptyPointTransport : RemoteEngineOperationTransport {
    override suspend fun execute(request: RemoteEngineOperationRequest): RemoteEngineOperationResponse {
        val target = request.state.boardSize.allCoordinates()
            .firstOrNull { coordinate -> coordinate !in request.state.stones }
            ?: error("No empty point left in the position the adapter sent")
        return RemoteEngineOperationResponse(
            status = com.worksoc.goaicoach.shared.enginecontract.EngineStatus.ready("remote mini engine ready"),
            summary = "remote mini engine played the first empty point it was shown",
            move = Move.Play(request.player, target),
        )
    }

    override fun abandonInFlightRequest() = Unit
}
