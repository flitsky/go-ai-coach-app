package com.worksoc.goaicoach.vision

import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV 네이티브 라이브러리를 **처음 쓸 때 한 번** 올린다(백로그 #263).
 *
 * ⚠️ 앱 시작에서 올리지 않는다 — 25MB짜리 `.so`라 「바둑판 사진 분석」을 열지 않는 사람에게는 쓸데없는 비용이다.
 * 못 올리면 `false` — 부르는 쪽은 지금의 순수 Kotlin 경로로 물러선다(인식이 멈추면 안 된다).
 */
internal object OpenCvRuntime {
    val isReady: Boolean by lazy {
        runCatching { OpenCVLoader.initLocal() }
            .onFailure { Log.w("OpenCvRuntime", "OpenCV를 올리지 못했다", it) }
            .getOrDefault(false)
    }
}
