package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 대국 종료 상태 화면의 **결과 배지**(백로그 #187) — 좌석 카드 둘 **사이**에 겹쳐 뜬다.
 *
 * ## ⚠️ 왜 필요했나
 * 「판정 결과」 팝업을 닫으면 **누가 어떻게 이겼는지 볼 곳이 없었다**(2026-09-22 사용자 제보).
 * 판과 좌석 카드는 남지만 결과는 팝업과 함께 사라진다.
 *
 * ## ⚠️ 지금은 **폰 배치에만** 붙어 있다
 * 이 배지를 그리는 곳은 [GameStatusPanel] 하나이고, 그것을 쓰는 것은 `GameScreenLayout.Phone`
 * 뿐이다. 넓은 배치 둘(좌우 기둥·두 줄)은 **다른 좌석 컴포넌트**(`CompactSeatCard`)를 쓰고
 * 가운데 자리를 이미 `WideScoreSummary`가 차지하고 있어, 같은 「가운데에 겹친다」가 성립하지
 * 않는다. **2026-09-22 지시가 가리킨 것은 판 아래의 그 패널**이라 거기까지만 했다 —
 * 넓은 배치의 자리는 별도 설계가 필요하다(백로그 #187의 남은 몫).
 *
 * ## 승리 진영을 **테두리**에도 싣는다 (2026-09-22, 백로그 #190)
 * #187은 결과를 **글자로만** 남겼다 — 읽어야 안다. 종국이 되면 좌석 카드 둘은 `isActiveTurn`이
 * **둘 다 false**라 회색으로 똑같아져서, 판 아래 이 구간에서 승패를 **색으로** 말해 주는 것이
 * 하나도 없었다. 그래서 이 배지가 **승자 진영색 테두리**를 두른다.
 *
 * ⚠️⚠️ **금색을 쓰지 않는다 — 이 앱에서 금색은 뜻이 이미 정해져 있다.** `PremiumTheme.kt`가
 * *"금색은 「프리미엄 기능이다」라는 뜻"* (2026-09-18 사용자 결정 ⓐ안)이라고 못박았고 대국
 * 메뉴의 프리미엄 옵션 셋이 그것을 쓴다. 트로피와 어울려 보인다는 이유로 **가장 고르기 쉬우면서
 * 고르면 안 되는 색**이다 — 두르는 순간 같은 화면에서 금색이 두 가지를 뜻한다.
 * ⚠️ **프라이머리(초록)도 쓰지 않는다** — [ActiveStateBorder]가 **「지금 차례」**다.
 *
 * 그래서 [winnerBorderColor]는 **좌석 카드가 이미 쓰는 진영색**을 그대로 쓴다(흑 `Color.Black` ·
 * 백 `Color.Gray`). 백을 흰색이 아니라 회색으로 그리는 그 규칙 덕에 밝은 배경에서도 안 사라지고,
 * 새 색을 하나도 만들지 않는다.
 *
 * ## ⚠️ 기권으로 끝난 판에는 [FinalScoreJudgement]가 **없다**
 * 그래서 승자를 두 경로로 찾는다 — 계가면 판정에서, 기권이면 **마지막 수에서**.
 * `Move.Resign`은 **던진 쪽**을 들고 있으므로 승자는 그 반대편이다(대국 기록이 쓰는 것과
 * 같은 규칙, `GameHistoryAppendApplication`). ⚠️ 2026-09-18 이전 **기록**은 승자를 모르지만
 * 여기는 **방금 끝난 판**이라 그 문제가 없다 — 착각해서 방어 코드를 넣지 말 것.
 */
@Composable
internal fun FinalResultBadge(
    gameState: GameState,
    judgement: FinalScoreJudgement?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val resignMove = gameState.moves.lastOrNull() as? Move.Resign
    val isResign = resignMove != null
    val winner: StoneColor? = judgement?.winner ?: resignMove?.player?.opponent
    val detail = strings.finalResultDetailLabel(margin = judgement?.margin, isResign = isResign)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        // ⚠️ **무승부에는 테두리를 두르지 않는다** — 아무도 안 이겼다. 중립색으로라도 두르면
        // 「누군가 이겼는데 색을 못 읽겠다」로 보인다(없는 편이 정직하다).
        border = winnerBorderColor(winner)?.let { BorderStroke(WinnerBorderWidth, it) },
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            // ⚠️ **고정 높이를 주지 않는다**(함정 9) — 세 줄이고 글꼴 배율이 1.3까지 올라간다.
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ⚠️ **무승부에는 트로피를 달지 않는다** — 아무도 이기지 않았다.
            // (덤이 모두 반집이라 실제로는 나올 수 없지만, 「없는 경우」를 그리면 조용히 틀린다.)
            if (winner != null) {
                // ⚠️ **테두리와 같은 것을 말한다** — 색맹·저대비 화면에서 테두리 색만으로는
                // 흑·백이 안 갈린다. 글리프가 그 두 번째 통로다(좌석 카드와 같은 `●`/`○`).
                Text(text = "$TrophyGlyph ${stoneGlyphOf(winner)}", fontSize = 22.sp)
            }
            Text(
                // ⚠️ **팝업과 같은 낱말을 쓴다** — 둘이 다른 말을 하면 어느 쪽이 맞는지 알 수 없다
                // (함정 39). 집수는 여기서 말하지 않고 아래 줄이 괄호로 맡는다.
                text = winner
                    ?.let { strings.winnerWithoutMarginLabel(strings.colorLabel(it)) }
                    ?: strings.drawLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * 승자 테두리 두께 — [ActiveStateBorder](1.5dp)보다 **굵다.** 종국 화면에서 가장 먼저 눈에
 * 들어와야 하는 것이 이 구간이고, 옆의 좌석 카드 둘은 이때 회색 1dp로 물러나 있다.
 */
private val WinnerBorderWidth = 2.5.dp

/**
 * 승자 진영의 테두리 색 — 무승부면 `null`(테두리 없음).
 *
 * ⚠️ **좌석 카드의 `stoneGlyphColor`와 같은 값을 쓴다**(`GameStatusPanel`의 `PlayerSeatCard`).
 * 백이 `Color.White`가 아니라 `Color.Gray`인 것이 요점이다 — 배지 바탕이 밝은 `surfaceVariant`라
 * 흰 테두리는 **있으나 마나**가 된다. 한쪽만 고치면 같은 화면에서 백이 두 색이 되므로 **둘을 함께
 * 고칠 것**(`FinalResultBadgeContractTest`가 이 규칙을 문자로 고정한다).
 */
private fun winnerBorderColor(winner: StoneColor?): Color? = when (winner) {
    StoneColor.Black -> Color.Black
    StoneColor.White -> Color.Gray
    null -> null
}

/** 좌석 카드와 같은 진영 글리프. */
private fun stoneGlyphOf(winner: StoneColor): String =
    if (winner == StoneColor.Black) "\u25CF" else "\u25CB"

/**
 * 승리 트로피(2026-09-22 사용자 지시).
 *
 * ⚠️ **이 저장소에는 이모지 대신 벡터를 쓴 선례가 있다**(#181 — *"기기·글꼴에 따라 렌더링이
 * 갈리는 이모지보다 크기·색이 항상 예측 가능하다"*). 여기서 이모지를 쓰는 것은 사용자 지시이고,
 * 실기에서 흑백으로 나오거나 크기가 튀면 그 선례를 따를 것.
 */
private const val TrophyGlyph = "🏆"
