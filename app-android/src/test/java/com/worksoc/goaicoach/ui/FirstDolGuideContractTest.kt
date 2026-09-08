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
        // ①(Landing)은 문구 없는 정적 장식이라 앵커가 없다 — 판정에도 참여하지 않는다.
        val needed = GuideStep.entries
            .filter { it != GuideStep.Landing }
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
