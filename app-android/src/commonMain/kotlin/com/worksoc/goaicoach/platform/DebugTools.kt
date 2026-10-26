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

/**
 * 개발자 메뉴의 손잡이(백로그 #294 — S25 · S26). 안드로이드는 오늘의 함수(`DeveloperModeResetCoordinator.kt` · `ReleaseResetCoordinator.kt` ·
 * `AdsConsentManager` · 진단 로그 파일)를 앱 문맥으로 부른다. iOS는 같은 이름의 저장소를 지우고, 프로세스를 다시 띄우지는 않는다.
 */
interface DeveloperTools {
    /** 「새로 설치한 것처럼」 — 앱 설정 · 진행도 · 대국 기록을 지운다(기기 id는 남긴다). */
    fun wipeToFreshInstall()

    /** 지우고 앱을 처음부터 다시 띄운다(안드로이드). */
    fun restartToFreshInstall()

    /** 출시 초기화를 한 번 더 돌린다 — 지웠으면 `true`. */
    fun runReleaseResetAgain(): Boolean

    fun resetAdConsent()

    /** 진단 로그 전체(뒤쪽만 보여 주는 것은 화면 몫). */
    fun diagnosticLogText(): String
}

/** 진단 리포트에 싣는 기기 정보(백로그 #294 — S26) — 리포트를 뽑는 그 순간에 읽는다. 안드로이드는 진동기 진단 · 빌드 스탬프. */
interface DebugReportPlatformInfo {
    fun hapticDiagnostic(): String

    fun buildStamp(): String
}
