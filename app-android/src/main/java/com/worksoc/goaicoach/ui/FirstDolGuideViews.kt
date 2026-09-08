package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCharacterId

/**
 * 첫돌이의 **얼굴과 정체를 한 곳에서** 정한다(백로그 #128).
 *
 * ## ⚠️ 이 파일이 첫돌이 id의 단일 출처다
 *
 * `UiStringsLanding.kt`가 같은 상수를 `private`으로 들고 있었는데, 가이드가 다섯 자리에서 첫돌이를
 * 그리게 되면서 **두 곳이 갈릴 위험**이 생겼다. 그래서 그 선언을 여기로 올리고 랜딩이 이것을
 * 참조한다 — 카탈로그 1단계가 바뀌면 고칠 자리도 하나다.
 *
 * ## ⚠️ 카탈로그에 없으면 **아무것도 그리지 않는다**
 *
 * [BotCharacterCatalog]가 첫돌이를 못 찾는 상황(id 개명 등)에서 자리표시자를 그리면 **회색 원이
 * 화면 곳곳에 남는다** — 그것이 정상인지 결함인지 아무도 모른다. 조용히 비우면 레이아웃만 살고,
 * 그 어긋남은 `FirstDolGuideContractTest`가 잡는다.
 */
internal val FirstDolCharacterId = BotCharacterId("fast_beginner_1")

/**
 * 첫돌이 그림 하나.
 *
 * @param mirrored 좌우 반전. 랜딩 상단 **좌우 한 쌍**을 마주 보게 하려고 둔 것이다.
 *   ⚠️ 원화가 비대칭이면 반전이 어색해 보일 수 있다 — 그 판정은 실기에서 눈으로 한다.
 */
@Composable
internal fun FirstDolAvatar(
    size: Dp = 40.dp,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier,
    seamColor: Color = Color.Unspecified,
) {
    val character = BotCharacterCatalog.all.firstOrNull { it.id == FirstDolCharacterId } ?: return
    BotCharacterAvatar(
        character = character,
        modifier = modifier
            .size(size)
            .then(if (mirrored) Modifier.graphicsLayer(scaleX = -1f) else Modifier),
        size = size,
        available = true,
        seamColor = if (seamColor == Color.Unspecified) MaterialThemeSurface() else seamColor,
    )
}

/** `BotCharacterAvatar`의 기본값과 같은 색. 별도 함수로 둔 것은 위 시그니처를 읽기 쉽게 하려는 것뿐이다. */
@Composable
private fun MaterialThemeSurface(): Color = androidx.compose.material3.MaterialTheme.colorScheme.surface
