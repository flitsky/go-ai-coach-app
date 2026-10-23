package com.worksoc.goaicoach.ui.l10n

/**
 * 학습 허브 하위 분류의 이름·한 줄 소개(백로그 #163, U-16 — 2026-09-20 사용자 결정).
 * 구조는 `UiStringsStudyVideos.kt`와 같다 — 키(여기서는 [StudyCategory])로 네 언어를 든다.
 *
 * ## ⚠️ 왜 [UiStrings]의 생성자가 아니라 여기인가
 *
 * **[UiStrings]의 생성자 자리가 다 찼다.** JVM 메서드 시그니처의 인자 한도는 **255칸**인데
 * 그 데이터 클래스는 이미 250개 필드를 갖고 있어 여유가 네댓 칸뿐이다. 실제로 이 조각을
 * 짜면서 아홉 줄(제목 4 · 소개 4 · 배지 1)을 생성자에 더했다가 **앱은 멀쩡히 컴파일되고
 * 테스트만 570개 중 29개가 `ClassFormatError: Too many arguments in method signature`로
 * 죽었다.** 컴파일러가 막아 주지 않으므로, 문구를 **묶음으로** 더할 일이 생기면 이 파일처럼
 * 곁표로 뺄 것 — 생성자가 아니라 함수로 읽으면 칸을 쓰지 않는다.
 *
 * ⚠️ **「준비 중」 배지는 분류마다 두지 않는다** — 셋이 같은 말을 네 언어로 세 번 적는 모양이
 * 되고, #164가 하나를 켤 때 지워야 할 줄만 늘어난다. 하나를 돌려 쓴다.
 */
/**
 * 분류 이름 — **짧은 명사형**(사용자 2026-10-22, #280: 바둑 규칙 · 기초 행마 · 유튜브 강좌 · 사활 풀기 — 그전에는 「…배우기」 · 「…보기」 · 「…공부하기」).
 * 영·일·중은 스레드가 한국어에 맞춰 줄인 것이다(원어민 확인 없음). 하위 화면의 머리 제목도 이 이름이다.
 */
private val StudyCategoryTitles: Map<StudyCategory, Map<UiLanguage, String>> = mapOf(
    StudyCategory.YoutubeLessons to mapOf(
        UiLanguage.Korean to "유튜브 강좌",
        UiLanguage.English to "YouTube Lessons",
        UiLanguage.Japanese to "YouTube講座",
        UiLanguage.ChineseSimplified to "YouTube讲座",
    ),
    StudyCategory.Rules to mapOf(
        UiLanguage.Korean to "바둑 규칙",
        UiLanguage.English to "Rules of Go",
        UiLanguage.Japanese to "囲碁のルール",
        UiLanguage.ChineseSimplified to "围棋规则",
    ),
    StudyCategory.Fundamentals to mapOf(
        UiLanguage.Korean to "기초 행마",
        UiLanguage.English to "Basic Shapes",
        UiLanguage.Japanese to "基本の形",
        UiLanguage.ChineseSimplified to "基础行棋",
    ),
    StudyCategory.LifeAndDeath to mapOf(
        UiLanguage.Korean to "사활 풀기",
        UiLanguage.English to "Life & Death Problems",
        UiLanguage.Japanese to "詰碁を解く",
        UiLanguage.ChineseSimplified to "死活练习",
    ),
)

private val StudyCategorySubtitles: Map<StudyCategory, Map<UiLanguage, String>> = mapOf(
    StudyCategory.YoutubeLessons to mapOf(
        UiLanguage.Korean to "엄선한 입문 강좌 세 편을 유튜브에서 봅니다.",
        UiLanguage.English to "Watch three hand-picked beginner lessons on YouTube.",
        UiLanguage.Japanese to "厳選した入門講座3本をYouTubeで見ます。",
        UiLanguage.ChineseSimplified to "在YouTube观看精选的三部入门讲座。",
    ),
    StudyCategory.Rules to mapOf(
        UiLanguage.Korean to "집·따냄·패까지, 처음 두는 사람을 위한 규칙.",
        UiLanguage.English to "Territory, capture, and ko — for your very first game.",
        UiLanguage.Japanese to "地・アタリ・コウまで、初めての方のためのルール。",
        UiLanguage.ChineseSimplified to "从围地、提子到打劫，为初学者准备的规则。",
    ),
    StudyCategory.Fundamentals to mapOf(
        UiLanguage.Korean to "돌을 잇고 끊는 기본 모양을 익힙니다.",
        UiLanguage.English to "Practice connecting and cutting stones.",
        UiLanguage.Japanese to "石をつなぐ・切る基本の形を身につけます。",
        UiLanguage.ChineseSimplified to "掌握连接与切断棋子的基本形。",
    ),
    StudyCategory.LifeAndDeath to mapOf(
        UiLanguage.Korean to "살리는 모양과 죽이는 급소를 연습합니다.",
        UiLanguage.English to "Train the shapes that live and the points that kill.",
        UiLanguage.Japanese to "生きる形と殺す急所を練習します。",
        UiLanguage.ChineseSimplified to "练习做活的形状与破眼的要点。",
    ),
)

private val StudyComingSoonBadge: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "준비 중",
    UiLanguage.English to "Coming soon",
    UiLanguage.Japanese to "準備中",
    UiLanguage.ChineseSimplified to "准备中",
)

/**
 * 표가 비면 분류 이름을 그대로 돌려준다 — `UiStringsStudyVideos.kt`의 폴백과 같은 이유다.
 * 빈칸이면 조용히 지나가지만, 화면에 `LifeAndDeath`라고 뜨면 눈에 띄어 바로 고친다.
 */
internal fun studyCategoryTitleFor(language: UiLanguage, category: StudyCategory): String =
    StudyCategoryTitles[category]?.get(language) ?: category.name

internal fun studyCategorySubtitleFor(language: UiLanguage, category: StudyCategory): String =
    StudyCategorySubtitles[category]?.get(language) ?: category.name

internal fun studyComingSoonFor(language: UiLanguage): String =
    StudyComingSoonBadge[language] ?: StudyComingSoonBadge.getValue(UiLanguage.Korean)

/** 「기초 학습」(#280, 사용자 2026-10-23) — 대국 페이지의 타일이 여는 목록: 바둑 규칙 · 기초 행마 · 유튜브 강좌. 영 · 일 · 중은 스레드가 정한 표기. */
private val StudyBasicsTitles: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "기초 학습",
    UiLanguage.English to "Basics",
    UiLanguage.Japanese to "基礎学習",
    UiLanguage.ChineseSimplified to "基础学习",
)

internal fun studyBasicsTitleFor(language: UiLanguage): String = StudyBasicsTitles.getValue(language)

/** 「기초 학습」이 모으는 분류 — 이 차례로 선다(사용자 2026-10-23). 「사활 풀기」는 여기 없다 — 대국 페이지의 타일이 곧바로 연다. */
internal val StudyBasicsCategories: List<StudyCategory> = listOf(StudyCategory.Rules, StudyCategory.Fundamentals, StudyCategory.YoutubeLessons)
