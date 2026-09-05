package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.preferences.AppFontScales
import com.worksoc.goaicoach.persistence.UserPreferencesStore

/**
 * 글꼴 크기 설정(백로그 #106) — **개발자 도구에서 정식 설정으로 승격됐다**(2026-09-05 사용자 지시).
 *
 * ## ⚠️ 이것은 접근성 항목이다
 * 이 앱은 **시스템 글꼴 배율을 따르지 않는다**(2026-09-04 사용자 결정). 시스템에서 글자를 키운
 * 사용자도 이 앱에서는 1.0으로 보게 되는데, 그 결정에는 **접근성 비용**이 있다고 그때 적어 뒀다
 * (`DefaultAppFontScale`의 KDoc). 이 설정이 그 비용을 갚는 자리다 — 개발자 모드 뒤에 숨어 있는
 * 동안에는 갚지 못했다.
 *
 * ## 왜 별도 파일인가
 * `SettingsScreen.kt`는 **상태 훅 13/13으로 여유가 0**이다(백로그 #102). 저장소를 그 화면에서
 * 만들면 그 예산을 넘긴다. 그리고 그게 옳기도 하다 — *"조립만 하는 셸은 상태를 소유하지
 * 않는다."* `LanguageSettingsPanel`과 같은 자리, 같은 모양이다.
 *
 * ## ⚠️ 순환 버튼이 아니라 선택지다
 * 개발자 도구일 때는 [배율 바꾸기] 한 버튼이 값을 **순환**시켰다. 도구로는 충분했지만 설정으로는
 * 아니다 — 사용자는 **지금 무엇이 골라져 있는지**와 **무엇을 고를 수 있는지**를 함께 봐야 한다.
 */
@Composable
internal fun FontScaleSettingsPanel(modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val store = remember(context) { UserPreferencesStore(context) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 라벨과 컨트롤을 한 행에 — 바로 위 언어 설정과 결을 맞춘다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.settingsFontScaleTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ⚠️ 라벨은 **배율 값이 아니라 뜻**이다("×1.3"이 아니라 "크게"). 그래서
                    // 선택지 수가 `AppFontScales`와 어긋나면 조용히 하나가 사라진다 —
                    // `FontScaleSettingChoiceTest`가 그 어긋남을 잡는다.
                    AppFontScales.forEachIndexed { index, scale ->
                        FilterChip(
                            selected = AppFontScaleState.scale == scale,
                            onClick = { AppFontScaleState.select(store, scale) },
                            label = {
                                Text(
                                    if (index == 0) {
                                        strings.settingsFontScaleNormal
                                    } else {
                                        strings.settingsFontScaleLarge
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
