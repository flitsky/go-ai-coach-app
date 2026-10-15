package com.worksoc.goaicoach.vision

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.vision.DetectedBoard
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max

/**
 * 신경망 돌 판정 — 교점 조각([StonePatches])을 빈 점·흑·백으로 가른다(백로그 #263 단계 5). **순수 Kotlin** 계산이다.
 *
 * 실험실 `engine-lab/vision/stone_net.py`의 `StoneNet`(PyTorch)과 같은 계산: 3×3 합성곱(둘레 0 채움) · ReLU · 2×2 최대 풀링을 세 번 →
 * 완전 연결 둘. 가중치 파일(`stone-net.bin`)은 실험실이 쓴다. 대조 테스트(`StoneNetParityTest`)가 파이썬과 같은 로짓을 내는지 본다.
 *
 * 왜 LiteRT가 아닌가: 모델이 작아(가중치 3만 개 남짓 · 130KB) 직접 계산해도 되고, 무엇보다 **맥 JVM 테스트가 앱과 같은 계산**을 한다 —
 * LiteRT는 JVM에서 못 돌아 OpenCV처럼 기록값으로 우회해야 한다(함정 91). 실행 엔진 `.so`도 필요 없다.
 *
 * 앱에서는 **규칙([GridStoneDetector])이 흔들리는 판만** 이것으로 읽는다([BoardCandidates.read] · 함정 97). 2026-10-15 사용자 사진 22장(사용자가 고른 것)을 더해
 * 학습하니 처음 보는 날의 실물 사진에서 규칙 94.7% → 99.9%였지만, 처음 보는 **그림 양식**(기준 이미지 03·05 · 기보 w48)에서는 규칙보다 못했다.
 * 그림·화면 캡처는 규칙이 흔들림 없이 읽는다 — 그래서 판마다 고른다.
 */
internal class StoneNet private constructor(
    private val size: Int,
    private val c1: Int,
    private val c2: Int,
    private val c3: Int,
    private val hidden: Int,
    private val w1: FloatArray,
    private val b1: FloatArray,
    private val w2: FloatArray,
    private val b2: FloatArray,
    private val w3: FloatArray,
    private val b3: FloatArray,
    private val f1w: FloatArray,
    private val f1b: FloatArray,
    private val f2w: FloatArray,
    private val f2b: FloatArray,
) {
    /** 조각 하나(채널 우선 3 × [size] × [size], [StonePatches.Patches.normalized])의 로짓 — 빈 점 · 흑 · 백. */
    fun logits(input: FloatArray): FloatArray {
        require(input.size == 3 * size * size) { "조각 크기가 다르다: ${input.size}" }
        var s = size
        var x = reluPool(conv(input, 3, s, w1, b1, c1), c1, s)
        s /= 2
        x = reluPool(conv(x, c1, s, w2, b2, c2), c2, s)
        s /= 2
        x = reluPool(conv(x, c2, s, w3, b3, c3), c3, s)
        val h = dense(x, f1w, f1b, hidden)
        for (i in h.indices) h[i] = max(0f, h[i])
        return dense(h, f2w, f2b, 3)
    }

    /** 세 갈래(빈 점 · 흑 · 백)의 확률 — 로짓의 소프트맥스. */
    fun probabilities(input: FloatArray): FloatArray {
        val z = logits(input)
        val top = z.max()
        val e = FloatArray(z.size) { exp(z[it] - top) }
        val sum = e.sum()
        return FloatArray(e.size) { e[it] / sum }
    }

    /** 판 하나의 교점마다 가장 그럴듯한 답 — 확률이 [DoubtfulBelow] 미만인 교점은 「확인 필요」로 함께 낸다. */
    fun detect(board: RectifiedBoard): Detection {
        val n = board.boardSize.value
        val patches = StonePatches.extract(board)
        val stones = HashMap<BoardCoordinate, StoneColor>()
        val doubtful = HashSet<BoardCoordinate>()
        for (row in 0 until n) {
            for (col in 0 until n) {
                val p = probabilities(patches.normalized(row * n + col))
                val best = p.indices.maxBy { p[it] }
                val at = BoardCoordinate(row = row, column = col)
                when (best) {
                    1 -> stones[at] = StoneColor.Black
                    2 -> stones[at] = StoneColor.White
                }
                if (p[best] < DoubtfulBelow) doubtful += at
            }
        }
        return Detection(DetectedBoard(boardSize = board.boardSize, stones = stones, confidence = 0.95f), doubtful)
    }

    /** [detect]의 결과 — 돌 배치와 확률이 낮은 교점(보정 화면의 「확인 필요」). */
    class Detection(val detected: DetectedBoard, val doubtful: Set<BoardCoordinate>)

    /**
     * 3×3 합성곱, 둘레 0 채움 — 출력 크기 = 입력 크기. 가중치는 PyTorch 순서 [출력][입력][3][3].
     * 입력을 먼저 「자리마다 3×3 이웃」 줄로 펴 두고(im2col) 출력 채널마다 그 줄들을 가중합한다 — 가장 안쪽 반복이 길고 연속이라 빠르다
     * (19줄 판 하나 — 교점 361개 — 맥 JVM 0.41 → 0.24초. 기기 속도는 S23에서 잰다, 함정 86).
     */
    private fun conv(x: FloatArray, cin: Int, s: Int, w: FloatArray, b: FloatArray, cout: Int): FloatArray {
        val hw = s * s
        val k = cin * 9
        val col = FloatArray(k * hw)
        for (c in 0 until cin) {
            for (ky in 0..2) {
                for (kx in 0..2) {
                    val row = (c * 9 + ky * 3 + kx) * hw
                    val dy = ky - 1
                    val dx = kx - 1
                    for (y in max(0, -dy) until minOf(s, s - dy)) {
                        val from = c * hw + (y + dy) * s + dx
                        val to = row + y * s
                        for (xx in max(0, -dx) until minOf(s, s - dx)) col[to + xx] = x[from + xx]
                    }
                }
            }
        }
        val out = FloatArray(cout * hw)
        for (o in 0 until cout) {
            val base = o * hw
            val bias = b[o]
            for (p in 0 until hw) out[base + p] = bias
            val wBase = o * k
            for (kk in 0 until k) {
                val wv = w[wBase + kk]
                val cBase = kk * hw
                for (p in 0 until hw) out[base + p] += wv * col[cBase + p]
            }
        }
        return out
    }

    /** ReLU 뒤 2×2 최대 풀링(보폭 2) — ReLU와 최댓값은 순서를 바꿔도 같다. */
    private fun reluPool(x: FloatArray, c: Int, s: Int): FloatArray {
        val h = s / 2
        val out = FloatArray(c * h * h)
        for (ch in 0 until c) {
            for (y in 0 until h) {
                for (xx in 0 until h) {
                    val i = ch * s * s + 2 * y * s + 2 * xx
                    val m = maxOf(maxOf(x[i], x[i + 1]), maxOf(x[i + s], x[i + s + 1]))
                    out[ch * h * h + y * h + xx] = max(0f, m)
                }
            }
        }
        return out
    }

    /** 완전 연결 — 가중치는 PyTorch 순서 [출력][입력]. */
    private fun dense(x: FloatArray, w: FloatArray, b: FloatArray, out: Int): FloatArray =
        FloatArray(out) { o ->
            var sum = b[o]
            val base = o * x.size
            for (i in x.indices) sum += w[base + i] * x[i]
            sum
        }

    companion object {
        /** 앱에 실린 가중치(`src/main/resources/vision/stone-net.bin` — 실험실 `stone_net.py --export`가 쓴다). JVM 테스트도 같은 파일을 읽는다. */
        const val ResourcePath = "vision/stone-net.bin"

        /**
         * 확률이 이것보다 낮으면 「확인 필요」. ⚠️ 스레드가 정한 값이다 — 교차 검증(처음 보는 묶음)에서 신경망이 틀린 교점의 대부분이 이 아래였고,
         * 맞은 교점은 거의 걸리지 않았다(`engine-lab/vision/README.md` 「신경망 돌 판정」).
         */
        const val DoubtfulBelow = 0.9f

        /** 실린 모델 — 못 읽으면 `null`(규칙만 쓴다). 처음 부를 때 한 번 읽는다. */
        val bundled: StoneNet? by lazy {
            runCatching { StoneNet::class.java.classLoader?.getResourceAsStream(ResourcePath)?.use { load(it.readBytes()) } }.getOrNull()
        }

        /** 실험실이 쓴 가중치 파일(`STN1`) — 머리(판 번호 · 조각 크기 · 채널 폭) 뒤 float32 리틀 엔디언 배열들. */
        fun load(bytes: ByteArray): StoneNet {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(String(magic) == "STN1") { "돌 판정 모델 파일이 아니다" }
            require(buf.int == 1) { "모르는 모델 판" }
            val size = buf.int
            val c1 = buf.int
            val c2 = buf.int
            val c3 = buf.int
            val hidden = buf.int
            require(size == StonePatches.Size) { "조각 크기가 다르다: $size" }
            fun floats(n: Int) = FloatArray(n).also { a -> for (i in a.indices) a[i] = buf.float }
            val flat = c3 * (size / 8) * (size / 8)
            return StoneNet(
                size, c1, c2, c3, hidden,
                floats(c1 * 3 * 9), floats(c1),
                floats(c2 * c1 * 9), floats(c2),
                floats(c3 * c2 * 9), floats(c3),
                floats(hidden * flat), floats(hidden),
                floats(3 * hidden), floats(3),
            ).also { require(!buf.hasRemaining()) { "모델 파일 끝에 남는 바이트가 있다" } }
        }
    }
}
