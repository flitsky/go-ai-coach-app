package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.Move

/**
 * 푸는 쪽 차례 하나 — 처음 국면에서 지금까지 둔 수(푸는 쪽 · 상대가 번갈아, 푸는 쪽이 먼저)로 정해진다. 정답만 두어 온 길이라
 * 수순이 곧 국면이다(틀린 수 뒤에는 차례가 이어지지 않는다 — 「다시 풀기」를 기다린다).
 *
 * @param node 그 자리에서 읽는 판정([SolvingJudge])이 쓰는 풀이기의 국면. 미리 읽은 표([AnswerTableJudge])는 수순만 본다.
 */
class LifeAndDeathTurn internal constructor(
    val moves: List<BoardCoordinate>,
    internal val node: LifeAndDeathSolver.Node?,
)

/** 푸는 쪽이 둔 한 수의 판정. */
sealed interface LifeAndDeathVerdict {
    /** 맞았다 — 상대는 [reply]에 받는다. `null`이면 더 받을 것이 없다(다 풀었다). */
    data class Correct(val reply: BoardCoordinate?) : LifeAndDeathVerdict

    /** 틀렸다 — 상대는 [refutation]으로 벌한다. `null`이면 판에 둘 응수가 없다(붉은 표시만 남는다). */
    data class Wrong(val refutation: BoardCoordinate?) : LifeAndDeathVerdict
}

/**
 * 문제를 푸는 동안의 판정 — 「이 수가 맞나, 상대는 어디에 받나」(백로그 #277). 둘이 있고 **판정의 규칙은 하나다**(풀이기 [LifeAndDeathSolver]):
 * - [SolvingJudge] — 폰에서 그 자리에서 끝까지 읽는다. 「기본 사활」처럼 국면이 [LifeAndDeathSolver.NodeBudget] 안에 드는 문제.
 * - [AnswerTableJudge] — 같은 풀이기로 **맥에서 미리** 읽어 둔 표를 읽는다. 폰에서 읽기에 큰 문제(고전 · 고급).
 * 화면의 흐름([LifeAndDeathSession])은 어느 쪽인지 모른다.
 */
interface LifeAndDeathJudge {
    fun root(): LifeAndDeathTurn

    /** [turn]에서 [move]를 판이 받는가(문제 안의 빈 점 · 자살수가 아님 · 패 금지가 아님). */
    fun accepts(turn: LifeAndDeathTurn, move: BoardCoordinate): Boolean

    /** [turn]에서 [move]를 두면(`null`이면 문제 밖 — 손 뺌). */
    fun verdict(turn: LifeAndDeathTurn, move: BoardCoordinate?): LifeAndDeathVerdict

    /** 맞은 [move]에 상대가 [reply]로 받은 다음 차례. */
    fun next(turn: LifeAndDeathTurn, move: BoardCoordinate, reply: BoardCoordinate): LifeAndDeathTurn

    /** [turn]에서 이기는 수 — 결판까지 가까운 것부터, 같으면 지은이의 의도가 먼저(「정답 보기」가 맨 앞을 둔다). */
    fun correctMoves(turn: LifeAndDeathTurn): List<BoardCoordinate>

    /** [turn]이 이미 결판났는가 — 손을 빼도 이긴다. */
    fun isSettled(turn: LifeAndDeathTurn): Boolean
}

/**
 * 폰에서 그 자리에서 읽는 판정 — 풀이기 그대로. ⚠️ 풀이기는 기억을 쌓으므로 문제마다 하나를 만들어 그 문제를 푸는 동안 다시 쓰고,
 * 스레드 안전하지 않다(한 번에 한 호출).
 */
class SolvingJudge(
    problem: LifeAndDeathProblem,
    /** 맥에서 「폰에서 열 만한가」를 잴 때만 준다([fitsTheNodeBudget]) — 넘으면 [LifeAndDeathSolver.NodeLimitExceeded]. */
    nodeLimit: Int = Int.MAX_VALUE,
) : LifeAndDeathJudge {
    private val solver = LifeAndDeathSolver(problem, nodeLimit)

    override fun root(): LifeAndDeathTurn = LifeAndDeathTurn(emptyList(), solver.root())

    override fun accepts(turn: LifeAndDeathTurn, move: BoardCoordinate): Boolean {
        val node = turn.solverNode()
        return BoardRules.validate(node.state, Move.Play(node.state.nextPlayer, move)) == null
    }

    override fun verdict(turn: LifeAndDeathTurn, move: BoardCoordinate?): LifeAndDeathVerdict {
        val node = turn.solverNode()
        val child = if (move != null) solver.play(node, move) else solver.tenuki(node)
        if (move != null && solver.solverWins(child)) return LifeAndDeathVerdict.Correct(solver.replyAfterCorrect(child))
        return LifeAndDeathVerdict.Wrong(solver.refutation(child, prefer = solver.correctMoves(node).toSet()))
    }

    override fun next(turn: LifeAndDeathTurn, move: BoardCoordinate, reply: BoardCoordinate): LifeAndDeathTurn =
        LifeAndDeathTurn(turn.moves + move + reply, solver.play(solver.play(turn.solverNode(), move), reply))

    override fun correctMoves(turn: LifeAndDeathTurn): List<BoardCoordinate> = solver.correctMoves(turn.solverNode())

    override fun isSettled(turn: LifeAndDeathTurn): Boolean = solver.isSettled(turn.solverNode())

    /** 지금까지 읽은 국면 수 — 맥에서 문제를 만들 때 크기를 적는다. */
    val visitedNodes: Int get() = solver.visitedNodes

    /** [turn]에서 판이 받는 문제 안의 수 전부 — 미리 읽는 표([AnswerTableJudge.write])가 차례마다 이것을 모두 판정한다. */
    fun legalMoves(turn: LifeAndDeathTurn): List<BoardCoordinate> = solver.legalPoints(turn.solverNode())

    private fun LifeAndDeathTurn.solverNode(): LifeAndDeathSolver.Node = requireNotNull(node) { "a solving judge's turn carries a solver node" }
}

/**
 * 폰에서 이 문제를 열 때 **그 자리에서 읽는 일**이 [LifeAndDeathSolver.NodeBudget] 안에 드는가 — 화면처럼 한 풀이기로 「정답 보기」 수순을 먼저 읽고
 * (문제를 열 때 기억을 데운다), 처음 국면에서 판이 받는 수 전부와 손 뺌을 판정해 본다. 안 들면 맥에서 판정 표를 만들어 싣는다
 * (`LifeAndDeathAnswerTableExportTest`). 실은 문제를 되묻는 테스트와 표를 만드는 도구가 이 한 함수로 가른다.
 */
fun LifeAndDeathProblem.fitsTheNodeBudget(): Boolean =
    try {
        val judge = SolvingJudge(this, nodeLimit = LifeAndDeathSolver.NodeBudget)
        LifeAndDeathSession(this, judge).answerLine()
        val root = judge.root()
        (judge.legalMoves(root) + null).forEach { judge.verdict(root, it) }
        true
    } catch (tooBig: LifeAndDeathSolver.NodeLimitExceeded) {
        false
    }
