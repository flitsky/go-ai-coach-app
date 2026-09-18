package com.worksoc.goaicoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.Move
import com.worksoc.goaicoach.shared.StoneColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 통과 알림이 떠 있는 시간. ⚠️ 1초보다 길게 둔다 — 짧으면 상대가 못 보고, 검증도 못 한다(함정 49). */
private const val PassNoticeMillis = 1_600L

/**
 * **누군가 통과하면 크게 알리고, 이어서 계가할지 묻는다**(백로그 #175, 2026-09-18 사용자).
 *
 * ## 왜 필요했나
 * 통과는 **판 위에 아무 흔적을 남기지 않는다.** 돌이 놓이지 않으므로 상대(특히 사람)는 *"내 차례가
 * 왜 다시 왔지"* 만 보고, 상대가 통과했다는 사실 자체를 모른다.
 *
 * ## ⚠️ 계가 여부를 묻는 것은 **규칙을 바꾸지 않는다**
 * 바둑의 종국은 **연속 두 번 통과**다(`GameState.hasConsecutivePasses`). 그래서 이 팝업의 **예**는
 * *"대국을 지금 끝낸다"* 가 아니라 ***"나도 통과한다"*** 이고, 그 결과로 두 번이 연속돼 규칙대로
 * 종국·계가로 간다. **아니오**는 아무것도 하지 않고 계속 둔다.
 * ⚠️ **여기서 곧바로 계가를 띄우고 싶어지더라도 하지 말 것** — 한쪽만 통과한 판을 끝내는 것은
 * 규칙 위반이고, 종국 판정은 `MatchReferee` 하나가 갖는다.
 *
 * ## ⚠️ 묻는 조건이 셋이다
 * ① **대국이 아직 안 끝났을 것** — 두 번째 통과였다면 이미 종국이라 물을 것이 없다(계가 팝업이 뜬다).
 * ② **지금 사람이 둘 차례일 것** — AI 차례에 물으면 사용자가 답해도 둘 수가 없다.
 *    ⭐ 이 조건 하나가 *"내가 통과했을 때는 안 묻는다"* 까지 함께 만든다 — 내가 통과하면 차례가
 *    상대로 넘어가므로 ②가 거짓이 된다.
 * ③ **알림이 끝난 뒤일 것** — 두 팝업이 겹쳐 뜨면 무엇에 답하는지 알 수 없다.
 */
@Composable
internal fun PassNoticeHost(
    screenState: GameScreenState,
    onPassAgain: () -> Unit,
) {
    val moveCount = screenState.gameState.moves.size
    val lastPass = screenState.gameState.moves.lastOrNull() as? Move.Pass
    val lastMoveIsPass = lastPass != null
    // ⚠️ 알림이 떠 있는 1.6초 동안 그 뒤의 수가 들어올 수 있다 — 색을 나중에 다시 읽으면
    // **엉뚱한 진영 색으로 바뀐다.** 띄우는 순간의 진영을 붙잡아 둔다.
    var noticePlayer by remember { mutableStateOf(StoneColor.Black) }
    // ⚠️ 장수 효과가 아니라 `moveCount`마다 다시 도는 효과지만, 콜백은 여전히 최신을 봐야 한다.
    val latestOnPassAgain by rememberUpdatedState(onPassAgain)

    // 이미 알린 수순은 다시 알리지 않는다 — 재구성마다 뜨면 알림이 화면을 점령한다.
    var announcedMoveCount by remember { mutableIntStateOf(0) }
    var showNotice by remember { mutableStateOf(false) }
    var showScorePrompt by remember { mutableStateOf(false) }
    // ⚠️ **알림을 띄우는 순간의 판정을 붙잡아 둔다.** 1.6초 뒤에 다시 보면 그 사이 AI가 두어
    // 조건이 바뀌어 있을 수 있다 — 그러면 통과와 무관한 순간에 계가를 묻게 된다.
    var askAfterNotice by remember { mutableStateOf(false) }

    LaunchedEffect(moveCount, lastMoveIsPass) {
        if (!lastMoveIsPass || moveCount == announcedMoveCount) return@LaunchedEffect
        announcedMoveCount = moveCount
        noticePlayer = lastPass?.player ?: StoneColor.Black
        askAfterNotice = !screenState.isGameEnded && screenState.matchSeats.current.canAcceptBoardInput
        showNotice = true
        delay(PassNoticeMillis)
        showNotice = false
        if (askAfterNotice) showScorePrompt = true
    }

    if (showNotice) {
        PassNoticeDialog(player = noticePlayer)
    }

    if (showScorePrompt) {
        ScoreNowPromptDialog(
            onYes = {
                showScorePrompt = false
                latestOnPassAgain()
            },
            onNo = { showScorePrompt = false },
        )
    }
}

/**
 * **통과한 진영의 자리에서 날아와 화면 가운데에 꽂히는 알림**(백로그 #177, 2026-09-19 사용자).
 *
 * ## 왜 날아오는가
 * 통과는 판에 **아무 흔적을 남기지 않는다.** 가운데에 조용히 나타나기만 하면 *"무엇이 통과했는지"*
 * 가 빠진다 — 움직임의 **출발점**이 그 답을 말한다. 좌석 카드는 흑이 왼쪽, 백이 오른쪽에 있고
 * 둘 다 판 아래에 있으므로, 그 방향에서 날아오면 누가 통과했는지가 글자를 읽기 전에 보인다.
 *
 * ## 색은 진영을 한 번 더 말한다
 * - **흑 통과** — 불투명한 **흰 상자** 위에 검은 글자
 * - **백 통과** — 불투명한 **검은 상자** 위에 흰 글자
 *
 * ⚠️ **상자는 불투명하고 글자만 반투명하다**(사용자 지시). 상자를 비치게 하면 판의 격자와 돌이
 * 글자에 겹쳐 읽히지 않는다 — 배경이 무엇이든 같은 대비를 보장하려고 상자를 막았다.
 *
 * ⚠️ **버튼이 없다** — 사용자가 닫을 것이 아니라 *"방금 이런 일이 있었다"* 를 알리는 자리다.
 * 뒤로가기·바깥 탭으로는 닫히게 둔다(급한 사용자를 붙잡아 두지 않는다).
 *
 * ⚠️ **`usePlatformDefaultWidth = false`가 필요하다** — 기본 다이얼로그 폭 안에서는 좌석 자리까지
 * 날아올 거리가 없다. 화면 전체를 받아야 출발점을 좌·우 아래로 잡을 수 있다.
 */
@Composable
private fun PassNoticeDialog(player: StoneColor) {
    val strings = LocalUiStrings.current
    // ⚠️ 이 알림이 떠 있는 동안 첫돌이 가이드를 기록하지 않는다(함정 40).
    GuideBlockingOverlays.TrackWhileShown()

    val isBlack = player == StoneColor.Black
    val boxColor = if (isBlack) Color.White else Color.Black
    // ⚠️ 글자 불투명도는 **낮추되 읽히는 선까지만** — 0.6 아래로 내리면 배율 1.0의 작은 화면에서
    //   획이 뭉개진다(#177 실기 조정).
    val textColor = (if (isBlack) Color.Black else Color.White).copy(alpha = 0.82f)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 출발점 — 좌석 카드가 있는 **판 아래**의 좌(흑)·우(백).
            // ⚠️ 화면 폭·높이의 비율로 잡는다. 고정 dp로 잡으면 큰 화면(폴드)에서 출발점이
            //   화면 안쪽으로 들어와 "날아온다"가 사라진다(함정 45가 경계한 것과 같은 결).
            val density = LocalDensity.current
            val startX = with(density) { (maxWidth * if (isBlack) -0.30f else 0.30f).toPx() }
            val startY = with(density) { (maxHeight * 0.38f).toPx() }

            val fly = remember { Animatable(0f) }
            val scale = remember { Animatable(0.35f) }
            LaunchedEffect(Unit) {
                launch { fly.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing)) }
                // 커졌다가 되튀는 것이 "탕!"이다 — 도착과 동시에 한 번 넘겼다가 제자리로 돌아온다.
                scale.animateTo(1.18f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = boxColor,
                border = BorderStroke(1.5.dp, textColor.copy(alpha = 0.25f)),
                modifier = Modifier.graphicsLayer {
                    translationX = startX * (1f - fly.value)
                    translationY = startY * (1f - fly.value)
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = (fly.value * 2.2f).coerceAtMost(1f)
                },
            ) {
                Text(
                    text = passNoticeTitleFor(strings.language),
                    // ⚠️ 고정 높이를 쓰지 않는다(함정 9) — 글꼴 배율 1.3에서 상자가 글자를 자르면 안 된다.
                    modifier = Modifier.padding(horizontal = 44.dp, vertical = 24.dp),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = textColor,
                )
            }
        }
    }
}

/** *"계가 하시겠습니까?"* — **예**는 «나도 통과»다(위 KDoc). */
@Composable
private fun ScoreNowPromptDialog(onYes: () -> Unit, onNo: () -> Unit) {
    val strings = LocalUiStrings.current
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(scoreNowPromptTitleFor(strings.language)) },
        text = { Text(scoreNowPromptBodyFor(strings.language)) },
        confirmButton = { TextButton(onClick = onYes) { Text(strings.yes) } },
        dismissButton = { TextButton(onClick = onNo) { Text(strings.no) } },
    )
}
