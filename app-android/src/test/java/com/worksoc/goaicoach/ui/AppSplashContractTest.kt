package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스플래시(백로그 #125)가 지켜야 할 것 셋. **셋 다 어겨도 컴파일은 되고 다른 테스트는 초록이다.**
 */
class AppSplashContractTest {

    /**
     * ⚠️ **`import` 줄을 함께 지운다 — 안 지우면 그물이 헐거워진다.** 이 계약을 처음 쓸 때
     * `pointerInput` 한정자를 지우고 변이 시험을 돌렸는데 **테스트가 통과했다**:
     * `import androidx.compose.ui.input.pointer.pointerInput` 줄에 그 이름이 그대로 남아
     * 있었기 때문이다(함정 10-2와 같은 모양 — 이름만으로 찾으면 쓰이지 않는 자리도 걸린다).
     */
    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val splash = source("src/main/java/com/worksoc/goaicoach/ui/AppSplash.kt")
    private val avatar = source("src/main/java/com/worksoc/goaicoach/ui/BotCharacterAvatar.kt")
    private val main = source("src/main/java/com/worksoc/goaicoach/MainActivity.kt")

    /**
     * ⚠️ 캐릭터 그림을 더하면서 [AllBotAvatarRes]를 빠뜨리면 **스플래시만 조용히 옛 5장을 계속
     * 쓴다.** 화면은 멀쩡해 보이고 아무 테스트도 빨개지지 않는다.
     */
    @Test
    fun theSplashUsesEveryAvatarThatBotAvatarResKnows() {
        val known = Regex("""R\.drawable\.(bot_[a-z0-9_]+)""")
            .findAll(avatar.substringAfter("fun botAvatarRes").substringBefore("internal val AllBotAvatarRes"))
            .map { it.groupValues[1] }.toList()
        val listed = Regex("""R\.drawable\.(bot_[a-z0-9_]+)""")
            .findAll(avatar.substringAfter("internal val AllBotAvatarRes"))
            .map { it.groupValues[1] }.toList()
        assertTrue("botAvatarRes에서 그림을 하나도 못 찾았다 — 이 계약의 전제가 무너졌다.", known.isNotEmpty())
        assertEquals(
            "`botAvatarRes`가 아는 그림과 `AllBotAvatarRes`의 목록이 어긋난다. 스플래시가 " +
                "새 캐릭터를 못 본다(백로그 #125).",
            known, listed,
        )
    }

    /**
     * ⚠️ **스플래시가 터치를 먹지 않으면**, 그것을 보는 1초 동안 누른 것이 **아래 홈으로 새어
     * 들어간다** — 홈은 이미 컴포즈돼 있기 때문이다(그래야 이 1초가 벌어 두는 시간이 된다).
     * 재현이 어렵고 원인을 짐작하기 힘든 종류라 그물을 단다.
     */
    @Test
    fun theSplashSwallowsTouchesWhileItIsUp() {
        assertTrue(
            "스플래시가 포인터 입력을 잡지 않는다 — 아래 홈으로 터치가 샌다(백로그 #125).",
            splash.contains("pointerInput") && splash.contains("detectTapGestures"),
        )
    }

    /**
     * ⚠️ **`setContent`보다 앞에서 부르면 함정 35번을 그대로 다시 밟는다.**
     * `StartupOrderContractTest`가 *"`super.onCreate`와 `setContent` 사이가 비어 있을 것"* 을
     * 지키지만, 스플래시는 그 사이가 아니라 **`setContent` 람다 안**에 있어야 한다는 별개의 조건이다.
     */
    @Test
    fun theSplashIsComposedInsideSetContent() {
        val setContent = main.indexOf("setContent {")
        val call = main.indexOf("AppSplash()")
        assertTrue("`setContent {`를 찾지 못했다.", setContent >= 0)
        assertTrue("`MainActivity`가 `AppSplash()`를 부르지 않는다(백로그 #125).", call >= 0)
        assertTrue(
            "`AppSplash()`가 `setContent`보다 앞에 있다 — 창이 서기 전에 도는 코드다(함정 35번).",
            call > setContent,
        )
    }
}
