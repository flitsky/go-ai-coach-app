package com.worksoc.goaicoach.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 착수 진동(#36)을 한 군데서 정의한다 — 반상과 설정 토글이 **같은 세기**를 써야 하기 때문이다.
 * 토글을 켤 때 울리는 진동이 곧 "앞으로 이만큼 울린다"는 미리듣기 역할을 한다.
 *
 * **세기를 올렸다**: 처음에는 [HapticFeedbackType.TextHandleMove]였는데 체감되지 않는다는
 * 피드백을 받았다(2026-08-30). 이 Compose 버전(BOM 2025.04.01)의 `HapticFeedbackType`은
 * `TextHandleMove`와 `LongPress` 둘뿐이고, `LongPress`가 프리베이크 `HEAVY_CLICK`으로
 * 내려간다 — 프레임워크가 정의한 세 단계(`TICK` < `CLICK` < `HEAVY_CLICK`) 중 가장 강하다.
 *
 * ⚠️ **`Vibrator`를 직접 쓰는 길로 새지 말 것 — 이미 시도했다가 되돌렸다.**
 * `VibrationEffect.EFFECT_HEAVY_CLICK`으로 가 보니 `dumpsys vibrator_manager`가 이 경로와
 * **완전히 같은 효과**(`Prebaked=HEAVY_CLICK`)를 재생하고 있었다. 즉 얻는 것은 없고
 * `android.permission.VIBRATE`(스토어 권한 목록에 노출된다)와 시스템 햅틱 설정을 직접
 * 확인하는 코드만 늘어난다. 이쪽은 프레임워크가 그 존중을 대신해 준다.
 *
 * ⚠️ **세기를 측정할 때 logcat만 보지 마라.** `VibratorManagerService`는 재생에 **실패했을
 * 때만** 경고를 남긴다(*"vibration absent for constant 9"*). 성공한 진동은 logcat에 아무것도
 * 남기지 않고 `dumpsys vibrator_manager`의 *Recent vibrations*에만 기록된다 — 이걸 몰라서
 * 멀쩡히 동작하던 `LongPress`를 "디스패치조차 안 된다"고 오판했다.
 */
internal fun HapticFeedback.performPlayHaptic() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}
