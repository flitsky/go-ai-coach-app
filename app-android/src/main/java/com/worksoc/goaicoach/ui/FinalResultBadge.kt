package com.worksoc.goaicoach.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.score.FinalScoreJudgement
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor

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
                Text(text = TrophyGlyph, fontSize = 22.sp)
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
 * 승리 트로피(2026-09-22 사용자 지시).
 *
 * ⚠️ **이 저장소에는 이모지 대신 벡터를 쓴 선례가 있다**(#181 — *"기기·글꼴에 따라 렌더링이
 * 갈리는 이모지보다 크기·색이 항상 예측 가능하다"*). 여기서 이모지를 쓰는 것은 사용자 지시이고,
 * 실기에서 흑백으로 나오거나 크기가 튀면 그 선례를 따를 것.
 */
private const val TrophyGlyph = "🏆"
