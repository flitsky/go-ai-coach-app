package com.worksoc.goaicoach.shared.playstyle

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot

/**
 * 5계층 — **대국의 국면**(백로그 #221, 사용자 2026-10-08). 수순 길이를 판의 자리 수로 나눈 값 하나로 가른다.
 *
 * | 국면 | 수순 길이 ÷ 자리 수 | 9줄 | 13줄 | 19줄 | AI가 진 판에서 하는 일([HopelessPosition]) |
 * | --- | --- | --- | --- | --- | --- |
 * | 초반 | 30% 미만 | ~24수 | ~50수 | ~108수 | 없다 — 그냥 둔다 |
 * | 중반 | 30~60% | 25수~ | 51수~ | 109수~ | 판의 15% 이상 뒤진 채 5회 → 기권을 제안한다 |
 * | 후반 | 60~80% | 49수~ | 102수~ | 217수~ | 판의 10% 이상 뒤진 채 5회 → 기권을 제안한다 |
 * | 종반 | 80% 이상 | 65수~ | 136수~ | 289수~ | 진 판이면 통과한다 |
 *
 * ⚠️ **국면을 묻는 자리는 여기 하나다.** 「판의 80%」 같은 비율을 다른 파일에 다시 적지 않는다 — 통과와 제안이 서로 다른 잣대를 쓰게 된다.
 * 놓인 돌 수가 아니라 **수순 길이**다(집은 빈 자리로 남아, 돌 수로는 끝난 판도 80%에 닿지 않는다).
 */
enum class GamePhase {
    Opening,
    Middle,
    Late,
    Endgame,
    ;

    companion object {
        const val MiddleStartsAt: Double = 0.3
        const val LateStartsAt: Double = 0.6
        const val EndgameStartsAt: Double = 0.8

        /** 수순 길이가 [moveCount]인 판의 국면. */
        fun at(moveCount: Int, boardSize: BoardSize): GamePhase {
            val points = boardSize.value * boardSize.value
            return when {
                moveCount >= points * EndgameStartsAt -> Endgame
                moveCount >= points * LateStartsAt -> Late
                moveCount >= points * MiddleStartsAt -> Middle
                else -> Opening
            }
        }

        fun of(state: GameState): GamePhase = at(state.moves.size, state.boardSize)
    }
}

/**
 * 5계층 — **진 판을 AI가 어떻게 끝내는가**의 판정(백로그 #213·#221). 국면([GamePhase])마다 하는 일이 하나씩이다.
 *
 * 1. **중반·후반의 기권 제안** — **상대가 둔 뒤의 형세**가 문턱보다 뒤진 채 [ReadingsBeforeOffer]회 이어지면 AI가 기권을 제안한다
 *    ([resignationGrounds]). 문턱은 판 크기에 비례하고 국면마다 다르다 — 일찍일수록 따라붙을 시간이 있어 높다([MiddleDeficitShare] ·
 *    [LateDeficitShare]). 바로 기권하지 않고 **묻는다** — 사용자가 받아들이거나 계속 둔다(한 판에 한 번, 묻는 쪽은 대국 세션이다).
 * 2. **종반의 통과** — 통과는 AI가 *더 둘 것이 없고 계가가 맞다*고 볼 때만 한다. 진 판([isLost])이 AI의 수 [LostTurnsBeforePass]번
 *    이어지고 종반이면 통과한다([LosingStreak.passesAt]). **종반 전에는 통과하지 않는다** — 그 밖의 통과는 심판(9단 정책)이 통과를
 *    1위로 볼 때뿐이다(`HumanMoveSampler.shouldPass`).
 *
 * ## 누가 무엇을 정했나 — 숫자를 바꾸려면 사용자에게 묻는다
 * - **사용자**: 통과는 종국에서만 · 「승률 0% · 판의 80%」 · 중반에는 한 번 제안하고 사용자가 고른다(2026-10-07). 국면을 수순 길이로
 *   30·60·80%에서 가른다 · 초반은 로직 없음 · **상대 착수 뒤**의 형세를 **5회** 모은다 · 문턱은 판 크기에 비례(2026-10-08).
 * - **스레드가 제안해 사용자가 골랐다**(2026-10-08): 문턱은 중반 15% · 후반 10%(사용자 안은 10% 하나였다) · 5회의 **지속**으로 가른다
 *   (사용자 안은 5회에 직선을 그어 종반의 형세를 내다보는 것이었다 — 실험실 E10 보조 `phases.py`: 직선은 가르는 데 보탬이 없고,
 *   종반의 형세를 「지금 그대로」보다 더 틀리게 내다본다).
 * - **스레드가 메운 것**(사용자 승인 2026-10-08, 백로그 U-66): 80%는 수순 길이로 읽는다 · 통과에도 2수 연속을 둔다.
 *
 * 근거(E10 보조 `phases.py` · `split.py`, 기보 414판 · 진영 표본 828): 제안은 216판에 걸려 그 진영이 끝내 이긴 판이 3(1.4%), 20집 넘게 진
 * 판의 92%를 덮는다. 종반의 통과는 169판에 잘못 1. 숫자를 바꾸면 실험실의 대응표(`engine-lab/lab/app_parity.py`)가 빨개진다 — 같이 고친다.
 */
object HopelessPosition {
    /** 진 판 — 상대 승률 99% 이상, 곧 내 승률이 이 값 이하(사용자가 말한 「승률 0%」). 종반의 통과가 본다. */
    const val MaxOwnWinRate: Double = 0.01

    /** 진 판이 종반에서 AI의 수 몇 번 이어지면 통과하는가. */
    const val LostTurnsBeforePass: Int = 2

    /** 중반에 기권을 제안하는 문턱 — 형세 점수차가 판의 자리 수의 이만큼 이상 뒤진다(9줄 12집 · 13줄 25집 · 19줄 54집). */
    const val MiddleDeficitShare: Double = 0.15

    /** 후반에 기권을 제안하는 문턱(9줄 8집 · 13줄 17집 · 19줄 36집) — 남은 수가 적어 중반보다 낮다. */
    const val LateDeficitShare: Double = 0.10

    /** 상대가 둔 뒤의 형세가 몇 회 **연속** 문턱 밖이어야 제안하는가 — 한두 번 튄 값으로 던지지 않는다. */
    const val ReadingsBeforeOffer: Int = 5

    /** 종반인가 — 통과가 「대국 중반의 통과」가 되지 않게 하는 문이다. */
    fun isEndgame(state: GameState): Boolean = GamePhase.of(state) == GamePhase.Endgame

    /** [player]에게 진 판인가 — 상대 승률 99% 이상. 값이 없으면 아니다(모르는 것으로 통과하지 않는다). */
    fun isLost(player: StoneColor, estimate: ScoreEstimate): Boolean {
        val whiteWinRate = estimate.whiteWinRate ?: return false
        val ownWinRate = if (player == StoneColor.White) whiteWinRate else 1.0 - whiteWinRate
        return ownWinRate <= MaxOwnWinRate
    }

    /** [phase]에서 기권을 제안하는 문턱(집). 초반·종반에는 제안하지 않는다(`null`). */
    fun deficitBar(phase: GamePhase, boardSize: BoardSize): Double? {
        val points = boardSize.value * boardSize.value
        return when (phase) {
            GamePhase.Middle -> points * MiddleDeficitShare
            GamePhase.Late -> points * LateDeficitShare
            GamePhase.Opening, GamePhase.Endgame -> null
        }
    }

    /**
     * [player](AI)가 둘 차례인 [state]에서 **기권을 제안할 근거** — 없으면 `null`.
     *
     * 근거는 수마다 쌓이는 형세 기록([snapshots])에서 읽는다: **상대가 둔 직후**의 값 가운데 가장 최근 [ReadingsBeforeOffer]개가
     * 모두 있고, 저마다 **제가 재어진 국면의 문턱**보다 뒤져 있어야 한다. 하나라도 따라붙었거나, 초반에 잰 값이거나, 기록이 비었으면
     * 근거가 없다(처음부터 다시 모인다). 형세 기록은 무르면 잘리고 이어하면 되살아나므로 따로 세는 것이 없다.
     *
     * ⚠️ 신경망이 본 값만 쓴다(`isNetworkEstimate`) — 돌 수만 센 국소 계가는 척도가 다르다.
     */
    fun resignationGrounds(player: StoneColor, state: GameState, snapshots: List<ScoreSnapshot>): ResignationGrounds? {
        if (state.nextPlayer != player) return null
        if (deficitBar(GamePhase.of(state), state.boardSize) == null) return null
        val opponentMoveNumbers = state.moves.indices.reversed()
            .filter { index -> state.moves[index].player != player }
            .take(ReadingsBeforeOffer)
            .map { index -> index + 1 }
        if (opponentMoveNumbers.size < ReadingsBeforeOffer) return null
        val byMoveNumber = snapshots.associateBy { snapshot -> snapshot.moveNumber }
        val readings = opponentMoveNumbers.map { moveNumber ->
            val bar = deficitBar(GamePhase.at(moveNumber, state.boardSize), state.boardSize) ?: return null
            val snapshot = byMoveNumber[moveNumber]?.takeIf { it.source.isNetworkEstimate } ?: return null
            val ownScoreLead = (snapshot.whiteScoreLead ?: return null) * towardWhite(player)
            if (ownScoreLead > -bar) return null
            ResignationReading(moveNumber = moveNumber, ownScoreLead = ownScoreLead, deficitBar = bar)
        }
        return ResignationGrounds(readings.reversed())
    }

    private fun towardWhite(player: StoneColor): Double = if (player == StoneColor.White) 1.0 else -1.0
}

/** 상대가 [moveNumber]번째 수를 둔 직후의 형세 — AI 기준 점수차([ownScoreLead], 뒤지면 −)와 그 국면의 문턱([deficitBar], 집). */
data class ResignationReading(
    val moveNumber: Int,
    val ownScoreLead: Double,
    val deficitBar: Double,
)

/** 기권을 제안하게 한 형세들 — 수순대로. 진단 로그에 그대로 적는다(왜 그때 물었는지 리포트만으로 알 수 있게). */
data class ResignationGrounds(val readings: List<ResignationReading>)

/**
 * 한 진영의 「진 판」이 이어진 차례 수 — AI 차례가 끝날 때마다 [after]로 잇는다. **종반의 통과**만 이것을 본다
 * (기권 제안은 형세 기록에서 읽는다 — [HopelessPosition.resignationGrounds]).
 *
 * @property countedAtMoveCount 센 직후의 수순 길이. 그 뒤로 상대가 정확히 한 수 둔 판이어야 「이어진 판」이다([continuesAt]) —
 *   새 대국·무르기·이어하기면 어긋나서 처음부터 센다.
 */
data class LosingStreak(
    val lostTurns: Int = 0,
    val countedAtMoveCount: Int = -1,
) {
    /** [state]가 이 기록에 이어지는 판인가 — 센 뒤로 상대가 한 수 뒀다. */
    fun continuesAt(state: GameState): Boolean = countedAtMoveCount + 1 == state.moves.size

    /** [player]가 방금 둔 뒤([state])의 형세로 기록을 잇는다. 형세를 모르면([estimate]가 `null`) 처음부터 센다. */
    fun after(player: StoneColor, estimate: ScoreEstimate?, state: GameState): LosingStreak =
        LosingStreak(
            lostTurns = if (estimate != null && HopelessPosition.isLost(player, estimate)) lostTurns + 1 else 0,
            countedAtMoveCount = state.moves.size,
        )

    /** 이 기록 다음의 AI 차례([state])에 **통과**하는가 — 진 판이 이어졌고 종반이다. 종반 전에는 통과하지 않는다. */
    fun passesAt(state: GameState): Boolean =
        continuesAt(state) && lostTurns >= HopelessPosition.LostTurnsBeforePass && HopelessPosition.isEndgame(state)
}
