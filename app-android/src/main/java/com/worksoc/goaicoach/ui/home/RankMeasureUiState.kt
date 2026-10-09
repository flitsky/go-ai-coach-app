package com.worksoc.goaicoach.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.application.engine.runEngineIo
import com.worksoc.goaicoach.application.gamehistory.findRecordedGame
import com.worksoc.goaicoach.application.orchestration.GameSettingsController
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureAdjustment
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureChange
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.StrongestStartingRank
import com.worksoc.goaicoach.application.rankmeasure.chooseStartingRank
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureBoardSizeFor
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureBoardSizesFor
import com.worksoc.goaicoach.application.rankmeasure.rankMeasureMatchup
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePlayerSetup
import com.worksoc.goaicoach.application.rankmeasure.rankMeasurePromotionSteps
import com.worksoc.goaicoach.application.rankmeasure.runRankMeasureAdjustment
import com.worksoc.goaicoach.application.rankmeasure.started
import com.worksoc.goaicoach.application.rankmeasure.withRankForNextMeasureGame
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.isRankMeasure
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.RankMeasureStore
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardChangedFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardLimitNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureBoardSizeLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeMessageFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeRanksFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChangeTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureChooseStartingRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureCurrentRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureKyuCapNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureNoAssistNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureOfficialNoteFor
import com.worksoc.goaicoach.ui.l10n.rankMeasurePeakRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureRulesFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureSideLabelFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureStartFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureStrongerFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureWeakerFor
import kotlinx.coroutines.delay

/**
 * **승급 대국**(백로그 #219)의 화면 상태 — 내 기력, 최고 기력, 연승·연패.
 *
 * `GoCoachApp.kt`의 상태 훅 예산이 꽉 차 있어([buildRankMeasureUiState]) 저장소 생성과 상태 보유를 여기로 뺐다 —
 * 셸에서는 호출 한 줄만 보인다(`buildConsumableUiState`와 같은 이유). 홈의 카드와 설정 창이 [LocalRankMeasureUiState]로 읽는다.
 */
internal data class RankMeasureUiState(
    val state: RankMeasureState = RankMeasureState(),
    /**
     * 이 기기에서 승급 대국을 열 수 있는가 — **사람 모델이 있을 때만**이다. 없으면 상대가 그 급수처럼 두지 못하므로
     * (가까운 캐릭터의 지금 방식으로 떨어진다) 잰 기력이 뜻을 잃는다. 그래서 홈의 카드를 아예 보이지 않는다.
     */
    val isAvailable: Boolean = false,
    /**
     * **지금 이 순간**의 상태 — [state]는 화면을 그릴 때의 값이라, 같은 탭 안에서 [update]한 직후에는 아직 옛 값이다.
     * 설정 창의 「대국 시작」은 시작 기력을 저장하고 곧바로 새 대국을 연다 — 그 길에서 [state]를 읽으면 방금 고른 기력이 20급으로 되돌아간다.
     */
    val latestState: () -> RankMeasureState = { state },
    /** 상태를 바꾸고 저장한다. */
    val update: (RankMeasureState) -> Unit = {},
    /** 끝나서 기록된 판을 기력에 반영한다 — 같은 판은 한 번만 센다(`runRankMeasureAdjustment`). */
    val countRecordedGame: suspend (GameState) -> RankMeasureAdjustment? = { null },
    /**
     * **가장 최근 기록**을 기력에 반영한다 — 판을 나가는 순간에 쓴다([leaveRankMeasureGame]). 뒤로 가기로 기권한 판은 나가면서
     * 기록되고 곧바로 화면에서 사라져, 끝난 판에 거는 효과([RankMeasureRecordedEffect])가 볼 틈이 없다.
     * 이미 반영한 판이거나 승급 대국이 아니면 `null`이라, 언제 불러도 한 판을 두 번 세지 않는다.
     */
    val countLatestRecordedGame: () -> RankMeasureAdjustment? = { null },
    /**
     * 아직 사용자에게 알리지 않은 **기력 변동**(승급·강급·이미 9단) — [RankMeasureChangeDialog]가 팝업으로 띄운다
     * (사용자 2026-10-07: 몇 급이 올랐는지 알 수 있게 팝업으로). 판을 반영한 쪽이 올려 두고, 사용자가 닫으면 비운다.
     */
    val pendingChange: RankMeasureChange? = null,
    val dismissChange: () -> Unit = {},
)

internal val LocalRankMeasureUiState = staticCompositionLocalOf { RankMeasureUiState() }

@Composable
internal fun buildRankMeasureUiState(
    context: Context,
    isAvailable: Boolean,
): RankMeasureUiState {
    val store = remember(context) { RankMeasureStore(context) }
    var state by remember(store) { mutableStateOf(store.load()) }
    var pendingChange by remember { mutableStateOf<RankMeasureChange?>(null) }
    // 판 하나를 반영한 결과를 화면에 올린다 — 기력이 바뀌었으면(또는 이미 9단이라고 알릴 일이면) 팝업으로 띄울 것을 남긴다.
    fun adopt(adjustment: RankMeasureAdjustment): RankMeasureAdjustment {
        state = adjustment.state
        if (adjustment.change != RankMeasureChange.None) pendingChange = adjustment.change
        return adjustment
    }
    return RankMeasureUiState(
        state = state,
        isAvailable = isAvailable,
        latestState = { state },
        update = { next ->
            store.save(next)
            state = next
        },
        countRecordedGame = { gameState ->
            val recorded = runEngineIo { GameHistoryStore(context).findRecordedGame(gameState.moves) }
            recorded?.let { (entry, replay) ->
                runRankMeasureAdjustment(entry, store, replay)?.let(::adopt)
            }
        },
        countLatestRecordedGame = {
            val history = GameHistoryStore(context)
            history.loadAll().lastOrNull()?.let { entry -> runRankMeasureAdjustment(entry, store, history.loadReplay(entry.id))?.let(::adopt) }
        },
        pendingChange = pendingChange,
        dismissChange = { pendingChange = null },
    )
}

/** 설정 창에서 고른 것 — 진영과 판 크기, 그리고 최초 1회에 한해 시작 기력. */
internal data class RankMeasureStart(
    val userColor: StoneColor,
    val boardSize: BoardSize,
    /** 스스로 고른 시작 기력. 이미 측정을 시작한 사람에게는 `null`이다(고를 수 없다). */
    val startingRank: KgsRank?,
)

/**
 * 승급 대국을 시작한다 — 좌석(사람 대 내 기력의 AI)·판 크기·호선을 설정에 올리고 새 대국을 연다.
 *
 * - 최초 1회의 기력 선택은 여기서 반영되고 **잠긴다**(`started`) — 그 뒤로는 이기고 지는 것만이 기력을 옮긴다.
 * - 늘 **호선 · 기본 덤**이다. 기력을 재는 판에 접바둑을 걸면 이긴 것이 그 급수를 이긴 것이 아니게 된다.
 * - 고른 판 크기가 그 기력에서 못 두는 판이면 둘 수 있는 가장 작은 판으로 옮긴다(`rankMeasureBoardSizeFor`).
 *
 * ⚠️ 이 설정은 **일반 대국 설정을 덮어쓰지 않는다** — 자동저장이 건너뛰고(`keepingRegularGameSetupDuringRankMeasure`),
 * 이 판을 나가면 [leaveRankMeasureGame]이 저장돼 있던 일반 설정으로 되돌린다.
 */
internal fun beginRankMeasureGame(
    start: RankMeasureStart,
    rankMeasure: RankMeasureUiState,
    settings: GameSettingsController,
    startConfiguredGame: () -> Unit,
) {
    val chosen = start.startingRank?.let(rankMeasure.state::chooseStartingRank) ?: rankMeasure.state
    val state = chosen.started()
    if (state != rankMeasure.state) rankMeasure.update(state)
    settings.changeHandicapCount(0)
    settings.changeKomi(DefaultKomi)
    settings.changeBoardSize(rankMeasureBoardSizeFor(state.rank, start.boardSize))
    settings.changePlayerSetup(rankMeasurePlayerSetup(start.userColor, state.rank))
    startConfiguredGame()
}

/**
 * 「재 대국」 직전 — 승급 대국이면 상대를 **지금의 내 기력**으로 맞추고, 그 기력에서 못 두는 판 크기면 둘 수 있는 가장 작은
 * 판으로 옮긴다(옮겼으면 한 줄로 알린다 — 말없이 다른 판으로 시작하지 않는다). 승급 대국이 아니면 아무 일도 하지 않는다.
 *
 * ⚠️ 새 대국을 여는 **모든** 길이 여기를 지난다(설정 창의 「대국 시작」도). 그래서 좌석·판 크기·기력은 **부르는 순간의 값**을 받아야
 * 한다 — 화면을 그릴 때 잡아 둔 값을 넘기면, 방금 설정 창에서 고른 진영·판 크기·시작 기력을 옛 값으로 되돌린다.
 */
internal fun prepareNextRankMeasureGame(
    context: Context,
    language: UiLanguage,
    playerSetup: PlayerSetup,
    boardSize: BoardSize,
    state: RankMeasureState,
    settings: GameSettingsController,
) {
    if (playerSetup.rankMeasureMatchup() == null) return
    val nextBoardSize = rankMeasureBoardSizeFor(state.rank, boardSize)
    if (nextBoardSize != boardSize) {
        settings.changeBoardSize(nextBoardSize)
        Toast.makeText(context, rankMeasureBoardChangedFor(language, state.rank, nextBoardSize), Toast.LENGTH_LONG).show()
    }
    playerSetup.withRankForNextMeasureGame(state).takeIf { it != playerSetup }?.let(settings::changePlayerSetup)
}

/**
 * 승급 대국을 **나간다** — 일반 대국에서 부르면 아무 일도 하지 않는다.
 *
 * 1. 방금 기록된 판을 기력에 반영한다. 뒤로 가기로 기권한 판은 나가는 순간에 기록되고 곧 화면에서 사라지므로 여기서 센다
 *    (이미 센 판이면 건너뛴다). 기력이 바뀌면 나간 자리(홈)에서 팝업이 뜬다([RankMeasureChangeDialog]).
 * 2. 살아 있는 설정을 **저장돼 있던 일반 대국 설정**(상대 캐릭터 · 판 크기 · 접바둑 · 덤)으로 되돌린다 — 「대국 하기」가 급수 AI와
 *    호선으로 열리지 않게. 승급 대국의 설정은 자동저장이 건너뛰므로 저장분은 그 전의 것 그대로다.
 *
 * ⚠️ 부르는 순서: 판을 기록한 **뒤**, 미리보기 판으로 갈아엎기 **전**. 기록 전에 부르면 반영할 판이 아직 없고, 좌석을 먼저 되돌리면
 * 그 판이 캐릭터와 둔 판으로 기록된다.
 */
internal fun leaveRankMeasureGame(
    playerSetup: PlayerSetup,
    rankMeasure: RankMeasureUiState,
    preferencesStore: UserPreferencesStorePort,
    settings: GameSettingsController,
) {
    if (!playerSetup.isRankMeasure()) return
    rankMeasure.countLatestRecordedGame()
    val regular = preferencesStore.load()
    settings.changePlayerSetup(regular.playerSetup)
    settings.changeBoardSize(regular.boardSize)
    settings.changeHandicapCount(regular.handicapCount)
    settings.changeKomi(regular.komi)
}

/**
 * 대국이 끝나 결과가 나면 그 판을 기력에 반영한다. 기력이 바뀌면 팝업이 뜬다([RankMeasureChangeDialog]).
 *
 * ⚠️ **결과가 난 뒤에 돈다**([isResultKnown], 함정 88) — 「끝났다」는 표시는 계가보다 먼저 켜지고, 기록은 결과가 나야 붙는다.
 * 기록 붙이기는 같은 프레임의 다른 효과가 하므로 잠깐 다시 찾는다.
 * 바뀐 기력은 **다음 대국을 시작할 때** 좌석에 옮겨 적는다([prepareNextRankMeasureGame]) — 끝난 판의 화면은 둔 급수 그대로다.
 */
@Composable
internal fun RankMeasureRecordedEffect(
    rankMeasure: RankMeasureUiState,
    isGameEnded: Boolean,
    isResultKnown: Boolean,
    sessionGeneration: Long,
    gameState: GameState,
) {
    val latest by rememberUpdatedState(rankMeasure)
    LaunchedEffect(isGameEnded, isResultKnown, sessionGeneration, gameState.moves.size) {
        if (!isGameEnded || !isResultKnown) return@LaunchedEffect
        repeat(RecordedGameLookupTries) { attempt ->
            if (attempt > 0) delay(RecordedGameLookupRetryMillis)
            if (latest.countRecordedGame(gameState) != null) return@LaunchedEffect
        }
    }
}

/**
 * **기력 변동 팝업**(사용자 2026-10-07) — 승급·강급을 한 줄 알림이 아니라 팝업으로 알린다: 어디서 어디로(`15급 → 9급`),
 * 왜·몇 단계(`61집 차로 이겨 6단계 올랐습니다`). 알릴 것이 없으면 아무것도 그리지 않는다.
 *
 * 홈과 대국 화면이 함께 부른다 — 계가로 끝난 판은 대국 화면에서, 뒤로 가기로 기권한 판은 나간 뒤 홈에서 뜬다.
 * ⚠️ 대국 화면은 **판정 결과 창이 닫힌 뒤에** 부른다: 다이얼로그는 저마다 별도 윈도우라 함께 뜨면 위아래가 정해지지 않는다(함정 7).
 */
@Composable
internal fun RankMeasureChangeDialog(rankMeasure: RankMeasureUiState) {
    val change = rankMeasure.pendingChange ?: return
    val strings = LocalUiStrings.current
    val language = strings.language
    val title = rankMeasureChangeTitleFor(language, change) ?: return
    // 크게 이겼는데 1단에서 멈췄으면 단계 수가 집 수 차이보다 적다 — 그 까닭을 한 줄 덧붙인다.
    val stoppedAtOneDan = change is RankMeasureChange.Promoted && !change.from.isDan && rankMeasurePromotionSteps(change.margin) > change.steps
    AlertDialog(
        onDismissRequest = rankMeasure.dismissChange,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                rankMeasureChangeRanksFor(language, change)?.let { ranks ->
                    Text(
                        text = ranks,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(RankMeasureChangeRanksTag),
                    )
                }
                rankMeasureChangeMessageFor(language, change)?.let { message -> Text(message) }
                if (stoppedAtOneDan) {
                    Text(
                        text = rankMeasureKyuCapNoteFor(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = rankMeasure.dismissChange) { Text(strings.confirm) } },
    )
}

/**
 * 승급 대국의 설정 창 — 고르는 것은 **진영과 판 크기**뿐이다. 측정 기록이 없는 사람에게만, 최초 1회에 한해 시작 기력을
 * 고르는 줄이 더 보인다(20급~1급).
 *
 * 판 크기는 그 기력에서 둘 수 있는 것만 누를 수 있다(20~11급 전부 · 10~1급 13·19줄 · 단 19줄).
 */
@Composable
internal fun RankMeasureSetupDialog(
    rankMeasure: RankMeasureUiState,
    onStart: (RankMeasureStart) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    val canChoose = rankMeasure.state.canChooseStartingRank
    var rank by remember { mutableStateOf(rankMeasure.state.rank) }
    var userColor by remember { mutableStateOf(StoneColor.Black) }
    var preferredBoardSize by remember { mutableStateOf(BoardSize.Nine) }
    val allowedBoardSizes = rankMeasureBoardSizesFor(rank)
    val boardSize = rankMeasureBoardSizeFor(rank, preferredBoardSize)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rankMeasureTitleFor(language), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space10),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space2)) {
                    Text(rankMeasureCurrentRankFor(language, rank), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(RankMeasureCurrentRankTag))
                    Text(rankMeasurePeakRankFor(language, rankMeasure.state.peakRank), style = MaterialTheme.typography.bodyMedium)
                }
                if (canChoose) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = { rank = rank.weakerBy(1) },
                            enabled = rank > KgsRank.Weakest,
                            modifier = Modifier.weight(1f).testTag(RankMeasureWeakerTag),
                        ) { Text(rankMeasureWeakerFor(language)) }
                        FilledTonalButton(
                            onClick = { rank = minOf(rank.strongerBy(1), StrongestStartingRank) },
                            enabled = rank < StrongestStartingRank,
                            modifier = Modifier.weight(1f).testTag(RankMeasureStrongerTag),
                        ) { Text(rankMeasureStrongerFor(language)) }
                    }
                    Text(
                        text = rankMeasureChooseStartingRankFor(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                ChoiceRow(
                    label = rankMeasureSideLabelFor(language),
                    options = listOf(StoneColor.Black, StoneColor.White),
                    selected = userColor,
                    optionLabel = strings::colorLabel,
                    isEnabled = { true },
                    onSelected = { userColor = it },
                )
                ChoiceRow(
                    label = rankMeasureBoardSizeLabelFor(language),
                    options = BoardSize.supported(),
                    selected = boardSize,
                    optionLabel = { size -> "${size.value}x${size.value}" },
                    isEnabled = { size -> size in allowedBoardSizes },
                    onSelected = { preferredBoardSize = it },
                )
                if (allowedBoardSizes.size < BoardSize.supported().size) {
                    Text(
                        text = rankMeasureBoardLimitNoteFor(language, rank),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                listOf(rankMeasureRulesFor(language), rankMeasureNoAssistNoteFor(language), rankMeasureOfficialNoteFor(language)).forEach { note ->
                    Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(RankMeasureStart(userColor, boardSize, startingRank = rank.takeIf { canChoose })) },
                modifier = Modifier.testTag(RankMeasureStartTag),
            ) { Text(rankMeasureStartFor(language)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/** 제목 한 줄과 그 아래의 선택 버튼들 — 고른 것은 채워서, 못 고르는 것은 흐리게. */
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    isEnabled: (T) -> Boolean,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            // ⚠️ 버튼 안쪽 여백을 기본(좌우 24dp)으로 두면 세 칸짜리 줄에서 `13x13`이 `13x1 / 3`으로 꺾인다(2026-10-06 에뮬레이터).
            // 여백을 줄이고 한 줄로 못 박는다 — 글자가 넘치면 꺾이지 않고 잘린다.
            options.forEach { option ->
                val modifier = Modifier.weight(1f)
                val label: @Composable () -> Unit = { Text(optionLabel(option), maxLines = 1, softWrap = false) }
                if (option == selected) {
                    Button(onClick = { onSelected(option) }, modifier = modifier, contentPadding = ChoiceButtonPadding) { label() }
                } else {
                    OutlinedButton(onClick = { onSelected(option) }, enabled = isEnabled(option), modifier = modifier, contentPadding = ChoiceButtonPadding) { label() }
                }
            }
        }
    }
}

internal const val RankMeasureCurrentRankTag = "rank-measure-current-rank"
internal const val RankMeasureWeakerTag = "rank-measure-weaker"
internal const val RankMeasureStrongerTag = "rank-measure-stronger"
internal const val RankMeasureStartTag = "rank-measure-start"
internal const val RankMeasureChangeRanksTag = "rank-measure-change-ranks"

private val ChoiceButtonPadding = PaddingValues(horizontal = AppSpacing.Space8)

private const val RecordedGameLookupTries = 4
private const val RecordedGameLookupRetryMillis = 400L
