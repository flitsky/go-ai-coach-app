package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor

/** 문제를 푸는 동안의 단계(백로그 #184). */
sealed interface LifeAndDeathPhase {
    /** 푸는 쪽이 둘 차례다. [afterCorrect]가 참이면 방금 둔 수가 맞았고 상대가 받았다(「계속하세요」). */
    data class Solving(val afterCorrect: Boolean) : LifeAndDeathPhase

    /**
     * 푸는 쪽의 돌이 놓였고, 상대가 [reply]에 받으려 한다 — 화면은 잠깐 뜸을 두고 [LifeAndDeathSession.applyReply]를
     * 부른다(돌이 한꺼번에 둘 놓이면 무엇에 무엇이 답했는지 안 보인다). [wrong]이면 그 응수가 틀린 수를 벌한다.
     */
    data class AwaitingReply(val reply: BoardCoordinate, val wrong: Boolean) : LifeAndDeathPhase

    /** 다 풀었다 — 목표를 이뤘거나, 이제 손을 빼도 이루는 국면이다. */
    data object Solved : LifeAndDeathPhase

    /** 틀렸다 — 붉은 표시와 상대의 응수가 판에 남아 있다. 「다시 풀기」나 「정답 보기」를 기다린다. */
    data object Failed : LifeAndDeathPhase
}

/**
 * 화면이 그리는 한 순간. [board]는 **판에 보이는 그대로**다(수순·마지막 수를 갖는다) — 판정의 차례([turn])와 따로 가는
 * 이유는 판정이 문제 밖에 둔 돌을 「손 뺌」으로 읽고, 틀린 수 뒤로는 차례를 잇지 않기 때문이다.
 *
 * @param mistake 틀린 수의 자리 — 그 돌 가운데에 붉은 표시를 한다(사용자 2026-10-02, U-51).
 */
data class LifeAndDeathAttemptState(
    val board: GameState,
    val phase: LifeAndDeathPhase,
    val mistake: BoardCoordinate? = null,
    internal val turn: LifeAndDeathTurn,
    /** 상대의 응수를 기다리는 동안, 푸는 쪽이 방금 둔 수(맞은 수일 때 다음 차례를 잇는 데 쓴다). */
    internal val played: BoardCoordinate? = null,
)

/**
 * 사활 문제 한 판을 푸는 흐름(백로그 #184). 정답·오답의 판정은 전부 [judge]가 하고(#277 — 그 자리에서 읽는 풀이기, 또는 맥에서
 * 미리 읽어 둔 표), 여기는 그 판정을 **판에 보이는 국면**으로 옮긴다.
 *
 * ⚠️ 그 자리에서 읽는 판정은 기억(메모)을 쌓으므로 문제마다 하나를 만들어 그 문제를 푸는 동안 다시 쓴다. 스레드 안전하지 않다 —
 * 한 번에 한 호출만 한다(화면은 계산하는 동안 판의 입력을 막는다).
 */
class LifeAndDeathSession(
    val problem: LifeAndDeathProblem,
    private val judge: LifeAndDeathJudge = lifeAndDeathJudgeFor(problem),
) {
    private val solverColor: StoneColor = problem.toPlay

    fun start(): LifeAndDeathAttemptState =
        LifeAndDeathAttemptState(
            board = problem.startState(),
            phase = LifeAndDeathPhase.Solving(afterCorrect = false),
            turn = judge.root(),
        )

    /**
     * 푸는 쪽이 [point]에 둔다. 둘 수 없는 자리(돌이 있다 · 자살수 · 패)이거나 지금 둘 차례가 아니면 `null` —
     * 화면은 아무 일도 하지 않는다(판이 이미 「둘 수 없음」 진동을 낸다).
     *
     * 문제 밖(`,`)에 둔 돌도 받는다 — 그 수는 「손을 뺀 것」이라 언제나 틀린 수다(아직 결판이 나지 않았으므로).
     */
    fun play(state: LifeAndDeathAttemptState, point: BoardCoordinate): LifeAndDeathAttemptState? {
        if (state.phase !is LifeAndDeathPhase.Solving) return null
        val move = Move.Play(solverColor, point)
        if (BoardRules.validate(state.board, move) != null) return null

        val inProblem = point in problem.playable
        // 판에 보이는 수와 판정이 읽는 수가 같은지 — 문제 안이면 같은 자리, 밖이면 손 뺌.
        if (inProblem && !judge.accepts(state.turn, point)) return null
        val verdict = judge.verdict(state.turn, point.takeIf { inProblem })
        val board = BoardRules.play(state.board, move).withKoUnbannedFor(solverColor.opponent)

        if (verdict is LifeAndDeathVerdict.Correct) {
            val reply = verdict.reply
                ?: return LifeAndDeathAttemptState(board, LifeAndDeathPhase.Solved, turn = state.turn, played = point)
            return LifeAndDeathAttemptState(board, LifeAndDeathPhase.AwaitingReply(reply, wrong = false), turn = state.turn, played = point)
        }

        val refutation = (verdict as LifeAndDeathVerdict.Wrong).refutation
            ?.takeIf { BoardRules.validate(board, Move.Play(board.nextPlayer, it)) == null }
        val phase = refutation?.let { LifeAndDeathPhase.AwaitingReply(it, wrong = true) } ?: LifeAndDeathPhase.Failed
        return LifeAndDeathAttemptState(board, phase, mistake = point, turn = state.turn, played = point)
    }

    /** 기다리던 상대의 응수를 판에 놓는다. 맞은 수에 대한 응수면 다시 푸는 쪽 차례, 틀린 수를 벌한 응수면 끝이다. */
    fun applyReply(state: LifeAndDeathAttemptState): LifeAndDeathAttemptState {
        val phase = state.phase as? LifeAndDeathPhase.AwaitingReply ?: return state
        val board = BoardRules.play(state.board, Move.Play(solverColor.opponent, phase.reply))
        if (phase.wrong) return state.copy(board = board, phase = LifeAndDeathPhase.Failed)
        return state.copy(
            board = board,
            turn = judge.next(state.turn, requireNotNull(state.played), phase.reply),
            played = null,
            phase = LifeAndDeathPhase.Solving(afterCorrect = true),
        )
    }

    /**
     * 「정답 보기」의 수순 — 처음 국면에서 푸는 쪽과 상대가 번갈아 둔다(푸는 쪽이 먼저). 첫 수는 지은이의 의도를,
     * 그 뒤는 결판까지 가장 가까운 수를 둔다. 상대는 가장 오래 버틴다.
     */
    fun answerLine(): List<BoardCoordinate> {
        val line = mutableListOf<BoardCoordinate>()
        var turn = judge.root()
        while (!judge.isSettled(turn) && line.size < MaxAnswerLength) {
            val correct = judge.correctMoves(turn)
            val move = if (line.isEmpty() && problem.answerPoint in correct) problem.answerPoint else correct.first()
            line += move
            val reply = (judge.verdict(turn, move) as LifeAndDeathVerdict.Correct).reply ?: break
            line += reply
            turn = judge.next(turn, move, reply)
        }
        return line
    }

    /** [answerLine]을 처음부터 둔 국면들 — `[i]`는 `i + 1`수를 둔 뒤다. 화면이 한 수씩 넘긴다. */
    fun answerBoards(): List<GameState> {
        var board = problem.startState()
        return answerLine().map { point ->
            board = BoardRules.play(board, Move.Play(board.nextPlayer, point)).withKoUnbannedFor(solverColor.opponent)
            board
        }
    }

    /** 상대에게는 팻감이 끝없다는 풀이기의 가정을 판에도 똑같이 건다 — 안 걸면 풀이기가 고른 되따냄을 판이 거부한다. */
    private fun GameState.withKoUnbannedFor(color: StoneColor): GameState =
        if (koForbiddenFor == color) copy(koPoint = null, koForbiddenFor = null) else this

    private companion object {
        const val MaxAnswerLength = 24
    }
}
