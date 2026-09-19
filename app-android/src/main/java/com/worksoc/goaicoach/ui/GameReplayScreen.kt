package com.worksoc.goaicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.gamehistory.blunderMoveNumbers
import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.application.movereview.MoveReviewMarker
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.StoneColor
import kotlin.math.roundToInt

/**
 * 4 Depth: 한 판을 수순대로 되짚는 화면(백로그 #156).
 *
 * ## 상태를 셸로 올리지 않는다
 * 재생 위치·토글 셋은 전부 **이 화면이 소유**한다. `GoCoachApp.kt`의 상태 훅 예산은 42/42로
 * 여유가 0이라(함정 3), 한 줄만 올려도 `LayeringContractTest`가 깨진다.
 *
 * ## ⚠️ 표시 옵션도 셸의 것을 쓰지 않는다
 * [KaTrainUxOptions]를 **지역 인스턴스로** 만든다. 셸의 `uxOptions`를 고치면 자동저장이 그대로
 * 받아 적어 **대국 중 설정이 영구히 바뀐다**(함정 2) — 다시보기에서 수순 번호를 켰다는 이유로
 * 다음 대국의 판에 번호가 남으면 안 된다.
 *
 * ## ⚠️ 판 위 착수 평가 색은 여기서 열지 않는다
 * `GoBoard`가 `uxOptions.showMoveReview && premium.isActive`로 **스스로** 가른다. 이 화면은
 * `showMoveReview`만 켜 두고 프리미엄 판정은 건드리지 않는다 — 여는 순간 대국 화면에서 파는
 * 기능을 뒷문으로 내주는 것이 된다(함정 14). 무료로 주는 것은 아래 **실착 구간 목록**이고,
 * 그건 `#156`의 목적이 명시한 지표다.
 */
@Composable
internal fun GameReplayScreen(
    entry: GameHistoryEntry,
    replay: GameReplayData,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current

    // ⚠️ **중첩 `BackHandler`다.** 셸(`GoCoachApp.kt:714`)의 핸들러는 목적지가 Home이 아니면
    // 무조건 `exitToHome()`을 불러 **복기를 건너뛰고 홈으로 튄다.** 디스패처는 나중에 등록된
    // 콜백부터 부르고, 이 화면은 셸의 `when(currentDestination)` 안에서 **뒤에** 컴포즈되므로
    // 여기가 먼저 잡는다. 이 저장소의 중첩 `BackHandler` **첫 사례**라 실기로 확인할 것.
    BackHandler { onBackClick() }

    val timeline = remember(entry.id, replay) {
        buildGameReplayTimeline(
            boardSize = BoardSize(entry.boardSize),
            ruleset = entry.ruleset,
            handicapCount = entry.handicapCount,
            komi = entry.komi,
            moves = replay.moves,
        )
    }
    val blunders = remember(replay) { blunderMoveNumbers(replay.moveEvaluations) }

    // ⚠️ **마지막 수에서 연다.** 목록 행이 방금 말한 것이 결과이고, 그 국면에서 시작해야 화면이
    // 이어진다. 처음부터 보려면 `⏮`가 한 번이다.
    var moveNumber by remember(entry.id) { mutableIntStateOf(timeline.lastMoveNumber) }
    var showMoveNumbers by remember { mutableStateOf(false) }
    var isScoreExpanded by remember { mutableStateOf(false) }

    val state = timeline.stateAt(moveNumber)
    val uxOptions = remember(showMoveNumbers) {
        KaTrainUxOptions(
            showMoveNumbers = showMoveNumbers,
            showMoveReview = true,
            showOwnershipOverlay = false,
            isDirectPlayEnabled = false,
            isDelayedPlayEnabled = false,
            isPlayHapticEnabled = false,
            isPlayEffectEnabled = false,
            isPlayMagnifierEnabled = false,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // ⚠️ **맨 아래 칸이 제스처 바에 깔린다**(targetSdk 36부터 앱이 시스템 바 영역까지
            // 그린다, #25). 실착 칩이 바로 그 자리라 실기에서 반쯤 가려졌다 — 위쪽은 헤더의
            // `statusBarsPadding()`이 맡고 아래는 이 한 줄이 맡는다(설정·학습 화면과 같은 방식).
            .navigationBarsPadding(),
    ) {
        ReplayHeader(
            title = gameReplayTitleFor(strings.language),
            subtitle = gameHistorySummaryLine(entry, strings),
            closeLabel = strings.close,
            onBackClick = onBackClick,
        )

        if (timeline.isTruncated) {
            Text(
                text = gameReplayTruncatedFor(strings.language, timeline.truncatedAtMoveNumber!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ⚠️ **이 앱에서 배너가 처음 화면에 붙는 자리다**(2026-09-19 사용자). 2026-08-08에 홈에서
        // 떼면서(`59d880c`) *"다음에 붙일 곳은 복기 화면"* 이라고 적어 둔 그 자리다
        // (`PREMIUM_MODE.md` 「배너 광고 재노출 위치 — 보류 결정」).
        // ⚠️ **헤더 아래다** — 위로 올리면 나가는 길(뒤로가기)이 광고 밑에 깔린다.
        // ⚠️ **구독자에게는 뜨지 않는다** — 판정은 `SubscriptionAwareBannerAd`가 갖는다.
        SubscriptionAwareBannerAd(modifier = Modifier.padding(top = 4.dp))

        ReplayBlunderSection(
            markers = replay.moveEvaluations,
            blunderMoveNumbers = blunders,
            currentMoveNumber = moveNumber,
            strings = strings,
            onJumpTo = { target -> moveNumber = target.coerceIn(0, timeline.lastMoveNumber) },
        )

        // ⚠️ **스크롤 부모를 두지 않는다.** 판은 `pointerInput`이 `awaitFirstDown().consume()`을
        // `inputEnabled` 판정보다 **먼저** 하므로(`GoBoard.kt:238`), 읽기 전용이어도 판 위에서
        // 시작한 끌기를 삼킨다 — 스크롤 안에 넣으면 판 위에서 화면이 안 굴러간다(함정 44).
        // 대신 판이 남는 높이를 받고, 조작부는 자기 높이만 쓴다.
        //
        // ⚠️ **판이 배너와 실착 칩 사이의 완충이기도 하다.** 누르는 것(칩) 바로 옆에 광고를 두지
        // 않는 것이 AdMob의 「실수 클릭 유도 배치 금지」이고, 이 배치에서는 칩이 광고 바로
        // 아래라 **칩과 광고 사이 여백을 일부러 둔다**(`ReplayBlunderSection`의 위 패딩).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = state,
                candidateMoves = emptyList(),
                moveReviews = replay.moveEvaluations,
                ownershipEstimate = null,
                uxOptions = uxOptions,
                inputEnabled = false,
                engineActivityIndicator = null,
                modifier = Modifier.fillMaxSize(),
                onCoordinateTap = {},
                isGameEnded = moveNumber == timeline.lastMoveNumber,
            )
        }

        ReplayControls(
            moveNumber = moveNumber,
            lastMoveNumber = timeline.lastMoveNumber,
            strings = strings,
            showMoveNumbers = showMoveNumbers,
            onMoveNumberChange = { next -> moveNumber = next.coerceIn(0, timeline.lastMoveNumber) },
            onToggleMoveNumbers = { showMoveNumbers = !showMoveNumbers },
        )

        ReplayScoreSection(
            replay = replay,
            moveNumber = moveNumber,
            capturedByBlack = state.capturedBy(StoneColor.Black),
            capturedByWhite = state.capturedBy(StoneColor.White),
            isExpanded = isScoreExpanded,
            onExpandedChange = { isScoreExpanded = it },
            strings = strings,
        )
    }
}

@Composable
private fun ReplayHeader(
    title: String,
    subtitle: String,
    closeLabel: String,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 대국 기록 목록과 같은 자리·같은 방식(#25).
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = closeLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ReplayControls(
    moveNumber: Int,
    lastMoveNumber: Int,
    strings: UiStrings,
    showMoveNumbers: Boolean,
    onMoveNumberChange: (Int) -> Unit,
    onToggleMoveNumbers: () -> Unit,
) {
    val language = strings.language
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (moveNumber == 0) {
                gameReplayStartPositionFor(language)
            } else {
                "${strings.moveCountPrefix} $moveNumber${strings.moveCountSuffix} / $lastMoveNumber"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (lastMoveNumber > 0) {
            Slider(
                value = moveNumber.toFloat(),
                onValueChange = { raw -> onMoveNumberChange(raw.roundToInt()) },
                valueRange = 0f..lastMoveNumber.toFloat(),
                // 눈금은 **수순 사이의 칸 수**다 — 끝 두 개는 `valueRange`가 이미 든다.
                steps = (lastMoveNumber - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionButton(
                label = "⏮",
                enabled = moveNumber > 0,
                onClick = { onMoveNumberChange(0) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "◀",
                enabled = moveNumber > 0,
                onClick = { onMoveNumberChange(moveNumber - 1) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "▶",
                enabled = moveNumber < lastMoveNumber,
                onClick = { onMoveNumberChange(moveNumber + 1) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "⏭",
                enabled = moveNumber < lastMoveNumber,
                onClick = { onMoveNumberChange(lastMoveNumber) },
                modifier = Modifier.weight(1f),
            )
            ReplayToggleButton(
                label = strings.moveNumbers,
                isOn = showMoveNumbers,
                onClick = onToggleMoveNumbers,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/**
 * 형세 지표. **켜고 끄는 것은 그래프 자신이 한다** — 대국 화면과 같은 컴포넌트를 쓴다.
 *
 * ⚠️ **형세 기록이 없으면 그래프를 아예 그리지 않는다.** 접힌 바는 값이 없을 때 `0.0`을 적는데,
 * 0.0은 "호각"이라는 뜻이라 **재지 않은 판을 호각이었다고 말하게 된다**(#156 착수 전 경고).
 */
@Composable
private fun ReplayScoreSection(
    replay: GameReplayData,
    moveNumber: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    strings: UiStrings,
) {
    val upToNow = remember(replay, moveNumber) {
        replay.scoreSnapshots.filter { it.hasScoreData && it.moveNumber <= moveNumber }
    }
    val hasAnyScoreData = remember(replay) { replay.scoreSnapshots.any { it.hasScoreData } }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        if (!hasAnyScoreData) {
            Text(
                text = gameReplayNoScoreDataFor(strings.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            return@Column
        }
        ScoreTimelineGraph(
            snapshots = upToNow,
            capturedByBlack = capturedByBlack,
            capturedByWhite = capturedByWhite,
            whiteWinRate = upToNow.lastOrNull()?.whiteWinRate,
            isExpanded = isExpanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 큰 실수 구간. 칩을 누르면 그 수로 뛴다.
 *
 * ⚠️ 평가가 **하나도 없는 것**과 **평가는 있는데 큰 실수가 없는 것**은 다른 말이다 — 앞의 것을
 * "실수 없음"으로 적으면 잴 자료가 없었던 판을 잘 둔 판으로 바꿔 말하게 된다.
 */
@Composable
private fun ReplayBlunderSection(
    markers: List<MoveReviewMarker>,
    blunderMoveNumbers: List<Int>,
    currentMoveNumber: Int,
    strings: UiStrings,
    onJumpTo: (Int) -> Unit,
) {
    val language = strings.language
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // ⚠️ 위 여백이 **광고와의 완충**이다(AdMob 「실수 클릭 유도 배치 금지」) — 칩이 배너
            // 바로 아래 줄이라 붙여 두면 광고를 누르려다 칩을, 칩을 누르려다 광고를 누른다.
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${gameReplayBlunderSectionFor(language)} · ${gameReplayBlunderCriterionFor(language)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        when {
            markers.isEmpty() -> Text(
                text = gameReplayNoMoveEvaluationsFor(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            blunderMoveNumbers.isEmpty() -> Text(
                text = gameReplayNoBlundersFor(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(blunderMoveNumbers) { target ->
                    val loss = markers.firstOrNull { it.moveNumber == target }?.pointLoss
                    val lossText = loss?.let { " \u00B7 ${gameReplayPointLossFor(language, it)}" }.orEmpty()
                    ReplayToggleButton(
                        // ⚠️ 이모지는 **칩에만** 붙인다. 판 위 표식은 `GoBoard`가 프리미엄으로
                        // 가르는 착수 평가 색이라, 거기에 같은 뜻의 그림을 무료로 얹으면
                        // 파는 기능을 뒷문으로 내주게 된다(함정 14).
                        label = "$BlunderChipEmoji ${gameReplayBlunderBadgeFor(language)} $target$lossText",
                        // 지금 서 있는 수의 칩은 **채워서** 표시한다. 프리미엄 테두리를 강조에
                        // 쓰지 않는다 — 그 금색은 "프리미엄 축의 기능"이라는 뜻이 이미 있다.
                        isOn = target == currentMoveNumber,
                        onClick = { onJumpTo(target) },
                        minHeight = BlunderChipMinHeight,
                        contentPadding = BlunderChipContentPadding,
                    )
                }
            }
        }
    }
}

/**
 * 켜짐이 **배경으로** 보이는 작은 버튼. 대국 화면의 `ToggleActionButton`과 생김새를 맞췄지만,
 * 그쪽은 `GameActionButtonState`·`GameUiEvent`를 받아 대국 세션에 묶여 있어 여기서는 못 쓴다.
 *
 * ⚠️ 높이를 고정하지 않는다(함정 9) — 글꼴 배율 2.0에서 글자 상자가 잘린다. 라벨은 한 줄로
 * 유지하되 넘치면 말줄임한다(대국 화면 버튼들과 같은 처리).
 */
@Composable
private fun ReplayToggleButton(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ActionButtonMinHeight,
    contentPadding: PaddingValues = ActionButtonContentPadding,
) {
    val content = @Composable {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (isOn) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight),
            shape = ActionButtonShape,
            contentPadding = contentPadding,
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight),
            shape = ActionButtonShape,
            contentPadding = contentPadding,
        ) { content() }
    }
}

/**
 * 실착 칩 앞의 표식(2026-09-18 사용자: *"급변 지점에 '실착' 이모지"*).
 *
 * ⚠️ **문구 표에 넣지 않는다** — 네 언어가 같은 그림을 쓰므로 표에 넣으면 번역자가 그림을
 * 바꿀 수 있는 자리가 된다. 글자는 [gameReplayBlunderBadgeFor]가, 그림은 여기가 갖는다.
 */
private const val BlunderChipEmoji = "\u2757"

/**
 * 실착 칩의 최소 높이 — 이동 버튼([ActionButtonMinHeight], 48dp)보다 **20% 낮다**
 * (2026-09-19 사용자: *"세로 여백이 많아 보인다"*).
 *
 * ⚠️ **48dp를 밑도는 것은 의도다.** Material의 권장 터치 영역이 48dp이므로 이 칩은 그보다 작고,
 * 그래서 **화면의 주 조작부에는 이 높이를 쓰지 않는다** — ⏮◀▶⏭와 수순 번호는 48dp 그대로다.
 * 칩은 "바로 그 수로 뛰는 지름길"이라 같은 일을 슬라이더·이동 버튼으로도 할 수 있다.
 * ⚠️ `height`가 아니라 `heightIn(min=)`이다(함정 9) — 글꼴 배율이 커지면 칩도 함께 자란다.
 */
private val BlunderChipMinHeight: Dp = ActionButtonMinHeight * 0.8f

/** 낮아진 칩에 맞춘 안쪽 여백 — 높이만 줄이고 패딩을 그대로 두면 글자가 상자에 낌다. */
private val BlunderChipContentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
