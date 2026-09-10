package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 개발자 테스트 **1차**의 '엔진 성능 측정' 한 줄 — 2026-09-10에 대국 화면 메뉴에서 옮겨 왔다.
 *
 * ## ⚠️ 왜 옮겼는가
 * 1~3분이 걸리는 진단이고 대국 중에 누를 일이 아니다(사용자 결정). 대국 화면 메뉴는
 * *"깜빡한 사람이 설정을 고치는 자리"* 로 성격이 정해졌으므로, 진단 도구는 여기가 맞다.
 *
 * ## ⚠️ 함께 옮기지 **않은** 것 — '진단 로그 복사'
 * 그쪽은 사용자가 **문제를 신고하는 경로**다. 개발자 모드 뒤로 숨기면 고장을 만난 사용자가
 * 보고할 방법을 잃으므로 대국 화면 메뉴에 남겼다. 옮기라고 지시받은 둘 중 하나만 옮긴 셈이고,
 * 그 판단의 근거가 이것이다.
 *
 * ## ⚠️ 1차인 이유
 * 두 단의 경계는 *"저장소에 무엇을 쓰는가"* 다(`DeveloperTestSection` 머리말). 측정은 권한을
 * 만들지 않고 기기 성능 프로필만 남기므로 release에 실려도 무해하다.
 *
 * ## ⚠️ 부제가 침묵을 메운다
 * 이 버튼은 **엔진이 바쁘면 아무 일도 일어나지 않는다** — 차단 사유가 `engineMessage`로만
 * 흘러가고 그 값은 앱 어디에서도 렌더되지 않기 때문이다(2026-09-10에 실기로 재현했다).
 * 그 침묵을 없애는 것은 별개 일감이고, 그전까지는 부제가 *"바쁘면 안 된다"* 를 미리 알린다.
 */
@Composable
internal fun DeveloperEngineBenchmarkControl(onBenchmark: () -> Unit) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠️ 고정 높이를 주지 않는다(함정 9번) — 주변 행들과 같은 골격이라 배율을 따라간다.
        Column(modifier = Modifier.weight(1f)) {
            Text(text = strings.benchmark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = strings.settingsDevBenchmarkSubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        // ⚠️ 옆 행의 `settingsDevReleaseResetAction`("실행")을 빌려 쓰지 않는다 — 남의 기능
        // 문구를 재사용하면 그쪽을 고치는 순간 이쪽 라벨이 함께 바뀐다.
        TextButton(onClick = onBenchmark) { Text(strings.settingsDevBenchmarkAction) }
    }
}
