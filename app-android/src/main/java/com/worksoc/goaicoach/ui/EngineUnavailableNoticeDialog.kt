package com.worksoc.goaicoach.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.application.engine.EngineAvailability
import com.worksoc.goaicoach.application.engine.engineAvailabilityFor
import com.worksoc.goaicoach.shared.EngineMode

/**
 * 엔진이 **끝내 뜨지 못했을 때** 알리는 팝업(백로그 #101 ④단계).
 *
 * ## ⚠️ 이 팝업이 다루는 것은 "아직"이 아니라 "안 된다" 이다
 * 백로그 「핵심 동작 기조」 1ⓒ가 이 둘을 갈라놓는다. **준비 중([EngineMode.Unknown])에는 절대
 * 뜨지 않는다** — 시간이 지나면 저절로 풀리는 상태를 문제로 만들지 않는 것이 그 기조다.
 * 준비 중 안내는 `GameSetupLobby`의 `engineNotReadyToStart`가 조용히 맡는다.
 *
 * ## ⚠️ 왜 이 알림이 필요한가 — 실패가 **성공처럼 보이기** 때문이다
 * `createEngineBootstrap`은 **예외를 던지지 않는다.** 네이티브 라이브러리나 모델이 없으면
 * `StubEngineAdapter`로 조용히 강등되는데, 그 스텁의 `initialize()`는 `EngineStatus.ready(...)`를
 * 돌려준다. 그래서 **`isEngineReady`가 `true`가 되고**, 앱은 정상으로 보이며, 사용자는 자기가
 * 스텁과 두고 있다는 것을 **어디서도 알 수 없었다.** 이 팝업이 그 침묵을 깬다.
 *
 * ## 세션 한정으로 닫힌다 — 저장하지 않는다
 * 다음 실행에도 여전히 스텁이면 **다시 알려야 한다.** "다시 보지 않기"를 저장하면 고쳐지지 않은
 * 고장을 영구히 숨기게 된다.
 *
 * 상태를 이 파일이 들고 있는 이유는 `ReleaseResetNoticeDialog`와 같다 — `GoCoachApp.kt`의
 * 상태 훅 예산(42/42, 여유 0)을 쓰지 않기 위해서다.
 *
 * @return 팝업이 지금 떠 있으면 `true`. ⚠️ 호출부는 이때 **다른 팝업을 띄우지 말 것** —
 *   Compose 다이얼로그는 각자 별도 윈도우라 **선언 순서로는 위아래가 정해지지 않는다**
 *   (`ReleaseResetNoticeDialog`가 2026-09-01 실기에서 확인한 것).
 */
@Composable
internal fun EngineUnavailableNoticeDialog(mode: EngineMode): Boolean {
    // ⚠️ 여기서 직접 `mode`를 비교하지 말 것 — 세 상태를 가르는 판단은 shared의 정책이
    // 소유한다(`engineAvailabilityFor`). 준비 중(Unknown)에 뜨면 **앱을 켤 때마다** 뜬다.
    if (engineAvailabilityFor(mode) != EngineAvailability.Unavailable) return false

    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return false

    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(strings.engineUnavailableTitle) },
        text = { Text(strings.engineUnavailableMessage) },
        confirmButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(strings.close)
            }
        },
    )
    return true
}
