package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.score.FinalScoreJudgement

internal fun FinalScoreJudgement.dialogKey(moveCount: Int): String =
    listOf(
        moveCount.toString(),
        winner?.name.orEmpty(),
        margin?.toString().orEmpty(),
        ruleset.name,
        isEstimatedDisplay.toString(),
        removedBlack.toString(),
        removedWhite.toString(),
        handicapCount.toString(),
    ).joinToString("|")

@Composable
internal fun FinalJudgementDialog(
    judgement: FinalScoreJudgement,
    strings: UiStrings,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    /**
     * 「복기 하기」 — 이 판을 다시보기로 연다(백로그 #185).
     *
     * ⚠️ **여기 있던 「재 대국」을 밀어낸 자리다.** 재대국 길은 하단 액션바에 그대로 있으므로
     * 막히지 않지만, **복기는 이 팝업을 닫으면 찾기 어렵다** — 그래서 더 값진 쪽을 남겼다
     * (2026-09-22 사용자 결정). 버튼은 계속 **둘**이다(2026-09-18 결정 유지).
     */
    onReplay: () -> Unit,
) {
    // ⚠️ 이 팝업이 떠 있는 동안 첫돌이 가이드를 **기록하지 않는다** — 뒤에 깔린 채 "봤음"으로
    //   소진되는 것을 막는다(그 사유는 `GuideBlockingOverlays`의 KDoc).
    GuideBlockingOverlays.TrackWhileShown()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.finalJudgementTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(judgement.resultText(strings))
                Text(judgement.gameModeLine(strings))
                Text(judgement.scoringRuleLine(strings))
                Text(judgement.removedStonesLine(strings))
                judgement.blackLine(strings)?.let { Text(it) }
                judgement.whiteLine(strings)?.let { Text(it) }
                judgement.note(strings)?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onReview) {
                Text(strings.reviewJudgement)
            }
        },
        // ⚠️ **버튼은 둘로 유지한다**(2026-09-18 사용자). 확인 쪽이 오른쪽(`confirmButton`),
        // 복기가 그 왼쪽이다 — Material3의 기본 배치가 그대로 원하는 순서를 만든다.
        dismissButton = {
            TextButton(onClick = onReplay) {
                // ⚠️ 목적지 화면의 제목(「대국 다시보기」)과 문구가 다르다 — 사유는
                // `reviewGameActionFor`의 KDoc(사용자 어휘를 따랐다).
                Text(reviewGameActionFor(strings.language))
            }
        },
    )
}
