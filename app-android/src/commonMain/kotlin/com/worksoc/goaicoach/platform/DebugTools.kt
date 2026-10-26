package com.worksoc.goaicoach.platform

/**
 * 디버그 무장 파일(백로그 #294 — S15) — 개발 빌드에서 앱 전용 폴더에 [name] 파일이 있으면 지우고 `true`(한 번 쓰고 사라진다).
 * 출시 빌드는 늘 `false`다. 안드로이드는 `files/<name>`(`adb shell touch`로 무장), iOS는 Documents 아래다.
 */
fun interface DebugArmFiles {
    fun consume(name: String): Boolean
}

/** 개발용 로그 한 줄 — 안드로이드는 `Log.i(tag, …)`(`adb logcat -s <tag>`), iOS는 표준 출력이다. */
expect fun debugLog(tag: String, message: String)
