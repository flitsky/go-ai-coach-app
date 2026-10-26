package com.worksoc.goaicoach.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.backgroundMusicLabelFor

/**
 * 설정 「소리」 절의 「앱 배경음악」 켜기/끄기(백로그 #244).
 *
 * 저장만 한다 — 트는 일은 앱 전체의 플레이어(`BackgroundMusicCoordinator`)가 저장소의 바뀜을 듣고 한다. 그래서 이 패널은
 * 플레이어를 모르고, 설정 화면 밖에서 음악이 이어지는 것도 이 패널과 상관없다.
 * ⚠️ 저장소는 이 패널이 스스로 갖는다 — 설정 화면의 상태는 원장으로 묶여 있다(#102, [FontScaleSettingsPanel]과 같은 자리·같은 모양).
 */
@Composable
internal fun BackgroundMusicSettingsPanel(modifier: Modifier = Modifier) {
    val language = LocalUiStrings.current.language
    val platform = LocalAppPlatform.current
    val store = remember(platform) { platform.stores.backgroundMusic }
    var isEnabled by remember(store) { mutableStateOf(store.isEnabled()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Corner8),
        tonalElevation = AppElevation.Level1,
        shadowElevation = AppElevation.Level0,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = backgroundMusicLabelFor(language),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    isEnabled = checked
                    store.setEnabled(checked)
                },
            )
        }
    }
}
