package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.DetectedBoard

/**
 * 판 찾기 후보를 견줘 하나를 고른다(백로그 #263) — 앱([AndroidBoardVisionScanner])과 JVM 테스트가 **같은 길**을 탄다.
 *
 * 1. 원근 틀이 있으면 펴고 찾고([PerspectiveLocate]), 없으면 그대로 찾는다([BoardLocator]). 그 판독이 확실하면([ScanStability.canSkipPins]) 끝.
 * 2. 아니면 후보를 더 만든다 — **펴지 않고 그대로 찾은 것**, 편 사진의 칸이 납작하면 **정사각이 되게 세로로 늘려 찾은 것**.
 * 3. 격자선이 판 밖으로 이어지지 않는 후보 중([BoardLocator.continuation]) 모서리를 흔들면 답이 바뀌는 교점의 비율([ScanStability.unstablePoints])이
 *    가장 낮은 후보 — 판을 바르게 찾았으면 흔들려도 같은 답이 나온다.
 *
 * 왜: ① 원근 틀은 사진 전체의 선으로 구하므로, 판이 큰 장면의 작은 일부면 방·바닥의 선이 틀을 정해 판을 일그러뜨린다 — 갤러리 f19(사진의 5%인
 * 9줄 판)는 펴면 사진 전체를 19줄로 읽었고 그대로 찾으면 정확했다. ② 편 사진은 가로세로 비율이 안 맞아 칸이 납작할 수 있고, 판 찾기는
 * 두 축 간격이 크게 다르면 한 축을 의심해 정사각으로 덮어쓴다 — 갤러리 f03(펴면 칸이 0.77배로 납작)은 격자가 위아래로 4줄 늘었다.
 * 확실한 판독이면 후보를 더 만들지 않는다 — 잘 되는 사진은 느려지지 않는다.
 *
 * 돌은 판마다 고른 판정으로 읽는다([read]) — 규칙이 흔들리지 않는 판은 규칙, 흔들리는 판(실물 사진)은 신경망([StoneNet] · 함정 97).
 * 후보를 견주는 잣대(흔들림)는 늘 규칙의 것이다 — 판을 바르게 찾았나를 재는 값이라 판정을 바꿔도 같아야 한다.
 */
internal object BoardCandidates {

    /** 판독이 어디서 왔나 — 펴고 찾음 · 그대로 찾음 · 편 사진을 늘려 찾음 · 격자로 펴고 잘라 찾음 · 손으로 놓은 핀. */
    enum class Kind { Flattened, Direct, Stretched, Lattice, Manual }

    /**
     * 후보 하나의 판독 — 찾은 판 · 흔들리는 교점 · 돌 · 「확인 필요」 · 핀 화면을 건너뛸 만큼 확실한가.
     * 신경망 판독은 처음 물을 때 한 번 한다 — 후보를 견줄 때는 흔들림만 보므로, 고른 판 하나만 신경망을 돌린다.
     */
    class Reading(
        val kind: Kind,
        val located: LocatedBoard,
        /** 규칙 판정([GridStoneDetector])을 모서리째 흔들면 답이 바뀌는 교점([ScanStability.unstablePoints]) — 판을 바르게 찾았나의 잣대. */
        val unstable: Set<BoardCoordinate>,
        /** 돌을 신경망이 읽는가 — 규칙이 흔들리는 판([ScanStability.rulesAreSteady]가 아닌 판)이고 모델이 실렸을 때. */
        val byNet: Boolean,
        private val ruled: DetectedBoard,
        private val net: Lazy<StoneNet.Detection?>,
        private val canSkipPins: (Map<BoardCoordinate, StoneColor>) -> Boolean,
    ) {
        val unstableRatio: Float get() = unstable.size.toFloat() / (located.boardSize.value * located.boardSize.value)

        /** 돌 배치 — 규칙이 흔들리는 판은 신경망, 아니면 규칙. */
        val detected: DetectedBoard get() = net.value?.detected ?: ruled

        /** 보정 화면의 「확인 필요」 — 신경망 판독은 확률이 낮은 교점, 규칙 판독은 흔들리는 교점. */
        val uncertain: Set<BoardCoordinate> get() = net.value?.doubtful ?: unstable

        /** 핀 화면을 건너뛸 만큼 확실한가([ScanStability.canSkipPins]) — 흔들림이 많으면 돌을 읽기 전에 아니라고 답한다. */
        val confident: Boolean by lazy { ScanStability.isConfident(unstable, located.boardSize) && canSkipPins(detected.stones) }
    }

    /** 편 사진의 칸이 이 범위 밖으로 납작해야 늘린 후보를 만든다. ⚠️ 0.85~1.18 · 0.55~1.8은 스레드가 정한 값이다(f03을 펴면 0.77). */
    private val SquareEnough = 0.85f..1.18f
    private val PlausibleAspect = 0.55f..1.8f

    fun locate(source: PixelSource, frame: PerspectiveFrame?, lattice: (() -> LatticeFrame?)? = null): Reading? =
        choose(source, candidates(source, frame, lattice))

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

    /**
     * 격자선이 판 밖으로 **이어지지 않는** 후보([BoardLocator.continuation] ≤ [MaxContinuation]) 중 흔들림이 가장 적은 것 — 같으면 앞의 것(펴고 찾은 것).
     * 모두 이어지면 전체에서 고른다.
     *
     * ⚠️ 흔들림만 보면 진짜 판 안쪽의 작은 조각이 이긴다 — 조각도 흔들리지 않는다. 갤러리 w13: 19줄 판(흔들림 5.5%) 대신 판 안의 13줄 조각(5.3%),
     * w52: 19줄 기보 대신 9줄 조각. 진짜 판의 바깥선 밖은 나무 여백·탁자라 선이 없고(정답 후보 52개: 이어짐 중앙값 0.07 · 최대 0.41), 조각은 선이 이어진다(중앙값 0.60).
     * ⚠️ 0.4는 스레드가 정한 값이다.
     */
    fun choose(source: PixelSource, readings: List<Reading>): Reading? {
        if (readings.size <= 1) return readings.firstOrNull()
        val closed = readings.filter { BoardLocator.continuation(source, it.located.corners, it.located.boardSize) <= MaxContinuation }
        return closed.ifEmpty { readings }.minByOrNull { it.unstableRatio }
    }

    private const val MaxContinuation = 0.4f

    /**
     * 찾은 판의 모서리로 돌을 읽고 흔들어 본다 — 앱의 보정 화면이 쓰는 것과 같은 값이다.
     * 규칙이 흔들리는 판이면 돌은 신경망이 읽는다([ScanStability.RulesSteadyRatio]) — 모델이 없으면(못 읽었으면) 규칙 그대로.
     */
    fun read(kind: Kind, source: PixelSource, located: LocatedBoard, net: StoneNet? = StoneNet.bundled): Reading {
        val board = BoardWarp.rectify(source, located.corners, located.boardSize)
        val ruled = GridStoneDetector.detect(board)
        val unstable = ScanStability.unstablePoints(source, located.corners, located.boardSize, ruled.stones)
        val byNet = net != null && !ScanStability.rulesAreSteady(unstable, located.boardSize)
        return Reading(
            kind = kind,
            located = located,
            unstable = unstable,
            byNet = byNet,
            ruled = ruled,
            net = lazy { if (byNet) net!!.detect(BoardWarp.rectify(source, located.corners, located.boardSize)) else null },
            canSkipPins = { stones ->
                ScanStability.canSkipPins(unstable, stones, located.boardSize, located.corners, source.width, source.height)
            },
        )
    }
}
