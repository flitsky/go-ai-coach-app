package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.persistence.ReleaseResetStore

/**
 * 정식 릴리즈 초기화가 **실제로 무언가를 지웠을 때** 한 번만 뜨는 안내(백로그 #63).
 *
 * ⚠️ **공지 없이 밀면 "버그로 사라졌다"로 읽힌다** — 이 다이얼로그가 있는 이유가 그것이므로,
 * 안내를 못 본 채 넘어가는 경로를 만들지 말 것. 그래서 바깥 탭·뒤로가기로 닫히게 두지 않고
 * (`dismissOnClickOutside`/`dismissOnBackPress` = false) **확인 버튼 하나로만** 닫는다.
 *
 * 상태를 컴포저블 안에 두는 것은 `AttendanceRewardClaimDialog`와 같은 이유다 — `GoCoachApp.kt`의
 * 훅/라인 예산을 쓰지 않기 위함이다.
 *
 * ⚠️ **호출부는 이 안내가 떠 있는 동안 출석 Claim 팝업을 미뤄야 한다** — 그래서 표시 여부를
 * 돌려준다. 초기화 때문에 출석이 1일차로 돌아가므로, 안내를 먼저 읽지 못하면 사용자는 "왜
 * 1일차인지" 모른 채 팝업을 보게 된다.
 *
 * ⚠️ **선언 순서로 해결하려다 실패했다**(2026-09-01 실기). Compose 다이얼로그는 각자 별도
 * 윈도우라 **나중에 선언한 쪽이 위로 오지 않는다** — 출석 팝업을 먼저 선언했더니 그쪽이 위에
 * 떴고, 안내는 그것을 닫아야 보였다. 순서가 아니라 **명시적 게이트**로만 보장된다.
 *
 * @return 안내가 지금 떠 있으면 `true`.
 */
@Composable
internal fun ReleaseResetNoticeDialog(context: Context): Boolean {
    val store = remember(context) { ReleaseResetStore(context) }
    var pending by remember(context) { mutableStateOf(store.isNoticePending()) }
    if (!pending) return false

    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = { /* 확인 버튼으로만 닫는다 — KDoc 참고 */ },
        title = { Text(releaseResetTitleFor(strings.language)) },
        text = { Text(releaseResetBodyFor(strings.language)) },
        confirmButton = {
            TextButton(
                onClick = {
                    store.clearNoticePending()
                    pending = false
                },
            ) {
                Text(releaseResetConfirmLabelFor(strings.language))
            }
        },
    )
    return true
}
