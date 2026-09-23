package com.worksoc.goaicoach.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.content.StudyLesson
import com.worksoc.goaicoach.shared.content.StudyLessonId
import com.worksoc.goaicoach.shared.content.StudyLessonTrack
import com.worksoc.goaicoach.shared.content.studyLessonsFor

/**
 * 3 Depth: 한 **갈래**의 단원 목록과, 고른 단원의 도해를 한 장씩 넘기는 화면
 * (백로그 #164 「바둑 규칙 배우기」 · #183 「바둑 기초 행마」).
 *
 * ⚠️ **갈래를 늘릴 때 이 파일을 복사하지 말 것** — 화면은 [track]만 받아 그린다. 새 갈래는
 * `StudyLessonTrack`에 값을 더하고 `studyLessons`에 단원을, 곁표에 문구를 더하면 열린다.
 *
 * ⚠️ **단원도 이 화면의 상태다 — 셸의 목적지가 아니다.** 학습 허브가 하위 분류를 자기 상태로
 * 가진 것과 같은 판단이다(백로그 #163·#156, 함정 3: 셸의 상태 훅 예산은 42/42로 여유 0).
 *
 * ⚠️ **뒤로가기가 이제 세 겹이다** — 단원 → 목록 → 허브 → 홈. 한 겹에 `BackHandler` 하나씩
 * 두고, **한 번에 하나만 컴포즈되게** 갈라 놓는다(아래 `return`). 둘이 동시에 살아 있으면
 * 어느 쪽이 먼저 잡는지가 등록 순서에 달리게 되어, 읽는 사람이 예측할 수 없다.
 */
@Composable
internal fun StudyLessonTrackScreen(
    track: StudyLessonTrack,
    category: StudyCategory,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val lessons = remember(track) { studyLessonsFor(track) }
    // ⚠️ 갈래가 바뀌면 열려 있던 단원을 버린다 — 키가 없으면 다른 갈래의 단원이 그대로 열린다.
    var openedLesson by remember(track) { mutableStateOf<StudyLessonId?>(null) }

    openedLesson?.let { lessonId ->
        StudyLessonScreen(
            lessons = lessons,
            lessonId = lessonId,
            onBackClick = { openedLesson = null },
            onOpenLesson = { next -> openedLesson = next },
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
            title = studyCategoryTitleFor(strings.language, category),
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lessons.forEach { lesson ->
                // ⚠️ 허브와 **같은 줄 컴포넌트**를 쓴다 — 한 칸 들어왔을 뿐인데 행의 생김새가
                // 바뀌면 다른 곳에 온 것처럼 읽힌다. 「준비 중」이 없는 것만 다르다.
                StudyEntryRow(
                    title = studyLessonTitleFor(strings.language, lesson.id),
                    subtitle = studyLessonSummaryFor(strings.language, lesson.id),
                    comingSoonLabel = null,
                    onClick = { openedLesson = lesson.id },
                )
            }
        }
    }
}

/**
 * 단원 한 편 — 도해를 한 장씩 넘기며 읽는다.
 *
 * ⚠️ **스크롤 부모를 두지 않는다**(선례: `GameReplayScreen.kt:203`). 판은 `pointerInput`이
 * `awaitFirstDown().consume()`을 `inputEnabled` 판정보다 **먼저** 하므로(`GoBoard.kt:238`),
 * 읽기 전용이어도 판 위에서 시작한 끌기를 삼킨다 — 스크롤 안에 넣으면 판 위에서 화면이
 * 굴러가지 않는다(함정 44). 대신 판과 설명이 **각자 몫의 높이를 나눠 갖고**, 설명만 자기
 * 안에서 구른다(글꼴 배율이 올라가도 판이 밀려나지 않는다).
 */
@Composable
private fun StudyLessonScreen(
    lessons: List<StudyLesson>,
    lessonId: StudyLessonId,
    onBackClick: () -> Unit,
    onOpenLesson: (StudyLessonId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val lesson = remember(lessonId) { lessons.single { it.id == lessonId } }
    // 도해는 수순에서 접는다 — 좌표 표를 손으로 적지 않으므로 규칙과 어긋날 수 없다.
    val states = remember(lessonId) { lesson.statesByStep() }
    // ⚠️ `remember`의 키가 [lessonId]다 — 「다음 단원」으로 갈아탈 때 이 조각은 그대로
    // 살아 있으므로, 키가 없으면 새 단원이 **이전 단원의 마지막 장**에서 열린다.
    var stepIndex by remember(lessonId) { mutableIntStateOf(0) }

    BackHandler { onBackClick() }

    val step = lesson.steps[stepIndex]
    val state = states[stepIndex]
    val isLastStep = stepIndex == lesson.steps.lastIndex
    val nextLesson = lessons.getOrNull(lessons.indexOf(lesson) + 1)?.id

    val bodyScroll = rememberScrollState()
    // 장을 넘겼는데 설명이 아래로 굴러가 있으면 첫 줄을 놓친다.
    LaunchedEffect(lessonId, stepIndex) { bodyScroll.scrollTo(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StudyScreenHeader(
            title = studyLessonTitleFor(strings.language, lessonId),
            onBackClick = onBackClick,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(BoardShare)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = state,
                candidateMoves = emptyList(),
                moveReviews = emptyList(),
                ownershipEstimate = null,
                // 좌표·수순 번호는 끈다 — 도해가 말하려는 것 위에 숫자가 덧씌워진다.
                // 마지막 수의 고리는 켜 둔다(기본값): 방금 무엇이 놓였는지가 설명의 주어다.
                uxOptions = remember { KaTrainUxOptions() },
                inputEnabled = false,
                engineActivityIndicator = null,
                modifier = Modifier.fillMaxSize(),
                // ⚠️ 가늠돌의 뜻은 **「다음은 여기」 하나뿐이다**(`StudyLessonStep.marker`).
                // 둘 수 없는 자리에 찍으면 판이 "놓을 수 있다"고 말해 글과 반대가 된다 —
                // `StudyLessonsTest.everyMarkerPointsAtALegalMove`가 그물이다.
                tentativeMove = step.marker,
                onCoordinateTap = {},
                // 마지막 단원은 두 번 거르며 실제로 끝난다 — 끝난 판의 톤을 그대로 받는다.
                isGameEnded = state.hasConsecutivePasses(),
            )
        }

        // 설명은 **자기 몫 안에서 가운데로** 선다. 짧은 단계가 많아 위로 붙이면 글과 버튼
        // 사이에 구멍이 뚫린 것처럼 보인다 — 남는 높이를 위아래로 나눠 여백으로 읽히게 한다.
        // ⚠️ 구르는 것은 안쪽 [Column]이다. 바깥 [Box]에 스크롤을 걸면 상자가 글 높이까지만
        // 줄어들어 가운데 정렬이 아무 일도 하지 않는다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(BodyShare),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = studyLessonBodyFor(strings.language, step.id),
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StudyLessonControls(
            stepNumber = stepIndex + 1,
            stepCount = lesson.steps.size,
            onPrevious = { stepIndex -= 1 }.takeIf { stepIndex > 0 },
            forwardLabel = when {
                !isLastStep -> studyLessonNextFor(strings.language)
                nextLesson != null -> studyLessonNextLessonFor(strings.language)
                else -> studyLessonBackToListFor(strings.language)
            },
            onForward = when {
                !isLastStep -> ({ stepIndex += 1 })
                nextLesson != null -> ({ onOpenLesson(nextLesson) })
                else -> onBackClick
            },
            previousLabel = studyLessonPreviousFor(strings.language),
        )
    }
}

/**
 * 판과 설명이 남은 높이를 나눠 갖는 비율. **dp가 아니라 비율인 이유**는 글꼴 배율이 올라가기
 * 때문이다(함정 9) — 고정 높이를 주면 큰 글씨에서 설명 상자가 글자를 잘라 먹는다.
 * ⚠️ 이 앱이 실제로 쓰는 배율은 **1.0과 1.3 둘뿐이다**(`AppFontScales`) — 시스템 배율을 따르지
 * 않으므로(`AppFontScalePolicy`) 2.0을 걱정할 것이 아니라 **1.3을 실기로 밟아야** 한다.
 * 판 쪽이 더 큰 것은 판이 이 화면의 주어라서다.
 */
private const val BoardShare = 1.2f
private const val BodyShare = 1f

/*
 * ⚠️ **이 비율은 실기로 정했다**(2026-09-22). 처음에 판을 2f로 두었더니 **영어 1.3배**에서
 * `forbidden.suicide`(네 언어 중 가장 긴 본문, CJK를 2로 세어 257칸)의 **마지막 줄이 잘렸다** —
 * 구를 수는 있지만 잘린 것처럼 보여 아무도 아래를 밀어 보지 않는다(#107이 같은 값을 치렀다).
 * 판은 작아져도 읽히고 글은 잘리면 못 읽으므로, 모자랄 때 **양보하는 쪽은 판**이다.
 * 비율을 줄일 때는 그 본문을 영어·1.3배로 다시 밟을 것 — 가장 긴 것 하나가 전부를 대표한다.
 */

/**
 * 장 넘김 조작부.
 *
 * ⚠️ **비활성이 글자 농도로만 보이면 안 된다**(함정 42). 채운 [Button]을 쓰는 이유가 그것이다
 * — M3가 비활성일 때 **바탕색까지** 내려 주므로 배경이라는 다른 축이 함께 움직인다.
 */
@Composable
private fun StudyLessonControls(
    stepNumber: Int,
    stepCount: Int,
    previousLabel: String,
    onPrevious: (() -> Unit)?,
    forwardLabel: String,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPrevious ?: {},
            enabled = onPrevious != null,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = previousLabel, textAlign = TextAlign.Center)
        }

        // 진행 표시는 숫자뿐이라 번역이 필요 없다 — 네 언어 어디서도 같은 폭이다(함정 21).
        Text(
            text = "$stepNumber / $stepCount",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
        )

        Button(
            onClick = onForward,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = forwardLabel, textAlign = TextAlign.Center)
        }
    }
}
