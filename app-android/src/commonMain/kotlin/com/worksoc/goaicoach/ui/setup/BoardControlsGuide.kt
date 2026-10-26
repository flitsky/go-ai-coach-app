package com.worksoc.goaicoach.ui.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.board.BoardGeometry
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.board.HeldGhostStoneGrowMillis
import com.worksoc.goaicoach.ui.board.PlayDragLiftCells
import com.worksoc.goaicoach.ui.board.PlayStoneButton
import com.worksoc.goaicoach.ui.board.PlayStoneFace
import com.worksoc.goaicoach.ui.board.drawGhostStone
import com.worksoc.goaicoach.ui.board.heldGhostStoneRadiusRatio
import com.worksoc.goaicoach.ui.board.playStoneFaceOf
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.GoBoardPalette
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.guide.FirstDolAvatar
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsBoardControlsGuide
import com.worksoc.goaicoach.ui.l10n.boardSizeSubjectFor
import com.worksoc.goaicoach.ui.l10n.boardSizeToggleLabelFor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 조작법 가이드의 단계(백로그 #249 ③). */
internal enum class BoardControlsStep { Drag, Confirm, BoardSize }

/**
 * 보여 줄 단계. 착수 확인이 꺼진 빌드에는 그 단계를 싣지 않는다 — 없는 기능을 가르치지 않는다(함정 51).
 */
internal fun boardControlsSteps(isConfirmModeEnabled: Boolean): List<BoardControlsStep> =
    BoardControlsStep.entries.filter { it != BoardControlsStep.Confirm || isConfirmModeEnabled }

/** 손을 뗀 뒤 시연으로 돌아가기까지(#258, 사용자 2026-10-13 — 처음 말한 2초에서 4초로: 착수 확인의 세 동작 사이에 판다의 말을 읽는 틈). */
internal const val GuideIdleReturnMillis = 4_000L

/**
 * 조작법 가이드의 모드(#258) — 알약으로 고르지 않는다. **판(무대)에 손가락이 닿으면 직접**, **손을 떼고 [GuideIdleReturnMillis]가 지나면 시연**.
 *
 * @property practicing 직접 해 보는 중인가.
 * @property fingerDown 손가락이 무대에 닿아 있는가 — 누르고 끄는 동안에는 시간을 세지 않는다.
 * @property releases 손을 뗀 횟수 — 뗄 때마다 대기 시간을 처음부터 센다(화면의 `LaunchedEffect` 키).
 * @property demoRun 시연을 새로 시작한 횟수 — 바뀌면 판을 첫 모양으로 새로 만든다(돌아온 시연은 처음부터, 사용자 2026-10-13).
 */
internal data class GuideAutoMode(
    val practicing: Boolean = false,
    val fingerDown: Boolean = false,
    val releases: Int = 0,
    val demoRun: Int = 0,
) {
    /** 손가락이 닿았다 — 그 즉시 직접. 판은 그대로 두므로 이 손가락이 바로 돌을 끈다. */
    fun pressed(): GuideAutoMode = copy(practicing = true, fingerDown = true)

    /** 손을 뗐다 — 여기서부터 기다린다. */
    fun released(): GuideAutoMode = copy(fingerDown = false, releases = releases + 1)

    /** 손을 떼고 기다리는 시간이 지났다 — 직접 해 보던 중이면 시연을 처음부터. 누르고 있으면 아무것도 바뀌지 않는다. */
    fun idleElapsed(): GuideAutoMode =
        if (practicing && !fingerDown) copy(practicing = false, demoRun = demoRun + 1) else this

    /** 단계를 바꿨다 — 그 단계의 시연부터. */
    fun stepChanged(): GuideAutoMode = GuideAutoMode(demoRun = demoRun + 1)

    /** 기다려야 하는가 — 직접 해 보는 중이고 손이 떨어져 있을 때만. */
    val waitsToReturn: Boolean get() = practicing && !fingerDown
}

/**
 * **바둑판 조작법 가이드**(백로그 #249 ②③ · #258) — 문하생 판다(첫돌이)가 **진짜 바둑판**(`GoBoard`)을 그려 놓고 「돌 끌기」·
 * 「착수 확인」·「판 크기」를 시연하고, 사용자가 판에 손을 대면 **그 자리에서 직접** 해 볼 수 있다. 엔진은 쓰지 않는다.
 *
 * 엔진 준비 화면([EngineSetupGateScreen])과 설정의 「바둑판 조작법」([BoardControlsGuideDialog])이 함께 쓴다 — 가이드는 한 곳이다.
 *
 * ## #258 — 손대면 직접, 손을 떼고 4초면 다시 시연(사용자 2026-10-13)
 * 예전에는 아래 알약 둘(「시연 보기 / 직접 해 보기」)로 골랐다. 이제 모드는 [GuideAutoMode]가 정하고, 아래에는 지금 상태를 알리는 한 줄만 있다.
 * ⚠️ **첫 터치가 삼켜지지 않게** — `GoBoard`는 누르는 순간 `inputEnabled`가 꺼져 있으면 그 제스처를 버리고, 그 값이 바뀌면 입력 처리를
 * 새로 시작한다. 그래서 시연과 직접이 **판 하나를 함께 쓰고 입력은 늘 켠다.** 무대는 손가락을 판**보다 먼저**(`PointerEventPass.Initial`)
 * 보기만 하고 소비하지 않는다 — 시연이 멈추고, 같은 손가락을 판이 그대로 받는다. 무대 밖(단계 알약 · 「나중에」)의 터치는 세지 않는다.
 * ⚠️ 시연 대본은 사용자 입력 경로(판의 `onCoordinateTap`, 버튼의 `onClick`)를 **부르지 않는다** — 그 경로들은 모드를 묻지 않고 늘 동작한다.
 *   모드를 물으면, 누른 순간과 뗀 순간 사이에 화면이 다시 그려지지 않은 빠른 탭에서 옛 모드를 읽어 첫 탭이 무시된다.
 *
 * ## ⚠️ `GoBoard`는 고치지 않는다 — 시연은 겉에 그린다
 * 판은 대국의 몸통이다(보조 기능이 메인을 훼손하지 않는다, 2026-09-20 사용자). 끌기 가늠돌은 판의 내부 상태라 밖에서 줄 수
 * 없으므로, 판 위에 겹친 캔버스가 **판과 같은 함수·상수**(`drawGhostStone`·`heldGhostStoneRadiusRatio`·`PlayDragLiftCells`)로
 * 그린다 — 실제 대국과 같아 보이게. 착수 확인의 가착수는 판의 `tentativeMove`로, 돌 버튼은 대국의 [PlayStoneButton]
 * 그대로 그린다. 직접 해 볼 때는 판이 입력을 그대로 받는다(대국 화면과 같은 제스처).
 */
@Composable
internal fun BoardControlsGuide(modifier: Modifier = Modifier) {
    val language = LocalUiStrings.current.language
    val strings = LocalUiStrings.current
    val steps = remember { boardControlsSteps(FeatureFlags.isPlayConfirmModeEnabled) }
    var step by remember { mutableStateOf(steps.first()) }
    var auto by remember { mutableStateOf(GuideAutoMode()) }
    var line by remember { mutableStateOf("") }

    // 손을 뗄 때마다(releases) 처음부터 센다. 누르고 있거나 시연 중이면 세지 않는다.
    LaunchedEffect(auto.waitsToReturn, auto.releases) {
        if (auto.waitsToReturn) {
            delay(GuideIdleReturnMillis)
            auto = auto.idleElapsed()
        }
    }

    Column(modifier = modifier) {
        // 좁은 화면·영어·큰 글꼴에서 단계 이름이 찌그러지지 않게 가로로 밀어 볼 수 있다(#253).
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            steps.forEachIndexed { index, candidate ->
                val title = when (candidate) {
                    BoardControlsStep.Drag -> UiStringsBoardControlsGuide.stepDrag(language)
                    BoardControlsStep.Confirm -> strings.confirmPlay
                    BoardControlsStep.BoardSize -> boardSizeSubjectFor(language)
                }
                GuidePill(
                    text = "${index + 1}. $title",
                    selected = candidate == step,
                    onClick = {
                        step = candidate
                        auto = auto.stepChanged()
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space12))
        PandaSays(text = line)
        Spacer(modifier = Modifier.height(AppSpacing.Space12))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 무대(판 · 돌 버튼 · 칩)에 닿는 손가락을 판보다 먼저 본다 — 소비하지 않으므로 판과 버튼은 같은 손가락을 그대로 받는다.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        auto = auto.pressed()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        auto = auto.released()
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            // 단계가 바뀌거나 시연이 새로 시작될 때만 판을 새로 만든다 — 시연에서 직접으로 넘어갈 때는 같은 판이다(첫 터치를 판이 받는다).
            key(step, auto.demoRun) {
                val onLine: (String) -> Unit = { line = it }
                val practice = auto.practicing
                when (step) {
                    BoardControlsStep.Drag -> DragStage(practice = practice, onLine = onLine)
                    BoardControlsStep.Confirm -> ConfirmStage(practice = practice, onLine = onLine)
                    BoardControlsStep.BoardSize -> BoardSizeStage(practice = practice, onLine = onLine)
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.Space8))
        // 지금 상태를 알리는 한 줄(#258) — 모드가 말없이 바뀌면 「왜 판이 바뀌지?」가 된다.
        Text(
            text = if (auto.practicing) UiStringsBoardControlsGuide.practiceHint(language) else UiStringsBoardControlsGuide.demoHint(language),
            fontSize = AppTextSize.Text13,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.Space6),
        )
    }
}

// ── 끌기 ─────────────────────────────────────────────────────────────

/** 돌 끌기 — 시연과 직접이 **판 하나**를 함께 쓴다(#258). 시연 대본은 [practice]가 켜지는 순간 멈추고, 손가락은 판이 그대로 받는다. */
@Composable
private fun DragStage(practice: Boolean, onLine: (String) -> Unit) {
    val language = LocalUiStrings.current.language
    val lines = UiStringsBoardControlsGuide.dragLines(language)
    var state by remember { mutableStateOf(DemoStartPosition) }
    var placed by remember { mutableIntStateOf(0) }
    // 손가락 자리 — 칸 단위(x = 열, y = 줄). 가늠돌은 손가락보다 PlayDragLiftCells칸 위, 가장 가까운 교차점이다(판과 같다).
    val finger = remember { Animatable(DragFingerStart, Offset.VectorConverter) }
    val growth = remember { Animatable(0f) }
    var fingerVisible by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val ghostAlpha = rememberGhostPulse()

    if (practice) {
        LaunchedEffect(placed) {
            onLine(if (placed == 0) UiStringsBoardControlsGuide.tryDrag(language) else UiStringsBoardControlsGuide.wellDone(language))
        }
    } else {
        LaunchedEffect(Unit) {
            while (true) {
                state = DemoStartPosition
                fingerVisible = false
                holding = false
                pressed = false
                growth.snapTo(0f)
                finger.snapTo(DragFingerStart)
                onLine(lines[0])
                delay(700)
                fingerVisible = true
                delay(500)
                pressed = true
                delay(450) // 길게 누름 임계
                holding = true
                growth.animateTo(1f, tween(HeldGhostStoneGrowMillis))
                delay(900)
                onLine(lines[1])
                finger.animateTo(DragFingerEnd, tween(1_600, easing = FastOutSlowInEasing))
                delay(700)
                onLine(lines[2])
                delay(500)
                val target = ghostCellUnder(finger.value)
                holding = false
                pressed = false
                state = state.playIfEmpty(target)
                delay(600)
                fingerVisible = false
                delay(2_400)
            }
        }
    }

    StageBoard(maxBoardHeightReserve = 0.dp) { side ->
        GuideBoard(
            gameState = state,
            modifier = Modifier.size(side),
            // 시연 중에도 켠다(#258) — 끄면 첫 터치를 판이 버린다(이 파일 KDoc).
            inputEnabled = true,
            // 모드를 묻지 않는다 — 시연 대본은 이 경로를 쓰지 않는다(이 파일 KDoc).
            onCoordinateTap = { coordinate ->
                val next = state.playIfEmpty(coordinate)
                if (next !== state) {
                    state = next
                    placed++
                }
            },
            overlay = if (practice) {
                null
            } else {
                { geometry ->
                    if (holding) {
                        drawGhostStone(
                            center = geometry.pointFor(ghostCellUnder(finger.value)),
                            radius = geometry.spacing * heldGhostStoneRadiusRatio(growth.value),
                            stone = state.nextPlayer,
                            alpha = ghostAlpha(),
                        )
                    }
                    if (fingerVisible) drawFinger(geometry.pointAt(finger.value), geometry.spacing, pressed)
                }
            },
        )
    }
}

// ── 착수 확인 ─────────────────────────────────────────────────────────

@Composable
private fun ConfirmStage(practice: Boolean, onLine: (String) -> Unit) {
    val strings = LocalUiStrings.current
    val language = strings.language
    var state by remember { mutableStateOf(DemoStartPosition) }
    var confirmOn by remember { mutableStateOf(false) }
    var tentative by remember { mutableStateOf<BoardCoordinate?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var buttonTapped by remember { mutableStateOf(false) }
    var placed by remember { mutableIntStateOf(0) }

    if (practice) {
        LaunchedEffect(placed) {
            onLine(
                if (placed == 0) {
                    UiStringsBoardControlsGuide.tryConfirm(language, strings.confirmPlay, strings.playMove)
                } else {
                    UiStringsBoardControlsGuide.wellDone(language)
                },
            )
        }
    } else {
        val lines = UiStringsBoardControlsGuide.confirmLines(language, strings.confirmPlay, strings.playMove)
        LaunchedEffect(Unit) {
            suspend fun tapBoard(coordinate: BoardCoordinate) {
                finger = Offset(coordinate.column.toFloat(), coordinate.row.toFloat())
                delay(350)
                tentative = coordinate
                delay(250)
                finger = null
            }
            suspend fun tapButton(action: () -> Unit) {
                buttonTapped = true
                delay(300)
                action()
                delay(200)
                buttonTapped = false
            }
            while (true) {
                state = DemoStartPosition
                confirmOn = false
                tentative = null
                onLine(lines[0])
                delay(1_400)
                tapButton { confirmOn = true }
                delay(1_200)
                onLine(lines[1])
                delay(700)
                tapBoard(BoardCoordinate(row = 4, column = 4))
                delay(1_600)
                onLine(lines[2])
                delay(700)
                tapBoard(BoardCoordinate(row = 3, column = 5))
                delay(1_600)
                onLine(lines[3])
                delay(1_000)
                tapButton {
                    tentative?.let { state = state.playIfEmpty(it) }
                    tentative = null
                }
                delay(2_600)
            }
        }
    }

    StageBoard(maxBoardHeightReserve = StoneButtonSize + AppSpacing.Space12) { side ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GuideBoard(
                gameState = state,
                modifier = Modifier.size(side),
                tentativeMove = tentative,
                // 착수 확인이 켜져 있으면 판은 가착수만 놓는다. 입력은 시연 중에도 켠다(#258) — 끄면 첫 터치를 판이 버린다.
                uxOptions = KaTrainUxOptions(isDirectPlayEnabled = !confirmOn),
                inputEnabled = true,
                onCoordinateTap = { coordinate ->
                    if (state.stoneAt(coordinate) == null) {
                        if (confirmOn) {
                            tentative = coordinate
                        } else {
                            // 바로 착수로도 놓이긴 하지만 칭찬하지 않는다(#253) — 이 단계가 가르치는 것은 「켜고 → 놓아 보고 → 확정」이다.
                            state = state.playIfEmpty(coordinate)
                        }
                    }
                },
                // 끌기 시작하면 잡아 둔 가착수를 내려놓는다 — 대국 화면과 같다(손끝의 돌이 하나다).
                onPlayDragStart = { tentative = null },
                overlay = { geometry -> if (!practice) finger?.let { drawFinger(geometry.pointAt(it), geometry.spacing, pressed = true) } },
            )
            Spacer(modifier = Modifier.height(AppSpacing.Space12))
            val face = playStoneFaceOf(isDirectPlay = !confirmOn, hasTentativeMove = tentative != null)
            Box(contentAlignment = Alignment.Center) {
                PlayStoneButton(
                    face = face,
                    label = if (face == PlayStoneFace.CommitMove) strings.playMove else strings.confirmPlayOnStone,
                    enabled = true,
                    // 모드를 묻지 않는다 — 시연 대본은 이 버튼을 누르지 않는다(이 파일 KDoc).
                    onClick = {
                        if (face == PlayStoneFace.CommitMove) {
                            tentative?.let { state = state.playIfEmpty(it) }
                            tentative = null
                            placed++
                        } else {
                            confirmOn = !confirmOn
                            tentative = null
                        }
                    },
                    modifier = Modifier.size(StoneButtonSize),
                )
                if (buttonTapped && !practice) FingerDot()
            }
        }
    }
}

// ── 판 크기 ───────────────────────────────────────────────────────────

@Composable
private fun BoardSizeStage(practice: Boolean, onLine: (String) -> Unit) {
    val language = LocalUiStrings.current.language
    val maxLabel = boardSizeToggleLabelFor(language, isMaxSize = true)
    var isMax by remember { mutableStateOf(true) }
    var chipTapped by remember { mutableStateOf(false) }
    val fraction by animateFloatAsState(if (isMax) 1f else BoardInsetFraction, tween(400), label = "guideBoardFraction")

    if (practice) {
        LaunchedEffect(Unit) { onLine(UiStringsBoardControlsGuide.trySize(language, maxLabel)) }
    } else {
        val lines = UiStringsBoardControlsGuide.sizeLines(language, maxLabel)
        LaunchedEffect(Unit) {
            while (true) {
                isMax = true
                onLine(lines[0])
                delay(1_600)
                chipTapped = true
                delay(300)
                isMax = false
                delay(200)
                chipTapped = false
                delay(1_400)
                onLine(lines[1])
                delay(2_600)
                chipTapped = true
                delay(300)
                isMax = true
                delay(200)
                chipTapped = false
                delay(1_600)
            }
        }
    }

    StageBoard(maxBoardHeightReserve = ChipRowHeight + AppSpacing.Space8) { side ->
        Column(modifier = Modifier.width(side)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(contentAlignment = Alignment.Center) {
                    // 대국 화면 판 위의 칩과 같은 규칙 — 글자는 하나(`바둑판 최대`)이고 켜짐·꺼짐은 테두리가 말한다.
                    GuidePill(
                        text = maxLabel,
                        selected = isMax,
                        // 모드를 묻지 않는다 — 시연 대본은 이 칩을 누르지 않는다(이 파일 KDoc).
                        onClick = { isMax = !isMax },
                        modifier = Modifier.height(ChipRowHeight),
                    )
                    if (chipTapped && !practice) FingerDot()
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.Space8))
            Box(modifier = Modifier.size(side), contentAlignment = Alignment.Center) {
                GuideBoard(gameState = DemoStartPosition, modifier = Modifier.size(side * fraction))
            }
        }
    }
}

// ── 공통 ──────────────────────────────────────────────────────────────

/** 받는 자리에 맞춰 판 한 변을 정한다 — 폭과 (높이 − 판 위아래에 붙는 것의 높이) 가운데 작은 쪽. */
@Composable
private fun StageBoard(maxBoardHeightReserve: Dp, content: @Composable (side: Dp) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth, (maxHeight - maxBoardHeightReserve).coerceAtLeast(MinBoardSide))
        content(side)
    }
}

/**
 * 진짜 대국 판에 시연용 그림을 겹친다. 겹친 캔버스는 판의 캔버스와 **같은 자리**다(판은 테두리 안쪽 `Space3`에 그린다).
 * 겹친 캔버스는 입력을 받지 않으므로 「직접 해 보기」의 손가락은 그대로 판에 간다.
 */
@Composable
private fun GuideBoard(
    gameState: GameState,
    modifier: Modifier,
    tentativeMove: BoardCoordinate? = null,
    uxOptions: KaTrainUxOptions = KaTrainUxOptions(),
    inputEnabled: Boolean = false,
    onCoordinateTap: (BoardCoordinate) -> Unit = {},
    onPlayDragStart: () -> Unit = {},
    overlay: (DrawScope.(BoardGeometry) -> Unit)? = null,
) {
    Box(modifier = modifier.aspectRatio(1f)) {
        GoBoard(
            gameState = gameState,
            candidateMoves = emptyList(),
            moveReviews = emptyList(),
            ownershipEstimate = null,
            uxOptions = uxOptions,
            inputEnabled = inputEnabled,
            engineActivityIndicator = null,
            modifier = Modifier.fillMaxSize(),
            tentativeMove = tentativeMove,
            onPlayDragStart = onPlayDragStart,
            onCoordinateTap = onCoordinateTap,
        )
        if (overlay != null) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(AppSpacing.Space3),
            ) {
                overlay(BoardGeometry.from(size, gameState.boardSize, showCoordinates = uxOptions.showCoordinates))
            }
        }
    }
}

/** 판다가 하는 말 — 두 줄 높이를 지켜 말이 바뀔 때 판이 들썩이지 않게 한다. */
@Composable
private fun PandaSays(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        FirstDolAvatar(size = 44.dp)
        Spacer(modifier = Modifier.width(AppSpacing.Space8))
        Surface(
            shape = RoundedCornerShape(AppRadius.Corner14),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(AppBorderWidth.Hairline, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                fontSize = AppTextSize.Text14,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                modifier = Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space10),
            )
        }
    }
}

@Composable
private fun GuidePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(AppRadius.Corner14),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            AppBorderWidth.Hairline,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space6)) {
            Text(
                text = text,
                fontSize = AppTextSize.Text13,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 판 밖(돌 버튼·칩)을 누르는 손가락. */
@Composable
private fun FingerDot() {
    Canvas(modifier = Modifier.size(FingerDotSize)) {
        drawFinger(center = center, spacing = size.minDimension / 1.1f, pressed = true)
    }
}

/** 화면 녹화의 터치 표시처럼 — 반투명 회색 원에 흰 테. 누르고 있으면 조금 작고 진하다. */
private fun DrawScope.drawFinger(center: Offset, spacing: Float, pressed: Boolean) {
    val radius = spacing * if (pressed) 0.46f else 0.54f
    drawCircle(color = GoBoardPalette.GuideFingerFill.copy(alpha = if (pressed) 0.30f else 0.18f), radius = radius, center = center)
    drawCircle(color = GoBoardPalette.GuideFingerRing.copy(alpha = 0.9f), radius = radius, center = center, style = Stroke(width = spacing * 0.07f))
}

/** 칸 단위(열·줄, 소수) → 판 캔버스의 점. */
private fun BoardGeometry.pointAt(cells: Offset): Offset = Offset(origin.x + cells.x * spacing, origin.y + cells.y * spacing)

/** 손가락 아래의 가늠돌 자리 — 판처럼 손가락보다 [PlayDragLiftCells]칸 위, 가장 가까운 교차점. */
private fun ghostCellUnder(finger: Offset): BoardCoordinate {
    val last = DemoBoardSize.value - 1
    return BoardCoordinate(
        row = (finger.y - PlayDragLiftCells).roundToInt().coerceIn(0, last),
        column = finger.x.roundToInt().coerceIn(0, last),
    )
}

/** 빈 자리면 두고, 아니면(이미 돌이 있거나 규칙상 못 두면) 판을 그대로 돌려준다. */
private fun GameState.playIfEmpty(coordinate: BoardCoordinate): GameState {
    if (stoneAt(coordinate) != null) return this
    return runCatching { play(Move.Play(nextPlayer, coordinate)) }.getOrDefault(this)
}

/** 판의 가착수·가늠돌이 깜빡이는 폭과 같다(`GoBoard`의 `ghostAlpha`: 0.35~0.65, 0.8초). */
@Composable
private fun rememberGhostPulse(): () -> Float {
    val transition = rememberInfiniteTransition(label = "guideGhostPulse")
    val alpha = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing), RepeatMode.Reverse),
        label = "guideGhostAlpha",
    )
    return { alpha.value }
}

private val DemoBoardSize = BoardSize.Nine

/** 시연의 시작 국면 — 빈 판보다 「대국 중」으로 읽히게 돌 몇 개를 둔다. 끌기·착수 확인의 목표 자리는 비어 있다. */
private val DemoStartPosition: GameState = GameState.empty(DemoBoardSize)
    .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 2, column = 6)))
    .play(Move.Play(StoneColor.White, BoardCoordinate(row = 6, column = 2)))
    .play(Move.Play(StoneColor.Black, BoardCoordinate(row = 6, column = 6)))
    .play(Move.Play(StoneColor.White, BoardCoordinate(row = 2, column = 2)))

/** 끌기 시연의 손가락 — 가늠돌은 1.5칸 위라 시작 (6줄, 3열) → 끝 (4줄, 5열)에 뜬다. */
private val DragFingerStart = Offset(3f, 6f + PlayDragLiftCells)
private val DragFingerEnd = Offset(5f, 4f + PlayDragLiftCells)

private val StoneButtonSize = 64.dp
private val ChipRowHeight = 32.dp
private val FingerDotSize = 40.dp
/** 판의 가장 작은 한 변 — 작은 화면에서 돌 버튼·칩을 밀어내지 않을 만큼 작게 둔다(#253, 예전 200dp). */
private val MinBoardSide = 120.dp

/** 「바둑판 최대」를 끈 판의 크기 — 대국 화면의 좌우 여백(16dp)을 시연 폭에 맞춰 조금 과장했다. */
private const val BoardInsetFraction = 0.86f
