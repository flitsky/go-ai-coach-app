package com.worksoc.goaicoach.application.engine

import com.worksoc.goaicoach.shared.domain.GameState
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * **오퍼레이션 락**(refactor backlog #15). [LocalEngineSessionClient]의 공개 suspend 메서드 하나가 곧 오퍼레이션
 * 하나이고, 그 명령 묶음(동기화 = `newGame` + 수마다 `playMove`, 그 뒤의 분석·추정·종국 판정)은 다른 오퍼레이션의
 * 명령과 섞이지 않는다.
 *
 * 예전에는 섞였다. 5계층의 busy 게이트는 check-then-act이고 **세대로 걸러** 세므로(무르기·새 대국 뒤의 낡은 작업은
 * 안 보인다), 게이트를 지난 두 오퍼레이션이 IO 스레드 둘에서 실제로 겹쳤다. 한 오퍼레이션의 수순 재생 사이에 다른
 * 오퍼레이션의 `newGame`이 끼면 둘 다 남의 판을 분석한다. 2계층의 왕복 락(#14)은 명령 하나만 묶는다. 락 하나가
 * 엔진 하나다 — GTP·analysis 두 프로세스와 2계층의 판 거울(`playedMoves` 등)을 함께 지킨다(분석은 GTP로 맞춘 판을
 * JSON 쿼리로 보낸다). 원격 클라이언트는 따로 만든 인스턴스라 제 락을 가진다.
 *
 * ## 누가 기다리고 누가 포기하는가
 * - **기다린다**([serialized]): 기동·새 대국·AI 착수·착수 뒤 동기화·AI 종국·재동기화 둘·기기 벤치마크(끝의 복원
 *   동기화까지 — 복원 전에 다른 오퍼레이션이 끼면 벤치마크 판 위에서 돈다). 기다리다 취소되면 줄에서 빠지고 돌지 않는다.
 * - **곧바로 포기한다**([serializedOrBusy] → [EngineOperationBusy]): 추천 수 분석(`analyzePosition`)과 형세 추정
 *   (`estimateScoreForState`). 받는 쪽이 기존의 "잠시 뒤"·"엔진이 바쁘다" 흐름으로 보낸다 — 기다리게 하면 AI가 생각하는
 *   동안 누른 형세가 그 탐색이 끝날 때까지 멈춘다.
 * - **목표마다 따로**: 캐시 최적화(`optimizePositionAnalysisCache`) — 목표와 목표 사이에 기다리던 AI 차례가 돈다.
 * - **배경 조각**([backgroundPiece]): 끝난 판의 형세 기록 재측정(`remeasureGraphScore`) — 엔진이 쓰이고 있으면 포기하고,
 *   도는 동안에는 위의 「곧바로 포기」하는 쪽이 이 조각(평가 1회)만 기다린다. 사용자가 누른 분석이 배경 작업 때문에 거절당하지 않는다.
 * - **잡지 않는다**: `forceResetEngine`(함정 71 — 멈춘 오퍼레이션을 풀려고 부르는 함수다), `capabilities`, 캐시 통계 둘.
 *
 * ## 멈춘 오퍼레이션이 모두를 얼리지 않는 이유 — 오퍼레이션 마감은 따로 두지 않는다(함정 71)
 * 쥔 쪽을 붙잡는 것은 엔진 왕복뿐이고, #14가 왕복마다 마감을 둬 넘으면 **그 호출의** 프로세스를 SIGKILL로 내린다. 쥔
 * 쪽이 가장 오래 쥐는 것은 제 일 + 왕복 마감 하나다(명령 30초, 캡 있는 탐색 캡+20초, 캡 없는 탐색 120초 —
 * `KataGoProtocolCommands`). 그 전에 푸는 길이 둘이다.
 * - 「엔진 다시 시작하기」: `forceResetEngine`은 락 없이 프로세스를 내린다 → 막힌 읽기가 EOF로 풀려 쥔 쪽이 실패하고
 *   락을 놓는다 → 기다리던 쪽이 새 프로세스에서 처음부터 판을 맞춘다.
 * - 취소(무르기·나가기·새 대국 — #74): 취소된 쪽은 답을 기다리지 않고 곧바로 돌아가 락을 놓는다. 답 받기는 2계층의
 *   배수가 그 프로세스의 왕복 락을 쥔 채 맡는다(`KataGoProcessEngineAdapter.roundTrip`) — 같은 프로세스로 가는 다음
 *   명령만 그것을 기다린다.
 * 정당한 오퍼레이션의 길이는 너무 다르다 — 기기 벤치마크는 수십 초, 캡 없는 탐색은 120초, 첫 실행의 `startSession`은
 * 엔진 설치를 기다린다(`DeferredEngineCoreApi` — 그 대기는 끊으면 엔진이 영영 안 뜬다). 락에 마감을 두르면 그중
 * 하나를 반드시 자른다. 예산을 한곳에 모으는 일은 #17이다.
 *
 * ⚠️ **재진입하지 않는다.** 오퍼레이션 안에서 같은 클라이언트의 공개 메서드를 부르면 크게 실패한다([refuseReentry]).
 * AI 차례 안의 분석은 공개 `analyzePosition`이 아니라 내부 캐시 헬퍼를 부른다 — 공개 쪽을 부르면 제 락에
 * 막혀 포기하고, AI는 조용히 `genMove`로 떨어진다.
 * ⚠️ **5계층의 `isEngineBusy`는 이 락이 아니다** — 세대로 거른 장부다. 직렬화의 근거로 쓰지 말 것. 이 락을 들여다보는
 * 눈대중은 [isInFlight]다.
 */
internal class LocalEngineOperationLock {
    private val mutex = Mutex()

    /** 지금 락을 쥔 것이 배경 작업의 한 조각인가([backgroundPiece]) — 아무 스레드에서나 읽는다. */
    @Volatile
    private var backgroundPieceInFlight = false

    val isInFlight: Boolean
        get() = mutex.isLocked

    /** [mutex]를 기다려 쥐고 [block]을 돈다. 기다리다 취소되면 줄에서 빠지고 [block]은 돌지 않는다. */
    suspend fun <T> serialized(
        operation: String,
        block: suspend () -> T,
    ): T {
        refuseReentry(operation)
        return mutex.withLock { holding(operation, block) }
    }

    /**
     * [mutex]가 비어 있을 때만 쥐고 [block]을 돈다. 누가 쥐고 있으면 기다리지 않고 [EngineOperationBusy] —
     * 단, 쥔 것이 **배경 작업의 한 조각**([backgroundPiece])이면 그 조각만 기다린다. 배경 작업이 사용자가 누른 분석을
     * "엔진이 바쁘다"로 돌려보내면 보조 기능이 주 기능을 막는 것이다. 조각은 평가 1회라 기다림이 짧다.
     * (조각이 끝난 직후 다른 오퍼레이션이 먼저 쥐면 그것까지 기다리게 된다 — 줄은 선착순이라 드물고, 기다릴 뿐 틀리지 않는다.)
     */
    suspend fun <T> serializedOrBusy(
        operation: String,
        block: suspend () -> T,
    ): T {
        refuseReentry(operation)
        if (!mutex.tryLock()) {
            if (!backgroundPieceInFlight) throw EngineOperationBusy(operation)
            mutex.lock()
        }
        try {
            return holding(operation, block)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * **배경 작업의 한 조각**(백로그 #215 — 끝난 판의 형세 기록 재측정). 엔진이 조금이라도 쓰이고 있으면 포기한다
     * ([EngineOperationBusy]) — 배경 작업은 줄을 서지 않는다. 도는 동안에는 표지를 세워, 포기하던 쪽([serializedOrBusy])이
     * 이 조각만큼은 기다리게 한다. 기다리는 쪽([serialized])은 늘 그렇듯 줄을 선다.
     */
    suspend fun <T> backgroundPiece(
        operation: String,
        block: suspend () -> T,
    ): T {
        refuseReentry(operation)
        if (!mutex.tryLock()) throw EngineOperationBusy(operation)
        backgroundPieceInFlight = true
        try {
            return holding(operation, block)
        } finally {
            backgroundPieceInFlight = false
            mutex.unlock()
        }
    }

    /** 쥔 동안에는 코루틴 문맥에 표지를 단다 — 안에서 다시 들어오는 호출을 [refuseReentry]가 알아본다. */
    private suspend fun <T> holding(
        operation: String,
        block: suspend () -> T,
    ): T =
        withContext(HeldEngineOperation(owner = this, operation = operation)) { block() }

    /**
     * 이미 이 클라이언트의 오퍼레이션 안이면 **크게 실패한다** — [Mutex]는 재진입을 모른다. 기다리는 쪽은 자기 자신을 영영
     * 기다리고, 포기하는 쪽은 조용히 [EngineOperationBusy]가 된다(AI 차례 안이면 조용히 `genMove`로 떨어진다). 둘 다
     * 테스트 밖에서는 보이지 않는 고장이라, 여기서 드러낸다.
     */
    private suspend fun refuseReentry(operation: String) {
        val held = currentCoroutineContext()[HeldEngineOperation] ?: return
        check(held.owner !== this) {
            "`$operation` was called from inside `${held.operation}` on the same engine session. The operation lock is not " +
                "re-entrant — call the private helper that runs under the held lock instead (refactor backlog #15)."
        }
    }
}

/**
 * **분석 계열 호출이 판을 두고 간 자리**(backlog #218). 대국 계열 오퍼레이션이 판을 맞추면 [AnalysisBoard.None]으로 돌아간다.
 *
 * 형세 추정의 `syncFirst = false`는 *"판이 이미 이 국면이다"* 라는 가정이다. 사람:AI·AI:AI 대국의 형세 보기가 그렇게
 * 부르고, 대국 안에서는 참이다 — AI 차례와 사람 착수가 매번 판을 맞춘다. 그런데 엔진은 하나이고 분석은 판을 **요청 국면으로
 * 바꾼다**. 대국 밖의 화면(다시보기·바둑판 사진)이 다른 국면을 분석한 뒤 대국 화면으로 돌아오면 그 가정이 깨진다 —
 * 끝난 판에서 「복기 하기」로 30수째를 분석하고 돌아와 형세 보기를 누르면 30수째의 형세가 나온다.
 * 그래서 분석 계열이 판을 **다른 국면**에 두고 갔거나([AnalysisBoard.At]), 맞추다 끊겨 **어디인지 모르면**
 * ([AnalysisBoard.Unknown]) 형세 추정이 그 가정을 거두고 판부터 맞춘다(`estimateScoreForState`).
 *
 * 신경망을 **갈아 올릴 때**도 같다(백로그 #215) — 새 프로세스의 판은 비어 있다. 갈아 올리기 직전에 [AnalysisBoard.Unknown]으로
 * 적어 두고(`NetworkSwapBoardTracker`), 판을 맞춘 오퍼레이션이 끝나야 제자리로 돌아온다. 갈아 올리다 끊긴 뒤의 형세 보기가
 * 빈 판을 읽지 않는다.
 *
 * ⚠️ **대국 흐름은 그대로다.** 대국 안의 추천 수가 맞추는 국면은 지금 국면이라 [GameState]가 같고, 그 뒤의 형세 보기는
 * 예전처럼 맞추지 않는다. 빌린 쪽이 돌려놓게 하지 않은 이유: 돌려놓기는 화면이 닫힌 뒤에 돌아야 해서, 돌아온 사용자가
 * 곧바로 누른 형세 보기와 순서를 다툰다. 가정을 쓰는 쪽이 락 안에서 확인하면 다툴 순서가 없다.
 * ⚠️ 락 안에서만 읽고 쓴다.
 */
internal sealed interface AnalysisBoard {
    /** 마지막으로 판을 맞춘 것은 대국 계열 오퍼레이션이다 — 판은 대국의 것이다. */
    data object None : AnalysisBoard

    /** 분석 계열이 판을 맞추는 중이다(또는 그러다 끊겼다) — 판이 어느 국면인지 모른다. */
    data object Unknown : AnalysisBoard

    /** 분석 계열이 판을 [state]로 맞춰 두었다. */
    data class At(val state: GameState) : AnalysisBoard

    /** 판이 [state]가 **아닐 수 있는가** — 참이면 "판이 이미 이 국면"이라는 가정을 쓸 수 없다. */
    fun isElsewhereThan(state: GameState): Boolean =
        when (this) {
            None -> false
            Unknown -> true
            is At -> this.state != state
        }
}

/**
 * [AnalysisBoard] 상태와 신경망 교체 시의 판 무효화([NetworkSwapBoardTracker])를 함께 관리한다(backlog #215·#218).
 * 신경망을 갈아 올리면 새 프로세스의 판은 비어 있으므로 갈아 올리기 직전에 [AnalysisBoard.Unknown]으로 적어 둔다.
 */
internal class AnalysisBoardTracker : NetworkSwapBoardTracker {
    var current: AnalysisBoard = AnalysisBoard.None
    private var beforeSwap: AnalysisBoard = AnalysisBoard.None

    override fun swapStarting() {
        beforeSwap = current
        current = AnalysisBoard.Unknown
    }

    override fun swapNotNeeded() {
        current = beforeSwap
    }

    fun isElsewhereThan(state: GameState): Boolean = current.isElsewhereThan(state)
}

/** 지금 이 코루틴이 [owner]의 오퍼레이션 락을 쥐고 [operation]을 돌고 있다는 표지(refactor backlog #15) — 재진입을 알아본다. */
private class HeldEngineOperation(
    val owner: LocalEngineOperationLock,
    val operation: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<HeldEngineOperation>
}
