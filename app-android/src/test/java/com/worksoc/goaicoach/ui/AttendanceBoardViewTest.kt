package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #57 — 도장판이 글자 대신 글리프를 그리게 되면서 생긴 **두 개의 조용한 실패**를 잡는다.
 *
 * ⚠️ **`rewardGlyphRes`를 직접 부르지 않는다.** `R.drawable.*`은 Android 리소스라 JVM 단위
 * 테스트에는 없다 — `BotCharacterAvatarTest`와 같은 이유로 **소스와 파일 시스템**으로 같은
 * 사실을 확인한다.
 */
class AttendanceBoardViewTest {

    private val drawableDir = File("src/main/res/drawable")
    private val source = File("src/main/java/com/worksoc/goaicoach/ui/AttendanceBoardView.kt").readText()

    /**
     * ⚠️ **이것이 이 파일의 핵심이다.** 소모품 분기는 `else -> null`로 닫혀 있어 **컴파일러가
     * 빠뜨림을 잡아 주지 않는다** — 카탈로그에 소모품을 하나 더하면 그 칸만 조용히 빈 칸이 된다
     * (`FeatureId` 쪽은 `when`이 열거형을 남김없이 덮으므로 컴파일러가 이미 지킨다).
     */
    @Test
    fun everyConsumableInTheCatalogHasAGlyphBranch() {
        val unlisted = ConsumableCatalog.all.filter { item ->
            !source.contains("ConsumableCatalog.${pascalCase(item.id.raw)} ->")
        }
        assertTrue("consumableGlyphRes에 빠진 소모품: ${unlisted.map { it.id.raw }}", unlisted.isEmpty())
    }

    /**
     * ⚠️ **표는 한 벌이어야 한다**(#60). 출석 도장판과 마이 페이지 재고 목록이 같은 1회권을 그리는데
     * 각자 `when`을 들고 있으면 카탈로그가 늘었을 때 **한쪽만 조용히 빈다.** 두 화면이 같은 함수를
     * 부르는지 소스로 확인한다.
     */
    @Test
    fun bothScreensDrawConsumablesFromTheSameGlyphTable() {
        assertTrue(
            "consumableGlyphRes가 없다 — 표가 다시 갈렸다",
            source.contains("internal fun consumableGlyphRes(item: ConsumableItem)"),
        )
        val myPage = File("src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt").readText()
        assertTrue("마이 페이지가 공유 표를 쓰지 않는다", myPage.contains("consumableGlyphRes(item)"))
        // 마이 페이지가 자기 대응표를 따로 들고 있으면 안 된다.
        assertTrue(
            "마이 페이지가 글리프 리소스를 직접 지목한다 — 표가 두 벌이 됐다",
            !myPage.contains("R.drawable.reward_"),
        )
    }

    @Test
    fun everyGlyphReferencedInTheSourceExistsAsADrawable() {
        val referenced = Regex("""R\.drawable\.(reward_\w+)""").findAll(source)
            .map { it.groupValues[1] }
            .toSortedSet()
        assertTrue("소스가 글리프를 하나도 안 쓴다", referenced.isNotEmpty())
        val missing = referenced.filterNot { File(drawableDir, "$it.xml").exists() }
        assertTrue("그림 파일이 없는 글리프: $missing", missing.isEmpty())
    }

    /**
     * 글리프가 서로 다른 뷰포트를 쓰면 같은 `size()`를 줘도 칸마다 크기가 튄다 —
     * 아바타 5종에 걸어 둔 규격(`BotCharacterAvatarTest.allAvatarsShareTheSameViewport`)과 같은 그물.
     */
    @Test
    fun allRewardGlyphsShareTheSameViewport() {
        val viewports = drawableDir.listFiles { file -> file.name.startsWith("reward_") }
            .orEmpty()
            .map { file ->
                val xml = file.readText()
                val width = Regex("""viewportWidth="([\d.]+)"""").find(xml)?.groupValues?.get(1)
                val height = Regex("""viewportHeight="([\d.]+)"""").find(xml)?.groupValues?.get(1)
                file.name to "${width}x$height"
            }
        assertTrue("글리프 파일이 하나도 없다", viewports.isNotEmpty())
        assertEquals("뷰포트가 서로 다르다: $viewports", 1, viewports.map { it.second }.distinct().size)
        assertEquals("24x24", viewports.first().second)
    }

    /** `eval_once` → `EvalOnce`. 카탈로그의 id에서 프로퍼티 이름을 되돌린다. */
    private fun pascalCase(rawId: String): String =
        rawId.split("_").joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
}
