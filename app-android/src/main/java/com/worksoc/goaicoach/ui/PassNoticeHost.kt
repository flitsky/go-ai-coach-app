package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.shared.Move
import kotlinx.coroutines.delay

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
    val lastMoveIsPass = screenState.gameState.moves.lastOrNull() is Move.Pass
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
        askAfterNotice = !screenState.isGameEnded && screenState.matchSeats.current.canAcceptBoardInput
        showNotice = true
        delay(PassNoticeMillis)
        showNotice = false
        if (askAfterNotice) showScorePrompt = true
    }

    if (showNotice) {
        PassNoticeDialog()
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
 * 스스로 사라지는 알림. ⚠️ **버튼이 없다** — 사용자가 닫을 것이 아니라 *"방금 이런 일이 있었다"* 를
 * 알리는 자리다. 뒤로가기·바깥 탭으로는 닫을 수 있게 둔다(급한 사용자를 붙잡아 두지 않는다).
 */
@Composable
private fun PassNoticeDialog() {
    val strings = LocalUiStrings.current
    // ⚠️ 이 알림이 떠 있는 동안 첫돌이 가이드를 기록하지 않는다 — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(함정 40).
    GuideBlockingOverlays.TrackWhileShown()
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(shape = PremiumCardShape, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = passNoticeTitleFor(strings.language),
                    // ⚠️ 고정 높이를 쓰지 않는다(함정 9) — 글꼴 배율 1.3에서 상자가 글자를 자르면 안 된다.
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
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
