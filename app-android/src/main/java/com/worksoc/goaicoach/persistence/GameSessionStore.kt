package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.decodePlayLevel
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayerSetup
import com.worksoc.goaicoach.persistence.PlayerSetupJsonCodec.encodePlayLevel
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameStateReplayer
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource
import org.json.JSONArray
import org.json.JSONObject

internal class GameSessionStore(context: Context) : SavedGameStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(snapshot: SavedGameSnapshot) {
        // A finished game is never "resumable" (see SavedGameSnapshot.isResumable), but a
        // snapshot carrying a final-score judgement still needs to survive process death so
        // the result popup can be restored on the next cold start.
        if (!snapshot.isResumable && snapshot.finalScoreJudgement == null) {
            clear()
            return
        }
        prefs.edit()
            .putString(SessionKey, SavedGameSessionCodec.encode(snapshot))
            .apply()
    }

    override fun load(): SavedGameSnapshot? {
        val raw = prefs.getString(SessionKey, null) ?: return null
        return SavedGameSessionCodec.decode(raw)
            .also { snapshot ->
                if (snapshot == null || (!snapshot.isResumable && snapshot.finalScoreJudgement == null)) {
                    clear()
                }
            }
            ?.takeIf { snapshot -> snapshot.isResumable || snapshot.finalScoreJudgement != null }
    }

    override fun clear() {
        prefs.edit().remove(SessionKey).apply()
    }

    /**
     * 저장된 원문 그대로 — **디버그 리포트의 `SavedSessionJson` 절에만** 싣는다(refactor backlog #86).
     *
     * ⚠️ **포트 메서드가 아니다.** 원문은 이 어댑터의 저장 형식이라 [SavedGameStorePort] 위로
     * 올리지 않고, 조립 루트만 이것을 불러 리포트에 불투명한 텍스트로 꽂는다.
     * ⚠️ **디코딩한 값으로 바꾸지 말 것** — 디코딩은 없는 키를 기본값으로 흡수하고(덤이 빠져도
     * 6.5로 보였던 2026-09-23 결함), 못 읽는 저장분은 [load]가 지운다. 원문만 그것을 보인다.
     */
    fun readRawJson(): String? {
        return prefs.getString(SessionKey, null)
    }

    private companion object {
        const val PrefsName = "go_ai_coach_session"
        const val SessionKey = "active_game_snapshot"
    }
}

/**
 * 진행 중 대국("이어하기")의 JSON 코덱.
 *
 * ⚠️ **판 정체성 필드를 손으로 골라 담는 구조다** — boardSize/ruleset/handicapCount/komi를
 * 각각 하나씩 적어 넣고, decode에서 다시 하나씩 읽어 [GameStateReplayer.replay]에 넘긴다.
 * 그래서 komi 하나가 빠져도 컴파일은 통과했고, 덤을 0.5나 7.5로 고른 사용자가 이어하기를
 * 하면 조용히 6.5로 돌아가 승패가 뒤집힐 수 있었다(2026-09-23 수정).
 *
 * 근본 해법은 판 정체성을 값 객체(GameSetup) 하나로 묶어 코덱이 그 하나만 왕복시키는 것이고,
 * 그건 별도 일감이다(`work/roadmap/260923-_ARCHITECTURE_DIAGNOSIS_AND_REFACTORING.md`).
 * 여기서는 사용자 피해를 먼저 멈추는 증상 수정 + 왕복 회귀 테스트까지만 한다.
 *
 * ⚠️ **[SchemaVersion]은 등호 검사다**(아래 decode). 번호를 올리면 저장된 대국이 통째로
 * 버려진다 — 새 필드는 `optXxx(키, 기존 기본값)` 흡수로만 더한다.
 */
internal object SavedGameSessionCodec {
    private const val SchemaVersion = 1

    fun encode(snapshot: SavedGameSnapshot): String =
        JSONObject()
            .put("schema", SchemaVersion)
            .put("savedAtMillis", snapshot.savedAtMillis)
            .put("boardSize", snapshot.gameState.boardSize.value)
            .put("ruleset", snapshot.gameState.ruleset.name)
            .put("handicapCount", snapshot.gameState.handicapCount)
            .put("komi", snapshot.gameState.komi)
            .put("moves", ReplayJsonCodec.encodeMoves(snapshot.gameState.moves, snapshot.gameState.boardSize))
            .put("playerSetup", encodePlayerSetup(snapshot.playerSetup))
            .put("playLevel", encodePlayLevel(snapshot.playLevel))
            .put("topMovesEnabled", snapshot.topMovesEnabled)
            .put("scoreSnapshots", ReplayJsonCodec.encodeScoreSnapshots(snapshot.scoreSnapshots))
            .put("finalScoreJudgement", snapshot.finalScoreJudgement?.let(::encodeFinalScoreJudgement) ?: JSONObject.NULL)
            .toString()

    fun decode(raw: String): SavedGameSnapshot? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", SchemaVersion) != SchemaVersion) {
                return@runCatching null
            }
            val boardSize = BoardSize(json.optInt("boardSize", BoardSize.Nine.value))
            val ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese)
            val handicapCount = json.optInt("handicapCount", 0)
            // ⚠️ 기본값은 반드시 [DefaultKomi]다 — komi 키가 없던 옛 저장분은 지금까지
            // `GameStateReplayer.replay`의 기본 인자(=DefaultKomi)로 복원돼 왔으므로, 같은
            // 값으로 흡수해야 스키마 번호를 올리지 않고도 옛 데이터가 그대로 이어진다.
            // (스키마 번호는 등호 검사라 올리면 이어하기가 통째로 날아간다 — 위 decode 참고.)
            val komi = json.optDouble("komi", DefaultKomi)
            val moves = ReplayJsonCodec.decodeMoves(json.optJSONArray("moves") ?: JSONArray(), boardSize)
            val gameState = GameStateReplayer.replay(
                boardSize = boardSize,
                ruleset = ruleset,
                moves = moves,
                handicapCount = handicapCount,
                komi = komi,
            )
            val scoreSnapshots = ReplayJsonCodec.decodeScoreSnapshots(json.optJSONArray("scoreSnapshots") ?: JSONArray())
            SavedGameSnapshot(
                gameState = gameState,
                playerSetup = decodePlayerSetup(json.optJSONObject("playerSetup")),
                playLevel = decodePlayLevel(json.optJSONObject("playLevel")),
                topMovesEnabled = json.optBoolean("topMovesEnabled", false),
                savedAtMillis = json.optLong("savedAtMillis", 0L),
                scoreSnapshots = scoreSnapshots,
                finalScoreJudgement = json.optJSONObject("finalScoreJudgement")?.let(::decodeFinalScoreJudgement),
            )
        }.getOrNull()

    private fun encodeFinalScoreJudgement(judgement: FinalScoreJudgement): JSONObject =
        JSONObject()
            .put("winner", judgement.winner?.name ?: JSONObject.NULL)
            .put("margin", judgement.margin ?: JSONObject.NULL)
            .put("ruleset", judgement.ruleset.name)
            .put("isEstimatedDisplay", judgement.isEstimatedDisplay)
            .put("removedBlack", judgement.removedBlack)
            .put("removedWhite", judgement.removedWhite)
            .put("blackArea", judgement.blackArea ?: JSONObject.NULL)
            .put("whiteAreaWithKomi", judgement.whiteAreaWithKomi ?: JSONObject.NULL)
            .put("capturedByBlack", judgement.capturedByBlack)
            .put("capturedByWhite", judgement.capturedByWhite)
            .put("komi", judgement.komi ?: JSONObject.NULL)
            .put("handicapCount", judgement.handicapCount)
            .put("whiteHandicapBonus", judgement.whiteHandicapBonus)

    private fun decodeFinalScoreJudgement(json: JSONObject): FinalScoreJudgement =
        FinalScoreJudgement(
            winner = if (json.isNull("winner")) null else enumOrDefault(json.optString("winner"), StoneColor.Black),
            margin = if (json.isNull("margin")) null else json.optDouble("margin"),
            ruleset = enumOrDefault(json.optString("ruleset"), Ruleset.Japanese),
            isEstimatedDisplay = json.optBoolean("isEstimatedDisplay", false),
            removedBlack = json.optInt("removedBlack", 0),
            removedWhite = json.optInt("removedWhite", 0),
            blackArea = if (json.isNull("blackArea")) null else json.optDouble("blackArea"),
            whiteAreaWithKomi = if (json.isNull("whiteAreaWithKomi")) null else json.optDouble("whiteAreaWithKomi"),
            capturedByBlack = json.optInt("capturedByBlack", 0),
            capturedByWhite = json.optInt("capturedByWhite", 0),
            komi = if (json.isNull("komi")) null else json.optDouble("komi"),
            handicapCount = json.optInt("handicapCount", 0),
            // ⚠️ 기본값은 반드시 0이다(#89) — 이 키가 없던 옛 판정은 보정 없이 계가됐으므로 0이어야
            // 그 판정의 백 합계(whiteAreaWithKomi)와 맞는다. 스키마 번호는 올리지 않는다(함정 69).
            // 거꾸로 새 저장본을 옛 코드가 읽어도 깨지지 않는다 — 이 decode는 키를 이름으로만 꺼내고
            // 모르는 키는 보지 않으며, 스키마 번호가 그대로라 등호 검사도 통과한다.
            whiteHandicapBonus = json.optDouble("whiteHandicapBonus", 0.0),
        )

}
