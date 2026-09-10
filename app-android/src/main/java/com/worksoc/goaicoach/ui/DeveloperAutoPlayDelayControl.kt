package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.match.AutoPlayDelaySetting

/**
 * 개발자 테스트 **1차**의 'AI 착수 지연' 한 줄 — 2026-09-10에 대국 설정에서 이리로 옮겼다(사용자 지시).
 *
 * ## ⚠️ 왜 1차인가 (2차가 아니라)
 * 두 단을 가르는 기준은 라벨이 아니라 **저장소에 무엇을 쓰는가**다(`DeveloperTestSection` 머리말).
 * 이 값은 `UserPreferencesSnapshot.autoPlayDelayMillis`에 쓰는 **취향**이지 권한이 아니다 —
 * 무료로 찍어내는 것이 없으므로 release에 실려도 무해하다. 반대로 2차에 두면
 * `BuildConfig.DEBUG`로 잘려 **release에서는 영영 조절할 수 없게 된다**(기본이 '즉시'로 바뀐 뒤라
 * AI 대 AI를 눈으로 따라가려는 사람이 손쓸 방법이 사라진다).
 *
 * ## ⚠️ 옮기기 전에도 아무 때나 보이던 줄이 아니다
 * `PlayerSetupPanel`에서 `state.showAutoPlayDelay`(= 양쪽 좌석이 모두 AI)로 가려져 있어
 * **AI 대 AI 대국에서만** 나타났다. 그래서 이 이동으로 *"있던 것이 사라졌다"* 고 느낄 사용자는
 * 사실상 없다 — 원래 대부분의 사용자에게는 존재하지 않던 줄이다.
 *
 * ## ⚠️ 여기서는 좌석 조건으로 가리지 않는다
 * 개발자 섹션은 대국 설정과 달리 **지금 좌석이 무엇인지 모르는 자리**이고, 값을 미리 정해 둔 뒤
 * AI 대 AI를 시작하는 순서가 자연스럽다. 대신 부제에 *"AI끼리 둘 때만 적용"* 을 적어, 사람과
 * 두는 동안 아무 일도 일어나지 않는 것이 고장으로 읽히지 않게 한다.
 */
@Composable
internal fun DeveloperAutoPlayDelayControl(
    selected: AutoPlayDelaySetting,
    onSelected: (AutoPlayDelaySetting) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠️ 고정 높이를 주지 않는다(함정 9번) — 주변 행들과 같은 골격이라 글꼴 배율을 따라간다.
        Column(modifier = Modifier.weight(1f)) {
            Text(text = strings.autoDelay, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = strings.settingsDevAutoPlayDelaySubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        SetupDropdown(
            selectedText = strings.autoPlayDelayLabel(selected),
            enabled = true,
            modifier = Modifier.weight(1f),
            options = AutoPlayDelaySetting.entries,
            optionLabel = { setting -> strings.autoPlayDelayLabel(setting) },
            onSelected = onSelected,
        )
    }
}
