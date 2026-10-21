package com.worksoc.goaicoach.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.persistence.LifeAndDeathProgressStore
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.content.LifeAndDeathPhase
import com.worksoc.goaicoach.shared.content.LifeAndDeathSession
import com.worksoc.goaicoach.shared.content.LifeAndDeathUnit
import com.worksoc.goaicoach.shared.content.lifeAndDeathProblemsOf
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.ui.board.GoBoard
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.StudyCategory
import com.worksoc.goaicoach.ui.l10n.keepWordsWhole
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathCorrectFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathExplanationFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathKeepGoingFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathNextProblemFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathPreviousProblemFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathPromptFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathRetryFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathShowAnswerFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathShowingAnswerFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathUnitSummaryFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathUnitTitleFor
import com.worksoc.goaicoach.ui.l10n.lifeAndDeathWrongFor
import com.worksoc.goaicoach.ui.l10n.studyCategoryTitleFor
import com.worksoc.goaicoach.ui.l10n.studyLessonBackToListFor
import com.worksoc.goaicoach.ui.l10n.studyLessonNextLessonFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 3 Depth: 「기본 사활」(백로그 #184) — 단원 목록과, 고른 단원의 문제를 **풀어 보는** 화면.
 *
 * 학습 단원(`StudyLessonTrackScreen`)과 형식이 다르다 — 거기는 도해를 넘겨 읽고, 여기는 판에 직접 둔다(U-51 「푸는 학습」).
 * 그래서 화면을 따로 두되 **머리말 · 목록 줄 · 판과 글의 비율 · 아래 조작부의 모양**은 학습 단원과 같은 것을 쓴다 —
 * 한 허브에서 들어온 화면들이 서로 다른 앱처럼 보이지 않게.
 *
 * ⚠️ **단원과 문제도 이 화면의 상태다 — 셸의 목적지가 아니다**(함정 3, 학습 단원과 같은 판단). 뒤로가기는 한 겹에 `BackHandler` 하나 —
 * 문제 → 단원 목록 → 허브 → 홈.
 */
@Composable
internal fun LifeAndDeathTrackScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val store = remember(context) { LifeAndDeathProgressStore(context) }
    // 푼 기록은 이 화면이 들고, 문제 화면은 풀 때마다 저장소가 돌려준 집합으로 갈아 끼운다(함정 13 — 저장소만 고치면 목록의 숫자가 낡는다).
    var solved by remember { mutableStateOf(store.solved()) }
    var opened by remember { mutableStateOf<OpenedProblem?>(null) }

    opened?.let { problem ->
        LifeAndDeathProblemScreen(
            unit = problem.unit,
            index = problem.index,
            solved = solved,
            onSolved = { id -> solved = store.markSolved(id) },
            onOpen = { unit, index -> opened = OpenedProblem(unit, index) },
            onBackClick = { opened = null },
            modifier = modifier,
        )
        return
    }

    BackHandler { onBackClick() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StudyScreenHeader(
            title = studyCategoryTitleFor(strings.language, StudyCategory.LifeAndDeath),
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.Space16),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
        ) {
            LifeAndDeathUnit.entries.forEach { unit ->
                val problems = lifeAndDeathProblemsOf(unit)
                val solvedCount = problems.count { it.id in solved }
                StudyEntryRow(
                    title = lifeAndDeathUnitTitleFor(strings.language, unit),
                    // 한국어는 어절 안에서 줄을 바꾸지 않는다 — 홈 명언(#273)과 같은 함수다(「빼앗는 / 다」가 됐다, 에뮬레이터 2026-10-21).
                    subtitle = keepWordsWhole(lifeAndDeathUnitSummaryFor(strings.language, unit), strings.language),
                    // 숫자만이라 번역이 필요 없다 — 네 언어 어디서도 같은 폭이다(함정 21).
                    badge = progressLabel(solvedCount, problems.size),
                    // 처음 들어가면 아직 안 푼 첫 문제부터 — 다 풀었으면 첫 문제.
                    onClick = {
                        opened = OpenedProblem(unit, problems.indexOfFirst { it.id !in solved }.coerceAtLeast(0))
                    },
                )
            }
        }
    }
}

private data class OpenedProblem(val unit: LifeAndDeathUnit, val index: Int)

/** 단원의 진행 — 다 풀면 앞에 ✓를 단다. */
private fun progressLabel(solved: Int, total: Int): String =
    if (solved >= total) "✓ $solved / $total" else "$solved / $total"

/**
 * 문제 한 판. 판에 두면 풀이기(`LifeAndDeathSession`)가 판정하고, 상대의 응수는 [ReplyDelayMillis] 뒤에 놓인다 —
 * 돌 두 개가 한꺼번에 놓이면 무엇이 무엇에 답했는지 안 보인다.
 *
 * ⚠️ 판정은 **배경 스레드에서** 한다 — 문제 구역을 끝까지 읽는 데 폰에서 수십 ms가 들 수 있다. 풀이기는 스레드 안전하지 않으므로
 * 계산하는 동안([busy]) 판의 입력을 막아 한 번에 한 호출만 간다. 문제를 열 때 정답 수순을 먼저 읽어 기억을 데워 둔다.
 * ⚠️ 스크롤 부모를 두지 않는다 — 판이 끌기를 가진다(함정 44, 학습 단원과 같다).
 */
@Composable
private fun LifeAndDeathProblemScreen(
    unit: LifeAndDeathUnit,
    index: Int,
    solved: Set<String>,
    onSolved: (String) -> Unit,
    onOpen: (LifeAndDeathUnit, Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    val problems = remember(unit) { lifeAndDeathProblemsOf(unit) }
    val problem = problems[index]
    // ⚠️ 키가 문제 id다 — 「다음 문제」로 갈아탈 때 이 조각은 살아 있으므로, 키가 없으면 앞 문제의 판과 풀이기가 그대로 남는다.
    val session = remember(problem.id) { LifeAndDeathSession(problem) }
    var state by remember(problem.id) { mutableStateOf(session.start()) }
    var busy by remember(problem.id) { mutableStateOf(true) }
    // 「정답 보기」 — 처음 국면부터 한 수씩 넘기는 판들. `null`이면 풀고 있는 중이다.
    var answerBoards by remember(problem.id) { mutableStateOf<List<GameState>?>(null) }
    var answerStep by remember(problem.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    BackHandler { onBackClick() }

    LaunchedEffect(problem.id) {
        withContext(Dispatchers.Default) { session.answerLine() }
        busy = false
    }
    LaunchedEffect(state) {
        val phase = state.phase
        if (phase is LifeAndDeathPhase.AwaitingReply) {
            delay(ReplyDelayMillis)
            state = session.applyReply(state)
        } else if (phase == LifeAndDeathPhase.Solved && problem.id !in solved) {
            onSolved(problem.id)
        }
    }
    LaunchedEffect(answerBoards) {
        val boards = answerBoards ?: return@LaunchedEffect
        for (step in boards.indices) {
            answerStep = step
            if (step < boards.lastIndex) delay(AnswerStepMillis)
        }
    }

    val showingAnswer = answerBoards != null
    val answerFinished = answerBoards?.let { answerStep == it.lastIndex } ?: false
    val board = answerBoards?.getOrNull(answerStep) ?: state.board
    val phase = state.phase
    val feedback = when {
        showingAnswer && answerFinished ->
            lifeAndDeathShowingAnswerFor(language) + "\n" + lifeAndDeathExplanationFor(language, problem.id)
        showingAnswer -> lifeAndDeathShowingAnswerFor(language)
        phase == LifeAndDeathPhase.Solved ->
            lifeAndDeathCorrectFor(language) + "\n" + lifeAndDeathExplanationFor(language, problem.id)
        phase == LifeAndDeathPhase.Failed ||
            (phase is LifeAndDeathPhase.AwaitingReply && phase.wrong) -> lifeAndDeathWrongFor(language, problem.goal)
        phase == LifeAndDeathPhase.Solving(afterCorrect = true) -> lifeAndDeathKeepGoingFor(language)
        else -> null
    }
    val pristine = !showingAnswer && state.board.moves.isEmpty()
    val nextUnit = LifeAndDeathUnit.entries.getOrNull(unit.ordinal + 1)
    val isLastProblem = index == problems.lastIndex

    val bodyScroll = rememberScrollState()
    LaunchedEffect(problem.id) { bodyScroll.scrollTo(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StudyScreenHeader(
            title = lifeAndDeathUnitTitleFor(language, unit),
            onBackClick = onBackClick,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(LifeAndDeathBoardShare)
                .padding(horizontal = AppSpacing.Space12),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = board,
                candidateMoves = emptyList(),
                moveReviews = emptyList(),
                ownershipEstimate = null,
                // 좌표·수순 번호는 끈다(학습 단원과 같다) — 마지막 수의 고리는 켜 둔다: 상대가 방금 어디에 받았는지가 보여야 한다.
                uxOptions = remember { KaTrainUxOptions() },
                inputEnabled = !busy && !showingAnswer && phase is LifeAndDeathPhase.Solving,
                engineActivityIndicator = null,
                modifier = Modifier.fillMaxSize(),
                mistakePoints = if (showingAnswer) emptySet() else setOfNotNull(state.mistake),
                onCoordinateTap = { point ->
                    if (busy || showingAnswer || state.phase !is LifeAndDeathPhase.Solving) return@GoBoard
                    busy = true
                    val before = state
                    scope.launch {
                        val next = withContext(Dispatchers.Default) { session.play(before, point) }
                        if (next != null) state = next
                        busy = false
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(LifeAndDeathBodyShare),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = AppSpacing.Space20, vertical = AppSpacing.Space8),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = keepWordsWhole(lifeAndDeathPromptFor(language, problem.goal), language),
                    fontSize = AppTextSize.Text16,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                if (feedback != null) {
                    Text(
                        text = keepWordsWhole(feedback, language),
                        fontSize = AppTextSize.Text15,
                        lineHeight = 23.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    OutlinedButton(
                        onClick = {
                            answerBoards = null
                            state = session.start()
                        },
                        enabled = !busy && !pristine,
                    ) {
                        Text(text = lifeAndDeathRetryFor(language), textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                val boards = withContext(Dispatchers.Default) { session.answerBoards() }
                                state = session.start()
                                answerStep = 0
                                answerBoards = listOf(state.board) + boards
                                busy = false
                            }
                        },
                        enabled = !busy && !showingAnswer,
                    ) {
                        Text(text = lifeAndDeathShowAnswerFor(language), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        LifeAndDeathControls(
            progress = "${index + 1} / ${problems.size}",
            solvedBefore = problem.id in solved,
            previousLabel = lifeAndDeathPreviousProblemFor(language),
            onPrevious = { onOpen(unit, index - 1) }.takeIf { index > 0 },
            forwardLabel = when {
                !isLastProblem -> lifeAndDeathNextProblemFor(language)
                nextUnit != null -> studyLessonNextLessonFor(language)
                else -> studyLessonBackToListFor(language)
            },
            onForward = when {
                !isLastProblem -> ({ onOpen(unit, index + 1) })
                nextUnit != null -> ({ onOpen(nextUnit, 0) })
                else -> onBackClick
            },
        )
    }
}

/**
 * 판과 글이 남은 높이를 나눠 갖는 비율 — 학습 단원의 `BoardShare`·`BodyShare`(1.2 : 1)와 같다. 글 밑에 버튼 한 줄이 더 있지만 글 상자가
 * 자기 안에서 구르므로 판을 더 줄이지 않는다. ⚠️ 바꿀 때는 가장 긴 해설(영어 · 글꼴 1.3배)을 360dp 폭에서 다시 밟을 것(함정 9·21).
 */
private const val LifeAndDeathBoardShare = 1.2f
private const val LifeAndDeathBodyShare = 1f

/** 상대가 받기까지의 뜸 — 내 돌이 먼저 보이고 상대 돌이 뒤따라야 「응수」로 읽힌다. */
private const val ReplyDelayMillis = 450L

/** 「정답 보기」에서 한 수씩 넘기는 간격. */
private const val AnswerStepMillis = 800L

/**
 * 문제 넘김 조작부 — 학습 단원의 장 넘김(`StudyLessonControls`)과 같은 모양이다.
 * ⚠️ 비활성이 글자 농도로만 보이면 안 된다(함정 42) — 채운 [Button]이라 바탕색까지 내려간다.
 */
@Composable
private fun LifeAndDeathControls(
    progress: String,
    solvedBefore: Boolean,
    previousLabel: String,
    onPrevious: (() -> Unit)?,
    forwardLabel: String,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        Button(
            onClick = onPrevious ?: {},
            enabled = onPrevious != null,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = previousLabel, textAlign = TextAlign.Center)
        }
        // ✓는 숫자 **위에** 작게 얹는다 — 옆에 붙이면 가운데가 넓어져 큰 글꼴 영어에서 「Previou / s」로 접혔다(에뮬레이터 360dp, 2026-10-21).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (solvedBefore) {
                Text(
                    text = "✓",
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = progress,
                fontSize = AppTextSize.Text14,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Button(
            onClick = onForward,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = forwardLabel, textAlign = TextAlign.Center)
        }
    }
}
