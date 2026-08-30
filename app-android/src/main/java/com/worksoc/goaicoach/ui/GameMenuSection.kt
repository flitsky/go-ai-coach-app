package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.BuildConfig
import com.worksoc.goaicoach.presentation.GameActionButtonRole
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.formatBuildTime



/**
 * 대국 화면 최상단. **두 줄로 나뉜다**(#35, 2026-08-30 사용자 지시).
 *
 * ```
 * [빌드시각]        수순 12수                 [☰]
 * 흑: 유저 / 백: KataGo 초고수
 * ```
 *
 * **왜 수순이 위로 올라왔는가**: 원래 `수순 N수`는 `GameStatusPanel`의 가운데 칸에서 `착수`
 * 버튼 바로 위에 있었다. 대국 중 계속 보게 되는 값인데 버튼 부속처럼 보였고, 무엇보다 그
 * 자리는 착수 모드 스위치가 쓸 자리다(#37). 그래서 헤더로 올리면서 **흑/백 정보가 쓰던
 * 글자 크기·색을 그대로 물려받아** 이 화면에서 가장 눈에 띄는 텍스트가 되게 했다.
 *
 * **왜 흑/백 정보는 아래로 내려갔는가**: 대국 시작 시 한 번 확인하면 되는 정보라 수순보다
 * 중요도가 낮다. 빌드시각 바로 위 수준(`labelSmall`, 흐린 색)으로 낮추되, **폭은 한 줄
 * 전체를 쓴다** — 이전에는 가중치 1:2:1의 가운데 칸(화면 폭의 절반)에 갇혀 배율 2.0배에서
 * 3줄이 필요했다(#30). 폭이 두 배가 되고 글자가 작아져 그 압박이 통째로 사라졌다.
 *
 * 엔진이 연산 중이라는 신호(primary 색 + 굵게)는 흑/백 줄에 그대로 남긴다 — 중요도를 낮춘
 * 것이지 없앤 것이 아니다.
 */
@Composable
internal fun GameHeaderSection(
    screenState: GameScreenState,
    isDisplayMenuExpanded: Boolean,
    onDisplayMenuExpandedChange: (Boolean) -> Unit,
) {
    val strings = LocalUiStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // [1] 좌측 끝: 빌드타임 [260717 15:33] — 개발/QA용 정보라 시각적 비중은 낮춘다.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = formatBuildTime(BuildConfig.BUILD_TIME),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }

            // [2] 가운데: 수순. `bodyMedium` + secondary는 **흑/백 줄이 쓰던 그대로**다 —
            // 두 정보의 우선순위를 맞바꾼 것이 이 항목의 요지이므로 서식도 함께 바꿔 준다.
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${strings.moveCountPrefix} ${screenState.gameState.moves.size}${strings.moveCountSuffix}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    // 한 줄로 충분하다 — 가장 긴 표기가 `Moves 999`(라틴) 수준이라 배율
                    // 2.0배·좁은 폭에서도 이 칸(화면 폭의 절반)을 넘지 않는다.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            // [3] 우측 끝: [메뉴 버튼]
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                KaTrainUxMenuButton(
                    menuExpanded = isDisplayMenuExpanded,
                    onMenuExpandedChange = onDisplayMenuExpandedChange,
                )
            }
        }

        // [4] 아랫줄: 흑/백 엔진 설정. 폭을 통째로 쓰되 비중은 빌드시각 바로 위 수준으로 낮춘다.
        Text(
            text = strings.setupSummary(screenState.playerSetup, screenState.engine.name),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = if (screenState.engine.isBusy) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
            },
            // 색만으로 구분하면 색맹 사용자에게 안 보인다 — 굵기도 함께 바꿔 이중으로 신호한다.
            fontWeight = if (screenState.engine.isBusy) FontWeight.Bold else FontWeight.Normal,
            // 폭이 한 줄 전체가 되고 글자가 `labelSmall`로 작아져, 예전 4줄 상한(#30)이
            // 필요했던 압박이 사라졌다. 그래도 엔진 이름은 길이 상한이 없으므로 2줄에서
            // 끊되 잘렸다는 사실은 보이게 한다.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ExpandedGameMenuSection(
    screenState: GameScreenState,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    onEvent: (GameUiEvent) -> Unit,
    showSettings: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSettings) {
            PlayerSetupPanel(
                state = screenState.playerSetupUi,
                enabled = !screenState.engine.isBusy,
                onPlayerSetupChange = { setup -> onEvent(GameUiEvent.ChangePlayerSetup(setup)) },
                onAutoPlayDelayChange = { setting -> onEvent(GameUiEvent.ChangeAutoPlayDelay(setting)) },
            )

            ScoringAndBoardSettingsPanel(
                ruleset = screenState.gameState.ruleset,
                boardSize = screenState.gameState.boardSize,
                handicapCount = screenState.handicapCount,
                komi = screenState.gameState.komi,
                canChangeRuleset = true,
                canChangeBoardSize = screenState.isGameEnded,
                canChangeHandicap = screenState.isGameEnded,
                canChangeKomi = true,
                onRulesetChange = { ruleset -> onEvent(GameUiEvent.ChangeScoringRule(ruleset)) },
                onBoardSizeChange = { size -> onEvent(GameUiEvent.ChangeBoardSize(size)) },
                onHandicapCountChange = { count -> onEvent(GameUiEvent.ChangeHandicapCount(count)) },
                onKomiChange = { komi -> onEvent(GameUiEvent.ChangeKomi(komi)) },
            )
        }

        LanguageSettingsPanel(
            selectedLanguage = selectedLanguage,
            onLanguageChange = onLanguageChange,
        )

        KaTrainUxMenuPanel(
            options = screenState.uxOptions,
            onOptionsChange = { nextOptions -> onEvent(GameUiEvent.ChangeUxOptions(nextOptions)) },
            isTopMovesEveryMove = screenState.actionButtons
                .firstOrNull { it.role == GameActionButtonRole.TopMoves }?.isFilled == true,
            // 추천 수는 uxOptions가 아니라 세션 설정에 있어 전용 이벤트로 뒤집는다.
            onTopMovesEveryMoveChange = { onEvent(GameUiEvent.ToggleTopMoves) },
        )

        SearchTimeSettingsPanel(
            settings = screenState.searchTimeSettings,
            enabled = !screenState.engine.isBusy,
            onSettingsChange = { settings -> onEvent(GameUiEvent.ChangeSearchTimeSettings(settings)) },
        )

        GameMenuActionsPanel(
            onCopyLog = { onEvent(GameUiEvent.CopyDebugReport) },
            onBenchmark = { onEvent(GameUiEvent.ShowEngineBenchmark) },
        )
    }
}

@Composable
internal fun LanguageSettingsPanel(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 나열형(`SettingChoiceRow`)이 아니라 드롭다운이다(#34) — 언어가 늘어도 한 줄이
            // 무너지지 않는다. 라벨과 컨트롤을 한 행에 놓아 다른 설정 항목과 결을 맞춘다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LanguageDropdownChip(
                    selectedLanguage = selectedLanguage,
                    onLanguageChange = onLanguageChange,
                )
            }
        }
    }
}

/**
 * 언어 선택 드롭다운. **원래 홈 우상단 칩이었는데 설정 화면 안으로 들어왔다**(#34,
 * 2026-08-30 사용자 지시).
 *
 * 설정에 이미 언어 절이 있었지만 [SettingChoiceRow]로 **지원 언어를 전부 나열**하는 방식이라,
 * 언어가 늘수록 한 줄이 감당이 안 된다. 드롭다운은 항목이 몇 개든 칩 하나로 접힌다 —
 * 그래서 나열형을 버리고 이쪽을 남겼다.
 */
@Composable
internal fun LanguageDropdownChip(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "🌐 ${selectedLanguage.menuLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            UiLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = lang.menuLabel,
                            fontWeight = if (lang == selectedLanguage) FontWeight.Bold else FontWeight.Normal,
                            color = if (lang == selectedLanguage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onLanguageChange(lang)
                    },
                )
            }
        }
    }
}
