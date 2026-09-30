package com.worksoc.goaicoach.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.premium.state.FeatureAccess
import com.worksoc.goaicoach.application.premium.state.FeatureId
import com.worksoc.goaicoach.platform.PlayHaptics
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.PremiumGoldDeep
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.MenuSection
import com.worksoc.goaicoach.ui.l10n.boardSizeToggleLabelFor
import com.worksoc.goaicoach.ui.l10n.largeHeldStoneLabelFor
import com.worksoc.goaicoach.ui.l10n.menuSectionTitleFor
import com.worksoc.goaicoach.ui.monetization.LocalPremiumUiState
import com.worksoc.goaicoach.ui.monetization.PremiumUpsellDialogHost

@Composable
internal fun KaTrainUxMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    OutlinedButton(
        onClick = { onMenuExpandedChange(!menuExpanded) },
    ) {
        Text(if (menuExpanded) strings.close else "\u2630")
    }
}

@Composable
internal fun KaTrainUxMenuPanel(
    options: KaTrainUxOptions,
    onOptionsChange: (KaTrainUxOptions) -> Unit,
    isTopMovesEveryMove: Boolean = false,
    onTopMovesEveryMoveChange: (Boolean) -> Unit = {},
) {
    val strings = LocalUiStrings.current
    val premium = LocalPremiumUiState.current
    var showPremiumUpsellDialog by remember { mutableStateOf(false) }
    val columnGap = (LocalConfiguration.current.screenWidthDp * 0.05f).dp

    PremiumUpsellDialogHost(
        visible = showPremiumUpsellDialog,
        onDismiss = { showPremiumUpsellDialog = false },
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Corner8),
        tonalElevation = AppElevation.Level1,
        shadowElevation = AppElevation.Level0,
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Space12),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
        ) {
            // **메뉴는 섹션 셋이다**(백로그 #198, 2026-09-30 사용자 결정) — 바둑판 → 착수 → AI 코치.
            // 섹션 제목이 뜻을 실어 주므로 라벨은 짧다(「착수」 섹션의 「진동」·「애니메이션」). 2026-09-12에 정한 차례
            // (판 → 착수 → 착수 뒤 표시 → 프리미엄)를 섹션으로 묶은 것이다. ⚠️ 차례를 바꾸려거든 사용자에게 물을 것 —
            // `MenuOptionOrderContractTest`가 지킨다. ⚠️ 이 패널은 **☰ 대국 메뉴와 설정 화면이 공유**한다.
            //
            // **판 위에 있던 토글 둘이 여기로 내려왔다**(백로그 #143) — 둘 다 기본값이 이미 그 값이라 대부분 한 번도 누르지 않는다.
            val hapticContext = LocalContext.current
            val hapticPreview = remember(hapticContext) { PlayHaptics(hapticContext) }
            // 프리미엄 셋의 판정은 FeatureAccessPolicy(6계층)에 위임한다. ⚠️ **라벨을 프리미엄 색으로 적는다**(2026-09-12) —
            // 흐리게(alpha)만 두면 "지금 못 쓴다"로는 읽혀도 **"프리미엄 기능이다"로는 읽히지 않는다.**
            val evalAllowed = premium.resolve(FeatureId.Eval) is FeatureAccess.Allowed
            val topMovesAllowed = premium.resolve(FeatureId.TopMoves) is FeatureAccess.Allowed
            val moveReviewAllowed = premium.resolve(FeatureId.MoveReview) is FeatureAccess.Allowed

            // ── 바둑판 ──
            OptionSectionTitle(title = menuSectionTitleFor(strings.language, MenuSection.Board), isFirst = true)
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionSwitchCell(
                    // ⚠️ 스위치의 라벨은 **켰을 때의 상태**를 적는다(`바둑판 최대`) — 주체 이름(`바둑판 크기`)만
                    // 적으면 켜짐이 무엇을 뜻하는지 알 수 없다.
                    label = boardSizeToggleLabelFor(strings.language, isMaxSize = true),
                    checked = options.isBoardMaxSize,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(isBoardMaxSize = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                OptionSwitchCell(
                    label = strings.coordinates,
                    checked = options.showCoordinates,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showCoordinates = it)) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionSwitchCell(
                    label = strings.moveNumbers,
                    checked = options.showMoveNumbers,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showMoveNumbers = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                Spacer(modifier = Modifier.weight(1f))
            }

            // ── 착수 ──
            OptionSectionTitle(title = menuSectionTitleFor(strings.language, MenuSection.Placing))
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionSwitchCell(
                    // 「마지막 수」 — 방금 둔 돌에 두르는 고리(#198 전 「착수 표시」).
                    label = strings.lastMoveRing,
                    checked = options.showLastMoveRing,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(showLastMoveRing = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                // 반상을 누르는 순간의 햅틱(#36). **켤 때 한 번 울려 준다**(2026-08-30 사용자 요청) —
                // 진동은 눈에 보이지 않아 켠 것이 먹혔는지 알 길이 없다. 끌 때는 울리지 않는다.
                OptionSwitchCell(
                    label = strings.playHaptic,
                    checked = options.isPlayHapticEnabled,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { enabled ->
                        if (enabled) hapticPreview.play()
                        onOptionsChange(options.copy(isPlayHapticEnabled = enabled))
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                // **애니메이션**(백로그 #145, #198 전 「착수 이펙트」) — 확정되는 순간 그 돌이 부풀었다가 제자리로 돌아온다.
                OptionSwitchCell(
                    label = strings.playEffect,
                    checked = options.isPlayEffectEnabled,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(isPlayEffectEnabled = it)) },
                )
                Spacer(modifier = Modifier.width(columnGap))
                // 「끌 때 크게」(백로그 #196·#197) — 길게 눌러 끌며 조준하는 동안 가늠돌을 키울지.
                OptionSwitchCell(
                    label = largeHeldStoneLabelFor(strings.language),
                    checked = options.isLargeHeldStoneEnabled,
                    modifier = Modifier.weight(1f),
                    onCheckedChange = { onOptionsChange(options.copy(isLargeHeldStoneEnabled = it)) },
                )
            }
            // '착수 확인 / 바로 착수'는 #143이 UX에서 지웠다 — 코드는 플래그 뒤에 그대로 남는다
            // (`FeatureFlags.isPlayConfirmModeEnabled`). 플래그가 켜지면 착수 섹션에 한 줄을 받는다.
            if (FeatureFlags.isPlayConfirmModeEnabled) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OptionSwitchCell(
                        label = strings.directPlay,
                        checked = options.isDirectPlayEnabled,
                        modifier = Modifier.weight(1f),
                        onCheckedChange = { onOptionsChange(options.copy(isDirectPlayEnabled = it)) },
                    )
                    Spacer(modifier = Modifier.width(columnGap))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // ── AI 코치(프리미엄) ── 대국 화면의 형세 보기·추천 수 버튼은 **1회성**이고, 수가 진행돼도 계속 갱신되는
            // 상시 표시는 여기서 켠다(2026-08-29). 제목도 프리미엄 색이다 — 섹션 전체가 프리미엄이라는 신호.
            OptionSectionTitle(title = menuSectionTitleFor(strings.language, MenuSection.AiCoach), color = PremiumGoldDeep)
            Row(modifier = Modifier.fillMaxWidth()) {
                OptionSwitchCell(
                    label = strings.everyMoveEval,
                    checked = options.showOwnershipOverlay && evalAllowed,
                    modifier = Modifier.weight(1f),
                    labelColor = PremiumGoldDeep,
                    switchAlpha = if (evalAllowed) 1f else 0.5f,
                    onCheckedChange = {
                        if (evalAllowed) {
                            onOptionsChange(options.copy(showOwnershipOverlay = it))
                        } else {
                            showPremiumUpsellDialog = true
                        }
                    },
                )
                Spacer(modifier = Modifier.width(columnGap))
                OptionSwitchCell(
                    label = strings.everyMoveTopMoves,
                    checked = isTopMovesEveryMove && topMovesAllowed,
                    modifier = Modifier.weight(1f),
                    labelColor = PremiumGoldDeep,
                    switchAlpha = if (topMovesAllowed) 1f else 0.5f,
                    onCheckedChange = {
                        if (topMovesAllowed) {
                            onTopMovesEveryMoveChange(it)
                        } else {
                            showPremiumUpsellDialog = true
                        }
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                // 착수 평가: 착수한 돌의 품질 색상 표시 여부. 기본 꺼짐 — 사용자가 의도적으로 켤 때만 노출한다.
                OptionSwitchCell(
                    label = strings.moveReviewToggle,
                    checked = options.showMoveReview && moveReviewAllowed,
                    modifier = Modifier.weight(1f),
                    labelColor = PremiumGoldDeep,
                    switchAlpha = if (moveReviewAllowed) 1f else 0.5f,
                    onCheckedChange = {
                        if (moveReviewAllowed) {
                            onOptionsChange(options.copy(showMoveReview = it))
                        } else {
                            showPremiumUpsellDialog = true
                        }
                    },
                )
                Spacer(modifier = Modifier.width(columnGap))
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 메뉴 섹션 제목(#198). 섹션 사이는 위 여백으로 가른다 — 선을 긋지 않는다(카드 하나 안의 묶음이라). */
@Composable
private fun OptionSectionTitle(title: String, isFirst: Boolean = false, color: Color? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color ?: MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (isFirst) 0.dp else AppSpacing.Space8),
    )
}

@Composable
private fun OptionSwitchCell(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    /**
     * 라벨 글자색. 기본값은 보통 옵션의 색이고, **프리미엄 전용 옵션만** 금색을 넘긴다
     * (2026-09-12 사용자 요청). ⚠️ 흐리게(alpha)만으로는 *"지금 못 쓴다"* 로 읽힐 뿐
     * *"프리미엄 기능이다"* 로는 읽히지 않아 색을 따로 준다.
     */
    labelColor: Color? = null,
    /**
     * **스위치만** 흐려지는 정도(0~1). 잠긴 프리미엄 옵션이 1 미만을 넘긴다.
     *
     * ⚠️ **라벨까지 흐리지 않는다**(2026-09-12 사용자 결정 ⓐ안). 흐림은 *"지금 잠겨 있다"*,
     * 금색은 *"프리미엄 기능이다"* 로 **서로 다른 말을 한다** — 둘을 겹쳐 걸면 금색이 배경으로
     * 섞여(실측 `(191,175,136)`, 의도 `(138,100,22)`) 프리미엄이라는 신호가 죽는다.
     */
    switchAlpha: Float = 1f,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = labelColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.alpha(switchAlpha),
        )
    }
}


/**
 * 돋보기 설정 한 줄 — 라벨 + 값 몇 개를 고르는 칩(백로그 #85).
 *
 * ⚠️ **드롭다운이 아니라 칩을 나란히 둔다.** 이 줄의 목적이 *"바꿔 보며 비교하는 것"* 이라
 * 선택지가 항상 보여야 한 번의 탭으로 옮겨 다닐 수 있다 — 드롭다운은 매번 두 번 눌러야 한다.
 * 값이 둘·셋뿐이라 폭도 문제가 되지 않는다.
 */