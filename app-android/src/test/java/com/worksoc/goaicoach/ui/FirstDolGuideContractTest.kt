package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.application.guide.GuideTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 첫돌이 가이드(백로그 #128)가 **조용히 사라지는 길**을 막는다.
 *
 * ## ⚠️ 왜 소스 계약인가
 *
 * 이 기능의 결함은 전부 *"안 뜬다"* 로 나타나고, **안 뜨는 것은 화면을 보고 있어야만 알아챈다.**
 * 판정 자체는 `shared`의 `FirstRunGuidePolicyTest`가 셀 수 있지만, **배선**(어느 화면이 어느 앵커를
 * 부르는가, 어느 컨트롤이 자기 자리를 알려 주는가)은 계측 테스트가 없는 이 저장소에서 소스로만
 * 잴 수 있다.
 *
 * ⚠️ **주석과 `import`를 걷어낸 뒤 센다**(함정 10-2). 이름만으로 찾으면 주석에 남은 이름이
 * 통과시켜 버린다 — `AppSplashContractTest`가 실제로 그렇게 헐거웠다(import에 `pointerInput`이
 * 남아 있어 한정자를 지워도 통과했다).
 */
class FirstDolGuideContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun code(path: String): String =
        File(repoRoot, path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val uiSources: Map<String, String> by lazy {
        File(repoRoot, "app-android/src/main/java/com/worksoc/goaicoach/ui")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .associate { file -> file.name to code("app-android/src/main/java/com/worksoc/goaicoach/ui/${file.name}") }
    }

    /**
     * ⚠️ **셸은 가이드를 모른다 — 그것이 이 설계의 이득이었다.**
     *
     * 설계 심사에서 provider 방식(셸에 `CompositionLocalProvider`를 꽂는 안)이 *"여유가 정확히 0인
     * 자원(훅 42/42)을 쓰면서 얻는 것이 없다"* 로 기각됐고, 앵커가 `LocalContext`로 저장소를 직접
     * 여는 쪽을 골라 **`GoCoachApp.kt`를 한 줄도 고치지 않았다**(git으로 확인).
     *
     * 이 단언은 그 사실을 *"안 했다"* 에서 *"안 하기로 했다"* 로 승격시킨다 — 나중에 누가 편의상
     * 셸에 provider를 꽂으면 **훅 예산이 터지기 전에** 여기서 먼저 걸린다.
     */
    @Test
    fun theShellKnowsNothingAboutTheGuide() {
        val shell = code("app-android/src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
        listOf("GuideProgressStore", "GuideAnchor", "GuideSurface", "markSeen", "FirstDolGuide")
            .forEach { name ->
                assertFalse(
                    "`GoCoachApp.kt`가 `$name`을 안다 — 가이드 배선이 셸로 올라왔다. 훅 예산이 " +
                        "42/42로 여유가 0이라 이 길은 곧 예산 테스트도 깨뜨린다(백로그 #128).",
                    shell.contains(name),
                )
            }
    }

    /**
     * ⚠️ **단계를 더하면서 화면 배선을 빠뜨리면 그 단계는 영원히 안 뜬다.**
     *
     * `guideBodyFor`의 `when`은 exhaustive라 **문구**를 빠뜨리면 컴파일이 잡아 준다. 그런데
     * *"그 표면에 `GuideAnchor` 호출이 있는가"* 는 컴파일러가 볼 수 없다 — 새 표면에 단계를 넣고
     * 화면에 한 줄 넣는 것을 잊으면 조용히 사라진다. 반대 방향도 같다: 어느 화면에서 앵커 한 줄을
     * 지우면 그 표면의 단계 전부가 사라진다.
     */
    @Test
    fun everyPlayableStepHasAnAnchorWiredOnItsSurface() {
        val wired = uiSources.values
            .flatMap { source -> Regex("""GuideAnchor\([^)]*GuideSurface\.(\w+)""").findAll(source).toList() }
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "어느 화면도 `GuideAnchor(surface = GuideSurface.…)`를 부르지 않는다 — 이 계약의 전제가 무너졌다.",
            wired.isNotEmpty(),
        )
        val needed = GuideStep.entries
            .map { it.surface.name }
            .toSet()
        assertEquals(
            "앵커가 배선되지 않은 표면이 있다 — 그 표면의 단계는 영원히 뜨지 않는다(백로그 #128).",
            emptySet<String>(),
            needed - wired,
        )
    }

    /**
     * ⚠️ **컨트롤이 자기 자리를 알려 주지 않으면 동그라미가 그려지지 않는다.**
     *
     * `GuideCoachMark`는 좌표가 없으면 **조용히 아무것도 그리지 않는다**(`?: return`). 그 편이
     * 엉뚱한 자리에 동그라미를 치는 것보다 낫지만, 대가로 **`Modifier.guideTarget` 한 줄을 지우면
     * 그 안내가 통째로 사라지고 아무것도 빨개지지 않는다.**
     */
    @Test
    fun everyCoachMarkTargetReportsItsPosition() {
        val reported = uiSources.values
            .flatMap { source -> Regex("""guideTarget\(GuideTarget\.(\w+)\)""").findAll(source).toList() }
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            "자기 자리를 알려 주지 않는 코치마크 대상이 있다 — 그 단계의 동그라미가 조용히 " +
                "사라진다(백로그 #128).",
            GuideTarget.entries.map { it.name }.toSet(),
            reported,
        )
    }

    /**
     * ⚠️ **다시보기가 진행도를 건드리면 자동 재생이 다시 무장된다.**
     *
     * `armed`를 켜거나 `seen_steps`를 비우면, 다시보기를 한 번 볼 때마다 홈·대국에서 말풍선이 또
     * 뜬다. *"복습했으니 처음부터 다시"* 는 그럴듯하게 들리는 실수라 그물을 단다.
     *
     * ⚠️ 이 화면이 **설정은 읽는다**(`UserPreferencesStore`) — ④ 문구가 말하는 접바둑·좌석이
     * 지금 값이어야 참이기 때문이다. 금지 대상은 **진행도**뿐이다.
     */
    @Test
    fun theReplayNeverTouchesGuideProgress() {
        val replay = code("app-android/src/main/java/com/worksoc/goaicoach/ui/FirstDolGuideReplay.kt")
        listOf("GuideProgressStore", "markSeen", "arm()")
            .forEach { name ->
                assertFalse(
                    "다시보기가 `$name`을 부른다 — 복습이 자동 재생을 되살려 말풍선이 다시 뜬다(백로그 #128).",
                    replay.contains(name),
                )
            }
    }

    /**
     * ⚠️ **홈의 `MenuCard`와 판 위 `BoardTopToggle`은 다시보기 하나 때문에 `internal`이 됐다.**
     *
     * 둘의 레이아웃 근거(#28·#29의 사고)는 **자기 화면의 열**에 묶여 있어서, 다른 화면에서 일반
     * 카드·토글 API로 쓰기 시작하면 그 사유가 함께 따라가지 않는다. 그래서 **쓸 수 있는 파일을
     * 개수로 못박는다** — 늘려야 할 이유가 생기면 이 단언을 고치면서 사유를 적게 된다.
     */
    /**
     * ⚠️ **팝업이 덮고 있으면 가이드는 기록하지 않아야 한다.**
     *
     * ③ 말풍선은 *"1.2초 컴포즈돼 있었으면 봤다"* 로 기록한다. 그래서 홈 위에 뜬 팝업이 그것을
     * 덮고 있어도 시간은 그대로 흘러 **한 번도 못 본 안내가 영구히 소진**된다(`seen_steps`는
     * 영구다). 2026-09-09 감사가 실제로 그 구멍을 짚었다 — 게이트가 **출석 팝업 하나**만 세고
     * 있었고, 엔진 안내·초기화 안내가 뜬 동안에는 출석 팝업이 억제되므로 게이트는 `false`였다.
     *
     * 그래서 **`*Dialog.kt` 파일 전부**를 훑는다(손으로 센 목록이 아니다 — 새 팝업 파일이
     * 들어오면 그물이 스스로 넓어진다). 팝업을 여는 자리 수와 [GuideBlockingOverlays.TrackWhileShown]
     * 호출 수가 같아야 한다.
     */
    @Test
    fun everyDialogTellsTheGuideItIsCoveringTheScreen() {
        val dialogFiles = File(repoRoot, "app-android/src/main/java/com/worksoc/goaicoach/ui")
            .listFiles { file -> file.name.endsWith("Dialog.kt") || file.name.endsWith("Dialogs.kt") }
            .orEmpty()
        assertTrue("`*Dialog.kt` 파일을 하나도 못 찾았다 — 이 그물이 아무것도 보지 않는다.", dialogFiles.size >= 5)

        val opensDialog = Regex("""(?<![A-Za-z])(?:AlertDialog|Dialog)\(""")
        val tracks = Regex("""GuideBlockingOverlays\.TrackWhileShown\(\)""")
        dialogFiles.forEach { file ->
            val source = code("app-android/src/main/java/com/worksoc/goaicoach/ui/${file.name}")
            assertEquals(
                "${file.name}: 팝업을 여는 자리 수와 `GuideBlockingOverlays.TrackWhileShown()` 수가 " +
                    "다르다 — 추적하지 않는 팝업이 첫돌이 말풍선을 덮은 채 \"봤음\"으로 소진시킨다(백로그 #128).",
                opensDialog.findAll(source).count(),
                tracks.findAll(source).count(),
            )
        }
    }

    /**
     * ⚠️ **라벨을 인용하는 단계는 앵커가 그 라벨을 넘겨야 한다.**
     *
     * 2026-09-09 감사 전까지 `guideBodyFor`가 인자가 없으면 **조용히 폴백**해서, 앵커에서 인자 한 줄만
     * 빠지면 거짓 문구가 **컴파일도 테스트도 통과**했다. 폴백은 없앴고(`requireNotNull`), 이 계약은 그
     * 크래시를 **개발 중에** 만나게 한다 — 사용자 기기에서 만나기 전에.
     *
     * 요구하는 인자를 **enum에서 파생**한다: 동그라미 대상이 있는 단계가 그 표면에 있으면 `toolLabels`.
     * (④의 `facts`는 #140이 ④를 인자 없는 한 문구로 바꾸며 없어졌다.)
     */
    @Test
    fun everyAnchorHandsInTheLabelsItsOwnStepsQuote() {
        val anchorCall = Regex("""GuideAnchor\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)""")
        val calls = uiSources
            .filterKeys { it != "FirstDolGuide.kt" }   // 선언 자체는 호출이 아니다
            .flatMap { (name, source) -> anchorCall.findAll(source).map { name to it.value } }
        assertTrue("`GuideAnchor(...)` 호출을 하나도 못 찾았다 — 정규식이 늘어났거나 배선이 사라졌다.", calls.isNotEmpty())

        calls.forEach { (name, call) ->
            val surface = Regex("""GuideSurface\.(\w+)""").find(call)?.groupValues?.get(1)
            assertTrue("$name: `GuideAnchor` 호출이 표면을 적지 않는다.", surface != null)
            val steps = GuideStep.entries.filter { it.surface.name == surface }
            if (steps.any { it.target != null }) {
                assertTrue(
                    "$name($surface): 라벨을 인용하는 단계가 이 표면에 있는데 `toolLabels =`를 " +
                        "넘기지 않는다 — ⑤가 빈 인용부호로 뜬다(백로그 #128).",
                    call.contains("toolLabels ="),
                )
            }
        }
    }

    /**
     * ⚠️ **코치마크가 떠 있으면 화면 어디를 눌러도 다음으로 넘어가고, 판은 그 터치를 받지 않는다**
     * (백로그 #140 — 사용자 피드백: *"가이드가 떴을 때 바둑판을 누르면 착수가 되어버림"*).
     *
     * 제스처는 계측 테스트가 없어 소스로 잡는다. 셋이 전부 맞아야 성립한다:
     * ⓐ 코치마크에 **흡수 층**(`pointerInput`)이 있고 떼는 순간 넘긴다,
     * ⓑ 그 층이 동그라미·말풍선보다 **먼저**(= 아래) 선언된다 — 뒤에 오면 `알겠어요`를 덮어 버튼이
     *   죽고, 부모에 걸면 버튼과 층이 한 터치에 둘 다 넘겨 **하나를 건너뛴다**,
     * ⓒ 늘 **최신** `onNext`를 **참조 동등성으로** 들고 부른다 — `rememberUpdatedState`(구조적 동등성)는
     *   `::ack`의 새 클로저를 옛것과 `==`로 보고 갱신을 걸러, 실기에서 둘째 코치마크부터 멈췄다
     *   (아래 [aLocalFunctionReferenceLooksEqualToItsStaleSelf]가 그 성질을 못박는다).
     */
    @Test
    fun theCoachMarkSwallowsEveryTouchAndAdvancesOnRelease() {
        val source = uiSources.getValue("FirstDolGuideCoachMark.kt")
        val body = source.substring(source.indexOf("fun GuideCoachMark("))

        val outerBox = body.indexOf("Box(")
        val layer = body.indexOf("Box(", outerBox + 1)
        val swallow = body.indexOf(".pointerInput(")
        val ring = body.indexOf("drawBehind")
        val bubble = body.indexOf("Column(")
        assertTrue("코치마크에 흡수 층(`pointerInput`)이 없다 — 그 틈으로 판이 착수한다(#140).", swallow >= 0)
        assertTrue(
            "흡수 층이 바깥 `Box`에 걸려 있다 — 조상은 `알겠어요`와 한 터치를 함께 받아 코치마크 하나를 건너뛴다.",
            swallow > layer && layer > outerBox,
        )
        assertTrue(
            "흡수 층이 동그라미·말풍선보다 뒤에 있다 — 위에 그려져 `알겠어요`를 덮는다.",
            swallow < ring && swallow < bubble,
        )

        val gesture = body.substring(swallow, ring)
        assertTrue("터치를 제스처 단위로 받지 않는다.", gesture.contains("awaitEachGesture"))
        assertTrue("받은 터치를 소비하지 않는다.", gesture.contains(".consume()"))
        val lastPressed = gesture.indexOf("while (event.changes.any { it.pressed })")
        val advance = gesture.indexOf("latestOnNext.value()")
        assertTrue("떼는 순간(모든 손가락이 떨어진 뒤) 최신 `onNext`로 넘기지 않는다.", lastPressed in 0 until advance)
        assertTrue(
            "최신 `onNext`를 **참조 동등성**으로 들고 있지 않다 — 옛 `ack`가 남아 둘째 코치마크부터 멈춘다.",
            body.contains("mutableStateOf(onNext, referentialEqualityPolicy())") && body.contains("latestOnNext.value = onNext"),
        )
        assertFalse(
            "`rememberUpdatedState`는 구조적 동등성이라 `::ack`의 새 클로저를 걸러낸다(실기에서 멈췄다).",
            body.contains("rememberUpdatedState"),
        )
    }

    /**
     * 위 ⓒ의 **근거**를 못박는다 — 테스트가 아니라 언어·런타임의 성질이다.
     *
     * 같은 지역 함수의 참조(`::ack`)는 부를 때마다 **다른 값을 쥔 새 클로저**인데도 서로 `==`다. 그래서
     * 구조적 동등성 상태(`mutableStateOf` 기본값 = `rememberUpdatedState`의 속)에 넣으면 새것이 **걸러지고
     * 옛것이 남는다.** 이 성질이 바뀌면(코틀린이 캡처까지 비교하게 되면) 이 테스트가 알려 준다 — 그때
     * 참조 동등성 우회가 필요 없어졌는지 다시 볼 것.
     */
    @Test
    fun aLocalFunctionReferenceLooksEqualToItsStaleSelf() {
        fun referenceFor(step: String): () -> String {
            fun ack() = step
            return ::ack
        }
        val first = referenceFor("InGameMagnifier")
        val second = referenceFor("InGameBoardSize")
        assertEquals("두 참조는 다른 단계를 쥔다", listOf("InGameMagnifier", "InGameBoardSize"), listOf(first(), second()))
        assertEquals("그런데도 `==`다 — 이것이 구조적 동등성이 갱신을 거르는 이유다", first, second)

        val structural = androidx.compose.runtime.mutableStateOf(first)
        structural.value = second
        assertEquals("구조적 동등성은 새것을 걸러 옛 단계를 남긴다", "InGameMagnifier", structural.value())

        val referential = androidx.compose.runtime.mutableStateOf(first, androidx.compose.runtime.referentialEqualityPolicy())
        referential.value = second
        assertEquals("참조 동등성은 새것을 받는다", "InGameBoardSize", referential.value())
    }

    /**
     * ⚠️ **첫 실행 처리는 화면보다 먼저, 그리고 한 곳에서만**(백로그 #140 — 랜딩을 없앤 뒤의 `FirstRunGate`).
     *
     * `GoCoachScreen`은 첫 컴포지션에서 설정을 읽고 자동저장이 곧바로 되쓴다. 첫 실행 저장이
     * `content()`보다 늦으면(예: `LaunchedEffect`로 옮기면) 자동저장이 `hasSeenOnboarding = false`를
     * 되써 **매 실행이 첫 실행**이 된다. 가이드 무장도 여기 한 곳이어야 기존 사용자에게 자동 재생이
     * 무장되지 않는다(`GuideProgress.armed`).
     */
    @Test
    fun theFirstRunIsSettledBeforeTheAppComposesAndArmsTheGuideOnce() {
        val gate = uiSources.getValue("FirstRunGate.kt")
        val save = gate.indexOf("store.save(completeFirstRun(")
        val arm = gate.indexOf("GuideProgressStore(context).arm()")
        val content = gate.lastIndexOf("content()")
        assertTrue("첫 실행을 저장하지 않는다.", save >= 0)
        assertTrue("가이드를 무장하지 않는다 — 신규 사용자에게 첫돌이 안내가 영영 뜨지 않는다.", arm >= 0)
        assertTrue("첫 실행 저장이 `content()`보다 늦다 — 자동저장이 되써 매 실행이 첫 실행이 된다.", save < content && arm < content)
        listOf("LaunchedEffect", "SideEffect", "DisposableEffect").forEach { effect ->
            assertFalse("`$effect`는 컴포지션 **뒤에** 돈다 — 위 순서가 깨진다.", gate.contains(effect))
        }
        val armers = uiSources.filterValues { it.contains(".arm()") }.keys
        assertEquals("가이드를 무장하는 곳이 `FirstRunGate` 하나가 아니다.", setOf("FirstRunGate.kt"), armers)
    }

    /**
     * ⚠️ **접두사가 지우는 힘을 만든다.** 개발자 초기화는 저장소를 손으로 열거하지 않고
     * `shared_prefs`를 `go_ai_coach_` 접두사로 훑는다 — 이름에서 접두사가 빠지면 *"최초 설치
     * 상태"* 를 만들었다고 믿는데 **가이드만 다시 뜨지 않는다.** 그 조용한 예외를 막는다.
     */
    @Test
    fun theGuideProgressStoreKeepsTheNameTheDeveloperResetSweeps() {
        val store = code("app-android/src/main/java/com/worksoc/goaicoach/persistence/GuideProgressStore.kt")
        val prefsName = Regex("""PrefsName = "([^"]+)"""").find(store)?.groupValues?.get(1)
        assertEquals(
            "`GuideProgressStore`의 prefs 이름이 `go_ai_coach_` 접두사를 잃었다 — 개발자 초기화가 " +
                "가이드 진행도를 조용히 지나친다(`DeveloperModeResetCoordinator`).",
            true,
            prefsName?.startsWith("go_ai_coach_"),
        )
    }

    @Test
    fun theBorrowedHomeAndBoardControlsStayBorrowedByTheGuideOnly() {
        mapOf(
            "MenuCard(" to setOf("GoCoachHomeScreen.kt", "FirstDolGuideReplay.kt"),
            "BoardTopToggle(" to setOf("GamePlaySection.kt", "FirstDolGuideReplay.kt"),
        ).forEach { (call, allowed) ->
            val callers = uiSources
                .filterValues { source -> source.contains(call) }
                .keys
            assertEquals(
                "`$call`를 부르는 파일이 늘었다. 이 컴포저블의 레이아웃 사유는 자기 화면에 묶여 " +
                    "있으므로, 다른 화면에서 쓰려면 그 사유부터 옮겨야 한다(백로그 #128).",
                allowed,
                callers,
            )
        }
    }
}
