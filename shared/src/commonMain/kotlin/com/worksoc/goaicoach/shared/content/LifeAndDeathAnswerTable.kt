package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.Move

/**
 * 맥에서 미리 읽어 둔 판정 표를 읽는다(백로그 #277) — 폰에서 그 자리에서 읽기에 큰 문제(고전 · 고급)를 위해. 표는 **같은 풀이기**가
 * 정답만 두어 닿는 차례를 모두 돌며 만든 것이다(`LifeAndDeathAnswerTableExportTest`) — 그래서 판정의 규칙은 여전히 하나다.
 *
 * 표에는 차례마다 판이 받는 수 전부(그리고 손 뺌)의 판정이 있다 — 맞으면 상대의 응수, 틀리면 상대의 반박. 틀린 수 뒤로는 차례가 이어지지
 * 않으므로(「다시 풀기」) 표는 정답의 길만큼만 자란다.
 *
 * ## 글 모양 — 한 줄에 한 차례
 * ```
 * fingerprint <16자리 16진수>
 * <수순>|<S 또는 ->|<맞는 수>|<틀린 수>
 * ```
 * 수순은 처음부터 둔 수를 `행,열`로 적어 `.`로 이은 것(처음 국면은 `-` — 판 크기를 몰라도 읽히게). 맞는 수는 `수>응수`(응수가 없으면 `수>.`)를 [LifeAndDeathJudge.correctMoves]의
 * 차례대로 `;`로 잇고, 틀린 수도 `수>반박`으로 잇는다 — 손 뺌은 수 자리에 `tenuki`.
 *
 * ⚠️ 문제의 그림 · 목표가 바뀌면 [fingerprintOf]가 달라진다 — 낡은 표는 쓰지 않고 그 자리에서 읽는 판정으로 돌아간다(느리지만 틀리지 않는다).
 * 테스트(`LifeAndDeathProblemsTest`)는 낡은 표를 빨갛게 잡는다 — 표를 다시 만들 것.
 */
class AnswerTableJudge private constructor(
    private val turns: Map<String, Entry>,
) : LifeAndDeathJudge {
    private class Entry(
        val settled: Boolean,
        val correct: List<BoardCoordinate>,
        val verdicts: Map<BoardCoordinate?, LifeAndDeathVerdict>,
    )

    override fun root(): LifeAndDeathTurn = LifeAndDeathTurn(emptyList(), node = null)

    override fun accepts(turn: LifeAndDeathTurn, move: BoardCoordinate): Boolean = move in entry(turn).verdicts

    override fun verdict(turn: LifeAndDeathTurn, move: BoardCoordinate?): LifeAndDeathVerdict =
        requireNotNull(entry(turn).verdicts[move]) { "the answer table has no verdict for $move at ${pathOf(turn.moves)}" }

    override fun next(turn: LifeAndDeathTurn, move: BoardCoordinate, reply: BoardCoordinate): LifeAndDeathTurn =
        LifeAndDeathTurn(turn.moves + move + reply, node = null)

    override fun correctMoves(turn: LifeAndDeathTurn): List<BoardCoordinate> = entry(turn).correct

    override fun isSettled(turn: LifeAndDeathTurn): Boolean = entry(turn).settled

    /** 표가 덮는 차례의 수 — 테스트가 표의 크기를 본다. */
    val turnCount: Int get() = turns.size

    private fun entry(turn: LifeAndDeathTurn): Entry =
        requireNotNull(turns[pathOf(turn.moves)]) { "the answer table has no turn ${pathOf(turn.moves)}" }

    companion object {
        private const val Tenuki = "tenuki"

        /** [text]가 [problem]의 표이면 읽고, 지문이 다르면(문제가 바뀐 뒤의 낡은 표) `null`. */
        fun read(problem: LifeAndDeathProblem, text: String): AnswerTableJudge? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val fingerprint = lines.firstOrNull()?.removePrefix("fingerprint ")
            if (fingerprint != fingerprintOf(problem)) return null
            val point = { label: String -> BoardCoordinate.fromLabel(label, problem.boardSize) }
            val turns = lines.drop(1).associate { line ->
                val (path, flag, correct, wrong) = line.split('|')
                val verdicts = mutableMapOf<BoardCoordinate?, LifeAndDeathVerdict>()
                val correctMoves = correct.split(';').filter { it.isNotEmpty() }.map { pair ->
                    val (move, reply) = pair.split('>')
                    verdicts[point(move)] = LifeAndDeathVerdict.Correct(reply.takeIf { it != "." }?.let(point))
                    point(move)
                }
                wrong.split(';').filter { it.isNotEmpty() }.forEach { pair ->
                    val (move, refutation) = pair.split('>')
                    verdicts[if (move == Tenuki) null else point(move)] = LifeAndDeathVerdict.Wrong(refutation.takeIf { it != "." }?.let(point))
                }
                path to Entry(settled = flag == "S", correct = correctMoves, verdicts = verdicts)
            }
            return AnswerTableJudge(turns)
        }

        /**
         * [problem]의 표를 [judge](그 자리에서 읽는 풀이기)로 만든다 — 맥에서만(문제 하나에 수십만 국면을 읽을 수 있다).
         * 정답만 두어 닿는 차례를 모두 돌며 차례마다 판이 받는 수 전부를 판정한다.
         */
        fun write(problem: LifeAndDeathProblem, judge: SolvingJudge): String {
            val label = { point: BoardCoordinate -> point.label(problem.boardSize) }
            val lines = mutableListOf("fingerprint ${fingerprintOf(problem)}")
            val pending = ArrayDeque(listOf(judge.root()))
            val seen = HashSet<String>()
            while (pending.isNotEmpty()) {
                val turn = pending.removeFirst()
                val path = pathOf(turn.moves)
                if (!seen.add(path)) continue
                val settled = judge.isSettled(turn)
                val correct = if (settled) emptyList() else judge.correctMoves(turn)
                val correctText = correct.joinToString(";") { move ->
                    val reply = (judge.verdict(turn, move) as LifeAndDeathVerdict.Correct).reply
                    if (reply != null) pending += judge.next(turn, move, reply)
                    "${label(move)}>${reply?.let(label) ?: "."}"
                }
                val wrongText = if (settled) {
                    ""
                } else {
                    (judge.legalMoves(turn).filter { it !in correct }.map { it to label(it) } + (null to Tenuki))
                        .joinToString(";") { (move, name) ->
                            val refutation = (judge.verdict(turn, move) as LifeAndDeathVerdict.Wrong).refutation
                            "$name>${refutation?.let(label) ?: "."}"
                        }
                }
                lines += "$path|${if (settled) "S" else "-"}|$correctText|$wrongText"
            }
            return lines.joinToString("\n")
        }

        /**
         * **원전의 정답 수순**으로 표를 만든다(백로그 #277 — 고전 사활, 사용자 2026-10-21 「고전 정답 활용 싣기」). 풀이기가 아니라 원전이 정답을 정한다:
         * 흑 차례마다 [line]의 수 하나만 맞고 상대는 [line]의 다음 수로 받는다. 그 밖의 수는 모두 오답이고, 상대의 반박은 [refutation]이 준다
         * (맥에서 엔진이 고른 수 — 없거나 판이 받지 않으면 붉은 표시만). 틀린 수 뒤로는 차례가 이어지지 않으므로 표는 원전 수순만큼만 자란다.
         *
         * @param line 처음부터 흑 · 백이 번갈아 둔 원전의 수순. 흑의 수로 끝나야 한다(흑이 둔 뒤 상대가 받을 것이 없으면 다 푼 것).
         * @param refutation 흑 차례 번호(0부터)와 흑이 둔 틀린 수(`null`이면 손 뺌) → 상대의 반박.
         */
        fun fromSourceLine(
            problem: LifeAndDeathProblem,
            line: List<BoardCoordinate>,
            refutation: (turn: Int, move: BoardCoordinate?) -> BoardCoordinate?,
        ): String {
            require(line.size % 2 == 1) { "${problem.id}: the source line ends with the solver's move" }
            val label = { point: BoardCoordinate -> point.label(problem.boardSize) }
            val lines = mutableListOf("fingerprint ${fingerprintOf(problem)}")
            var board = problem.startState()
            for (turn in 0..line.size / 2) {
                val move = line[turn * 2]
                val reply = line.getOrNull(turn * 2 + 1)
                val played = line.subList(0, turn * 2)
                val legal = problem.playable.filter { point ->
                    point != move && BoardRules.validate(board, Move.Play(board.nextPlayer, point)) == null
                }
                val legalReply = { after: BoardCoordinate?, candidate: BoardCoordinate? ->
                    val next = if (after == null) BoardRules.play(board, Move.Pass(board.nextPlayer)) else BoardRules.play(board, Move.Play(board.nextPlayer, after))
                    candidate?.takeIf { BoardRules.validate(next, Move.Play(next.nextPlayer, it)) == null }
                }
                val wrong = (legal.map { it as BoardCoordinate? } + null).joinToString(";") { wrongMove ->
                    val answer = legalReply(wrongMove, refutation(turn, wrongMove))
                    "${wrongMove?.let(label) ?: Tenuki}>${answer?.let(label) ?: "."}"
                }
                lines += "${pathOf(played)}|-|${label(move)}>${reply?.let(label) ?: "."}|$wrong"
                board = BoardRules.play(board, Move.Play(board.nextPlayer, move))
                if (reply != null) board = BoardRules.play(board, Move.Play(board.nextPlayer, reply))
            }
            return lines.joinToString("\n")
        }

        private fun pathOf(moves: List<BoardCoordinate>): String =
            if (moves.isEmpty()) "-" else moves.joinToString(".") { "${it.row},${it.column}" }

        /** 문제의 판정을 바꾸는 것 전부(그림 · 목표 · 목표 무리 · 차례)의 지문 — FNV-1a 64비트. 기기와 맥에서 같은 값이어야 한다. */
        fun fingerprintOf(problem: LifeAndDeathProblem): String {
            val text = listOf(problem.goal.name, problem.toPlay.name, problem.target, problem.answer, problem.diagram.joinToString("/"))
                .joinToString("|")
            var hash = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
            for (byte in text.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash.toULong().toString(16).padStart(16, '0')
        }
    }
}

/**
 * 표가 실린 문제는 표를, 아니면 폰에서 그 자리에서 읽는다. 낡은 표(문제를 고친 뒤 다시 만들지 않은 표)는 쓰지 않는다 — 느려질 뿐 틀리지는 않는다.
 */
fun lifeAndDeathJudgeFor(problem: LifeAndDeathProblem): LifeAndDeathJudge =
    lifeAndDeathAnswerTables[problem.id]?.let { AnswerTableJudge.read(problem, it) } ?: SolvingJudge(problem)
