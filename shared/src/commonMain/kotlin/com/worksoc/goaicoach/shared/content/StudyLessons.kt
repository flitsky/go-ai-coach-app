package com.worksoc.goaicoach.shared.content

import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardRules
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameState
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 학습 단원이 속한 **갈래**(백로그 #164·#183).
 *
 * ⚠️ **갈래를 늘릴 때 화면이나 데이터 모델을 복사하지 말 것** — 여기에 값을 더하고
 * [studyLessons]에 단원을 더하면 화면은 그대로 그린다. 「규칙」과 「행마」가 형식이 같아서
 * (ⓐ+ 정적 문구 + 도해 넘김) 갈래 하나로 갈라 두는 것이 두 벌을 유지하는 것보다 싸다.
 */
enum class StudyLessonTrack {
    /** 바둑 규칙 배우기(#164) — 처음 두는 사람이 한 판도 두기 전에 읽는다. */
    Rules,

    /** 바둑 기초 행마(#183) — [Rules]를 읽었다는 전제로 쓰여 있다. */
    Shapes,
}

/**
 * 학습 단원의 이름표(백로그 #164·#183).
 *
 * ⚠️ **갈래가 달라도 한 enum이다** — 문구 표의 열쇠이자 화면의 상태라서, 갈래마다 enum을
 * 따로 두면 그 둘을 받는 쪽이 전부 갈라진다. 접두사(`Rule…`/`Shape…`)로 읽는다.
 * ⚠️ **순서가 곧 화면의 순서다.** 앞 단원을 읽었다는 전제로 뒤 단원의 문구가 쓰여 있으므로
 * 임의로 섞지 말 것.
 */
enum class StudyLessonId {
    // ── 규칙 ────────────────────────────────────────────────────────
    /** 교차점·번갈아 두기 — 판에 처음 손을 대는 사람이 읽는 한 쪽. */
    RuleBasics,

    /** 활로. 뒤의 따냄·착수금지·패가 전부 이 개념 위에 선다. */
    RuleLiberties,

    /** 단수와 따냄. */
    RuleCapture,

    /** 착수금지(자살수)와 그 예외(따내는 수는 둘 수 있다). */
    RuleForbidden,

    /** 패 — 되따냄 금지와 팻감. */
    RuleKo,

    /** 집·거르기·계가·덤. */
    RuleTerritory,

    // ── 행마 ────────────────────────────────────────────────────────
    /** 대각으로 놓인 두 점은 아직 이어져 있지 않다 — 끊는 자리와 잇는 수. */
    ShapeCut,

    /** 단수에 몰린 돌을 뻗어 달아나기, 그리고 언제 버릴지. */
    ShapeExtend,

    /** 호구 — 뛰어들면 바로 잡히는, 끊기지 않는 이음. */
    ShapeTigerMouth,

    /** 한 칸 뜀 — 빠르면서도 쉽게 끊기지 않는 행마. */
    ShapeJump,

    /** 날일자 — 더 빠르지만 그만큼 약한 행마. */
    ShapeKnight,
}

/**
 * 단원 안의 한 단계 = **도해 한 장 + 설명 한 덩이.**
 *
 * @param id 문구 표(`UiStringsStudyRules.kt`)의 키. 좌표를 키로 쓰지 않는 이유는 도해를
 *   고쳐도 문구를 그대로 두는 경우가 흔하기 때문이다(`StudyVideoEntry.id`와 같은 판단).
 * @param move 이 단계에서 **실제로 두는 수.** `null`이면 판을 그대로 두고 설명만 바꾼다.
 *   ⚠️ 두는 수는 [BoardRules]를 그대로 지난다 — 차례가 어긋나거나 위법수면 **예외가 난다.**
 *   도해가 규칙을 어기는 사고를 컴파일이 아니라 테스트가 잡아 준다(`StudyLessonsTest`).
 * @param marker 가늠돌로 가리킬 자리 — 뜻은 **"다음은 여기"** 하나뿐이다.
 *   ⚠️ **둘 수 없는 자리를 가리키는 데 쓰지 말 것.** 판은 이것을 `tentativeMove`로 받아
 *   *놓을 수 있는 자리*의 깜빡이는 돌로 그린다(`GoBoard.kt:442`) — 착수금지 자리에 찍으면
 *   그림과 글이 정반대를 말한다. 그 단계는 `marker = null`로 두고 문구가 설명한다.
 */
data class StudyLessonStep(
    val id: String,
    val move: Move? = null,
    val marker: BoardCoordinate? = null,
)

/**
 * 한 단원. [setupBlack]·[setupWhite]는 **규칙을 거치지 않고 앉히는** 출발 국면이고(도해의
 * 배경), [steps]의 수만 [BoardRules]를 지난다.
 *
 * ⚠️ **판은 9x9 하나뿐이다** — [BoardSize]가 9/13/19만 허용해 귀 도형만 잘라 낼 수 없다.
 * 그래서 귀 설명도 9x9 전체 위에 그린다.
 */
data class StudyLesson(
    val id: StudyLessonId,
    val track: StudyLessonTrack,
    val boardSize: BoardSize,
    val setupBlack: List<BoardCoordinate> = emptyList(),
    val setupWhite: List<BoardCoordinate> = emptyList(),
    val firstPlayer: StoneColor = StoneColor.Black,
    val steps: List<StudyLessonStep>,
) {
    /** 아무 수도 두기 전의 국면. */
    fun startState(): GameState =
        GameState(
            boardSize = boardSize,
            ruleset = Ruleset.Japanese,
            nextPlayer = firstPlayer,
            stones = setupBlack.associateWith { StoneColor.Black } +
                setupWhite.associateWith { StoneColor.White },
            moves = emptyList(),
        )

    /**
     * 단계마다의 국면 — `[i]`는 `steps[0..i]`를 다 둔 뒤다. 크기는 [steps]와 같다.
     *
     * 도해를 좌표 표로 손으로 적지 않고 **수순에서 접는** 이유가 여기다: 따낸 돌이 사라지는
     * 것도, 패의 금지점이 서는 것도 규칙이 직접 만든다 — 도해가 규칙과 어긋날 수 없다.
     */
    fun statesByStep(): List<GameState> {
        var state = startState()
        return steps.map { step ->
            step.move?.let { state = BoardRules.play(state, it) }
            state
        }
    }
}

/** 9x9 좌표를 바둑 표기(`E5`)로 적는다 — 바둑 두는 사람이 읽고 검수할 수 있게. */
private fun p(label: String): BoardCoordinate =
    BoardCoordinate.fromLabel(label, BoardSize.Nine)

private fun black(coordinate: String) = Move.Play(StoneColor.Black, p(coordinate))

private fun white(coordinate: String) = Move.Play(StoneColor.White, p(coordinate))

/**
 * 학습 단원의 콘텐츠 전문 — **바이너리에 못박는다**(U-19, 2026-09-22 사용자 결정).
 * 원격 배포를 열면 서버·캐시·실패 폴백이 새로 생기고 데이터 보안 양식까지 따라 고쳐야 한다
 * (함정 60). 지금 앱에 원격 콘텐츠 경로는 0건이고, 그게 이 앱의 「오프라인」 정체성이다.
 *
 * ⚠️ **문구는 여기 없다** — 네 언어 표가 `app-android`의 `UiStringsStudyRules.kt`·
 * `UiStringsStudyShapes.kt`에 있다. 여기는 판 위의 사실(어디에 무엇이 놓이는가)만 든다.
 * ⚠️ **갈래는 [StudyLessonTrack]이 가른다** — 목록 하나에 순서대로 두고, 화면은
 * [studyLessonsFor]로 받는다.
 */
val studyLessons: List<StudyLesson> = listOf(
    // ── 1. 바둑판과 첫 수 ──────────────────────────────────────────────
    StudyLesson(
        id = StudyLessonId.RuleBasics,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        steps = listOf(
            StudyLessonStep(id = "rule.basics.points", marker = p("E5")),
            StudyLessonStep(id = "rule.basics.black", move = black("E5")),
            StudyLessonStep(id = "rule.basics.white", move = white("G7")),
            StudyLessonStep(id = "rule.basics.goal", move = black("C3")),
        ),
    ),

    // ── 2. 활로 ────────────────────────────────────────────────────────
    // 한가운데 4 → 귀 2 → 이으면 6. 숫자가 줄었다 늘어나는 순서라 한 장씩 세어 볼 수 있다.
    StudyLesson(
        id = StudyLessonId.RuleLiberties,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        steps = listOf(
            StudyLessonStep(id = "rule.liberties.center", move = black("E5")),
            StudyLessonStep(id = "rule.liberties.corner", move = white("A9")),
            StudyLessonStep(id = "rule.liberties.connect", move = black("F5")),
        ),
    ),

    // ── 3. 단수와 따냄 ─────────────────────────────────────────────────
    // 백 한 점(E5)이 활로 하나(E4)만 남은 상태에서 출발한다 — 흑이 한 수로 따내는 것을
    // 그 자리에서 보여 주려면 단수 국면이 이미 서 있어야 한다.
    StudyLesson(
        id = StudyLessonId.RuleCapture,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("D5"), p("F5")),
        setupWhite = listOf(p("E5")),
        steps = listOf(
            StudyLessonStep(id = "rule.capture.atari", marker = p("E4")),
            StudyLessonStep(id = "rule.capture.take", move = black("E4")),
            StudyLessonStep(id = "rule.capture.prisoner"),
        ),
    ),

    // ── 4. 둘 수 없는 자리 ─────────────────────────────────────────────
    // 판 하나에 **두 모양**을 같이 둔다: 가운데는 착수금지(E5), 좌상귀는 그 예외(A9).
    // 규칙과 예외를 한 화면에서 견줘야 "따내는 수는 둘 수 있다"가 단서가 아니라 규칙으로 읽힌다.
    StudyLesson(
        id = StudyLessonId.RuleForbidden,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("E4"), p("D5"), p("F5"), p("B9"), p("A8"), p("B8")),
        setupWhite = listOf(p("C9"), p("C8"), p("B7"), p("A7")),
        firstPlayer = StoneColor.White,
        steps = listOf(
            // ⚠️ 가늠돌을 찍지 않는다 — 여기는 **둘 수 없는** 자리다(`StudyLessonStep.marker` 주석).
            StudyLessonStep(id = "rule.forbidden.suicide"),
            StudyLessonStep(id = "rule.forbidden.exception", marker = p("A9")),
            StudyLessonStep(id = "rule.forbidden.capture", move = white("A9")),
        ),
    ),

    // ── 5. 패 ──────────────────────────────────────────────────────────
    // 흑이 D5로 백 한 점(E5)을 따내면 되따냄이 금지된다 — `BoardRules`가 `koPoint`를 직접
    // 세우므로 이 도해의 "둘 수 없다"는 문구가 아니라 규칙이 보증한다.
    StudyLesson(
        id = StudyLessonId.RuleKo,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("E4"), p("F5")),
        setupWhite = listOf(p("E5"), p("D6"), p("D4"), p("C5")),
        steps = listOf(
            StudyLessonStep(id = "rule.ko.shape", marker = p("D5")),
            StudyLessonStep(id = "rule.ko.take", move = black("D5")),
            // ⚠️ 가늠돌 없음 — E5는 지금 백이 둘 수 없는 자리다.
            StudyLessonStep(id = "rule.ko.forbidden"),
            StudyLessonStep(id = "rule.ko.threat", move = white("B8")),
            StudyLessonStep(id = "rule.ko.answer", move = black("C8")),
            StudyLessonStep(id = "rule.ko.retake", move = white("E5")),
        ),
    ),

    // ── 6. 집과 계가 ───────────────────────────────────────────────────
    // 흑 벽(D줄)과 백 벽(E줄)이 판을 세로로 가른다 — 공배가 한 점도 없어 집 수를 그대로
    // 셀 수 있다. 실제 대국이 이렇게 갈리지는 않지만, 세는 법을 처음 배울 때는 군더더기가
    // 없는 편이 낫다.
    StudyLesson(
        id = StudyLessonId.RuleTerritory,
        track = StudyLessonTrack.Rules,
        boardSize = BoardSize.Nine,
        setupBlack = (0..8).map { BoardCoordinate(row = it, column = 3) },
        setupWhite = (0..8).map { BoardCoordinate(row = it, column = 4) },
        steps = listOf(
            StudyLessonStep(id = "rule.territory.fence"),
            StudyLessonStep(id = "rule.territory.pass", move = Move.Pass(StoneColor.Black)),
            StudyLessonStep(id = "rule.territory.end", move = Move.Pass(StoneColor.White)),
            StudyLessonStep(id = "rule.territory.count"),
        ),
    ),

    // ══ 행마(#183) — 규칙 여섯 단원을 읽었다는 전제로 쓰여 있다 ════════

    // ── 1. 이음과 끊음 ─────────────────────────────────────────────────
    // 판 하나에 **같은 모양 둘**을 위아래로 둔다 — 위는 끊기고 아래는 이어져, 한 화면에서
    // 두 갈래를 견줄 수 있다. 「그때 이었으면」을 말로만 하면 남지 않는다.
    StudyLesson(
        id = StudyLessonId.ShapeCut,
        track = StudyLessonTrack.Shapes,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("D7"), p("E8"), p("D3"), p("E4")),
        firstPlayer = StoneColor.White,
        steps = listOf(
            StudyLessonStep(id = "shape.cut.diagonal"),
            StudyLessonStep(id = "shape.cut.cut", move = white("E7")),
            StudyLessonStep(id = "shape.cut.connect", move = black("E3")),
        ),
    ),

    // ── 2. 뻗어서 달아나기 ─────────────────────────────────────────────
    // #164의 「단수와 따냄」이 문구로만 언급하고 지나간 쪽(달아나기)을 여기서 판으로 보여 준다.
    StudyLesson(
        id = StudyLessonId.ShapeExtend,
        track = StudyLessonTrack.Shapes,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E5")),
        setupWhite = listOf(p("E6"), p("D5"), p("F5")),
        steps = listOf(
            StudyLessonStep(id = "shape.extend.atari", marker = p("E4")),
            StudyLessonStep(id = "shape.extend.run", move = black("E4")),
            StudyLessonStep(id = "shape.extend.chase", move = white("E3")),
            StudyLessonStep(id = "shape.extend.judge"),
        ),
    ),

    // ── 3. 호구 ────────────────────────────────────────────────────────
    // ⚠️ 여기서는 가늠돌이 **둘 수 있는 자리**를 가리킨다 — 백은 뛰어들 수 있다(자살수가
    // 아니다). 다만 뛰어드는 순간 단수라서 손해일 뿐이다. 「못 둔다」가 아니라 「두면 잡힌다」다.
    StudyLesson(
        id = StudyLessonId.ShapeTigerMouth,
        track = StudyLessonTrack.Shapes,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("D5"), p("E6"), p("F5")),
        firstPlayer = StoneColor.White,
        steps = listOf(
            StudyLessonStep(id = "shape.tiger.shape", marker = p("E5")),
            StudyLessonStep(id = "shape.tiger.in", move = white("E5")),
            StudyLessonStep(id = "shape.tiger.take", move = black("E4")),
        ),
    ),

    // ── 4. 한 칸 뜀 ────────────────────────────────────────────────────
    StudyLesson(
        id = StudyLessonId.ShapeJump,
        track = StudyLessonTrack.Shapes,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("D6")),
        steps = listOf(
            StudyLessonStep(id = "shape.jump.jump", move = black("D4")),
            StudyLessonStep(id = "shape.jump.cut", move = white("D5")),
            StudyLessonStep(id = "shape.jump.atari", move = black("E5")),
            StudyLessonStep(id = "shape.jump.why"),
        ),
    ),

    // ── 5. 날일자 ──────────────────────────────────────────────────────
    StudyLesson(
        id = StudyLessonId.ShapeKnight,
        track = StudyLessonTrack.Shapes,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E5")),
        steps = listOf(
            StudyLessonStep(id = "shape.knight.shape", move = black("G4")),
            StudyLessonStep(id = "shape.knight.press", move = white("F5")),
            StudyLessonStep(id = "shape.knight.answer", move = black("F4")),
        ),
    ),
)

/**
 * 한 갈래의 단원들 — **선언 순서 그대로.** 화면은 이것만 받아 그리므로, 갈래를 늘릴 때
 * 화면을 건드릴 일이 없다.
 */
fun studyLessonsFor(track: StudyLessonTrack): List<StudyLesson> =
    studyLessons.filter { it.track == track }
