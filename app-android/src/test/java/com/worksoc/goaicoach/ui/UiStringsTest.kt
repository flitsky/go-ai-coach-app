package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.shared.SearchTimeLimit
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStringsTest {
    /**
     * 이 값들은 **3분할 버튼에 들어가야 해서** 짧다(#29). 360dp 화면에서 그 버튼의 글자
     * 가용폭은 96dp뿐인데, 예전 일본어 `新しい対局を開始`는 전각 8자 = 92dp라 배율 1.0배에서도
     * 말줄임됐다. 길이를 되돌리려면 폭 계산부터 다시 해야 한다.
     *
     * 같은 뜻의 긴 문구는 [UiStrings.overwriteWarningTitle]이 그대로 갖고 있다 — 그쪽은
     * 다이얼로그 제목이라 폭이 넉넉하다. 아래 테스트가 그 분기를 함께 고정한다.
     *
     * **영어만 그대로 둔 이유**: 폭으로는 `Rematch`(48dp)가 `New Game`(58dp)보다 유리하지만,
     * 이 버튼이 쏘는 `StartConfiguredGame`은 **그 시점의 현재 설정**으로 시작한다. 대국이
     * 끝난 뒤 헤더에서 상대나 판 크기를 바꿔 놓고 눌러도 되므로 "같은 상대와 다시"를 뜻하는
     * `Rematch`는 거짓이 될 수 있다. 나머지 세 언어가 전부 "새 대국"인데 영어만 뜻이 갈리는
     * 것도 나쁘다. 대신 영어는 배율 1.8배 이상에서 `New Ga…`로 말줄임되는데(#27의 Ellipsis),
     * 일본어가 **기본 배율에서** 잘리던 것에 비하면 훨씬 가벼운 손해다.
     */
    @Test
    fun startGameActionUsesShortLocalizedCopyInEverySupportedLanguage() {
        assertEquals("새 대국", UiStrings.forLanguage(UiLanguage.Korean).newGameAction)
        assertEquals("New Game", UiStrings.forLanguage(UiLanguage.English).newGameAction)
        assertEquals("新規対局", UiStrings.forLanguage(UiLanguage.Japanese).newGameAction)
        assertEquals("新对局", UiStrings.forLanguage(UiLanguage.ChineseSimplified).newGameAction)
    }

    @Test
    fun maximumSearchTimeOptionsAreLocalizedInEverySupportedLanguage() {
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)

            assertEquals(5, SearchTimeLimit.entries.map(strings::searchTimeLimitLabel).distinct().size)
        }
        assertEquals("최대 탐색 시간 제한", UiStringsKorean.maximumSearchTimeLimit)
        assertEquals("사용 안 함", UiStringsKorean.searchTimeLimitLabel(SearchTimeLimit.Off))
        assertEquals("10초 이내", UiStringsKorean.searchTimeLimitLabel(SearchTimeLimit.WithinTenSeconds))
    }

    @Test
    fun compactDisplayLabelsUseShortLocalizedCopy() {
        assertEquals("착수 표시", UiStringsKorean.lastMoveRing)
        assertEquals("Move mark", UiStringsEnglish.lastMoveRing)
        assertEquals("着手表示", UiStringsJapanese.lastMoveRing)
        assertEquals("落子标记", UiStringsChineseSimplified.lastMoveRing)
    }

    @Test
    fun overwriteWarningStringsUseLocalizedCopyInEverySupportedLanguage() {
        assertEquals("새 대국 시작", UiStrings.forLanguage(UiLanguage.Korean).overwriteWarningTitle)
        assertEquals("Start New Match", UiStrings.forLanguage(UiLanguage.English).overwriteWarningTitle)
        assertEquals("新しい対局を開始", UiStrings.forLanguage(UiLanguage.Japanese).overwriteWarningTitle)
        assertEquals("开始新对局", UiStrings.forLanguage(UiLanguage.ChineseSimplified).overwriteWarningTitle)
    }

    /**
     * **누락 방지 그물**(#31). 언어별 인스턴스는 [UiStringsKorean]을 `copy()` 해서 만든다 —
     * `copy()`는 지정하지 않은 필드를 원본 값 그대로 남기므로, 번역을 빠뜨려도 **컴파일은 통과하고
     * 화면에만 한글이 뜬다.** 실제로 일본어·중국어의 `finalJudgementTitle`·`reviewJudgement`가
     * 그렇게 샜고, 종국한 일·중 사용자는 매판 다이얼로그의 제목과 버튼을 못 읽었다.
     *
     * 그래서 "필드를 다 채웠는가"가 아니라 **"비한국어 인스턴스에 한글이 남았는가"** 를 본다.
     * 이 불변식을 고른 이유는 **예외 목록을 사람이 관리하지 않아도 되기 때문**이다 —
     * `appTitle`은 언어마다 다른 값을 갖는데(`바둑 AI`/`Go AI`/`囲碁AI`/`围棋AI`) 셋 다 한글이
     * 없으니 그냥 통과한다. ⚠️ 2026-09-06까지 이 주석은 *"브랜드명이라 세 언어가 일부러 상속한다"*
     * 고 적고 있었다 — #97이 이름을 번역 대상으로 바꾸면서 **두 번 낡은 서술**이 됐다(#112). 필드가 183개, 184개로 늘어도 리플렉션이 알아서 따라간다.
     * (2026-08-30 네 언어 파일 전수 확인 — 한글을 일부러 남겨야 하는 필드는 하나도 없다.)
     */
    @Test
    fun nonKoreanUiStringsNeverInheritKoreanTextFromTheKoreanBaseline() {
        // 자기검증. 필터가 조용히 0개를 집으면 아래 검사는 아무것도 안 보면서 통과한다 —
        // 한국어 인스턴스에서 한글이 쏟아지는지 먼저 확인해 "필드를 읽고 있다"를 못 박는다.
        val baseline = koreanBearingFields(UiStringsKorean)
        assertTrue(
            "리플렉션이 UiStrings의 String 필드를 못 읽고 있다 — 한국어에서 ${baseline.size}개만 잡혔다.",
            baseline.size > 100,
        )

        val leaks = UiLanguage.entries
            .filter { it != UiLanguage.Korean }
            .flatMap { language ->
                koreanBearingFields(UiStrings.forLanguage(language))
                    .map { (field, value) -> "${language.name}.$field = \"$value\"" }
            }

        assertEquals(
            "비한국어 UiStrings에 한글이 남아 있다 = UiStringsKorean에서 copy()로 조용히 상속된 필드다.\n" +
                "해당 언어 파일(UiStringsEn/Ja/Zh.kt)에 직접 채워라:\n" + leaks.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            leaks,
        )
    }

    /**
     * 위 그물은 "한글만 아니면 통과"라서, 일본어 자리에 영어를 박아 넣는 식의 대충 메우기는
     * 잡지 못한다. #31이 실제로 샜던 두 필드는 값까지 못 박는다.
     *
     * **`reviewJudgement`이 "검토"가 아닌 이유**: 이 버튼은 다이얼로그를 닫아 **이미 켜져 있는**
     * 집계 오버레이가 깔린 반상을 보게 해 준다(`GamePlaySection.kt`의 ownership takeIf가 종국이면
     * 프리미엄과 무관하게 통과시킨다). 수순을 되짚는 복기 모드가 아니므로 일본어 `検討`,
     * 중국어 `复盘`은 하지 않는 일을 약속하게 된다.
     */
    @Test
    fun theFinalJudgementDialogSpeaksEveryLanguage() {
        val titles = UiLanguage.entries.associateWith { UiStrings.forLanguage(it).finalJudgementTitle }
        assertEquals("판정 결과", titles[UiLanguage.Korean])
        assertEquals("Judgement", titles[UiLanguage.English])
        assertEquals("対局結果", titles[UiLanguage.Japanese])
        assertEquals("终局结果", titles[UiLanguage.ChineseSimplified])

        val reviews = UiLanguage.entries.associateWith { UiStrings.forLanguage(it).reviewJudgement }
        assertEquals("판정 검토", reviews[UiLanguage.Korean])
        assertEquals("Review", reviews[UiLanguage.English])
        assertEquals("盤面で確認", reviews[UiLanguage.Japanese])
        assertEquals("查看盘面", reviews[UiLanguage.ChineseSimplified])
    }

    /**
     * 학습 강좌 소개도 **함수**로 나오므로 위 필드 그물의 사각지대다(백로그 #33) —
     * `UiStringsBotCharacterTest`가 캐릭터 문구에 대해 하는 일을 여기서 강좌에 대해 한다.
     *
     * ⚠️ **비한국어에만 `한국어`/`Korean` 표기가 붙는다.** 강의 자체가 한국어 유튜브라, 자동
     * 번역 자막으로 따라갈 수는 있어도 들어가 보고 알게 두지는 않기로 했다(2026-08-30 결정,
     * 근거는 `UiStringsStudyVideos.kt`). 그 표기가 한국어 문구에까지 붙는 실수를 함께 막는다.
     */
    @Test
    fun studyVideoBlurbsAreLocalizedAndFlagTheLectureLanguage() {
        val leaks = studyVideoEntries.flatMap { entry ->
            UiLanguage.entries
                .filter { it != UiLanguage.Korean }
                .map { language -> language to UiStrings.forLanguage(language).studyVideoDescription(entry) }
                .filter { (_, value) -> value.containsHangul() || value == entry.id }
                .map { (language, value) -> "${language.name} ${entry.id} = \"$value\"" }
        }
        assertEquals(
            "비한국어 강좌 소개가 번역되지 않았거나 표에서 빠졌다:\n" + leaks.joinToString("\n") { "  - $it" },
            emptyList<String>(),
            leaks,
        )

        // 한국어 강의라는 사실은 비한국어에만 밝힌다.
        val first = studyVideoEntries.first()
        assertTrue(UiStringsEnglish.studyVideoDescription(first).contains("Korean"))
        assertTrue(UiStringsJapanese.studyVideoDescription(first).contains("韓国語"))
        assertTrue(UiStringsChineseSimplified.studyVideoDescription(first).contains("韩语"))
        assertTrue(
            "한국어 사용자에게 '한국어 강의'라고 알릴 이유가 없다 — 되묻게 만든다.",
            !UiStringsKorean.studyVideoDescription(first).contains("한국어"),
        )
    }

    /**
     * `UiStrings`의 String 필드 중 한글이 섞인 것을 (필드명 to 값)으로 돌려준다.
     *
     * ⚠️ `isAccessible = true`가 없으면 안 된다 — 코틀린은 생성자 프로퍼티의 백킹 필드를
     * 프로퍼티가 `internal`이어도 `private final`로 낸다. `private`은 클래스 스코프라 테스트가
     * 같은 패키지에 있어도 소용없고, 없으면 `IllegalAccessException`으로 **엉뚱한 이유로**
     * 실패해서 실패 메시지가 번역 누락을 한마디도 알려주지 않는다.
     *
     * ⚠️ 정적 필드를 걸러야 한다 — Compose 컴파일러가 붙이는 `$stable`(int)과 `Companion`이
     * 섞인다. `type == String` 필터가 둘 다 걷어내지만 의도를 남겨 둔다.
     * 리스트/셋 타입 문자열 필드가 생기면 이 필터가 못 잡는다는 한계도 함께 안다.
     */
    private fun koreanBearingFields(strings: UiStrings): List<Pair<String, String>> =
        UiStrings::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(strings) as? String)?.let { field.name to it }
            }
            .filter { (_, value) -> value.containsHangul() }

    // 한글 판정 기준은 `HangulDetection.kt`가 단독으로 갖는다 — #32가 함수 반환값을 훑는 두 번째
    // 그물(`UiStringsBotCharacterTest`)을 추가하면서, 두 그물이 서로 다른 기준을 갖지 않게 뺐다.
}
