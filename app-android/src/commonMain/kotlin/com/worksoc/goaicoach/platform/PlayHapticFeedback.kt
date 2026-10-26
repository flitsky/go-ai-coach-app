package com.worksoc.goaicoach.platform

/**
 * 착수 진동(백로그 #294 — S10). 안드로이드는 `PlayHaptics`(진동기 — 세기와 근거는 그 파일), iOS는 UIKit 햅틱이다.
 * [play]는 돌을 놓았을 때, [playInvalid]는 놓을 수 없는 자리를 눌렀을 때(짧게 두 번).
 */
interface PlayHapticFeedback {
    fun play()

    fun playInvalid()
}
