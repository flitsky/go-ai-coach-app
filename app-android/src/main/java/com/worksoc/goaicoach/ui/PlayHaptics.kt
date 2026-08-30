package com.worksoc.goaicoach.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * 착수 진동(#36). 반상과 설정 토글이 **같은 세기**를 써야 해서 한 군데로 모았다 — 토글을 켤 때
 * 울리는 진동이 곧 "앞으로 이만큼 울린다"는 미리듣기다.
 *
 * ## 왜 프레임워크 햅틱을 버렸는가 (2026-08-30 사용자 결정)
 *
 * `View.performHapticFeedback`은 [Settings.System.HAPTIC_FEEDBACK_ENABLED]가 꺼져 있으면
 * 조용히 삼켜진다. 사용자 폰이 정확히 그 상태였다 — 진단상 `attempts=9 accepted=9`인데도
 * 아무 진동이 없었고 `systemHapticFeedbackEnabled=false`였다.
 *
 * 그 설정은 **"터치 피드백"**(키보드 탭 등)을 가리킨다. 사용자가 그걸 끈 이유는 보통 키보드
 * 진동이 싫어서이지 이 앱을 조용히 시키려던 게 아니고, 이 앱에는 **전용 토글이 따로 있어
 * 명시적 의사가 이미 표현돼 있다.** 그래서 [Vibrator]로 직접 울린다 — 게임이 의도적으로 내는
 * 진동은 원래 이쪽 소관이다.
 *
 * ⚠️ 대신 **앱 토글이 유일한 관문이 된다.** 호출부(`GoBoard`, `KaTrainUxPanels`)가
 * `isPlayHapticEnabled`를 확인하고 부르므로, 여기서 시스템 설정을 다시 보지 않는다.
 * 이 결정을 되돌리려면 [play] 안에서 [Settings.System.HAPTIC_FEEDBACK_ENABLED]를 확인하면 된다.
 *
 * ⚠️ `android.permission.VIBRATE`가 필요하다(normal 권한, 런타임 프롬프트 없음). 다만
 * **스토어 권한 목록에 노출**되므로 등록정보와 어긋나지 않는지 확인할 것.
 */
private const val OneShotMillis = 35L

/** 0~255. 최대치로 두는 이유는 사용자가 "약하다"고 두 번 보고했기 때문이다(#36). */
private const val OneShotAmplitude = 255

internal class PlayHaptics(context: Context) {
    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /**
     * 반상을 누르는 순간 한 번 울린다.
     *
     * `EFFECT_HEAVY_CLICK`은 프레임워크 프리베이크 3단계(`TICK` < `CLICK` < `HEAVY_CLICK`) 중
     * 가장 강하다. 기기가 이 효과를 모르면 프레임워크가 알아서 폴백한다.
     *
     * `USAGE_ASSISTANCE_SONIFICATION`으로 용도를 밝혀 둔다 — 터치 피드백 계열임을 시스템에
     * 알려, 무음 모드에서 알림 진동처럼 취급돼 엉뚱하게 억제되거나 반대로 튀지 않게 한다.
     */
    fun play() {
        val vibrator = vibrator ?: run { PlayHapticDiagnostics.record(false, "no vibrator service"); return }
        if (!vibrator.hasVibrator()) {
            PlayHapticDiagnostics.record(false, "device reports no vibrator")
            return
        }
        val result = runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(OneShotMillis, OneShotAmplitude))
        }
        PlayHapticDiagnostics.record(result.isSuccess, result.exceptionOrNull()?.message ?: "vibrate() called")
    }

    fun diagnosticReport(): String = PlayHapticDiagnostics.report(appContext, vibrator)
}

/**
 * "진동이 안 느껴진다"를 원격으로 가려내기 위한 기록(#36). 디버그 리포트의 `[Haptics]` 절로
 * 나간다 — 처음에는 리포트에 햅틱 정보가 **하나도 없어서** 사용자 기기에서 무엇이 잘못됐는지
 * 볼 방법이 없었다.
 *
 * ⚠️ 시스템 설정 키를 **여러 개** 읽는다. 첫 진단은 [Settings.System.HAPTIC_FEEDBACK_ENABLED]
 * 하나만 봤는데, 제조사 UI의 "터치 피드백" 토글이 그 키와 1:1로 대응하지 않는 기기가 있다 —
 * 사용자가 설정을 켰다고 했는데도 그 키가 계속 0으로 읽히는 상황을 겪었다. 이제는 진동 세기
 * 키와 무음 모드까지 함께 찍어, 다음에 또 막히면 어느 축인지 바로 보이게 했다.
 */
internal object PlayHapticDiagnostics {
    private var attempts: Int = 0
    private var succeeded: Int = 0
    private var lastDetail: String? = null

    fun record(success: Boolean, detail: String) {
        attempts++
        if (success) succeeded++
        lastDetail = detail
    }

    private fun systemInt(context: Context, key: String): String =
        runCatching { Settings.System.getInt(context.contentResolver, key).toString() }
            .getOrElse { "unset" }

    fun report(context: Context, vibrator: Vibrator?): String = buildString {
        appendLine("path=Vibrator.createOneShot(${OneShotMillis}ms, amplitude=$OneShotAmplitude) gate=앱 토글만(#36 ⓑ안)")
        appendLine("attempts=$attempts succeeded=$succeeded lastDetail=${lastDetail ?: "none"}")
        appendLine("deviceHasVibrator=${runCatching { vibrator?.hasVibrator() }.getOrNull() ?: "unknown"}")
        appendLine(
            "systemKeys: haptic_feedback_enabled=${systemInt(context, "haptic_feedback_enabled")}" +
                " haptic_feedback_intensity=${systemInt(context, "haptic_feedback_intensity")}" +
                " vibrate_when_ringing=${systemInt(context, "vibrate_when_ringing")}",
        )
        append(
            when {
                attempts == 0 ->
                    "해석: 앱이 아직 한 번도 부르지 않았다 — 앱 내 '착수 진동' 토글이 꺼져 있거나, " +
                        "AI 차례/종국이라 입력이 막혔거나, 반상 밖을 눌렀다."
                succeeded == 0 -> "해석: vibrate() 호출이 전부 예외로 끝났다. lastDetail을 볼 것."
                else ->
                    "해석: vibrate()가 정상 호출됐다. 이제 시스템 '터치 피드백' 설정과 무관하게 울린다 — " +
                        "그런데도 약하면 기기의 **진동 세기** 설정(haptic_feedback_intensity)을 볼 것."
            },
        )
    }
}
