package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.BoardCornerPoints
import com.worksoc.goaicoach.shared.vision.PointF2D

/**
 * 읽은 답이 **샘플 위치를 조금 흔들어도 같은가**(백로그 #263 단계 6) — 인식의 확신도.
 *
 * 모서리 넷을 같은 방향으로 [ShiftCells]칸씩(오른쪽·왼쪽·아래·위) 옮겨 다시 읽고, 한 번이라도 답이 바뀐 교점을 「흔들린다」고 본다.
 * - **교점**: 흔들리는 교점은 보정 화면에 「확인 필요」로 표시한다 — 경계에 걸린 돌·어긋난 격자에서 답이 바뀐다.
 * - **판 전체**: 흔들리는 교점이 [ConfidentRatio] 이하일 때만 핀 화면을 건너뛴다 — 판을 잘못 찾으면 수십 개가 흔들린다.
 *
 * 숫자의 근거(2026-10-13 측정, 기준 이미지 5장 + 변형 30장 — `BoardPhotoReportTest`): 바르게 찾은 판은 흔들리는 교점이 0~28개
 * (한 칸이 15px인 아주 작은 인쇄본만 65~76개), 판을 잘못 찾은 판(인식률 33~70%)은 95~131개였다. 작은 인쇄본의 실제로 틀린 2~3점은 모두 흔들렸다.
 * ⚠️ 0.1칸·15%는 스레드가 정한 값이다 — 사용자 사진(폰 2048px)으로 다시 잰다.
 */
internal object ScanStability {
    const val ShiftCells = 0.1f
    const val ConfidentRatio = 0.15f

    fun unstablePoints(
        source: PixelSource,
        corners: BoardCornerPoints,
        boardSize: BoardSize,
        base: Map<BoardCoordinate, StoneColor>,
    ): Set<BoardCoordinate> {
        val n = boardSize.value
        val c = corners
        val ux = (c.topRight.x - c.topLeft.x) / (n - 1) * ShiftCells
        val uy = (c.topRight.y - c.topLeft.y) / (n - 1) * ShiftCells
        val vx = (c.bottomLeft.x - c.topLeft.x) / (n - 1) * ShiftCells
        val vy = (c.bottomLeft.y - c.topLeft.y) / (n - 1) * ShiftCells
        val out = HashSet<BoardCoordinate>()
        listOf(ux to uy, -ux to -uy, vx to vy, -vx to -vy).forEach { (dx, dy) ->
            val moved = BoardCornerPoints(c.topLeft.moved(dx, dy), c.topRight.moved(dx, dy), c.bottomRight.moved(dx, dy), c.bottomLeft.moved(dx, dy))
            val other = GridStoneDetector.detect(BoardWarp.rectify(source, moved, boardSize)).stones
            for (row in 0 until n) {
                for (col in 0 until n) {
                    val p = BoardCoordinate(row = row, column = col)
                    if (base[p] != other[p]) out += p
                }
            }
        }
        return out
    }

    fun isConfident(unstable: Set<BoardCoordinate>, boardSize: BoardSize): Boolean =
        unstable.size <= ConfidentRatio * boardSize.value * boardSize.value

    private fun PointF2D.moved(dx: Float, dy: Float) = PointF2D(x + dx, y + dy)
}
