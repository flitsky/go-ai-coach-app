package com.worksoc.goaicoach.ui.gamerecord

import com.worksoc.goaicoach.shared.enginecontract.EngineStatus
import com.worksoc.goaicoach.shared.enginecontract.ScoreEstimate
import com.worksoc.goaicoach.shared.enginecontract.ownershipEstimateOf
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshot
import com.worksoc.goaicoach.shared.scoring.ScoreSnapshotSource

/**
 * 앱에 실은 기보의 **미리 잰 형세**(백로그 #269 — 사용자 2026-10-19: 형세 보기를 켜 두면 수를 넘길 때마다 형세가 그려져 변해 가는 것을 본다).
 *
 * 맥에서 앱과 **같은 모델 · 같은 명령**(`kata-raw-nn 0`)으로 잰 값이다(`engine-lab/tools/game_record_evals.py`) — 그래서 엔진에 물은 형세와 같은 뜻이고,
 * 영역도 같은 함수([ownershipEstimateOf])로 센다. 엔진을 부르지 않으니 자동 진행에도 끊기지 않는다. 누구에게 보이는가는 다시보기와 같은 게이트다
 * (`ReplayAnalysis.kt` — 구독·광고 1시간은 켜진 채 따라가고, 1회권은 한 장에 국면 하나).
 *
 * 형식은 그 도구의 머리 주석이 정본이다: 머리 16바이트 · 국면마다 집 차이·승률 f32 · 영역 i8(첫 국면은 그대로, 다음부터 앞 국면과의 차이).
 */
internal class GameRecordEvaluation(
    val boardSize: Int,
    /** 0 일본식, 1 중국식 — 기보를 읽은 해석과 같아야 한다(`GameRecordCatalogContractTest`). */
    val rulesCode: Int,
    val komi: Double,
    val positions: Int,
    private val whiteLeads: FloatArray,
    private val whiteWins: FloatArray,
    /** 국면마다 판 크기² 칸, 백 기준 −127~127(풀어 둔 값). */
    private val ownership: ByteArray,
) {
    /** 판 전체 형세 그래프·변곡점이 읽는 기록 — 다시보기의 형세 기록과 같은 모양(주 모델의 신경망 평가). */
    val snapshots: List<ScoreSnapshot> by lazy {
        (0 until positions).map { move ->
            ScoreSnapshot(
                moveNumber = move,
                whiteScoreLead = whiteLeads[move].toDouble(),
                whiteWinRate = whiteWins[move].toDouble().coerceIn(0.0, 1.0),
                source = ScoreSnapshotSource.EngineEstimate,
            )
        }
    }

    /** [moveNumber]수째 국면의 형세 — 엔진에 물었을 때 받는 것과 같은 모양. 범위 밖이면 null. */
    fun estimateAt(moveNumber: Int): ScoreEstimate? {
        if (moveNumber !in 0 until positions) return null
        val cells = boardSize * boardSize
        val base = moveNumber * cells
        val rows = List(boardSize) { row -> List(boardSize) { column -> ownership[base + row * boardSize + column] / OwnershipScale } }
        return ScoreEstimate(
            status = EngineStatus.ready("Precomputed raw NN estimate (game record)."),
            whiteWinRate = whiteWins[moveNumber].toDouble().coerceIn(0.0, 1.0),
            whiteScoreLead = whiteLeads[moveNumber].toDouble(),
            ownership = ownershipEstimateOf(rows),
            summary = "Precomputed raw NN estimate. Positive score lead favors White; negative favors Black.",
        )
    }
}

private const val OwnershipScale = 127.0
private val Magic = "GREV".encodeToByteArray()
private const val SupportedVersion = 1
private const val HeaderBytes = 16

/** `.eval` 바이트 → [GameRecordEvaluation]. 머리·길이가 맞지 않으면 null — 그 판은 형세 없이 연다(엔진에 묻는 길은 그대로다). */
internal fun readGameRecordEvaluation(bytes: ByteArray): GameRecordEvaluation? {
    if (bytes.size < HeaderBytes || !bytes.copyOfRange(0, Magic.size).contentEquals(Magic)) return null
    val buffer = LittleEndianReader(bytes, position = Magic.size)
    val version = buffer.get().toInt() and 0xFF
    val size = buffer.get().toInt() and 0xFF
    val rules = buffer.get().toInt() and 0xFF
    buffer.get()
    val komi = buffer.float().toDouble()
    val positions = buffer.int()
    val cells = size * size
    if (version != SupportedVersion || size !in setOf(9, 13, 19) || positions <= 0) return null
    if (bytes.size != HeaderBytes + positions * 8 + positions * cells) return null
    val leads = FloatArray(positions)
    val wins = FloatArray(positions)
    for (move in 0 until positions) {
        leads[move] = buffer.float()
        wins[move] = buffer.float()
    }
    val ownership = ByteArray(positions * cells)
    buffer.get(ownership, 0, cells)
    for (move in 1 until positions) {
        val base = move * cells
        for (cell in 0 until cells) {
            ownership[base + cell] = (ownership[base - cells + cell] + buffer.get()).toByte()
        }
    }
    return GameRecordEvaluation(size, rules, komi, positions, leads, wins, ownership)
}

/** 리틀 엔디언 바이트 읽기 — `java.nio.ByteBuffer`(LITTLE_ENDIAN)의 이 파일이 쓰던 몫만(#294 — 공용 코드에는 nio가 없다). 길이는 부르는 쪽이 먼저 확인한다. */
private class LittleEndianReader(private val bytes: ByteArray, private var position: Int) {
    fun get(): Byte = bytes[position++]

    fun int(): Int {
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun float(): Float = Float.fromBits(int())

    fun get(destination: ByteArray, offset: Int, length: Int) {
        bytes.copyInto(destination, offset, position, position + length)
        position += length
    }
}
