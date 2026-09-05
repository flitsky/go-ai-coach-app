package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.engine.EngineAvailability

/**
 * 대국 화면에 **계속 떠 있는** 엔진 고장 표식(백로그 #105).
 *
 * ## ⚠️ 왜 팝업만으로는 부족한가
 * `EngineUnavailableNoticeDialog`은 **한 번 닫으면 사라진다** — 그렇게 만든 것이 맞다
 * (사용자 결정 2026-09-05: 스텁이어도 **막지 않는다**). 그런데 닫고 나면 자기가 가짜와 두고
 * 있다는 표시가 화면 어디에도 없어, #101 ④가 깬 침묵이 **대국 중에 그대로 돌아온다.**
 * 이 배지가 그 자리를 메운다.
 *
 * ## ⚠️ 준비 중에는 뜨지 않는다
 * [EngineAvailability.Preparing]은 **정상**이다(기조 1ⓒ). 여기서 걸리면 앱을 켤 때마다
 * 잠깐씩 경고가 번쩍인다 — 정확히 기조가 금지하는 것이다.
 */
@Composable
internal fun EngineUnavailableBadge(
    availability: EngineAvailability,
    modifier: Modifier = Modifier,
) {
    if (availability != EngineAvailability.Unavailable) return

    val strings = LocalUiStrings.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.engineUnavailableBadge,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
