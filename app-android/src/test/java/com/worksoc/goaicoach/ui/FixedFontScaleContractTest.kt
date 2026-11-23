package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.PackageImportGraph
import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **앱은 글자를 1.0 한 크기로만 그린다 — 대화상자 · 팝업 · iOS 알림 층도**(QA-05, 사용자 2026-11-23, `docs/spec/DECISIONS.md` §1).
 * 안드로이드 글자 토스트는 예외다 — 시스템 UI가 그린다(targetSdk 30부터, `MainActivity.attachBaseContext`의 KDoc).
 *
 * ⚠️ **본 화면의 `LocalDensity` 덮어쓰기만으로는 모자랐다.** 안드로이드의 `Dialog` · `Popup`은 창마다 컴포즈 뷰를 새로 만들고,
 * 그 뷰가 `LocalDensity`를 **창의 Context 설정**(`Configuration.fontScale`)에서 다시 짓는다 — 실기에서 휴대폰 글자 크기를 키우면
 * 본 화면은 1.0인데 대화상자만 커졌다(`work/qa/261122-screens/m10-qa05-dialog-follows-system.jpg`). 그래서 액티비티의 설정
 * 자체를 1.0으로 덮는다. iOS는 알림 층(`IosNoticeOverlay`)이 1.0 덮어쓰기 **밖**에 있어 Dynamic Type을 따랐다.
 *
 * ⚠️ 글자 크기는 측정 결과라 값 검사로는 안 잡힌다 — **처방을 소스에 못박는다.** 주석과 문자열은 걷어내고 본다.
 * 모양은 넓게 받는다: 값은 `1f` · `1.0f` 또는 그 값의 상수, 덮어쓰기는 `CompositionLocalProvider(LocalDensity provides …)`
 * 또는 그것을 감싼 컴포저블(`content`를 받는 함수).
 */
class FixedFontScaleContractTest {

    private val mainActivity = RepoPaths.compositionFile("MainActivity.kt")
    private val iosRoots = listOf("ios/MainViewController.kt", "ios/GoCoachRoot.kt").map { RepoPaths.appAndroid(it) }

    /**
     * 안드로이드 — 액티비티의 설정에 `fontScale` 1.0을 넣는다. 대화상자 · 팝업 창은 그 설정에서 배율을 읽는다.
     * ⚠️ 저장된 배율을 다시 읽으면 안 된다 — 「크게」(1.3)는 없앴고, 옛 저장값은 읽을 때 버린다(옮기기 없음).
     */
    @Test
    fun theActivityPinsItsOwnConfigurationFontScaleToOne() {
        val activity = codeOf(mainActivity)
        // 덮어쓰기 **호출의 인자 안**에서 1.0을 적어야 한다 — `resources.configuration.fontScale = 1f` 같은 다른 자리의 대입은
        // 대화상자 창에 닿지 않는다(창은 액티비티 Context의 **덮어쓴 설정**을 읽는다).
        val overrideArgs = ConfigurationOverrideHooks.flatMap { hook ->
            Regex("""\b$hook\s*\(""").findAll(activity).mapNotNull { call ->
                val open = call.range.last
                val close = matching(activity, open)
                if (close < 0) null else open..close
            }.toList()
        }
        assertTrue(
            "MainActivity가 액티비티 설정을 덮지 않는다(${ConfigurationOverrideHooks.joinToString(" · ")} 중 하나) — 본 화면의 " +
                "`LocalDensity`만 덮으면 대화상자 · 팝업은 휴대폰 글자 크기를 따른다(QA-05).",
            overrideArgs.isNotEmpty(),
        )
        val writes = fontScaleWrites(activity)
        assertTrue(
            "MainActivity의 설정 덮어쓰기 안에 `fontScale` 1.0이 없다 — 덮는 설정에 넣어야 대화상자 · 팝업 창이 읽는다(QA-05).",
            writes.any { write -> overrideArgs.any { write.at in it } && resolvesToOne(write.value) },
        )
        val notOne = writes.filterNot { resolvesToOne(it.value) }.map { it.value.trim() }
        assertTrue("MainActivity의 `fontScale`이 1.0이 아니다: $notOne — 앱은 한 크기로만 그린다(QA-05).", notOne.isEmpty())
        assertTrue(
            "MainActivity가 저장된 글꼴 배율을 읽는다 — 「크게」는 없앴다(QA-05, 2026-11-23).",
            "AppFontScaleState" !in activity && "appFontScale" !in activity,
        )
    }

    /**
     * 본 화면과 iOS 알림 층이 1.0 덮어쓰기 **안**에서 그려진다. 안드로이드도 컴포즈 쪽 덮어쓰기는 남긴다 — 두 플랫폼의 뿌리가
     * 같은 모양이어야 한쪽만 고쳐지는 일이 없다.
     */
    @Test
    fun theAppAndTheIosNoticeLayerDrawInsideTheOneDensity() {
        val helpers = densityHelpers()
        val uncovered = uncoveredCalls(listOf(mainActivity), "GoCoachApp", helpers.keys) +
            uncoveredCalls(iosRoots, "GoCoachApp", helpers.keys) +
            uncoveredCalls(iosRoots, "IosNoticeOverlay", helpers.keys)
        assertTrue(
            "1.0 덮어쓰기(`LocalDensity provides`) 밖에서 그리는 뿌리가 있다 — 그 층은 휴대폰 글자 크기를 따른다(QA-05):\n" +
                uncovered.joinToString("\n"),
            uncovered.isEmpty(),
        )
        val notOne = (listOf(mainActivity) + iosRoots + helpers.values).distinct().flatMap(::densityScaleOffenders)
        assertTrue("덮어쓰는 `Density`의 배율이 1.0이 아니다 — 앱은 한 크기로만 그린다(QA-05):\n${notOne.joinToString("\n")}", notOne.isEmpty())
    }

    /**
     * iOS — 대화상자 · 팝업 층은 부른 자리의 `LocalDensity`를 물려받지 않고 화면의 Dynamic Type 특성에서 배율을 다시 짓는다
     * (2026-11-23 시뮬레이터: 더 큰 텍스트 최대에서 출석 창만 커졌다). 그래서 컨트롤러의 글자 크기 특성을 기본값으로 묶는다 —
     * 안드로이드의 액티비티 설정 덮기와 짝이다.
     */
    @Test
    fun theIosControllerPinsItsContentSizeTraitSoDialogsStayAtOne() {
        val controller = codeOf(RepoPaths.appAndroid("ios/MainViewController.kt"))
        assertTrue(
            "MainViewController가 `traitOverrides.preferredContentSizeCategory`를 기본값(Large)으로 묶지 않는다 — iOS 대화상자가 " +
                "휴대폰 글자 크기를 따른다(QA-05).",
            Regex("""traitOverrides\.preferredContentSizeCategory\s*=\s*UIContentSizeCategoryLarge""").containsMatchIn(controller),
        )
    }

    /**
     * ⚠️ **덮어쓰기와 함께 `configChanges`의 `fontScale`도 남긴다.** 빼면 휴대폰 글자 크기를 바꾸는 순간 액티비티가 다시
     * 만들어져 두던 판이 날아간다 — 앱이 그 값을 무시해도 시스템은 바뀐 줄 안다.
     */
    @Test
    fun theActivityStillHandlesFontScaleChangesItself() {
        val manifest = RepoPaths.appAndroidMain("AndroidManifest.xml").readContractSource()
        val activity = Regex("""<activity\b[^>]*android:name="\.MainActivity"[^>]*>""").find(manifest)?.value
        assertTrue("매니페스트에서 MainActivity를 찾지 못했다 — 이 계약의 전제가 무너졌다.", activity != null)
        val changes = Regex("android:configChanges=\"([^\"]*)\"").find(activity.orEmpty())?.groupValues?.get(1).orEmpty().split('|')
        assertTrue(
            "MainActivity의 `configChanges`에서 `fontScale`이 빠졌다 — 휴대폰 글자 크기를 바꾸면 액티비티가 다시 만들어진다(QA-05).",
            "fontScale" in changes,
        )
    }

    // ── 소스 읽기 ──────────────────────────────────────────────────────────────

    private data class FontScaleWrite(val at: Int, val value: String)

    private data class FunctionSpan(val name: String, val parameters: String, val body: IntRange)

    private val codeCache = mutableMapOf<File, String>()

    private fun codeOf(file: File): String =
        codeCache.getOrPut(file) { PackageImportGraph.stripCommentsAndStrings(file.readContractSource().replace("\r\n", "\n")) }

    private fun kotlinFilesUnder(roots: List<File>): List<File> =
        roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    /** 상수 이름을 값으로 풀 때 보는 프로덕션 소스 — app-android 소스셋 셋과 :shared commonMain. */
    private val constantSources: List<String> by lazy {
        kotlinFilesUnder(RepoPaths.appAndroidDirs("") + RepoPaths.shared()).map(::codeOf)
    }

    /** `fontScale = …`(이름 붙은 인자든 설정 필드 대입이든) 전부. `==` 비교는 뺀다. */
    private fun fontScaleWrites(code: String): List<FontScaleWrite> =
        FontScaleAssignment.findAll(code).map { FontScaleWrite(it.range.first, it.groupValues[1]) }.toList()

    /** `Density(` … `)`의 괄호 구간(여는 괄호 ~ 닫는 괄호). `LocalDensity`는 뺀다. */
    private fun densityCallSpans(code: String): List<IntRange> =
        DensityCall.findAll(code).mapNotNull { match ->
            val open = match.range.last
            val close = matching(code, open)
            if (close < 0) null else open..close
        }.toList()

    /** 값이 1.0인가 — 숫자 그대로이거나, 숫자 1을 담은 (대문자로 시작하는) 상수. */
    private fun resolvesToOne(expression: String): Boolean {
        val value = expression.trim()
        if (OneLiteral.matches(value)) return true
        if (!ConstantName.matches(value)) return false
        val declaration = Regex(
            """(?m)^\s*(?:(?:public|internal|private)\s+)?(?:const\s+)?val\s+${Regex.escape(value)}\s*(?::\s*Float\s*)?=\s*([0-9][0-9.]*[fF]?)\s*$""",
        )
        return constantSources.any { code -> declaration.find(code)?.groupValues?.get(1)?.let(OneLiteral::matches) == true }
    }

    /** [file] 안의 `Density(…)` · `fontScale = …` 중 1.0이 아닌 것. 이름 없이 둘째 자리에 둔 배율도 본다(빠지면 기본값 1). */
    private fun densityScaleOffenders(file: File): List<String> {
        val code = codeOf(file)
        val fromCalls = densityCallSpans(code).mapNotNull { span ->
            val arguments = topLevelArguments(code.substring(span.first + 1, span.last))
            val named = arguments.firstOrNull { FontScaleArgument.containsMatchIn(it) }
            val value = named?.substringAfter('=') ?: arguments.takeIf { all -> all.none { NamedArgument.containsMatchIn(it) } }?.getOrNull(1)
            if (value == null || resolvesToOne(value)) null else "${file.name}: Density(…, ${value.trim()})"
        }
        val fromWrites = fontScaleWrites(code).filterNot { resolvesToOne(it.value) }.map { "${file.name}: fontScale = ${it.value.trim()}" }
        return (fromCalls + fromWrites).distinct()
    }

    /**
     * `LocalDensity provides`를 감싼 컴포저블 — `content`(컴포저블 람다)를 받고 본문에서 밀도를 덮는 함수. 이름 → 그 파일.
     * 뿌리가 덮어쓰기를 공용 함수로 옮겨도 이 계약이 따라간다.
     */
    private fun densityHelpers(): Map<String, File> =
        kotlinFilesUnder(RepoPaths.appAndroidDirs("")).flatMap { file ->
            val code = codeOf(file)
            if (!LocalDensityProvides.containsMatchIn(code)) return@flatMap emptyList<Pair<String, File>>()
            functionsIn(code)
                .filter { ComposableContent.containsMatchIn(it.parameters) }
                .filter { LocalDensityProvides.containsMatchIn(code.substring(it.body.first, it.body.last + 1)) }
                .map { it.name to file }
        }.toMap()

    /** 1.0 덮어쓰기의 본문 구간 — `CompositionLocalProvider(… LocalDensity provides …) { … }`와 [helpers] 호출의 끝 람다. */
    private fun densityScopes(code: String, helpers: Set<String>): List<IntRange> {
        val providers = ProviderCall.findAll(code).mapNotNull { match ->
            val open = match.range.last
            val close = matching(code, open)
            if (close < 0 || !LocalDensityProvides.containsMatchIn(code.substring(open, close))) null else lambdaAfter(code, close + 1)
        }
        val wrappers = helpers.asSequence().flatMap { name ->
            callSites(code, name).mapNotNull { at ->
                var next = skipSpaces(code, at + name.length)
                if (next < code.length && code[next] == '(') next = matching(code, next).takeIf { it >= 0 }?.plus(1) ?: return@mapNotNull null
                lambdaAfter(code, next)
            }
        }
        return (providers + wrappers).toList()
    }

    /**
     * [callName] 호출 중 1.0 덮어쓰기 밖에 있는 것. 덮어쓰기 밖이면 그 호출을 품은 함수의 호출 자리를 [files]에서 다시 본다
     * (뿌리 함수가 통째로 덮어쓰기 안에서 불리는 모양). 그 자리가 없으면 밖이다.
     */
    private fun uncoveredCalls(files: List<File>, callName: String, helpers: Set<String>, depth: Int = 0): List<String> {
        val sites = files.flatMap { file -> callSites(codeOf(file), callName).map { file to it } }
        if (sites.isEmpty()) return listOf("`$callName(` 호출을 찾지 못했다(${files.joinToString { it.name }})")
        return sites.flatMap { (file, at) ->
            val code = codeOf(file)
            if (densityScopes(code, helpers).any { at in it }) return@flatMap emptyList<String>()
            val caller = functionsIn(code).filter { at in it.body }.minByOrNull { it.body.last - it.body.first }
            if (caller == null || depth >= MaxCallerDepth) {
                listOf("${file.name}: `$callName(`")
            } else {
                uncoveredCalls(files, caller.name, helpers, depth + 1).map { "${file.name}: `$callName(` ← $it" }
            }
        }
    }

    /** 선언(`fun Name(`)을 뺀 호출 자리. */
    private fun callSites(code: String, name: String): List<Int> =
        Regex("""(?<![\w.])${Regex.escape(name)}\s*[({]""").findAll(code)
            .map { it.range.first }
            .filterNot { at -> code.substring(0, at).trimEnd().endsWith("fun") }
            .toList()

    /** 본문이 있는 함수 선언 전부 — 블록 본문은 짝 괄호까지, 식 본문은 그 식이 끝날 때까지. */
    private fun functionsIn(code: String): List<FunctionSpan> =
        FunctionDeclaration.findAll(code).mapNotNull { match ->
            val parametersOpen = match.range.last
            val parametersClose = matching(code, parametersOpen)
            if (parametersClose < 0) return@mapNotNull null
            val bodyStart = code.indexOfAny(charArrayOf('{', '='), parametersClose + 1)
            if (bodyStart < 0 || !ReturnTypeOnly.matches(code.substring(parametersClose + 1, bodyStart))) return@mapNotNull null
            val bodyEnd = if (code[bodyStart] == '{') matching(code, bodyStart) else expressionEnd(code, bodyStart + 1)
            if (bodyEnd < 0) null else FunctionSpan(match.groupValues[1], code.substring(parametersOpen, parametersClose + 1), bodyStart..bodyEnd)
        }.toList()

    /** 식 본문의 끝 — 괄호 깊이 0에서 줄이 바뀌고 다음 줄이 이어지는 연산(`.` · `?`)이 아니면 거기서 끝난다. */
    private fun expressionEnd(code: String, from: Int): Int {
        var depth = 0
        var i = skipSpaces(code, from)
        while (i < code.length) {
            when (code[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (--depth < 0) return i - 1
                '\n' -> if (depth == 0) {
                    val next = skipSpaces(code, i + 1)
                    if (next < code.length && code[next] != '.' && code[next] != '?') return i - 1
                }
            }
            i++
        }
        return code.length - 1
    }

    private fun lambdaAfter(code: String, from: Int): IntRange? {
        val open = skipSpaces(code, from)
        if (open >= code.length || code[open] != '{') return null
        val close = matching(code, open)
        return if (close < 0) null else open..close
    }

    private fun skipSpaces(code: String, from: Int): Int {
        var i = from
        while (i < code.length && code[i].isWhitespace()) i++
        return i
    }

    /** 여는 `(` 또는 `{`의 짝 — 주석 · 문자열을 걷어낸 코드라 같은 종류의 괄호만 세면 된다. */
    private fun matching(code: String, open: Int): Int {
        val opening = code[open]
        val closing = if (opening == '(') ')' else '}'
        var depth = 0
        for (i in open until code.length) {
            if (code[i] == opening) depth++
            if (code[i] == closing && --depth == 0) return i
        }
        return -1
    }

    /** 괄호 깊이 0의 쉼표로 나눈 인자들. */
    private fun topLevelArguments(inner: String): List<String> {
        val arguments = mutableListOf<String>()
        var depth = 0
        var start = 0
        inner.forEachIndexed { i, c ->
            when (c) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                ',' -> if (depth == 0) {
                    arguments += inner.substring(start, i)
                    start = i + 1
                }
            }
        }
        arguments += inner.substring(start)
        return arguments.filter { it.isNotBlank() }
    }

    private companion object {
        /** 액티비티의 설정을 덮는 길 — 어느 것을 쓰든 대화상자 창이 읽는 설정이 바뀐다. */
        val ConfigurationOverrideHooks = listOf("applyOverrideConfiguration", "createConfigurationContext")
        const val MaxCallerDepth = 3

        val OneLiteral = Regex("""1(?:\.0+)?[fF]?""")
        val ConstantName = Regex("""[A-Z]\w*""")
        val FontScaleAssignment = Regex("""\bfontScale\s*=(?!=)\s*([^,;)}\n]+)""")
        val FontScaleArgument = Regex("""^\s*fontScale\s*=(?!=)""")
        val NamedArgument = Regex("""^\s*\w+\s*=(?!=)""")
        val DensityCall = Regex("""(?<![\w.])Density\s*\(""")
        val ProviderCall = Regex("""\bCompositionLocalProvider\s*\(""")
        val LocalDensityProvides = Regex("""\bLocalDensity\s+provides\b""")
        val ComposableContent = Regex("""@Composable\s*(?:\w+\.)*\(\s*\)\s*->\s*Unit""")
        val FunctionDeclaration = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(""")
        val ReturnTypeOnly = Regex("""\s*(?::\s*[^{=\n]+)?\s*""")
    }
}
