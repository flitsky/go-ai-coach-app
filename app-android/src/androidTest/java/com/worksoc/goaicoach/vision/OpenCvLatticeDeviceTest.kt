package com.worksoc.goaicoach.vision

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.hypot
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 앱의 격자 틀([OpenCvLattice])이 실험실(`engine-lab/vision/lab/lattice.py`)과 **같은 틀**을 내는가(백로그 #263).
 *
 * 이미지는 [OpenCvPerspectiveDeviceTest]와 같은 변형 30장이다(저장소 밖 — `measure_perspective.py --export`가 쓴 `test-fixtures/board-photos/variants/`를
 * `adb push … /sdcard/Android/data/<패키지>/files/board-photo-variants/`). 없으면 건너뛴다. 기대값은 단위 테스트와 같은 `board-photos/lattice-frames.json`.
 * 같다고 보는 기준: 있음/없음이 같고, 찾은 줄 수가 같고, 틀의 네 꼭짓점이 원본 긴 변의 2% 안. 무작위가 없어 원래는 거의 같은 자리에 온다.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvLatticeDeviceTest {

    @Test
    fun framesMatchTheLab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "board-photo-variants")
        val images = dir.listFiles { f -> f.extension == "png" }.orEmpty()
        assumeTrue("변형 이미지가 없다 — $dir 로 adb push", images.isNotEmpty())
        assertTrue("OpenCV를 못 올렸다", OpenCvRuntime.isReady)
        val expected = JSONObject(javaClass.classLoader!!.getResource("board-photos/lattice-frames.json").readText())
        val failures = mutableListOf<String>()
        images.sortedBy { it.name }.forEach { file ->
            val key = file.nameWithoutExtension
            val bitmap = BitmapFactory.decodeFile(file.path)
            val started = System.nanoTime()
            val frame = OpenCvLattice.frameOf(bitmap)
            val ms = (System.nanoTime() - started) / 1_000_000
            val want = expected.optJSONObject(key)
            println("격자 틀 $key: ${frame?.let { "${it.cols}x${it.rows}줄" } ?: "없음"} · 실험실 ${want?.getJSONArray("lines") ?: "없음"} · ${ms}ms")
            when {
                want == null && frame != null -> failures += "$key 실험실은 못 찾는데 앱은 찾았다"
                want != null && frame == null -> failures += "$key 실험실은 찾았는데 앱은 못 찾았다"
                want != null && frame != null -> {
                    val lines = want.getJSONArray("lines")
                    if (lines.getInt(0) != frame.cols || lines.getInt(1) != frame.rows) {
                        failures += "$key 줄 수가 다르다(앱 ${frame.cols}x${frame.rows} · 실험실 $lines)"
                    }
                    val quad = want.getJSONArray("quad")
                    val tolerance = 0.02 * maxOf(bitmap.width, bitmap.height)
                    frame.frame.quad.toList().forEachIndexed { i, p ->
                        val q = quad.getJSONArray(i)
                        val d = hypot(p.x - q.getDouble(0), p.y - q.getDouble(1))
                        if (d > tolerance) failures += "$key 꼭짓점 $i 가 ${"%.1f".format(d)}px 어긋났다(허용 ${"%.1f".format(tolerance)})"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
