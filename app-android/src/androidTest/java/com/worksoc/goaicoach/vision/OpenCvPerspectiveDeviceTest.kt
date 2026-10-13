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
 * 앱의 OpenCV 원근 틀([OpenCvPerspective])이 실험실(`engine-lab/vision/lab/perspective.py`)과 **같은 틀**을 내는가(백로그 #263).
 *
 * 이미지는 저장소 밖이다 — 실험실이 `measure_perspective.py --export`로 `test-fixtures/board-photos/variants/`에 쓴 것을 기기로 올린다:
 * ```
 * adb push test-fixtures/board-photos/variants/. /sdcard/Android/data/<패키지>/files/board-photo-variants/
 * ```
 * 없으면 건너뛴다. 기대값은 단위 테스트와 같은 `board-photos/perspective-frames.json`(androidTest가 test 리소스를 함께 읽는다).
 * 틀의 네 꼭짓점이 펼 크기의 3% 안이면 같다고 본다 — 난수가 달라 RANSAC 표본이 다르다.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvPerspectiveDeviceTest {

    @Test
    fun framesMatchTheLab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "board-photo-variants")
        val images = dir.listFiles { f -> f.extension == "png" }.orEmpty()
        assumeTrue("변형 이미지가 없다 — $dir 로 adb push", images.isNotEmpty())
        assertTrue("OpenCV를 못 올렸다", OpenCvRuntime.isReady)
        val expected = JSONObject(javaClass.classLoader!!.getResource("board-photos/perspective-frames.json").readText())
        val failures = mutableListOf<String>()
        images.sortedBy { it.name }.forEach { file ->
            val key = file.nameWithoutExtension
            val bitmap = BitmapFactory.decodeFile(file.path)
            val started = System.nanoTime()
            val frame = OpenCvPerspective.frameOf(bitmap)
            val ms = (System.nanoTime() - started) / 1_000_000
            val want = expected.optJSONObject(key)
            println("원근 틀 $key: ${if (frame == null) "없음" else "${frame.width}x${frame.height}"} · 실험실 ${if (want == null) "없음" else "있음"} · ${ms}ms")
            when {
                want == null && frame != null -> failures += "$key 실험실은 펴지 않는데 앱은 폈다"
                want != null && frame == null -> failures += "$key 실험실은 폈는데 앱은 못 폈다"
                want != null && frame != null -> {
                    val quad = want.getJSONArray("quad")
                    val size = want.getJSONArray("size")
                    val tolerance = 0.03 * maxOf(size.getInt(0), size.getInt(1))
                    frame.quad.toList().forEachIndexed { i, p ->
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
