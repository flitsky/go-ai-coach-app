package com.worksoc.goaicoach.platform

/**
 * 바둑판 사진 분석의 입구(백로그 #294 — S18). 안드로이드만 있다(카메라 · OpenCV) — iOS 첫 판은 사진 분석을 싣지 않는다(U-84)라
 * `AppPlatform.boardScan`이 `null`이고, 그러면 홈의 카메라 단추 · 실험실 타일이 감춰진다.
 */
interface BoardScanEntry {
    /** 다른 앱에서 「공유」로 받은 바둑판 사진이 기다리는가(#263). */
    fun hasPendingSharedPhoto(): Boolean
}
