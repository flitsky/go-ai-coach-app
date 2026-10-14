package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.vision.DetectedBoard

/**
 * 판 찾기 후보를 견줘 하나를 고른다(백로그 #263) — 앱([AndroidBoardVisionScanner])과 JVM 테스트가 **같은 길**을 탄다.
 *
 * 1. 원근 틀이 있으면 펴고 찾고([PerspectiveLocate]), 없으면 그대로 찾는다([BoardLocator]). 그 판독이 확실하면([ScanStability.canSkipPins]) 끝.
 * 2. 아니면 후보를 더 만든다 — **펴지 않고 그대로 찾은 것**, 편 사진의 칸이 납작하면 **정사각이 되게 세로로 늘려 찾은 것**.
 * 3. 모서리를 흔들면 답이 바뀌는 교점의 비율([ScanStability.unstablePoints])이 가장 낮은 후보 — 판을 바르게 찾았으면 흔들려도 같은 답이 나온다.
 *
 * 왜: ① 원근 틀은 사진 전체의 선으로 구하므로, 판이 큰 장면의 작은 일부면 방·바닥의 선이 틀을 정해 판을 일그러뜨린다 — 갤러리 f19(사진의 5%인
 * 9줄 판)는 펴면 사진 전체를 19줄로 읽었고 그대로 찾으면 정확했다. ② 편 사진은 가로세로 비율이 안 맞아 칸이 납작할 수 있고, 판 찾기는
 * 두 축 간격이 크게 다르면 한 축을 의심해 정사각으로 덮어쓴다 — 갤러리 f03(펴면 칸이 0.77배로 납작)은 격자가 위아래로 4줄 늘었다.
 * 확실한 판독이면 후보를 더 만들지 않는다 — 잘 되는 사진은 느려지지 않는다.
 */
internal object BoardCandidates {

    /** 판독이 어디서 왔나 — 펴고 찾음 · 그대로 찾음 · 편 사진을 늘려 찾음 · 격자로 펴고 잘라 찾음 · 손으로 놓은 핀. */
    enum class Kind { Flattened, Direct, Stretched, Lattice, Manual }

    /** 후보 하나의 판독 — 찾은 판 · 돌 · 흔들리는 교점 · 핀 화면을 건너뛸 만큼 확실한가. */
    class Reading(
        val kind: Kind,
        val located: LocatedBoard,
        val detected: DetectedBoard,
        val unstable: Set<BoardCoordinate>,
        val confident: Boolean,
    ) {
        val unstableRatio: Float get() = unstable.size.toFloat() / (located.boardSize.value * located.boardSize.value)
    }

    /** 편 사진의 칸이 이 범위 밖으로 납작해야 늘린 후보를 만든다. ⚠️ 0.85~1.18 · 0.55~1.8은 스레드가 정한 값이다(f03을 펴면 0.77). */
    private val SquareEnough = 0.85f..1.18f
    private val PlausibleAspect = 0.55f..1.8f

    fun locate(source: PixelSource, frame: PerspectiveFrame?, lattice: (() -> LatticeFrame?)? = null): Reading? =
        choose(candidates(source, frame, lattice))

    /** 후보를 만드는 순서대로 — 첫 판독이 확실하면 그것 하나뿐이다. */
    fun candidates(source: PixelSource, frame: PerspectiveFrame?, lattice: (() -> LatticeFrame?)? = null): List<Reading> {
        val flattened = frame?.let { PerspectiveLocate.locate(source, it) }
        val direct by lazy { BoardLocator.locate(source) }
        val first = flattened?.let { read(Kind.Flattened, source, it) } ?: direct?.let { read(Kind.Direct, source, it) }
        if (first?.confident == true) return listOf(first)
        return buildList {
            first?.let { add(it) }
            if (flattened != null) {
                direct?.let { add(read(Kind.Direct, source, it)) }
                val aspect = frame.let { f -> PerspectiveLocate.flatten(source, f)?.let { (flat, _) -> BoardLocator.cellAspect(flat) } }
                if (aspect != null && aspect !in SquareEnough && aspect in PlausibleAspect) {
                    PerspectiveLocate.locateStretched(source, frame, 1f / aspect)?.let { add(read(Kind.Stretched, source, it)) }
                }
            }
            lattice?.invoke()?.let { f -> PerspectiveLocate.locateLattice(source, f) }?.let { add(read(Kind.Lattice, source, it)) }
        }
    }

    /** 흔들림이 가장 적은 후보 — 같으면 앞의 것(펴고 찾은 것)을 남긴다. */
    fun choose(readings: List<Reading>): Reading? = readings.minByOrNull { it.unstableRatio }

    /** 찾은 판의 모서리로 돌을 읽고 흔들어 본다 — 앱의 보정 화면이 쓰는 것과 같은 값이다. */
    fun read(kind: Kind, source: PixelSource, located: LocatedBoard): Reading {
        val detected = GridStoneDetector.detect(BoardWarp.rectify(source, located.corners, located.boardSize))
        val unstable = ScanStability.unstablePoints(source, located.corners, located.boardSize, detected.stones)
        val confident = ScanStability.canSkipPins(unstable, detected.stones, located.boardSize, located.corners, source.width, source.height)
        return Reading(kind, located, detected, unstable, confident)
    }
}
