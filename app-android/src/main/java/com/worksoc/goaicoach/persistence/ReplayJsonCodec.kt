package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.application.movereview.MoveReviewTone
import com.worksoc.goaicoach.shared.BoardCoordinate
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.ScoreSnapshot
import com.worksoc.goaicoach.shared.ScoreSnapshotSource
import com.worksoc.goaicoach.shared.StoneColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * 수순·형세 타임라인·착수 평가의 **공용 JSON 표현**(백로그 #151).
 *
 * ⚠️ **새로 쓴 포맷이 아니다** — [GameSessionStore]가 "진행 중 대국 이어하기"용으로 쓰던
 * private 코덱을 그대로 끌어올린 것이다. 대국 기록(리플레이)도 같은 모양을 써야 다시보기가
 * 두 저장소를 오가며 다른 규칙을 배울 필요가 없다.
 *
 * ⚠️ **`JsonPositionAnalysisCacheStore`와 통합하지 말 것.** 그쪽은 엔진 분석 결과를 위치
 * 기준으로 담는 **다른 포맷**이고, 한 코덱으로 묶으면 분석 캐시가 깨진다.
 *
 * ⚠️ **좌표는 `BoardSize`에 의존한다**(`label`/`fromLabel`). 디코드할 때 저장 당시와 같은 판
 * 크기를 넘기지 않으면 좌표가 어긋난다 — 그래서 두 함수 모두 `boardSize`를 받는다.
 */
internal object ReplayJsonCodec {

    fun encodeMoves(
        moves: List<Move>,
        boardSize: BoardSize,
    ): JSONArray =
        JSONArray().also { array ->
            moves.forEach { move ->
                array.put(
                    JSONObject()
                        .put("type", move.typeName())
                        .put("player", move.player.name)
                        .also { moveJson ->
                            if (move is Move.Play) {
                                moveJson.put("coordinate", move.coordinate.label(boardSize))
                            }
                        },
                )
            }
        }

    fun decodeMoves(
        json: JSONArray,
        boardSize: BoardSize,
    ): List<Move> =
        List(json.length()) { index ->
            val moveJson = json.getJSONObject(index)
            val player = enumOrDefault(moveJson.getString("player"), StoneColor.Black)
            when (moveJson.getString("type")) {
                "play" -> Move.Play(
                    player = player,
                    coordinate = BoardCoordinate.fromLabel(moveJson.getString("coordinate"), boardSize),
                )
                "pass" -> Move.Pass(player)
                "resign" -> Move.Resign(player)
                else -> error("Unknown move type: ${moveJson.getString("type")}")
            }
        }

    fun encodeScoreSnapshots(snapshots: List<ScoreSnapshot>): JSONArray =
        JSONArray().also { array ->
            snapshots.forEach { snapshot ->
                array.put(
                    JSONObject()
                        .put("moveNumber", snapshot.moveNumber)
                        .put("whiteScoreLead", snapshot.whiteScoreLead ?: JSONObject.NULL)
                        .put("whiteWinRate", snapshot.whiteWinRate ?: JSONObject.NULL)
                        .put("source", snapshot.source.name),
                )
            }
        }

    fun decodeScoreSnapshots(json: JSONArray): List<ScoreSnapshot> =
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            ScoreSnapshot(
                moveNumber = item.getInt("moveNumber"),
                whiteScoreLead = if (item.isNull("whiteScoreLead")) null else item.getDouble("whiteScoreLead"),
                whiteWinRate = if (item.isNull("whiteWinRate")) null else item.getDouble("whiteWinRate"),
                source = enumOrDefault(item.optString("source"), ScoreSnapshotSource.EngineEstimate),
            )
        }

    fun encodeMoveEvaluations(
        evaluations: List<MoveReviewMarker>,
        boardSize: BoardSize,
    ): JSONArray =
        JSONArray().also { array ->
            evaluations.forEach { marker ->
                array.put(
                    JSONObject()
                        .put("moveNumber", marker.moveNumber)
                        .put("coordinate", marker.coordinate.label(boardSize))
                        .put("tone", marker.tone.name)
                        .put("pointLoss", marker.pointLoss ?: JSONObject.NULL),
                )
            }
        }

    fun decodeMoveEvaluations(
        json: JSONArray,
        boardSize: BoardSize,
    ): List<MoveReviewMarker> =
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            MoveReviewMarker(
                coordinate = BoardCoordinate.fromLabel(item.getString("coordinate"), boardSize),
                moveNumber = item.getInt("moveNumber"),
                tone = enumOrDefault(item.optString("tone"), MoveReviewTone.Unknown),
                pointLoss = if (item.isNull("pointLoss")) null else item.getDouble("pointLoss"),
            )
        }

    private fun Move.typeName(): String =
        when (this) {
            is Move.Play -> "play"
            is Move.Pass -> "pass"
            is Move.Resign -> "resign"
        }
}
