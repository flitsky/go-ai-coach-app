package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.ui.designsystem.AppTextLink
import com.worksoc.goaicoach.ui.l10n.UiStrings
import kotlinx.coroutines.launch

/**
 * 광고 개인정보 옵션(동의 철회) 진입점 한 줄(백로그 #89).
 *
 * ⚠️ **AdMob 콘솔에서 "개인정보 옵션 링크 포함"을 켠 경우에만 그린다.** 켜지 않았다면
 * `privacyOptionsRequirementStatus`가 `REQUIRED`가 아니고, 그때는 누를 것이 없으므로 행 자체를
 * 그리지 않는 것이 맞다 — 눌러도 아무 일이 없는 줄을 남기지 않는다.
 *
 * ## ⚠️ 왜 별도 파일인가 — `SettingsScreen`에 상태를 더할 수 없다
 * `SettingsScreen.kt`의 **상태 훅 예산이 13/13으로 여유가 정확히 0**이다
 * (`LayeringContractTest`가 `remember|mutableStateOf|LaunchedEffect`를 센다). 이 행은 런타임
 * 값(`REQUIRED`인가)을 읽어야 해서 훅이 최소 하나 필요하므로, **자기 상태를 자기가 소유하고**
 * `SettingsScreen`은 한 줄로 호출만 한다 — [AppUpdateRow]와 같은 모양이다.
 * ⚠️ `GoCoachApp`을 경유하는 설계는 더 나쁘다(그쪽은 42/42다).
 */
@Composable
internal fun AdPrivacyOptionsRow(strings: UiStrings) {
    val monetization = rememberAppMonetization()
    val scope = rememberCoroutineScope()
    // 콘솔 설정에 따라 정해지는 값이라 조회 결과를 한 번 들고 있는다. 폼을 닫고 나면 요구
    // 상태가 바뀔 수 있어(예: 철회) 다시 읽는다.
    var required by remember { mutableStateOf(monetization.isPrivacyOptionsRequired()) }
    if (!required) return

    // 앱 정보 카드의 다른 링크와 같은 `AppTextLink` — 48dp로 맞닿으니 앞의 Spacer 8dp는 뺐다(QA-18, 2026-11-23).
    AppTextLink(
        text = strings.settingsAdPrivacyOptionsLabel,
        onClick = {
            // 액티비티가 아니면 폼을 띄우지 않는다 — 그 판정은 안드로이드 구현(`AndroidAppMonetization`)에 있다.
            scope.launch {
                monetization.showPrivacyOptionsForm()
                required = monetization.isPrivacyOptionsRequired()
            }
        },
    )
}
