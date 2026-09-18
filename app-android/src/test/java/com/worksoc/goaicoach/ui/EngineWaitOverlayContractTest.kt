package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **엔진 대기 오버레이가 지금 무엇을 기다리는지 맞게 말하는가**(백로그 #176).
 *
 * ## 왜 계약이 필요한가
 * 2026-09-18 사용자 제보: 종료 화면에서 `대국 시작`을 눌렀는데 *"계가를 위해 준비 중입니다"* 가
 * 떴다. 원인은 `hasConsecutivePasses()`가 **양통과로 끝난 판에서 종료 뒤에도 참으로 남는다**는
 * 것이었다 — 조건이 *"계가 중"* 을 뜻한다고 읽히지만 실제로는 *"이 판이 양통과로 끝났(었)다"* 였다.
 *
 * ⚠️ **틀린 안내는 조용하다** — 크래시도, 테스트 실패도 없고, 사용자가 말해 줘야 알 수 있다.
 * 그래서 되돌아가기 쉬운 이 한 조건을 그물로 잡는다.
 */
class EngineWaitOverlayContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val board: String = File(
        repoRoot,
        "app-android/src/main/java/com/worksoc/goaicoach/ui/GoBoard.kt",
    ).readText()

    /**
     * ⚠️ 이 조건을 지우면 **새 대국을 준비하는 동안 "계가 중"이라고 말한다.**
     * 위의 `Preparing` 오버레이("새 대국을 위해 준비 중입니다")와 둘이 동시에 떠서 이쪽이 덮는다.
     */
    @Test
    fun theScoringOverlayNeverShowsAfterTheGameHasAlreadyEnded() {
        assertTrue(
            "계가 대기 오버레이가 `!isGameEnded`를 보지 않는다 — `hasConsecutivePasses()`는 종료 뒤에도 " +
                "참이라, 종료 화면에서 `대국 시작`을 누르면 \"계가를 위해 준비 중입니다\"가 뜬다(#176).",
            board.contains("gameState.hasConsecutivePasses() && isEngineBusy && !isGameEnded"),
        )
    }

    /**
     * 두 오버레이가 **서로 다른 문구**를 써야 한다 — 같은 문구면 구분할 이유가 없어지고,
     * 다음 사람이 하나로 합쳐 버린다.
     */
    @Test
    fun theTwoWaitOverlaysSayDifferentThings() {
        assertTrue("새 대국 준비 문구가 사라졌다(#176).", board.contains("strings.enginePreparingTitle"))
        assertTrue("계가 준비 문구가 사라졌다(#176).", board.contains("strings.scoringPreparingTitle"))
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            assertTrue(
                "${language.name}에서 두 대기 문구가 같아졌다 — 구분이 사라지면 #176이 되살아난다.",
                strings.enginePreparingTitle != strings.scoringPreparingTitle,
            )
        }
    }
}
