package com.worksoc.goaicoach.ui.play

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferAcceptFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferBodyFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferDeclineFor
import com.worksoc.goaicoach.ui.l10n.aiResignationOfferTitleFor

/**
 * **AI의 기권 제안**(백로그 #213, 사용자 2026-10-07) — 형세가 가망 없이 기울면 AI가 **한 판에 한 번** 기권을 제안하고,
 * 사용자가 받아들이거나 계속 둔다. 대국 중반에 AI가 통과해 버리는 대신 이 창이 뜬다.
 *
 * 세션이 이 국면에서 사용자의 답을 기다리는 동안(`GameScreenState.isAwaitingAiResignationChoice`)만 그린다 — 그동안 AI는 두지 않는다.
 * ⚠️ **밖을 눌러서는 닫히지 않는다.** 실수로 닫히면 「계속 두기」로 처리되어 이 대국에서 다시 물을 길이 없다 — 둘 중 하나를 골라야 닫힌다.
 * ⚠️ 끝난 판에서는 그리지 않는다 — 받아들인 직후 판이 끝나는 프레임에 한 번 더 그려지지 않게.
 */
@Composable
internal fun AiResignationOfferHost(
    screenState: GameScreenState,
    onAnswer: (accepted: Boolean) -> Unit,
) {
    if (!screenState.isAwaitingAiResignationChoice || screenState.isGameEnded) return
    val language = LocalUiStrings.current.language
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(aiResignationOfferTitleFor(language), fontWeight = FontWeight.Bold) },
        text = { Text(aiResignationOfferBodyFor(language)) },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }, modifier = Modifier.testTag(AiResignationAcceptTag)) {
                Text(aiResignationOfferAcceptFor(language))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }, modifier = Modifier.testTag(AiResignationDeclineTag)) {
                Text(aiResignationOfferDeclineFor(language))
            }
        },
    )
}

internal const val AiResignationAcceptTag = "ai-resignation-accept"
internal const val AiResignationDeclineTag = "ai-resignation-decline"
