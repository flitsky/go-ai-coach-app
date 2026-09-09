package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * **레이아웃에 0을 기여하는** 오버레이(백로그 #128 ③).
 *
 * ## ⚠️ 이것이 없으면 배율 2.0에서 카드가 밀린다
 *
 * 설계안 셋이 공통으로 *"래퍼 `Box` 높이는 max(카드 120dp, 말풍선)이라 카드 높이 그대로"* 라고
 * 주장했는데 **거짓이다.** `Modifier.offset`이 크기에 반영되지 않는 것과 별개로, 말풍선의 **측정
 * 높이는 Box 크기에 그대로 참여**한다 — 배율 1.3~2.0에서 말풍선이 120dp를 넘기는 순간 Box가 자라
 * **아래 카드 세 장이 밀리고 사용자가 눌러야 할 표적이 움직인다.** #28이 만졌던 그 열의 고정 자식
 * 합계가 커지는 것이다.
 *
 * 그래서 자식을 **무제한 제약으로 재고**(제 크기대로 그려지게) **`layout(0, 0)`으로 보고한다.**
 * 부모는 0×0을 보므로 어떤 배율에서도 레이아웃이 흔들리지 않는다. Compose는 기본적으로 자식을
 * 클리핑하지 않으므로 경계 밖으로 그려진 그림은 그대로 보인다.
 *
 * ## ⚠️ 그래서 이 안에 **누를 것을 두지 말 것**
 *
 * 부모 경계 **밖**에 그려진 자식은 히트테스트를 받지 못한다 — 버튼을 넣으면 눌리지 않는 버튼이
 * 된다. ③ 말풍선을 **비인터랙티브 그림**으로 둔 이유가 이것이고, 대신 ③은 **1.2초 동안 팝업에
 * 덮이지 않은 채** 있었으면 스스로 "봤음"으로 기록된다(`GuideBlockingOverlays`).
 *
 * ⚠️ 이 문단은 2026-09-09까지 *"사슬을 끄는 「그만 보기」는 눌릴 수 있는 자리(④⑤ 카드·마이페이지)에
 * 둔다"* 로 끝나고 있었다 — 그 버튼은 같은 날 **없어졌다**(사용자 판정: `GuideCard`의 KDoc).
 */
@Composable
internal fun ZeroSizeOverlay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val placeable = measurables.first().measure(Constraints())
        layout(0, 0) {
            // 기준점(카드 우측 상단)에서 **위·왼쪽으로** 그린다.
            placeable.place(x = -placeable.width, y = -placeable.height)
        }
    }
}

/**
 * ③ 홈 카드 우상단에 뜨는 말풍선. **비인터랙티브**다([ZeroSizeOverlay]의 이유).
 *
 * 꼬리를 그리지 않고 **첫돌이를 말풍선 옆에 세워** 누가 말하는지 보이게 한다 — 꼬리를 그리려면
 * 앵커 좌표가 필요하고, 그 좌표를 얻으려면 화면에 상태가 생긴다(설계 심사에서 기각된 경로다).
 */
@Composable
internal fun GuideBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = 260.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FirstDolAvatar(size = 32.dp)
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * ② 출석 보상 팝업 안에서 첫돌이가 거드는 한 줄.
 *
 * ⚠️ 이 팝업은 **이미 보상 캐릭터 그림을 그린다**(5·6·7·28일차). 그래서 첫돌이를 그것들과 같은
 * 크기·같은 모양으로 두면 *"받는 캐릭터"* 로 오해된다 — 작게(24dp), 보상 표 **밖에**, 말하는
 * 문장과 함께 두어 **안내자**로 읽히게 한다.
 */
@Composable
internal fun GuideLine(
    text: String,
    modifier: Modifier = Modifier,
    // ⚠️ 문구가 **두 줄 이상**으로 접힐 수 있는 자리에서는 `Top`을 넘긴다 — 기본값(`가운데`)은
    // 얼굴을 문단 중간에 세운다. 마이페이지가 좁은 폭(제목과 한 줄을 쓴다)에서 그렇고, 실기
    // 영어·큰 글꼴에서 얼굴이 둘째 줄 옆에 서 있었다. 출석 팝업은 폭이 넉넉해 기본값을 쓴다.
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = verticalAlignment,
    ) {
        FirstDolAvatar(size = 24.dp)
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * ④⑤가 쓰는 **누를 수 있는** 카드. 창 안 다이얼로그다.
 *
 * ## ⚠️ 말풍선과 달리 이쪽은 기록 시점이 "사용자가 확인한 순간"이다
 *
 * 이 카드는 벤치마크·최종판정·엔진멈춤 팝업에 **덮일 수 있다**(그것들은 별도 윈도우다). 보여준
 * 순간 기록하면 **덮인 채 소진**돼 사용자 기준으로는 0번 보게 된다 — 그래서 두 동작 중 하나를
 * 누를 때만 기록한다. ③ 말풍선은 창 안 비모달이라 덮일 수 없어 시간 기준을 쓴다(그 비대칭이 의도다).
 *
 * ## 동작은 **하나뿐이다**
 *
 * ⚠️ 한때 *"그만 보기"*(사슬 전체 끄기)를 나란히 뒀다가 **없앴다**(2026-09-09 사용자 판정) —
 * 사슬이 짧아 *"알겠어요"* 를 연타하면 곧 끝나는데, **사정거리가 다른 버튼 둘**을 나란히 두면
 * 사용자가 무엇을 껐는지 알 수 없다. 되살리려면 설정 항목으로 둘 것(`GuideProgress`의 KDoc).
 */
@Composable
internal fun GuideCard(
    text: String,
    onAck: () -> Unit,
) {
    val strings = LocalUiStrings.current
    AlertDialog(
        // ⚠️ 바깥 탭·뒤로 가기는 *"알겠어요"* 와 같다 — 닫는 방법에 따라 결과가 달라지면 사용자가
        // 무엇을 껐는지 알 수 없다(출석 팝업이 같은 이유로 모든 갈래를 한 함수로 모았다).
        onDismissRequest = onAck,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FirstDolAvatar(size = 36.dp)
            }
        },
        text = { Text(text = text, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onAck) { Text(strings.guideAckAction) } },
    )
}
