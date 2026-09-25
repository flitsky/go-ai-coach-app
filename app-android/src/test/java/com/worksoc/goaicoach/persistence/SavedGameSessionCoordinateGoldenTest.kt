package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.PlayLevelSetting
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * **이어하기 저장분의 좌표 골든 — 리터럴 JSON과 정수 좌표 사이를 양방향으로 고정한다**(refactor backlog #36).
 *
 * `SavedGameSessionCodecTest`는 전부 *encode→decode* 왕복이라, `label`과 `fromLabel`이 **함께**
 * 바뀌는 변경(알파벳에 `I`를 넣기, 행 원점 뒤집기)에 초록으로 남는다. 그 사이 사용자 기기에 이미
 * 저장된 `active_game_snapshot`은 다른 점으로 읽히거나, 판 밖이 돼 `decode`가 `null`을 돌려주고
 * **이어하기가 조용히 사라진다.** 여기서는 저장된 모양을 **문자열 리터럴로** 두고 정수 좌표와 대조한다.
 *
 * ⚠️ 저장 포맷·`SchemaVersion`은 건드리지 않는다(함정 69) — 이 테스트는 지금 포맷을 고정만 한다.
 */
class SavedGameSessionCoordinateGoldenTest {

    @Test
    fun literalSavedSessionDecodesToIntegerCoordinates() {
        val restored = SavedGameSessionCodec.decode(SavedSession19x19)

        assertNotNull("리터럴 이어하기 JSON이 디코드되지 않는다 — 기기의 저장분이 조용히 사라진다", restored)
        val gameState = restored!!.gameState
        assertEquals(BoardSize.Nineteen, gameState.boardSize)
        assertEquals(GoldenMoves, gameState.moves)
        assertEquals(
            mapOf(
                BoardCoordinate(3, 15) to StoneColor.Black,
                BoardCoordinate(15, 3) to StoneColor.White,
                BoardCoordinate(9, 9) to StoneColor.Black,
            ),
            gameState.stones,
        )
        assertEquals(StoneColor.Black, gameState.nextPlayer)
    }

    @Test
    fun integerBuiltSessionEncodesGoldenCoordinateStrings() {
        val gameState = GoldenMoves.fold(GameState.empty(BoardSize.Nineteen, Ruleset.Japanese)) { state, move ->
            state.play(move)
        }
        val json = JSONObject(
            SavedGameSessionCodec.encode(
                SavedGameSnapshot(
                    gameState = gameState,
                    playerSetup = PlayerSetup(),
                    playLevel = PlayLevelSetting(),
                    topMovesEnabled = false,
                    savedAtMillis = 1234L,
                ),
            ),
        )

        assertEquals(1, json.getInt("schema"))
        assertEquals(19, json.getInt("boardSize"))
        val moves = json.getJSONArray("moves")
        val encoded = List(moves.length()) { index ->
            val move = moves.getJSONObject(index)
            listOf(move.getString("type"), move.getString("player"), move.optString("coordinate", "<없음>"))
        }
        assertEquals(
            listOf(
                listOf("play", "Black", "Q16"),
                listOf("play", "White", "D4"),
                listOf("play", "Black", "K10"),
                listOf("pass", "White", "<없음>"),
            ),
            encoded,
        )
        assertFalse("통과 수에는 좌표 키가 없다", moves.getJSONObject(3).has("coordinate"))
    }

    private companion object {
        /** Q16=(3,15)·D4=(15,3)·K10=(9,9) — K는 `I`를 건너뛴 뒤의 열이라 알파벳 드리프트가 여기서 드러난다. */
        val GoldenMoves: List<Move> = listOf(
            Move.Play(StoneColor.Black, BoardCoordinate(3, 15)),
            Move.Play(StoneColor.White, BoardCoordinate(15, 3)),
            Move.Play(StoneColor.Black, BoardCoordinate(9, 9)),
            Move.Pass(StoneColor.White),
        )

        /** `SavedGameSessionCodec.encode`가 쓰는 모양 그대로의 19x19 이어하기 저장분. */
        val SavedSession19x19 = """
            {
              "schema": 1,
              "savedAtMillis": 1234,
              "boardSize": 19,
              "ruleset": "Japanese",
              "handicapCount": 0,
              "komi": 6.5,
              "moves": [
                {"type": "play", "player": "Black", "coordinate": "Q16"},
                {"type": "play", "player": "White", "coordinate": "D4"},
                {"type": "play", "player": "Black", "coordinate": "K10"},
                {"type": "pass", "player": "White"}
              ],
              "playerSetup": {
                "black": {"controller": "Human", "humanGameType": "Normal", "playLevel": {"group": "FastBeginner", "level": 1}},
                "white": {"controller": "Ai", "humanGameType": "Normal", "playLevel": {"group": "FastBeginner", "level": 3}}
              },
              "playLevel": {"group": "FastBeginner", "level": 3},
              "topMovesEnabled": false,
              "scoreSnapshots": [],
              "finalScoreJudgement": null
            }
        """.trimIndent()
    }
}
