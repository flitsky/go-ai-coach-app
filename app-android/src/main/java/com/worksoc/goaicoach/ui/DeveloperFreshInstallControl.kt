package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.restartToFreshInstall

/**
 * 개발자 테스트 2차의 **'앱 최초 실행 상태로 되돌리기'** 한 줄(2026-09-09 사용자 요청).
 *
 * ## ⚠️ 왜 `DeveloperTestSection`에 직접 넣지 않았는가
 * 넣었더니 `LayeringContractTest`가 **훅 예산(4)과 줄 예산(350)을 동시에** 걷어찼다. 그 계약이
 * 적어 둔 안내가 정확히 이 경우다 — *"훅이 필요하다면 정말 이 섹션이 상태를 가져야 하는지 먼저
 * 물을 것."* 확인 다이얼로그의 열림 여부는 **이 컨트롤만의 사정**이라 섹션이 알 이유가 없다.
 * 그래서 상태를 쓰는 쪽으로 내려 보냈고, 섹션은 호출 한 줄만 갖는다(`SettingsScreen`에서
 * 개발자 섹션을 떼어낸 #102와 같은 결의 정리다).
 *
 * ## ⚠️ 릴리즈 초기화(#63)와 뜻이 다르다 — 나란히 있어서 더 헷갈린다
 * 그쪽은 **권한 넷**(출석·캐릭터·1회권·프리미엄)만 밀고 대국 기록·설정·언어·온보딩 완료는
 * 일부러 남긴다(`FEATURE_ACCESS_PRINCIPLES.md` 8.3-2: 권한이 아닌 것은 건드리지 않는다).
 * 이 컨트롤은 **그 남긴 것들까지** 밀어 첫 실행 처리와 가이드가 다시 도는 상태를 만든다.
 *
 * ## ⚠️ 지울 목록을 여기 적지 않는다
 * `wipeToFreshInstall`의 **접두사 훑기**를 그대로 쓴다(함정 6번) — 저장소가 새로 늘어도 자동으로
 * 포함되고, 목록이 두 벌로 갈라지지 않는다.
 */
@Composable
internal fun DeveloperFreshInstallControl() {
    val context = LocalContext.current
    val strings = LocalUiStrings.current
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠️ 고정 높이를 주지 않는다(함정 9번) — 주변 행들과 같은 `Row` + `Column(weight(1f))`
        // 골격이라 글꼴 배율을 저절로 따라간다.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.settingsDevFreshInstallTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = strings.settingsDevFreshInstallSubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        // ⚠️ **확인을 한 단 둔다.** 되돌릴 수 없는 데다 **누르는 즉시 앱이 죽고 다시 뜬다.**
        // 옆의 릴리즈 초기화보다 넓게 지우므로, 같은 일을 하는 개발자 모드 끄기가 확인
        // 다이얼로그를 두는 쪽을 따른다.
        TextButton(onClick = { showConfirm = true }) {
            Text(strings.settingsDevFreshInstallAction)
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(strings.settingsDevFreshInstallConfirmTitle) },
            text = { Text(strings.settingsDevFreshInstallConfirmMessage) },
            confirmButton = {
                TextButton(
                    // ⚠️ 토스트를 띄우지 않는다 — 이 줄 다음에 **프로세스가 끝난다.** 띄워 봐야
                    // 보이지 않고, 재시작된 앱의 첫돌이 가이드가 그 자체로 결과를 알린다.
                    onClick = { restartToFreshInstall(context) },
                ) { Text(strings.settingsDevFreshInstallAction) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(strings.cancel) }
            },
        )
    }
}
