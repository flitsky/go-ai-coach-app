package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotUnlockSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #48 — 카탈로그의 캐릭터와 실제 그림 파일이 어긋나지 않게 잡는 그물.
 *
 * ⚠️ **`botAvatarRes`를 직접 부르지 않는다.** 그 함수는 `R.drawable.*`(Android 리소스)를 만지는데
 * 이건 JVM 단위 테스트라 리소스가 없다. 그래서 같은 사실을 **파일 시스템**으로 확인한다 —
 * `avatarRef`가 가리키는 이름의 drawable이 실제로 있는지. 목적(캐릭터를 추가하고 그림을
 * 빠뜨리는 사고를 막는 것)은 그대로 달성되고, 계측 테스트를 새로 세울 필요가 없다.
 */
class BotCharacterAvatarTest {

    private val drawableDir = File("src/main/res/drawable")

    @Test
    fun everyCatalogCharacterHasAnAvatarReference() {
        val missing = BotCharacterCatalog.all.filter { it.avatarRef.isNullOrBlank() }
        assertTrue("avatarRef가 빈 캐릭터: ${missing.map { it.id.raw }}", missing.isEmpty())
    }

    @Test
    fun everyAvatarReferenceResolvesToADrawableFile() {
        val missing = BotCharacterCatalog.all.filter { character ->
            !File(drawableDir, "${character.avatarRef}.xml").exists()
        }
        assertTrue(
            "그림 파일이 없는 캐릭터: ${missing.map { "${it.id.raw} -> ${it.avatarRef}" }}",
            missing.isEmpty(),
        )
    }

    /**
     * ⚠️ 이 규격이 깨지면 캐러셀(#49)에서 카드마다 크기가 튄다. 다섯 종이 같은 뷰포트를
     * 쓰는지 그림 자체를 읽어 확인한다 — 주석으로만 적어 둔 약속은 지켜지지 않는다.
     */
    @Test
    fun allAvatarsShareTheSameViewport() {
        val viewports = BotCharacterCatalog.all.map { character ->
            val xml = File(drawableDir, "${character.avatarRef}.xml").readText()
            val width = Regex("""viewportWidth="([\d.]+)"""").find(xml)?.groupValues?.get(1)
            val height = Regex("""viewportHeight="([\d.]+)"""").find(xml)?.groupValues?.get(1)
            character.id.raw to "${width}x$height"
        }
        assertEquals(
            "뷰포트가 서로 다르다: $viewports",
            1,
            viewports.map { it.second }.distinct().size,
        )
        assertEquals("96x96", viewports.first().second)
    }

    /**
     * `botAvatarRes`는 이름을 실행 중에 찾지 않고 **명시적으로** 적어 둔다(#47에서 R8을 켠 뒤로는
     * 특히 중요하다). 카탈로그가 늘었는데 그 `when`을 안 고치는 실수를 잡는다.
     */
    @Test
    fun avatarResolverListsEveryCatalogReference() {
        val source = File("src/main/java/com/worksoc/goaicoach/ui/BotCharacterAvatar.kt").readText()
        val unlisted = BotCharacterCatalog.all.filter { character ->
            !source.contains("\"${character.avatarRef}\" -> R.drawable.${character.avatarRef}")
        }
        assertTrue("botAvatarRes에 빠진 캐릭터: ${unlisted.map { it.id.raw }}", unlisted.isEmpty())
    }

    // ── 조각 시계방향 공개(#50) ─────────────────────────────────────────────────
    // 그림이 아니라 **각도 계산**이 틀리기 쉬운 곳이라, 순수 함수만 떼어 여기서 고정한다.

    @Test
    fun noShardsRevealNothingAndAFullSetRevealsTheWholeCircle() {
        assertEquals(0f, shardSweepDegrees(acquired = 0, required = 5))
        assertEquals(360f, shardSweepDegrees(acquired = 5, required = 5))
    }

    @Test
    fun eachShardOpensAnEqualWedge() {
        // 5조각이면 한 조각당 72도, 10조각이면 36도 — 카탈로그의 두 조각 경로가 그 둘이다.
        assertEquals(72f, shardSweepDegrees(acquired = 1, required = 5))
        assertEquals(216f, shardSweepDegrees(acquired = 3, required = 5))
        assertEquals(36f, shardSweepDegrees(acquired = 1, required = 10))
        assertEquals(252f, shardSweepDegrees(acquired = 7, required = 10))
    }

    /**
     * ⚠️ 방어적으로 잘라내는지 본다. 조각이 요구치를 넘거나(중복 지급 등) 요구치가 0인 값이
     * 흘러들면 한 바퀴를 넘는 부채꼴이나 0으로 나누기가 생긴다 — 화면에서는 조용히 깨진다.
     */
    @Test
    fun outOfRangeProgressIsClampedInsteadOfOverdrawing() {
        assertEquals(360f, shardSweepDegrees(acquired = 9, required = 5))
        assertEquals(0f, shardSweepDegrees(acquired = -1, required = 5))
        assertEquals(0f, shardSweepDegrees(acquired = 3, required = 0))
        assertEquals(0f, shardSweepDegrees(acquired = 3, required = -2))
    }

    /**
     * 연출이 적용되는 캐릭터가 **2종뿐**이라는 것을 고정한다(#50 착수 전에 초안이 놓쳤던 사실).
     * 나중에 조각 경로가 늘거나 줄면 이 테스트가 먼저 말해 준다.
     */
    @Test
    fun onlyTheAdShardCharactersHaveAPartialRevealPath() {
        val shardPaths = BotCharacterCatalog.all
            .mapNotNull { it.unlockSource as? BotUnlockSource.AdShards }
            .map { it.required }
        assertEquals(listOf(5, 10), shardPaths)
    }
}
