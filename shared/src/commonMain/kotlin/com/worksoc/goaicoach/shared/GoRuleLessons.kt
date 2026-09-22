package com.worksoc.goaicoach.shared

/**
 * 「바둑 규칙 배우기」의 단원(백로그 #164 — U-17 ⓐ+ / U-19 바이너리, 2026-09-22 사용자 결정).
 *
 * ⚠️ **순서가 곧 화면의 순서다** — 선언 순서로 목록을 그린다. 앞 단원을 읽었다는 전제로
 * 뒤 단원의 문구가 쓰여 있으므로 임의로 섞지 말 것.
 */
enum class GoRuleLessonId {
    /** 교차점·번갈아 두기 — 판에 처음 손을 대는 사람이 읽는 한 쪽. */
    Basics,

    /** 활로. 뒤의 따냄·착수금지·패가 전부 이 개념 위에 선다. */
    Liberties,

    /** 단수와 따냄. */
    Capture,

    /** 착수금지(자살수)와 그 예외(따내는 수는 둘 수 있다). */
    Forbidden,

    /** 패 — 되따냄 금지와 팻감. */
    Ko,

    /** 집·거르기·계가·덤. */
    Territory,
}

/**
 * 단원 안의 한 단계 = **도해 한 장 + 설명 한 덩이.**
 *
 * @param id 문구 표(`UiStringsStudyRules.kt`)의 키. 좌표를 키로 쓰지 않는 이유는 도해를
 *   고쳐도 문구를 그대로 두는 경우가 흔하기 때문이다(`StudyVideoEntry.id`와 같은 판단).
 * @param move 이 단계에서 **실제로 두는 수.** `null`이면 판을 그대로 두고 설명만 바꾼다.
 *   ⚠️ 두는 수는 [BoardRules]를 그대로 지난다 — 차례가 어긋나거나 위법수면 **예외가 난다.**
 *   도해가 규칙을 어기는 사고를 컴파일이 아니라 테스트가 잡아 준다(`GoRuleLessonsTest`).
 * @param marker 가늠돌로 가리킬 자리 — 뜻은 **"다음은 여기"** 하나뿐이다.
 *   ⚠️ **둘 수 없는 자리를 가리키는 데 쓰지 말 것.** 판은 이것을 `tentativeMove`로 받아
 *   *놓을 수 있는 자리*의 깜빡이는 돌로 그린다(`GoBoard.kt:442`) — 착수금지 자리에 찍으면
 *   그림과 글이 정반대를 말한다. 그 단계는 `marker = null`로 두고 문구가 설명한다.
 */
data class GoRuleStep(
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
data class GoRuleLesson(
    val id: GoRuleLessonId,
    val boardSize: BoardSize,
    val setupBlack: List<BoardCoordinate> = emptyList(),
    val setupWhite: List<BoardCoordinate> = emptyList(),
    val firstPlayer: StoneColor = StoneColor.Black,
    val steps: List<GoRuleStep>,
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
 * 「바둑 규칙 배우기」의 콘텐츠 전문 — **바이너리에 못박는다**(U-19, 2026-09-22 사용자 결정).
 * 원격 배포를 열면 서버·캐시·실패 폴백이 새로 생기고 데이터 보안 양식까지 따라 고쳐야 한다
 * (함정 60). 지금 앱에 원격 콘텐츠 경로는 0건이고, 그게 이 앱의 「오프라인」 정체성이다.
 *
 * ⚠️ **문구는 여기 없다** — 네 언어 표가 `app-android`의 `UiStringsStudyRules.kt`에 있다.
 * 여기는 판 위의 사실(어디에 무엇이 놓이는가)만 든다.
 */
val goRuleLessons: List<GoRuleLesson> = listOf(
    // ── 1. 바둑판과 첫 수 ──────────────────────────────────────────────
    GoRuleLesson(
        id = GoRuleLessonId.Basics,
        boardSize = BoardSize.Nine,
        steps = listOf(
            GoRuleStep(id = "basics.points", marker = p("E5")),
            GoRuleStep(id = "basics.black", move = black("E5")),
            GoRuleStep(id = "basics.white", move = white("G7")),
            GoRuleStep(id = "basics.goal", move = black("C3")),
        ),
    ),

    // ── 2. 활로 ────────────────────────────────────────────────────────
    // 한가운데 4 → 귀 2 → 이으면 6. 숫자가 줄었다 늘어나는 순서라 한 장씩 세어 볼 수 있다.
    GoRuleLesson(
        id = GoRuleLessonId.Liberties,
        boardSize = BoardSize.Nine,
        steps = listOf(
            GoRuleStep(id = "liberties.center", move = black("E5")),
            GoRuleStep(id = "liberties.corner", move = white("A9")),
            GoRuleStep(id = "liberties.connect", move = black("F5")),
        ),
    ),

    // ── 3. 단수와 따냄 ─────────────────────────────────────────────────
    // 백 한 점(E5)이 활로 하나(E4)만 남은 상태에서 출발한다 — 흑이 한 수로 따내는 것을
    // 그 자리에서 보여 주려면 단수 국면이 이미 서 있어야 한다.
    GoRuleLesson(
        id = GoRuleLessonId.Capture,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("D5"), p("F5")),
        setupWhite = listOf(p("E5")),
        steps = listOf(
            GoRuleStep(id = "capture.atari", marker = p("E4")),
            GoRuleStep(id = "capture.take", move = black("E4")),
            GoRuleStep(id = "capture.prisoner"),
        ),
    ),

    // ── 4. 둘 수 없는 자리 ─────────────────────────────────────────────
    // 판 하나에 **두 모양**을 같이 둔다: 가운데는 착수금지(E5), 좌상귀는 그 예외(A9).
    // 규칙과 예외를 한 화면에서 견줘야 "따내는 수는 둘 수 있다"가 단서가 아니라 규칙으로 읽힌다.
    GoRuleLesson(
        id = GoRuleLessonId.Forbidden,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("E4"), p("D5"), p("F5"), p("B9"), p("A8"), p("B8")),
        setupWhite = listOf(p("C9"), p("C8"), p("B7"), p("A7")),
        firstPlayer = StoneColor.White,
        steps = listOf(
            // ⚠️ 가늠돌을 찍지 않는다 — 여기는 **둘 수 없는** 자리다(`GoRuleStep.marker` 주석).
            GoRuleStep(id = "forbidden.suicide"),
            GoRuleStep(id = "forbidden.exception", marker = p("A9")),
            GoRuleStep(id = "forbidden.capture", move = white("A9")),
        ),
    ),

    // ── 5. 패 ──────────────────────────────────────────────────────────
    // 흑이 D5로 백 한 점(E5)을 따내면 되따냄이 금지된다 — `BoardRules`가 `koPoint`를 직접
    // 세우므로 이 도해의 "둘 수 없다"는 문구가 아니라 규칙이 보증한다.
    GoRuleLesson(
        id = GoRuleLessonId.Ko,
        boardSize = BoardSize.Nine,
        setupBlack = listOf(p("E6"), p("E4"), p("F5")),
        setupWhite = listOf(p("E5"), p("D6"), p("D4"), p("C5")),
        steps = listOf(
            GoRuleStep(id = "ko.shape", marker = p("D5")),
            GoRuleStep(id = "ko.take", move = black("D5")),
            // ⚠️ 가늠돌 없음 — E5는 지금 백이 둘 수 없는 자리다.
            GoRuleStep(id = "ko.forbidden"),
            GoRuleStep(id = "ko.threat", move = white("B8")),
            GoRuleStep(id = "ko.answer", move = black("C8")),
            GoRuleStep(id = "ko.retake", move = white("E5")),
        ),
    ),

    // ── 6. 집과 계가 ───────────────────────────────────────────────────
    // 흑 벽(D줄)과 백 벽(E줄)이 판을 세로로 가른다 — 공배가 한 점도 없어 집 수를 그대로
    // 셀 수 있다. 실제 대국이 이렇게 갈리지는 않지만, 세는 법을 처음 배울 때는 군더더기가
    // 없는 편이 낫다.
    GoRuleLesson(
        id = GoRuleLessonId.Territory,
        boardSize = BoardSize.Nine,
        setupBlack = (0..8).map { BoardCoordinate(row = it, column = 3) },
        setupWhite = (0..8).map { BoardCoordinate(row = it, column = 4) },
        steps = listOf(
            GoRuleStep(id = "territory.fence"),
            GoRuleStep(id = "territory.pass", move = Move.Pass(StoneColor.Black)),
            GoRuleStep(id = "territory.end", move = Move.Pass(StoneColor.White)),
            GoRuleStep(id = "territory.count"),
        ),
    ),
)
