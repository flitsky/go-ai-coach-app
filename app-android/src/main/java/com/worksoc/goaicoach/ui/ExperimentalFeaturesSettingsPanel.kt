package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.persistence.ExperimentalFeaturesStore

/**
 * 설정 화면의 "🧪 실험실 기능" 패널.
 *
 * ## ⚠️ 왜 별도 파일로 분리하는가
 * `SettingsScreen.kt`는 **상태 훅 13/13으로 여유가 0**입니다(백로그 #102).
 * 저장소를 `SettingsScreen.kt`에서 선언하면 `settingsScreenStaysAShellAndTheDeveloperSectionStaysItsOwnRole`
 * 계약 테스트가 즉시 실패합니다. 따라서 [FontScaleSettingsPanel]과 동일하게 상태와 저장소를
 * 이 패널이 자체적으로 소유합니다.
 */
@Composable
internal fun ExperimentalFeaturesSettingsPanel(modifier: Modifier = Modifier) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val store = remember(context) { ExperimentalFeaturesStore(context) }
    var isCameraBoardScanEnabled by remember(store) {
        mutableStateOf(store.isCameraBoardScanEnabled())
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 헤더: "🧪 실험실" + 설명
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "🧪",
                    fontSize = 18.sp,
                )
                Text(
                    text = strings.labsTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = strings.labsSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // 항목 1: 카메라 바둑판 사진 분석 (Beta)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = strings.cameraBoardScanToggleTitle(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.cameraBoardScanToggleDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 16.sp,
                    )
                }

                Switch(
                    checked = isCameraBoardScanEnabled,
                    onCheckedChange = { nextEnabled ->
                        isCameraBoardScanEnabled = nextEnabled
                        store.setCameraBoardScanEnabled(nextEnabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
