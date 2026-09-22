package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.StudyLessonId
import com.worksoc.goaicoach.shared.StudyLessonTrack

/**
 * 갈래별 문구 표를 **합치고 읽는** 곳(백로그 #164·#183). 표 자체는 갈래마다 한 파일에 있다 —
 * `UiStringsStudyRules.kt`, `UiStringsStudyShapes.kt`.
 *
 * ⚠️ **[UiStrings] 생성자에 넣지 말 것 — 자리가 0칸이다.** `copy$default`가 JVM 인자 한도
 * 255칸에 딱 붙어 있어 한 줄만 더해도 **앱은 컴파일되고 테스트만 통째로** `ClassFormatError`로
 * 죽는다(함정 61, `StudyHubContractTest`가 칸수를 센다). 여기처럼 곁표 + 함수로 뺀다.
 *
 * ⚠️ **합치기는 최상위 `val`이라 한 번만 돈다** — 읽을 때마다 `+`로 합치면 목록을 그릴 때마다
 * 표 전체를 새로 만든다.
 * ⚠️ **열쇠가 갈래끼리 부딪히면 조용히 엉뚱한 본문이 나온다** — 그래서 단계 키에 `rule.`·
 * `shape.` 접두사를 달고 `StudyLessonsTest`가 그것을 센다. 합치는 쪽에서도
 * `StudyLessonCopyContractTest`가 겹침을 본다.
 */
private val StudyLessonTitles: Map<StudyLessonId, Map<UiLanguage, String>> =
    StudyRuleLessonTitles + StudyShapeLessonTitles

private val StudyLessonSummaries: Map<StudyLessonId, Map<UiLanguage, String>> =
    StudyRuleLessonSummaries + StudyShapeLessonSummaries

private val StudyLessonStepBodies: Map<String, Map<UiLanguage, String>> =
    StudyRuleStepBodies + StudyShapeStepBodies

private val StudyLessonPrevious: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "이전",
    UiLanguage.English to "Previous",
    UiLanguage.Japanese to "前へ",
    UiLanguage.ChineseSimplified to "上一步",
)

private val StudyLessonNext: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "다음",
    UiLanguage.English to "Next",
    UiLanguage.Japanese to "次へ",
    UiLanguage.ChineseSimplified to "下一步",
)

private val StudyLessonNextLesson: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "다음 단원",
    UiLanguage.English to "Next lesson",
    UiLanguage.Japanese to "次の単元",
    UiLanguage.ChineseSimplified to "下一单元",
)

private val StudyLessonBackToList: Map<UiLanguage, String> = mapOf(
    UiLanguage.Korean to "단원 목록으로",
    UiLanguage.English to "Lesson list",
    UiLanguage.Japanese to "単元一覧へ",
    UiLanguage.ChineseSimplified to "返回单元目录",
)

/**
 * 갈래와 허브 분류를 잇는 **유일한 자리.** 화면은 분류를 받아 갈래를 물어보고, 갈래만으로
 * 단원과 문구를 얻는다 — 새 갈래를 열 때 고칠 곳이 여기 하나여야 한다.
 */
internal fun studyLessonTrackFor(category: StudyCategory): StudyLessonTrack? =
    when (category) {
        StudyCategory.Rules -> StudyLessonTrack.Rules
        StudyCategory.Fundamentals -> StudyLessonTrack.Shapes
        StudyCategory.YoutubeLessons, StudyCategory.LifeAndDeath -> null
    }

/**
 * 표가 비면 키를 그대로 돌려준다 — `UiStringsStudyCategories.kt`의 폴백과 같은 이유다.
 * 빈칸이면 조용히 지나가지만, 화면에 `shape.knight.press`라고 뜨면 눈에 띄어 바로 고친다.
 */
internal fun studyLessonTitleFor(language: UiLanguage, lesson: StudyLessonId): String =
    StudyLessonTitles[lesson]?.get(language) ?: lesson.name

internal fun studyLessonSummaryFor(language: UiLanguage, lesson: StudyLessonId): String =
    StudyLessonSummaries[lesson]?.get(language) ?: lesson.name

internal fun studyLessonBodyFor(language: UiLanguage, stepId: String): String =
    StudyLessonStepBodies[stepId]?.get(language) ?: stepId

internal fun studyLessonPreviousFor(language: UiLanguage): String =
    StudyLessonPrevious[language] ?: StudyLessonPrevious.getValue(UiLanguage.Korean)

internal fun studyLessonNextFor(language: UiLanguage): String =
    StudyLessonNext[language] ?: StudyLessonNext.getValue(UiLanguage.Korean)

internal fun studyLessonNextLessonFor(language: UiLanguage): String =
    StudyLessonNextLesson[language] ?: StudyLessonNextLesson.getValue(UiLanguage.Korean)

internal fun studyLessonBackToListFor(language: UiLanguage): String =
    StudyLessonBackToList[language] ?: StudyLessonBackToList.getValue(UiLanguage.Korean)

/**
 * 표가 실제로 덮는 범위 — **그물 전용**(`StudyRulesContractTest`).
 *
 * ⚠️ **"값이 폴백과 같은가"로는 빠진 줄을 못 본다.** 영어 단원 이름 `Liberties`가 enum 이름과
 * 똑같은 낱말이라, 멀쩡히 번역된 줄이 *빠진 줄*로 읽힌다(실제로 처음 판은 그렇게 헛걸렸다).
 * 표를 직접 물어야 한다.
 */
internal fun studyLessonTitleLanguages(lesson: StudyLessonId): Set<UiLanguage> =
    StudyLessonTitles[lesson]?.filterValues { it.isNotBlank() }?.keys.orEmpty()

internal fun studyLessonSummaryLanguages(lesson: StudyLessonId): Set<UiLanguage> =
    StudyLessonSummaries[lesson]?.filterValues { it.isNotBlank() }?.keys.orEmpty()

internal fun studyLessonBodyLanguages(stepId: String): Set<UiLanguage> =
    StudyLessonStepBodies[stepId]?.filterValues { it.isNotBlank() }?.keys.orEmpty()

/** 표에 적힌 단계 키 전부 — 단계를 지웠는데 본문만 남는 것을 그물이 본다. */
internal fun studyLessonBodyKeys(): Set<String> = StudyLessonStepBodies.keys

/** 갈래별 열쇠 수 — 합치면서 조용히 덮인 줄이 있는지 센다(`+`는 같은 키를 말없이 덮는다). */
internal fun studyLessonCopyKeyCounts(): Triple<Int, Int, Int> =
    Triple(
        StudyRuleLessonTitles.size + StudyShapeLessonTitles.size,
        StudyRuleLessonSummaries.size + StudyShapeLessonSummaries.size,
        StudyRuleStepBodies.size + StudyShapeStepBodies.size,
    )
