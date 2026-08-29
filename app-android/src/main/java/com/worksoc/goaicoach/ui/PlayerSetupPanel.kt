package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.clampToOwnedBotCharacter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.presentation.PlayerSetupSideUiState
import com.worksoc.goaicoach.presentation.PlayerSetupUiState
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import com.worksoc.goaicoach.shared.StoneColor

@Composable
internal fun PlayerSetupPanel(
    state: PlayerSetupUiState,
    enabled: Boolean,
    onPlayerSetupChange: (PlayerSetup) -> Unit,
    onAutoPlayDelayChange: (AutoPlayDelaySetting) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(strings.playerSetup, fontWeight = FontWeight.SemiBold)
            PlayerSetupSideRow(
                state = state.black,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(state.setup.updateSide(StoneColor.Black, side)) },
            )
            PlayerSetupSideRow(
                state = state.white,
                enabled = enabled,
                onSideChange = { side -> onPlayerSetupChange(state.setup.updateSide(StoneColor.White, side)) },
            )
            if (state.showAutoPlayDelay) {
                AutoPlayDelayRow(
                    selected = state.autoPlayDelaySetting,
                    onSelected = onAutoPlayDelayChange,
                )
            }
        }
    }
}

@Composable
internal fun SearchTimeSettingsPanel(
    settings: SearchTimeSettings,
    enabled: Boolean,
    onSettingsChange: (SearchTimeSettings) -> Unit,
) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MaximumSearchTimeLimitRow(
                selected = settings.limit,
                enabled = enabled,
                onSelected = { limit -> onSettingsChange(settings.withLimit(limit)) },
            )
        }
    }
}

@Composable
private fun MaximumSearchTimeLimitRow(
    selected: SearchTimeLimit,
    enabled: Boolean,
    onSelected: (SearchTimeLimit) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.maximumSearchTimeLimit,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = strings.searchTimeLimitLabel(selected),
            enabled = enabled,
            modifier = Modifier.weight(1f),
            options = SearchTimeLimit.entries,
            optionLabel = strings::searchTimeLimitLabel,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun AutoPlayDelayRow(
    selected: AutoPlayDelaySetting,
    onSelected: (AutoPlayDelaySetting) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.autoDelay,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SetupDropdown(
            selectedText = strings.autoPlayDelayLabel(selected),
            enabled = true,
            modifier = Modifier.weight(1f),
            options = AutoPlayDelaySetting.entries,
            optionLabel = { setting -> strings.autoPlayDelayLabel(setting) },
            onSelected = onSelected,
        )
    }
}

@Composable
private fun PlayerSetupSideRow(
    state: PlayerSetupSideUiState,
    enabled: Boolean,
    onSideChange: (SidePlayerSetup) -> Unit,
) {
    val strings = LocalUiStrings.current
    val side = state.side
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.colorLabel(state.color),
                modifier = Modifier.weight(0.38f),
                fontWeight = FontWeight.SemiBold,
            )
            SeatControllerPill(
                label = strings.controllerLabel(SeatController.Human),
                selected = side.controller == SeatController.Human,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.seatControllerPill(state.color, SeatController.Human)),
                onClick = { onSideChange(side.copy(controller = SeatController.Human)) },
            )
            SeatControllerPill(
                label = strings.controllerLabel(SeatController.Ai),
                selected = side.controller == SeatController.Ai,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.seatControllerPill(state.color, SeatController.Ai)),
                onClick = { onSideChange(side.copy(controller = SeatController.Ai)) },
            )
        }
        // AI 선택 시에만 노출되는 난이도 드롭다운 — "AI" 버튼 아래쪽에서 파생된 것처럼 우측 정렬.
        // 2026-08-18부터 그룹(빠른초급/초급/중급/고급)→단계의 2뎁스 선택을 없애고,
        // 빠른 초급의 5단계(초보~초고수)만 노출하는 1뎁스 드롭다운으로 간소화했다 —
        // 초심자 진입 난이도를 낮추는 게 목적. 초급/중급/고급 그룹은 코드는 남아있지만
        // 이 화면에서는 완전히 숨겼다(대체 진입 경로는 docs/engine-research의
        // FAST_BEGINNER_FIVE_TIER_REDESIGN_PLAN 문서 로드맵 참고).
        // 2026-08-29(#10)부터 단계 드롭다운을 **캐릭터 픽커**로 대체했다 — 7.1절대로 캐릭터
        // 하나가 티어 하나에 1:1로 대응하므로 "캐릭터를 고르는 행위 = 난이도 선정"이다. 버튼은
        // 지금 상대를 이름+티어명으로 보여주고, 탭하면 5종 목록(잠긴 것 포함)이 열린다.
        if (side.controller == SeatController.Ai) {
            val fastBeginnerLevel = if (side.playLevel.group == PlayLevelGroup.FastBeginner) {
                side.playLevel.safeLevel
            } else {
                1
            }
            val current = BotCharacterCatalog.forPlayLevel(
                PlayLevelSetting(group = PlayLevelGroup.FastBeginner, level = fastBeginnerLevel),
            )
            // 광고 코루틴을 다이얼로그가 아니라 여기서 돌리는 이유(#20): 다이얼로그 안에서
            // 돌리면 픽커가 닫히는 순간 스코프까지 함께 취소돼, 조각 적립 결과를 알리는 것도
            // 광고 뒤 픽커를 되살리는 것도 못 한다. 패널은 광고 Activity 전환에서 살아남는 것이
            // 계측으로 확인됐다(`panel-dispose`가 한 번도 찍히지 않는다).
            var showPicker by remember { mutableStateOf(false) }
            var adInProgress by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val bots = LocalBotCharacterUiState.current
            val context = LocalContext.current

            // 저장된 레벨이 획득하지 않은 캐릭터를 가리키면 여기서 낮춘다(#22). 획득 여부는
            // 그동안 픽커에서 **새로 고를 때만** 강제돼서, 저장된 값이 잠긴 캐릭터를 가리키면
            // 획득 시스템이 통째로 우회됐다 — #10 이전 드롭다운이 5단계를 게이트 없이 제공했으므로
            // 기존 사용자 상당수가 해당된다.
            //
            // 판정 자체는 순수 함수가 하고 여기서는 **적용과 안내**만 한다. 적용은 기존
            // `onSideChange` 경로를 그대로 타므로 자동 저장까지 함께 따라간다.
            val clamp = clampToOwnedBotCharacter(side.playLevel, bots.collection)
            val latestClamp by rememberUpdatedState(clamp)
            val latestSide by rememberUpdatedState(side)
            // ⚠️ 키를 `Unit`으로 두는 것이 중요하다. `clamp != null` 같은 조건을 키로 쓰면 클램프가
            // **반영되는 순간 키가 뒤집혀 코루틴이 취소**돼, 바로 다음에 오는 안내가 영영 안 뜬다
            // (2026-08-29에 실제로 그렇게 실패했다). 이 이펙트는 좌석이 화면에 있는 동안 한 번만
            // 돌면 되므로 수명을 좌석에 맞춘다.
            LaunchedEffect(Unit) {
                val initial = latestClamp ?: return@LaunchedEffect
                // ⚠️ **한 번만 보내면 안 된다.** 설정 변경은 `GameSettingsController.changePlayerSetup`의
                // 엔진 사용 중 게이트를 지나는데, 앱을 켠 직후에는 KataGo가 아직 기동 중이라 그
                // 게이트가 요청을 **조용히 버린다**(2026-08-29 계측 확인: 판정과 이펙트는 정상
                // 실행되는데 `side.playLevel`이 그대로였다). 그대로 두면 앱을 켤 때마다 잠긴
                // 상대가 남는다. 그래서 반영될 때까지 다시 보낸다.
                //
                // 안내는 **반영된 뒤에만** 한다 — 끝내 실패하면 바뀌지 않은 것을 바뀌었다고
                // 말하게 된다. 시도를 유한하게 묶는 것도 같은 이유다(엔진이 영영 안 뜨는
                // 상황에서 무한히 도는 대신 조용히 포기한다).
                repeat(BotLevelClampRetryCount) {
                    val target = latestClamp
                    if (target == null) {
                        Toast.makeText(
                            context,
                            strings.botLevelClampedMessage(initial.from, initial.to),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@LaunchedEffect
                    }
                    onSideChange(latestSide.copy(playLevel = target.playLevel))
                    delay(BotLevelClampRetryIntervalMillis)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = enabled,
                ) {
                    Text(
                        text = current?.let(strings::botCharacterLabel)
                            ?: strings.fastBeginnerTierLabel(fastBeginnerLevel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (showPicker) {
                BotCharacterPickerDialog(
                    selected = current,
                    adInProgress = adInProgress,
                    onSelect = { character ->
                        character.toPlayLevelSetting()?.let { level ->
                            onSideChange(side.copy(playLevel = level))
                        }
                    },
                    onWatchAd = { character ->
                        scope.launch {
                            adInProgress = true
                            watchAdForShardAndReport(character, bots, strings, context)
                            adInProgress = false
                            // 만에 하나 광고가 픽커를 닫았더라도 여기서 되살린다. 지금은 바깥 탭
                            // 경로를 막아 둬서(`BotCharacterPickerDialog`) 실제로 닫히지 않지만,
                            // 이 줄은 광고 쪽 사정이 바뀌어도 자리를 지켜 주는 보험으로 남긴다.
                            showPicker = true
                        }
                    },
                    onDismiss = { showPicker = false },
                )
            }
        }
    }
}

@Composable
private fun SeatControllerPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

/**
 * 잠긴 상대를 낮추는 요청(#22)을 몇 번까지 다시 보낼지. 엔진 기동이 끝나기를 기다리는 것이
 * 목적이라 넉넉하지만 유한하게 잡는다 — 에뮬레이터에서 KataGo 기동에 수십 초가 걸리는 것을
 * 관측했다.
 */
private const val BotLevelClampRetryCount: Int = 40

/** 위 재시도 간격. */
private const val BotLevelClampRetryIntervalMillis: Long = 500
