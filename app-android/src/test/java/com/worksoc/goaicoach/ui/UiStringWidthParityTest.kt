package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import java.lang.reflect.Modifier
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **다국어 문구는 네 언어의 길이를 맞춰 번역한다**(`docs/spec/DECISIONS.md` §1, 사용자 2026-11-23) — 그 원칙의 게이트.
 *
 * ## 왜 언어별 실기 확인이 아니라 길이인가
 * 사용자 말 그대로다 — *"모든 언어의 length를 유사하게 맞추는 번역이 필수. 이렇게 하면 각 언어별 테스트를 굳이 하지 않아도
 * 크게 벗어나지 않을뿐더러, 조금 벗어나더라도 추가 수정 보완이 더 수월해짐."* 같은 지시가 세 번 있었다(함정 21 · #159 ·
 * QA-01의 「← ホーム」이 일본어 1.3배에서 수순과 겹친 일). 화면은 **한국어로 설계하고 확인한다** — 다른 언어가 한국어 폭을
 * 크게 넘지 않으면 따로 재지 않아도 들어간다.
 *
 * ## 규칙 — 한국어가 기준, 위로만 본다
 * 폭은 [displayWidth](전각 2 · 반각 1). 한국어 폭 `k`에 대해 다른 세 언어는 [allowanceFor]를 넘지 않는다:
 * - 짧은 라벨(`k ≤ 12` — 단추 · 칩 · 탭 · 제목): `k + 2`(전각 한 글자)
 * - 한 줄 문구(`k ≤ 30`): `k + 6`
 * - 접히는 문장(그 위): `k × 1.2`
 *
 * ⚠️ **짧은 쪽은 보지 않는다** — 중국어는 같은 뜻을 본래 훨씬 적은 칸에 담는다. 띠를 맞추겠다고 늘리면 군더더기다
 * ([PremiumSubscriptionCopyWidthTest]의 KDoc과 같은 이유). 넘치면 **긴 쪽을 줄임말 · 짧은 동의어로** 줄인다.
 * ⚠️ [displayWidth]는 라틴 문자를 전각의 절반으로 세어 영어에 조금 불리하다 — 여유 2칸 · 6칸이 그 몫을 함께 안는다.
 * 숫자 셋(2 · 6 · 1.2)과 구간(12 · 30)은 스레드가 정한 값이다(2026-11-23 — 사용자 확인 전 첫 값). 바꾸면 이 KDoc과
 * `DECISIONS.md`의 그 줄을 함께 고친다.
 *
 * ## 무엇을 재는가
 * - [UiStrings]의 `String` 필드 전부(네 언어 인스턴스를 리플렉션으로).
 * - `ui/l10n`의 `Map<UiLanguage, String>` 곁표 전부 — 파일 클래스 `…Kt`와 파일 안 `object`의 정적 필드 둘 다.
 * - 중첩 표 `Map<열쇠, Map<UiLanguage, String>>`(캐릭터 이름 · 학습 갈래 · 메뉴 절 제목처럼 id마다 네 언어) — 열쇠마다 하나로
 *   잰다(`파일.표[열쇠]`).
 * - ⚠️ **그물 밖**: `fun …For(language)` 안의 `when` 문구, 값을 끼워 만드는 문구(함정 10-2와 같은 사각지대), `List`를 값으로
 *   갖는 표, `get() =`로 계산되는 [UiStrings] 속성. 그런 문구를 새로 쓸 때는 곁표(`Map`)로 두면 이 그물에 걸린다.
 *
 * ## 기준선(래칫)
 * 2026-11-23에 이미 넘던 문구는 [UiStringWidthParityBaseline]에 묶었다 — **새로 넘는 문구만** 막는다. 목록은 **줄기만** 한다:
 * 고친 문구는 그 줄을 지워야 통과하고([theBaselineOnlyShrinks]), 줄 수는 [BaselineSize]와 같아야 한다([theBaselineNeverGrows]) —
 * 줄을 지우면 그 숫자도 낮춘다. 넘는 문구를 기준선에 더해 통과시키지 말 것 — 문구를 줄인다. 정리 일감은 활성 백로그 #311.
 */
class UiStringWidthParityTest {

    private data class Copy(val key: String, val byLanguage: Map<UiLanguage, String>) {
        private val korean = byLanguage.getValue(UiLanguage.Korean).displayWidth()
        val allowance = allowanceFor(korean)
        val exceeds: Boolean = byLanguage.any { (language, text) -> language != UiLanguage.Korean && text.displayWidth() > allowance }
        fun describe(): String =
            "$key — 한국어 ${korean}칸 · 허용 ${allowance}칸 · " +
                byLanguage.entries.joinToString(" · ") { (language, text) -> "${language.name} ${text.displayWidth()}「${text.take(24).replace("\n", "⏎")}」" }
    }

    @Test
    fun noNewStringRunsLongerThanItsKoreanWidthAllows() {
        val fresh = collect().filter { it.exceeds && it.key !in UiStringWidthParityBaseline }
        assertTrue(
            "네 언어 길이 원칙(DECISIONS §1)을 넘는 새 문구 ${fresh.size}개 — 긴 쪽을 줄임말 · 짧은 동의어로 줄일 것" +
                "(짧은 쪽을 늘리지 말 것 · 기준선에 더하지 말 것):\n" + fresh.joinToString("\n") { "  " + it.describe() },
            fresh.isEmpty(),
        )
    }

    @Test
    fun theBaselineOnlyShrinks() {
        val byKey = collect().associateBy { it.key }
        val stale = UiStringWidthParityBaseline.filter { key -> byKey[key]?.exceeds != true }
        assertTrue(
            "기준선의 ${stale.size}개는 이제 넘지 않거나(고쳤다) 없어졌다 — 기준선에서 지울 것:\n" + stale.joinToString("\n") { "  $it" },
            stale.isEmpty(),
        )
    }

    @Test
    fun theBaselineNeverGrows() {
        assertTrue(
            "기준선이 ${UiStringWidthParityBaseline.size}줄이다(정해 둔 수 ${BaselineSize}) — 넘는 문구를 기준선에 더하지 말고 문구를 줄일 것. " +
                "기준선을 줄였다면 BaselineSize도 함께 낮춘다.",
            UiStringWidthParityBaseline.size == BaselineSize,
        )
    }

    /** 자기검증 — 리플렉션이 아무것도 못 찾으면 위 검사들은 빈손으로 초록이 된다(함정 24 · 66). */
    @Test
    fun theNetActuallyHoldsTheAppsStrings() {
        val copies = collect()
        val maps = copies.count { !it.key.startsWith("UiStrings.") }
        assertTrue("UiStrings 필드를 거의 못 찾았다(${copies.size - maps}개)", copies.size - maps >= 200)
        assertTrue("l10n 곁표(Map)를 거의 못 찾았다(${maps}개) — 파일 클래스 이름 규칙이 바뀌었나", maps >= 100)
        assertTrue(
            "알려진 곁표가 그물에 없다 — 파일 · 필드 이름을 다시 볼 것",
            copies.any { it.key == "UiStringsGameFlow.FinishedGameHomeActions" },
        )
        assertTrue("object 안의 곁표(UiStringsDownloadGuide)가 그물에 없다", copies.any { it.key.startsWith("UiStringsDownloadGuide.") })
        assertTrue(
            "중첩 표(캐릭터 이름)가 그물에 없다",
            copies.count { it.key.startsWith("UiStringsBotCharacters.BotCharacterNames[") } >= 5,
        )
    }

    @Test
    fun theAllowanceBandsAreWhatTheDecisionSays() {
        assertTrue(allowanceFor(2) == 4)
        assertTrue(allowanceFor(12) == 14)
        assertTrue(allowanceFor(13) == 19)
        assertTrue(allowanceFor(30) == 36)
        assertTrue(allowanceFor(100) == 120)
    }

    private fun collect(): List<Copy> = uiStringsFields() + l10nMaps()

    private fun uiStringsFields(): List<Copy> {
        val instances = UiLanguage.entries.associateWith { UiStrings.forLanguage(it) }
        return UiStrings::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { field ->
                field.isAccessible = true
                Copy("UiStrings.${field.name}", instances.mapValues { (_, strings) -> field.get(strings) as String })
            }
    }

    /**
     * 곁표를 담는 클래스 — 파일 클래스 `…Kt`(맨 위 선언)와 파일 안의 `object`(정적 필드가 그 클래스에 붙는다). 맨 위 선언 없이
     * `object` 하나뿐인 파일은 `…Kt`가 아예 없다(`UiStringsDownloadGuide.kt` · `UiStringsBoardControlsGuide.kt`).
     */
    private fun l10nMaps(): List<Copy> =
        RepoPaths.appAndroidDirs("ui/l10n")
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.extension == "kt" } }
            .sortedBy { it.name }
            .flatMap { file ->
                val objects = ObjectDeclaration.findAll(file.readText()).map { it.groupValues[1] }.toList()
                (listOf("${file.nameWithoutExtension}Kt") + objects).distinct()
                    .mapNotNull { name -> runCatching { Class.forName("com.worksoc.goaicoach.ui.l10n.$name") }.getOrNull() }
                    .map { holder -> file.nameWithoutExtension to holder }
            }
            .flatMap { (fileName, holder) ->
                holder.declaredFields
                    .filter { Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type) }
                    .flatMap { field ->
                        field.isAccessible = true
                        copiesOf("$fileName.${field.name}", field.get(null) as? Map<*, *>)
                    }
            }

    /** 네 언어 표 하나, 또는 열쇠마다 네 언어 표가 달린 중첩 표. 둘 다 아니면 빈 목록(값이 `List` · 람다인 표). */
    private fun copiesOf(key: String, map: Map<*, *>?): List<Copy> {
        if (map.isNullOrEmpty()) return emptyList()
        languageTable(map)?.let { return listOf(Copy(key, it)) }
        val nested = map.mapNotNull { (outer, inner) -> (inner as? Map<*, *>)?.let(::languageTable)?.let { Copy("$key[$outer]", it) } }
        return if (nested.size == map.size) nested else emptyList()
    }

    private fun languageTable(map: Map<*, *>): Map<UiLanguage, String>? {
        if (map.keys != UiLanguage.entries.toSet() || map.values.any { it !is String }) return null
        @Suppress("UNCHECKED_CAST")
        return map as Map<UiLanguage, String>
    }

    private companion object {
        /** 기준선 줄 수 — 줄이면 함께 낮춘다. 늘리지 않는다([theBaselineNeverGrows]). */
        const val BaselineSize = 277

        val ObjectDeclaration = Regex("""^\s*(?:internal\s+|private\s+)?object\s+(\w+)""", RegexOption.MULTILINE)

        fun allowanceFor(koreanWidth: Int): Int = when {
            koreanWidth <= 12 -> koreanWidth + 2
            koreanWidth <= 30 -> koreanWidth + 6
            else -> ceil(koreanWidth * 1.2).toInt()
        }
    }
}
