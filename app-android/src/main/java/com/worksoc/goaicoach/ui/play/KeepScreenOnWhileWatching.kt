package com.worksoc.goaicoach.ui.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.worksoc.goaicoach.match.MatchMode

/**
 * **AI 대 AI를 관전하는 동안만 화면을 켜 둔다**(백로그 #205, U-58 사용자 결정 2026-10-01 「AI 대 AI 관전 중만」).
 *
 * ## 왜
 * 관전 중에는 아무도 화면을 만지지 않아 실기기는 30초~1분이면 화면이 꺼지고, 그러면 #202에 따라 AI 차례가 멈춘다
 * (오탐은 아니지만 관전이 끊긴다). 사람이 두는 판은 두는 순간마다 화면을 만지므로 지금처럼 폰 설정을 따른다.
 *
 * ⚠️ **대국이 끝나면 풀린다** — 종국 화면을 켜 둔 채 자리를 비우면 배터리만 쓴다. 대국 중 좌석을 「유저」로 바꿔도(#199)
 * 조건이 거짓이 되어 곧바로 풀린다. 화면(컴포지션)을 떠나도 풀린다 — 창 플래그가 아니라 **이 화면의 뷰**에 거는 이유다.
 */
internal fun shouldKeepScreenOnWhileWatching(matchMode: MatchMode, isGameEnded: Boolean): Boolean =
    matchMode == MatchMode.AiVsAi && !isGameEnded

@Composable
internal fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (active) view.keepScreenOn = true
        onDispose {
            if (active) view.keepScreenOn = false
        }
    }
}
