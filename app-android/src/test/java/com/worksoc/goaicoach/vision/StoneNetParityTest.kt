package com.worksoc.goaicoach.vision

import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱의 신경망 계산([StoneNet] — 순수 Kotlin)이 실험실(PyTorch, `engine-lab/vision/stone_net.py`)과 **같은 로짓**을 내는가(백로그 #263 단계 5).
 *
 * 가중치(앱에 실린 `vision/stone-net.bin` — [StoneNet.bundled]가 읽는 그 파일)와 대조 벡터(`vision/stone-net-parity.json` — 빈 점·흑·백 조각 넷씩과 그 로짓)는 실험실이 `--export`로 함께 쓴다.
 * 둘이 어긋나면 맥에서 잰 성적이 앱의 성적이 아니다.
 */
class StoneNetParityTest {

    @Test
    fun kotlinMatchesPyTorch() {
        val loader = javaClass.classLoader!!
        val net = requireNotNull(StoneNet.bundled) { "앱에 실린 모델을 못 읽었다: ${StoneNet.ResourcePath}" }
        val parity = JSONObject(requireNotNull(loader.getResource("vision/stone-net-parity.json")).readText())
        val inputs = parity.getJSONArray("inputs")
        val expected = parity.getJSONArray("logits")
        assertEquals(inputs.length(), expected.length())
        var worst = 0.0
        for (k in 0 until inputs.length()) {
            val arr = inputs.getJSONArray(k)
            val x = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
            val got = net.logits(x)
            val want = expected.getJSONArray(k)
            for (c in 0..2) worst = maxOf(worst, abs(got[c] - want.getDouble(c)))
            val argGot = got.indices.maxBy { got[it] }
            val argWant = (0..2).maxBy { want.getDouble(it) }
            assertEquals("조각 $k 판정", argWant, argGot)
        }
        assertTrue("로짓이 최대 $worst 어긋났다", worst < 1e-3)
    }
}
