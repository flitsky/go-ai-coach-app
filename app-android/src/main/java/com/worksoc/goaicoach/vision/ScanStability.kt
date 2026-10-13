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
 * - **판 전체**: 흔들리는 교점이 [ConfidentRatio] 이하이고 배치가 말이 될 때만 핀 화면을 건너뛴다([canSkipPins]) — 판을 잘못 찾으면 수십 개가 흔들린다.
 *
 * 숫자의 근거(2026-10-13 측정, 기준 이미지 5장 + 변형 30장 — `BoardPhotoReportTest`): 바르게 찾은 판은 흔들리는 교점이 0~28개
 * (한 칸이 15px인 아주 작은 인쇄본만 65~76개), 판을 잘못 찾은 판(인식률 33~70%)은 95~131개였다. 작은 인쇄본의 실제로 틀린 2~3점은 모두 흔들렸다.
 * ⚠️ 0.1칸·8%·20%는 스레드가 정한 값이다 — 사용자 사진(폰 2048px)으로 다시 잰다.
 */
internal object ScanStability {
    const val ShiftCells = 0.1f

    /**
     * 흔들리는 교점이 이 비율 이하라야 확실하다. 처음엔 15%였다 — 인터넷 실물 사진(2026-10-14 갤러리)에서 격자가 판 아래로 한두 줄
     * 밀린 판이 30~39개(8~11%) 흔들리고도 「확실」로 통과했다. 기준 이미지의 바르게 찾은 판은 0~10개(한 칸 15px인 인쇄본 04만 25~47개 —
     * 그 사진은 핀 화면으로 간다).
     */
    const val ConfidentRatio = 0.08f

    /** 판이 사진에서 이만큼(넓이 비율)은 차지해야 확실하다 — 작게 찍힌 판·배경을 판으로 잡은 경우(갤러리 f09)를 거른다. */
    const val MinCoverage = 0.2f

    /** 격자 바깥선 밖으로 이만큼(칸)은 사진 안에 보여야 판이 잘리지 않았다고 본다([showsMarginAllAround]). */
    const val MarginCells = 0.3f

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

    /**
     * 핀 화면을 건너뛰어도 될 만큼 확실한가 — 흔들림이 적고(**[isConfident]**), 읽은 판이 **바둑으로 말이 되고**([isPlausible]),
     * 판이 사진에서 충분히 크고([MinCoverage]), 판이 사진 끝에서 잘리지 않았다([showsMarginAllAround]). 넷 다여야 한다.
     * 흔들림만으로는 못 거르는 틀림이 있다 — 노란 조명 아래 백돌을 **전부** 빈 점으로 읽으면 답이 흔들리지 않는다(갤러리 f30: 흑 134 · 백 0).
     */
    fun canSkipPins(
        unstable: Set<BoardCoordinate>,
        stones: Map<BoardCoordinate, StoneColor>,
        boardSize: BoardSize,
        corners: BoardCornerPoints,
        imageWidth: Int,
        imageHeight: Int,
    ): Boolean = isConfident(unstable, boardSize) && isPlausible(stones, boardSize) &&
        coverage(corners, imageWidth, imageHeight) >= MinCoverage && showsMarginAllAround(corners, boardSize, imageWidth, imageHeight)

    /**
     * 격자 바깥선 밖 [MarginCells]칸이 사방 모두 사진 안에 보이는가 — 판 전체를 찍었으면 바깥선 밖에 판 여백이 보인다. 사진이 격자선에서
     * 잘렸으면 판이 더 이어질 수 있다(갤러리 w05: 19줄 판의 일부를 13줄 판으로 읽고 「확실」이었다). 반 칸으로 두면 바짝 잘라 낸 인쇄 도면
     * (기준 이미지 05)까지 걸려서 0.3칸이다.
     */
    fun showsMarginAllAround(corners: BoardCornerPoints, boardSize: BoardSize, imageWidth: Int, imageHeight: Int): Boolean {
        val last = (boardSize.value - 1).toFloat()
        val grid = listOf(PointF2D(0f, 0f), PointF2D(last, 0f), PointF2D(last, last), PointF2D(0f, last))
        val toImage = BoardHomography.fit(grid, corners.toList()) ?: return false
        val out = -MarginCells.toDouble()
        val far = (last + MarginCells).toDouble()
        val mid = last / 2.0
        return listOf(out to out, mid to out, far to out, far to mid, far to far, mid to far, out to far, out to mid).all { (x, y) ->
            val p = toImage.map(x, y)
            p.x >= 0f && p.y >= 0f && p.x <= imageWidth - 1f && p.y <= imageHeight - 1f
        }
    }

    /**
     * 실제 대국에서 나올 법한 배치인가 — 숨 없는 돌 무리가 없고, 흑백 개수 차이가 접바둑(9점)과 따낸 돌로 설명될 만하다
     * (차이 ≤ max(9, 돌 수의 25%)). ⚠️ 25%는 스레드가 정한 값이다.
     */
    fun isPlausible(stones: Map<BoardCoordinate, StoneColor>, boardSize: BoardSize): Boolean =
        stonesWithoutLiberties(stones, boardSize).isEmpty() && !isColorImbalanced(stones)

    /** 흑백 개수 차이가 상식 밖인가 — 차이 > max(9, 돌 수의 25%). 보정 화면의 경고(한쪽 색을 통째로 놓쳤을 수 있다)도 이것을 쓴다. */
    fun isColorImbalanced(stones: Map<BoardCoordinate, StoneColor>): Boolean {
        val black = stones.values.count { it == StoneColor.Black }
        val white = stones.size - black
        return kotlin.math.abs(black - white) > maxOf(9, (stones.size * 0.25f).toInt())
    }

    /** 모서리 사각형이 사진에서 차지하는 넓이 비율(신발끈 공식). */
    fun coverage(corners: BoardCornerPoints, imageWidth: Int, imageHeight: Int): Float {
        val p = corners.toList()
        var twice = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            twice += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(twice) / 2f / (imageWidth.toFloat() * imageHeight)
    }

    private fun PointF2D.moved(dx: Float, dy: Float) = PointF2D(x + dx, y + dy)
}
