package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 사활 문제를 **문제 구역 안에서 끝까지** 풀어 본다(백로그 #184). 정답은 「지은이가 적은 수순」이 아니라
 * **목표를 이루는 모든 수**다 — 같은 결과에 닿는 다른 수를 두어도 틀렸다고 하지 않는다.
 *
 * ## 무엇을 「이긴다」고 보나
 * - [LifeAndDeathGoal.Kill]: 목표 무리를 실제로 **따내면** 푸는 쪽이 이긴다. 둘이 잇달아 통과하면 판이
 *   끝나고, 그때 목표 무리가 판에 남아 있으면(살았거나 빅) 푸는 쪽이 진다.
 * - [LifeAndDeathGoal.Live]: 그 반대 — 잇달아 통과로 끝났을 때 목표 무리가 남아 있으면 이긴다.
 *
 * ## 패는 푸는 쪽의 승리로 치지 않는다
 * 사활 문제의 정답은 **조건 없이** 이루는 것이어야 한다. 그래서 상대에게는 **팻감이 끝없이 있다**고 둔다 —
 * 푸는 쪽이 패를 따내도 상대는 곧바로 되따낼 수 있다(패의 금지를 상대에게만 풀어 준다). 반대로 상대가 따낸 패는
 * 보통 규칙대로 푸는 쪽이 곧바로 되따낼 수 없다. 같은 국면이 수순 안에서 되풀이되면(패의 맴돌기) 푸는 쪽이 진
 * 것으로 본다. 결과: 패가 나는 수는 정답이 아니다.
 *
 * ## 언제 「다 풀었다」고 하나 — 손을 빼도 되는 순간
 * 따낼 때까지(또는 판이 끝날 때까지) 두게 하면 뻔한 공배 메우기가 길게 이어진다. 그래서 **푸는 쪽이 지금 손을
 * 빼도(문제 밖에 한 수를 두어도) 여전히 이기는 국면**에 닿으면 다 푼 것이다([isSettled]). 상대의 응수도 같은 잣대로
 * 고른다 — 정답 뒤에는 **가장 오래 버티는 수**(손을 뺄 수 있는 국면까지 가장 먼 수), 오답 뒤에는 **가장 빨리
 * 결판을 내는 수**를 둔다. 같은 값이면 오답 뒤에는 놓친 급소를 먼저([refutation]), 그다음은 판의 앞쪽(위·왼쪽) 자리가
 * 먼저다 — 결정적이어야 기기마다 같다.
 *
 * ⚠️ 바둑 규칙은 [BoardRules] 하나만 쓴다 — 따냄·자살수·패의 판정을 여기서 다시 쓰지 않는다(한 규칙은 한 곳에).
 * ⚠️ 판의 크기와 상관없이 **문제 구역의 빈 점 수**가 비용을 정한다. 구역을 넓게 잡으면 국면 수가 곱으로 는다 —
 * 테스트(`LifeAndDeathProblemsTest`)가 문제마다 본 국면 수를 [NodeBudget] 아래로 묶는다.
 */
class LifeAndDeathSolver(
    private val problem: LifeAndDeathProblem,
    /**
     * 이만큼 넘게 읽으면 [NodeLimitExceeded]를 던진다 — 맥에서 문제를 **만들 때만** 쓴다(백로그 #277: 후보를 수만 개 걸러 보다가
     * 한 후보에 몇 분씩 묶이지 않게). 폰은 상한 없이 연다 — 실은 문제는 테스트가 [NodeBudget] 아래로 묶는다.
     * ⚠️ 던진 뒤의 풀이기는 버린다 — 읽던 수순의 흔적이 남아 있다.
     */
    private val nodeLimit: Int = Int.MAX_VALUE,
) {
    private val solver: StoneColor = problem.toPlay
    private val opponent: StoneColor = solver.opponent

    private val winMemo = HashMap<NodeKey, Boolean>()
    private val onPath = HashSet<NodeKey>()
    private val depthMemo = HashMap<Pair<NodeKey, Boolean>, Int>()
    private val depthOnPath = HashSet<Pair<NodeKey, Boolean>>()

    /** 지금까지 판정한 국면 수 — 비용의 잣대(테스트가 [NodeBudget]으로 묶는다). */
    var visitedNodes: Int = 0
        private set

    /**
     * 풀이의 한 국면. [passes]는 바로 앞까지 **잇달아** 통과한 수(2가 되면 판이 끝난다).
     * 손 빼기(문제 밖에 두기)는 통과와 같은 국면을 만들지만 [passes]를 0으로 둔다 — 실제 대국에서 손을 뺀 것은
     * 통과가 아니라서, 그 뒤에 상대가 한 번 통과해도 판이 끝나지 않는다.
     */
    data class Node(val state: GameState, val passes: Int = 0)

    fun root(): Node = Node(problem.startState())

    /** [node]에서 둘 수 있는 자리(문제 구역 안의 빈 점 중 규칙이 허락하는 것). 통과는 따로 다룬다. */
    fun legalPoints(node: Node): List<BoardCoordinate> =
        if (isTerminal(node)) {
            emptyList()
        } else {
            problem.playable.filter { point ->
                BoardRules.validate(node.state, Move.Play(node.state.nextPlayer, point)) == null
            }
        }

    /** [node]에 [point]를 둔 다음 국면. 상대 차례의 패 금지는 여기서 풀어 준다(팻감이 끝없다). */
    fun play(node: Node, point: BoardCoordinate): Node {
        val next = BoardRules.play(node.state, Move.Play(node.state.nextPlayer, point)).withoutHistory()
        return Node(next.unbanKoForOpponent(), passes = 0)
    }

    /** [node]에서 둘 차례인 쪽이 통과한 다음 국면. */
    fun pass(node: Node): Node =
        Node(BoardRules.play(node.state, Move.Pass(node.state.nextPlayer)).withoutHistory(), passes = node.passes + 1)

    /** [node]에서 둘 차례인 쪽이 **손을 뺀** 다음 국면 — 통과와 같되 판이 끝나는 쪽으로 세지 않는다. */
    fun tenuki(node: Node): Node = pass(node).copy(passes = 0)

    /** 목표 무리가 따내졌거나 둘이 잇달아 통과해 **판이 끝났는가.** */
    fun isTerminal(node: Node): Boolean = targetCaptured(node) || node.passes >= 2

    /** 끝까지 가장 잘 두었을 때 **푸는 쪽이 목표를 이루는가.** */
    fun solverWins(node: Node): Boolean = winOf(node)

    /**
     * 푸는 쪽 차례인 [node]가 **이미 결판났는가** — 푸는 쪽이 여기서 손을 빼도 이긴다. 정답 수순은 여기서 멈춘다.
     * ⚠️ 상대 차례인 국면에는 묻지 않는다(그 판정은 상대의 응수가 하나도 필요 없는가 — [replyAfterCorrect]가 `null`).
     */
    fun isSettled(node: Node): Boolean {
        require(node.state.nextPlayer == solver) { "isSettled asks about the solver's turn" }
        return isTerminal(node) || solverWins(tenuki(node))
    }

    /**
     * 푸는 쪽이 정답을 둔 뒤(상대 차례인 [node]) 상대가 둘 수 — **가장 오래 버티는 수.** 어느 수를 두어도 푸는 쪽이
     * 손을 뺄 수 있으면 결판난 것이라 `null`이다.
     * 상대의 통과는 응수로 고르지 않는다 — 통과 뒤의 국면은 [node]에서 푸는 쪽이 손을 뺀 것과 같아서, [node]가 푸는
     * 쪽의 승리인 한 언제나 「손을 뺄 수 있는」 국면이다(버티는 길이 될 수 없다).
     */
    fun replyAfterCorrect(node: Node): BoardCoordinate? {
        require(node.state.nextPlayer == opponent && solverWins(node)) { "A reply after a correct move comes from a losing opponent" }
        if (isTerminal(node)) return null
        var best: BoardCoordinate? = null
        var bestDepth = 0
        for (point in legalPoints(node)) {
            val depth = settleDepth(play(node, point), forSolver = true)
            if (depth > bestDepth) {
                best = point
                bestDepth = depth
            }
        }
        return best
    }

    /**
     * 푸는 쪽이 틀린 수를 둔 뒤(상대 차례인 [node]) 상대가 둘 수 — **가장 빨리 결판을 내는 수.** 같은 값이면
     * [prefer](푸는 쪽이 두었어야 할 자리 — 화면이 넘긴다) 안의 수를 먼저 고른다: 놓친 급소를 상대가 차지하는 것이
     * 「왜 틀렸나」를 가장 잘 보여 준다.
     * 돌로 이기는 길이 없고 통과로만 이기면 `null`(응수 없이 붉은 표시만 남는다).
     */
    fun refutation(node: Node, prefer: Set<BoardCoordinate> = emptySet()): BoardCoordinate? {
        require(node.state.nextPlayer == opponent && !solverWins(node)) { "A refutation answers a wrong move" }
        if (isTerminal(node)) return null
        return legalPoints(node)
            .filter { !solverWins(play(node, it)) }
            .minWithOrNull(
                compareBy<BoardCoordinate> { settleDepth(play(node, it), forSolver = false) }
                    .thenBy { if (it in prefer) 0 else 1 },
            )
    }

    /**
     * 푸는 쪽 차례인 [node]에서 이기는 수 — 결판까지 가장 가까운 것부터. 「정답 보기」가 맨 앞을 두되, 지은이의
     * 의도([LifeAndDeathProblem.answerPoint])가 그 가운데 같은 거리에 있으면 그것을 앞에 둔다.
     */
    fun correctMoves(node: Node): List<BoardCoordinate> {
        require(node.state.nextPlayer == solver) { "correctMoves asks about the solver's turn" }
        return legalPoints(node)
            .filter { solverWins(play(node, it)) }
            .sortedWith(
                compareBy<BoardCoordinate> { settleDepthAfterSolverMove(play(node, it)) }
                    .thenBy { if (it == problem.answerPoint) 0 else 1 },
            )
    }

    /**
     * 정답만 두어(상대는 [replyAfterCorrect]로 받는다) 닿을 수 있는 푸는 쪽 차례 가운데, 아직 결판나지 않았는데 **판에 둘 정답이 없는**
     * 국면 — 통과해야만 이기는 자리다(백로그 #277, 맥에서 만든 후보에서 처음 나왔다). 화면에는 통과 단추가 없어 거기서는 무엇을 두어도
     * 틀리므로 그런 문제는 싣지 않는다 — 비어 있어야 한다.
     */
    fun turnsWithoutAPlayableAnswer(): List<Node> {
        val stuck = mutableListOf<Node>()
        val pending = ArrayDeque(listOf(root()))
        val visited = HashSet<NodeKey>()
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!visited.add(node.key()) || isSettled(node)) continue
            val correct = correctMoves(node)
            if (correct.isEmpty()) stuck += node
            for (move in correct) {
                val afterMove = play(node, move)
                val reply = replyAfterCorrect(afterMove) ?: continue
                pending += play(afterMove, reply)
            }
        }
        return stuck
    }

    /** 결판까지 푸는 쪽이 더 두어야 하는 수(정답을 둔 직후, 상대 차례에서 잰다). */
    fun settleDepthAfterSolverMove(node: Node): Int = settleDepth(node, forSolver = true)

    // ── 판정 ───────────────────────────────────────────────────────────

    /**
     * 끝까지 읽은 승패. ⚠️ 수순 안에서 같은 국면이 되풀이되면 푸는 쪽의 패배로 본다(패의 맴돌기).
     * 그 판정에 기댄 결과도 그대로 기억한다 — 「그 수순에서만」 참인 판정이 다른 수순으로 새어 나갈 수 있지만(바둑
     * 풀이기들이 함께 받아들이는 근사), 기억하지 않으면 궁도가 일곱 점만 돼도 같은 국면을 수백만 번 다시 읽는다
     * (2026-10-21 실측 — 790만 국면). 새어 나간 판정이 정답을 바꾸는지는 문제마다 테스트가 지은이의 의도와 견준다.
     */
    private fun winOf(node: Node): Boolean {
        if (targetCaptured(node)) return problem.goal == LifeAndDeathGoal.Kill
        if (node.passes >= 2) return problem.goal == LifeAndDeathGoal.Live
        val key = node.key()
        winMemo[key]?.let { return it }
        // 목표 무리가 무조건 살면(눈 둘) 더 읽을 것이 없다 — 판정은 같고 읽는 국면만 준다(#277: 고전 · 고급 문제의 크기).
        if (targetUnconditionallyAlive(node)) return (problem.goal == LifeAndDeathGoal.Live).also { winMemo[key] = it }
        if (key in onPath) return false

        visitedNodes += 1
        if (visitedNodes > nodeLimit) throw NodeLimitExceeded(visitedNodes)
        onPath += key
        val solverToMove = node.state.nextPlayer == solver
        val value = if (solverToMove) children(node).any { winOf(it) } else children(node).all { winOf(it) }
        onPath -= key
        winMemo[key] = value
        return value
    }

    /**
     * 이긴 쪽([forSolver]가 참이면 푸는 쪽)이 **손을 뺄 수 있는 국면까지** 더 두어야 하는 수.
     * 이긴 쪽 차례: 손을 빼도 이기면 0, 아니면 1 + 이기는 수들 중 가장 가까운 것.
     * 진 쪽 차례: 진 쪽의 모든 수(통과 포함) 중 가장 먼 것 — 가장 오래 버틴다.
     * ⚠️ 이 국면이 정말 [forSolver] 쪽의 승리일 때만 부른다. 수순이 맴돌면 [Unreachable]로 본다.
     */
    private fun settleDepth(node: Node, forSolver: Boolean): Int {
        if (isTerminal(node)) return 0
        val winner = if (forSolver) solver else opponent
        val key = node.key() to forSolver
        depthMemo[key]?.let { return it }
        if (key in depthOnPath) return Unreachable

        depthOnPath += key
        val depth = if (node.state.nextPlayer == winner) {
            if (winsFor(forSolver, tenuki(node))) {
                0
            } else {
                val nearest = legalPoints(node)
                    .map { play(node, it) }
                    .filter { winsFor(forSolver, it) }
                    .minOfOrNull { settleDepth(it, forSolver) }
                    ?: Unreachable
                if (nearest >= Unreachable) Unreachable else nearest + 1
            }
        } else {
            children(node).maxOf { settleDepth(it, forSolver) }
        }
        depthOnPath -= key
        depthMemo[key] = depth
        return depth
    }

    private fun winsFor(forSolver: Boolean, node: Node): Boolean = solverWins(node) == forSolver

    /** 둘 수 있는 모든 다음 국면 — 통과가 맨 끝이다. 지연 순열이라 먼저 결판난 가지 뒤의 수는 두어 보지도 않는다. */
    private fun children(node: Node): Sequence<Node> =
        legalPoints(node).asSequence().map { play(node, it) } + sequenceOf(pass(node))

    private fun targetCaptured(node: Node): Boolean = node.state.stones[problem.targetPoint] != problem.targetColor

    private fun targetUnconditionallyAlive(node: Node): Boolean =
        isUnconditionallyAlive(node.state.stones, node.state.boardSize.value, problem.targetColor, problem.targetPoint)

    /** 상대 차례의 패 금지를 푼다 — 상대에게는 팻감이 끝없다(클래스 KDoc). 푸는 쪽의 금지는 그대로 둔다. */
    private fun GameState.unbanKoForOpponent(): GameState =
        if (koForbiddenFor == opponent) copy(koPoint = null, koForbiddenFor = null) else this

    /** 풀이는 수순을 쓰지 않는다 — 수마다 목록을 복사하는 비용만 남으므로 지운다. */
    private fun GameState.withoutHistory(): GameState = if (moves.isEmpty()) this else copy(moves = emptyList())

    /** 국면의 열쇠 — 돌 배치·차례·패 금지·잇단 통과. 돌은 판 전체를 비트로 접는다(81점이면 Long 둘). */
    private fun Node.key(): NodeKey {
        val size = state.boardSize.value
        val black = LongArray((size * size + 63) / 64)
        val white = LongArray(black.size)
        for ((point, color) in state.stones) {
            val index = point.row * size + point.column
            val target = if (color == StoneColor.Black) black else white
            target[index / 64] = target[index / 64] or (1L shl (index % 64))
        }
        return NodeKey(
            black = black.toList(),
            white = white.toList(),
            next = state.nextPlayer,
            ko = state.koPoint,
            koBanned = state.koForbiddenFor,
            passes = passes,
        )
    }

    private data class NodeKey(
        val black: List<Long>,
        val white: List<Long>,
        val next: StoneColor,
        val ko: BoardCoordinate?,
        val koBanned: StoneColor?,
        val passes: Int,
    )

    /** [nodeLimit]를 넘었다 — 그 후보는 폰에서 열기에 너무 크다. */
    class NodeLimitExceeded(val visited: Int) : RuntimeException("read more than the node limit ($visited)")

    companion object {
        /** 문제 하나를 풀며 볼 수 있는 국면 수의 상한 — 폰에서 문제를 열 때 한 번 푼다(테스트가 묶는다). */
        const val NodeBudget: Int = 20_000

        private const val Unreachable: Int = 1_000_000
    }
}
