package com.worksoc.goaicoach.vision

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * 신경망 돌 판정 — 교점 조각([StonePatches])을 빈 점·흑·백으로 가른다(백로그 #263 단계 5). **순수 Kotlin** 계산이다.
 *
 * 실험실 `engine-lab/vision/stone_net.py`의 `StoneNet`(PyTorch)과 같은 계산: 3×3 합성곱(둘레 0 채움) · ReLU · 2×2 최대 풀링을 세 번 →
 * 완전 연결 둘. 가중치 파일(`stone-net.bin`)은 실험실이 쓴다. 대조 테스트(`StoneNetParityTest`)가 파이썬과 같은 로짓을 내는지 본다.
 *
 * 왜 LiteRT가 아닌가: 모델이 작아(가중치 1만 개 남짓) 직접 계산해도 빠르고, 무엇보다 **맥 JVM 테스트가 앱과 같은 계산**을 한다 —
 * LiteRT는 JVM에서 못 돌아 OpenCV처럼 기록값으로 우회해야 한다(함정 91). 실행 엔진 `.so`도 필요 없다.
 *
 * ⚠️ 2026-10-14 기준 **앱의 판정 경로에 쓰이지 않는다** — 묶음 교차 검증에서 규칙([GridStoneDetector])보다 낮았다(정답 모서리 97.3% 대 97.8%,
 * 앱 모서리 98.6% 대 99.5% — 대국 23묶음이라 그림 양식마다 한 장뿐). 사용자 사진(S23)으로 다시 학습해 규칙을 넘으면 연결한다.
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

    /** 3×3 합성곱, 둘레 0 채움 — 출력 크기 = 입력 크기. 가중치는 PyTorch 순서 [출력][입력][3][3]. */
    private fun conv(x: FloatArray, cin: Int, s: Int, w: FloatArray, b: FloatArray, cout: Int): FloatArray {
        val out = FloatArray(cout * s * s)
        for (o in 0 until cout) {
            val base = o * s * s
            for (i in 0 until s * s) out[base + i] = b[o]
            for (c in 0 until cin) {
                val wBase = (o * cin + c) * 9
                val xBase = c * s * s
                for (ky in 0..2) {
                    for (kx in 0..2) {
                        val wv = w[wBase + ky * 3 + kx]
                        val dy = ky - 1
                        val dx = kx - 1
                        for (y in max(0, -dy) until minOf(s, s - dy)) {
                            val row = xBase + (y + dy) * s
                            val outRow = base + y * s
                            for (xx in max(0, -dx) until minOf(s, s - dx)) out[outRow + xx] += wv * x[row + xx + dx]
                        }
                    }
                }
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
