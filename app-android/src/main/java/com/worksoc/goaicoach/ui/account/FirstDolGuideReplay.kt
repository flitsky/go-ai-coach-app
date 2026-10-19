package com.worksoc.goaicoach.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.application.guide.GuideTarget
import com.worksoc.goaicoach.application.premium.state.MatchFreeUses
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameActionButtonState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.ui.board.PlayStoneButton
import com.worksoc.goaicoach.ui.board.playStoneFaceOf
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.SingleActionButton
import com.worksoc.goaicoach.ui.designsystem.ToggleActionButton
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.guide.FirstDolAvatar
import com.worksoc.goaicoach.ui.home.GameHistoryBoardIcon
import com.worksoc.goaicoach.ui.home.GamePlayPreviewIcon
import com.worksoc.goaicoach.ui.home.HomeMenuTile
import com.worksoc.goaicoach.ui.home.MenuCard
import com.worksoc.goaicoach.ui.home.MenuTile
import com.worksoc.goaicoach.ui.home.RankMeasureIcon
import com.worksoc.goaicoach.ui.home.StudyPreviewIcon
import com.worksoc.goaicoach.ui.home.currentAiCharacterOrDefault
import com.worksoc.goaicoach.ui.l10n.GuideToolLabels
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide
import com.worksoc.goaicoach.ui.l10n.UiStringsGuideReplay
import com.worksoc.goaicoach.ui.l10n.freeAnalysisMarkFor
import com.worksoc.goaicoach.ui.l10n.guideBodyFor
import com.worksoc.goaicoach.ui.l10n.guideMyPageGreetingFor
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.myCharactersTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSubtitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.settingsHelpSectionFor

/**
 * **가이드 다시보기**(백로그 #128). 진입점은 **설정 하단** 하나다(`SettingsHelpLinks`, 백로그 #241 — 2026-10-09에 사용자가
 * 처음의 ⓑ *"마이페이지에만"* 을 뒤집었다).
 *
 * ## ⚠️ `Dialog`여야 한다 — 창 안 오버레이로 그리면 뒤로가기에 진다
 *
 * 셸의 `BackHandler`(`GoCoachApp.kt`)가 **마이페이지에서 활성**이고 `exitToHome()`을 부른다.
 * 같은 창 안에 오버레이로 그리면 뒤로가기가 이 화면을 닫는 대신 **홈으로 나가 버린다** —
 * 설계 심사가 코드로 확인해 지시한 항목이다. 별도 윈도우(`Dialog`)는 자기 뒤로가기를 갖는다.
 *
 * ## ⚠️ 그림이 아니라 **실물을 조립한다**
 *
 * ③은 홈의 **진짜 `MenuCard`** 를 같은 문구(`strings.startMatch`)로 그리고, ⑤ 둘은 **판 위에 적히는
 * 그 라벨**(`strings.eval`·`strings.topMovesAction`)을 `GuideToolLabels`로 그대로 부른다.
 * 정적 삽화나 캡처를 쓰지 않는 이유는
 * 이 저장소의 이력이다 — 화면이 바뀔 때 그림만 낡아 **다시보기가 거짓을 말하는** 사고를 네 번
 * 겪었다(#87·#97·#124·#127, §0 B-2). 실물을 부르면 화면이 바뀌는 순간 여기도 함께 바뀐다.
 *
 * ## ⚠️ 진행도를 **되감지 않는다**
 *
 * 여기서 `armed`나 `seen_steps`를 건드리면 다시보기를 한 번 볼 때마다 **자동 재생이 다시 무장돼**
 * 홈·대국에서 말풍선이 또 뜬다. 그래서 **진행도 저장소는 열지 않는다**(`FirstDolGuideContractTest`가
 * 그것을 계약으로 지킨다).
 * (한때 ④ 문구가 접바둑·좌석을 인용해서 **설정 저장소는 읽었다** — #140이 그 문구를 한 가지로 바꿔
 * 이제는 읽지 않는다. 금지 대상은 그때도 지금도 저장소 일반이 아니라 **진행도**다.)
 *
 * ## 왜 마법사가 아니라 한 장의 스크롤인가
 *
 * 재생할 단계가 다섯이라(②③④ + ⑤ 둘) 마법사로 만들면 **다섯 번 눌러야** 하고, 정작 다시보기의
 * 목적은 *"아까 그거 뭐였지"* 를 **골라 읽는** 것이다. 그래서 화면별로 묶어 한 장에 늘어놓는다.
 */
@Composable
internal fun FirstDolGuideReplayDialog(onClose: () -> Unit) {
    val strings = LocalUiStrings.current
    val toolLabels = GuideToolLabels(
        eval = strings.eval,
        topMoves = strings.topMovesAction,
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FirstDolAvatar(size = 32.dp)
                        Text(
                            text = strings.guideReplayAction,
                            fontSize = AppTextSize.Text18,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space16),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Space20),
                ) {
                    // 화면별로 묶는다 — 사용자가 *"그 화면에서 본 것"* 으로 찾는다.
                    ReplaySection(title = strings.myPageTitle) {
                        ReplayLine(guideMyPageGreetingFor(strings.language))
                        // #261 — 내 정보의 「내 캐릭터」(#256)와 구독. 줄은 실물이 아니라 글로만 — 그 줄은 누르면 캐러셀이 열리는 살아 있는 줄이다.
                        ReplayLine(UiStringsGuideReplay.myCharacters(strings.language, myCharactersTitleFor(strings.language)))
                        ReplayLine(UiStringsGuideReplay.premium(strings.language))
                    }
                    ReplaySection(title = strings.attendanceRewardTitle) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.AttendanceClaim))
                    }
                    ReplaySection(title = strings.startMatch) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.HomeStartMatch))
                        // ③이 가리키는 그 카드를 **진짜로** 그린다(위 KDoc). 누르면 다시보기를
                        // 닫는 편이 자연스러울 수도 있지만, 여기서는 **보여주는 것**이 목적이라
                        // 아무 일도 하지 않는다.
                        MenuCard(
                            title = strings.startMatch,
                            subtitle = strings.homeStartMatchSubtitle,
                            onClick = {},
                            // ⚠️ **마지막 선택이 아니라 항상 기본값이다**(백로그 #181). 이 다시보기는
                            // 진행도 저장소를 열지 않는다는 계약이 있는데(위 KDoc), 실제 선택을 보여
                            // 주려면 `playerSetup`을 이 다이얼로그까지 새로 꿰어야 한다 — 여기서 보여
                            // 주는 것은 "이 카드가 무엇인가"이지 "지금 무엇이 골라져 있나"가 아니므로
                            // 기본 캐릭터(수제자 반상)로 충분하다.
                            icon = { GamePlayPreviewIcon(character = currentAiCharacterOrDefault(PlayerSetup())) },
                        )
                    }
                    ReplaySection(title = strings.matchSetup) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.MatchSetup))
                    }
                    ReplaySection(title = strings.guideReplayInGameTitle) {
                        // ⚠️ **문구마다 그 버튼을 실물로 함께 그린다**(2026-09-09 사용자 지시:
                        // *"'대국 하기' 버튼 보여주듯 각 가이드마다 해당 버튼이 그려지면 좋겠다"*).
                        // 아래 버튼 둘은 `ToggleActionButton` — (판 위 토글 둘은 #143이 메뉴로 옮기며 단계째 사라졌다.)
                        // 대국 화면이 쓰는 **그 컴포저블**이다. 라벨도 같은 함수에서 나온다.
                        GuideStep.entries
                            .filter { it.surface == GuideSurface.InGame }
                            .forEach { step ->
                                ReplayControlCard(
                                    step = step,
                                    text = guideBodyFor(strings.language, step, toolLabels = toolLabels),
                                )
                            }
                        // #261 — 첫돌이 가이드 뒤에 생긴 대국 화면의 버튼 둘. 같은 방식으로 **실물**을 그린다(누르면 아무 일 없음).
                        // 착수 확인은 플래그가 꺼진 빌드에서 가르치지 않는다(함정 51 — 조작법 가이드와 같은 판정).
                        if (FeatureFlags.isPlayConfirmModeEnabled) {
                            ReplaySampleCard(text = UiStringsGuideReplay.confirmButton(strings.language, strings.confirmPlay, strings.playMove)) {
                                PlayStoneButton(
                                    face = playStoneFaceOf(isDirectPlay = false, hasTentativeMove = false),
                                    label = strings.confirmPlayOnStone,
                                    enabled = true,
                                    onClick = {},
                                    modifier = Modifier.size(ReplayStoneButtonSize),
                                )
                            }
                        }
                        ReplaySampleCard(text = UiStringsGuideReplay.undo(strings.language, strings.undo)) {
                            SingleActionButton(
                                action = GameActionButtonState(
                                    role = GameActionButtonRole.Undo,
                                    label = strings.undo,
                                    // 이벤트는 쏘지 않는다 — 아래 `onEvent`가 빈 람다다.
                                    event = GameUiEvent.UndoLastTurn,
                                    enabled = true,
                                    isFilled = false,
                                ),
                                label = strings.undo,
                                // 대국 화면이 판 시작에 보이는 그 표시(`무료 3`) — 숫자는 상수에서.
                                mark = freeAnalysisMarkFor(strings.language, MatchFreeUses.PerMatch),
                                onEvent = {},
                            )
                        }
                    }
                    // #261 — 홈의 나머지 메뉴. 카드와 타일은 홈이 그리는 **그 컴포저블**이다. 기력은 저장소를 열지 않고 예시(20급)로 —
                    // 이 다이얼로그는 진행도 저장소를 열지 않는다는 계약이 있고, 보여 주는 것은 "이 카드가 무엇인가"다(캐릭터 대국 카드와 같은 이유).
                    ReplaySection(title = rankMeasureTitleFor(strings.language)) {
                        ReplayLine(UiStringsGuideReplay.rankMeasure(strings.language))
                        MenuCard(
                            title = rankMeasureTitleFor(strings.language),
                            subtitle = rankMeasureSubtitleFor(strings.language),
                            onClick = {},
                            icon = { RankMeasureIcon(rank = ReplaySampleRank, rankLabel = kgsRankLabelFor(strings.language, ReplaySampleRank)) },
                        )
                    }
                    ReplaySection(title = strings.gameHistoryTitle) {
                        ReplayLine(UiStringsGuideReplay.gameHistory(strings.language))
                        MenuTile(HomeMenuTile(strings.gameHistoryTitle, onClick = {}) { GameHistoryBoardIcon() }, modifier = Modifier.fillMaxWidth(0.5f))
                    }
                    ReplaySection(title = strings.study) {
                        ReplayLine(UiStringsGuideReplay.study(strings.language))
                        MenuTile(HomeMenuTile(strings.study, onClick = {}) { StudyPreviewIcon() }, modifier = Modifier.fillMaxWidth(0.5f))
                    }
                    // #261 — 바둑판 조작법(#249·#258)으로 가는 길. 이름은 설정이 쓰는 그 글자다.
                    ReplaySection(title = settingsHelpSectionFor(strings.language)) {
                        ReplayLine(
                            UiStringsGuideReplay.boardControls(
                                strings.language,
                                helpTitle = settingsHelpSectionFor(strings.language),
                                guideTitle = UiStringsDownloadGuide.boardControlsGuide(strings.language),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 한 단계를 **박스 하나**에 담는다 — 첫 줄에 `첫돌이 + 그 버튼`, 그 아래 설명(2026-09-09 사용자 지시).
 *
 * ## ⚠️ 왜 이 모양인가 — 버튼이 설명 사이에 끼면 위아래가 헷갈린다
 *
 * 처음에는 설명 박스와 버튼을 **형제로** 늘어놓았다(`ReplayLine` 다음에 컨트롤). 그러면 버튼이
 * 위 설명의 것인지 아래 설명의 것인지 알 수 없다 — 사용자가 캡처를 보고 지적한 지점이다.
 * 박스가 경계를 그어 주고, **첫 줄이 "무엇에 대한 설명인가"** 를 먼저 말하는 순서가 된다.
 */
@Composable
private fun ReplayControlCard(step: GuideStep, text: String) {
    ReplaySampleCard(text = text) { ReplayControlSample(step) }
}

/** [ReplayControlCard]의 몸 — 첫 줄에 `첫돌이 + 그 실물`, 아래 설명. #261에서 단계(`GuideStep`)가 없는 버튼(착수 확인 · 무르기)도 같은 상자에 담으려고 뗐다. */
@Composable
private fun ReplaySampleCard(text: String, sample: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(AppRadius.Corner12))
            .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space10),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FirstDolAvatar(size = 26.dp, seamColor = MaterialTheme.colorScheme.surfaceVariant)
            sample()
        }
        Text(
            text = text,
            fontSize = AppTextSize.Text13,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 그 단계가 가리키는 **실제 컨트롤**을 그린다.
 *
 * ⚠️ **눌러도 아무 일도 하지 않는다** — 여기서 실제로 켜지면 다시보기가 대국 설정을 바꾸는
 * 화면이 된다. 보여주는 것이 목적이라 `onClick`/`onEvent`를 빈 람다로 넘긴다.
 * ⚠️ 잔량(`형세 보기 (30)`의 괄호)은 **인용하지 않는다** — 다시보기가 그 숫자를 말하면
 * 재고가 바뀔 때마다 낡는다. `mark`를 비워 이름만 보여 준다.
 */
@Composable
private fun ReplayControlSample(step: GuideStep) {
    val strings = LocalUiStrings.current
    when (step.target) {
        GuideTarget.Eval -> ReplayActionButtonSample(
            role = GameActionButtonRole.Eval,
            label = strings.eval,
        )
        GuideTarget.TopMoves -> ReplayActionButtonSample(
            role = GameActionButtonRole.TopMoves,
            label = strings.topMovesAction,
        )
        // 홈 윗줄 배경음악 버튼(#270)은 1.6.x의 **업데이트 안내**라 다시보기에 싣지 않는다 — 이 함수는 대국 화면 단계만 받는다.
        GuideTarget.MusicToggle, null -> Unit
    }
}

@Composable
private fun ReplayActionButtonSample(role: GameActionButtonRole, label: String) {
    ToggleActionButton(
        action = GameActionButtonState(
            role = role,
            label = label,
            // 이벤트는 쏘지 않는다 — 아래 `onEvent`가 빈 람다다.
            event = GameUiEvent.ToggleEvalWithGradient,
            enabled = true,
            isFilled = false,
        ),
        label = label,
        onEvent = {},
    )
}

@Composable
private fun ReplaySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun ReplayLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(AppRadius.Corner12))
            .padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space10),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        verticalAlignment = Alignment.Top,
    ) {
        FirstDolAvatar(size = 26.dp, seamColor = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            text = text,
            fontSize = AppTextSize.Text13,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 승급 대국 카드에 보이는 예시 기력(#261) — 처음 시작하는 자리. 진행도 저장소를 열지 않으므로 지금 기력이 아니다. */
private val ReplaySampleRank = KgsRank.kyu(20)

private val ReplayStoneButtonSize = 56.dp
