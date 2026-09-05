package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 개발자 섹션을 `SettingsScreen`에서 떼어낸 뒤의 계약(백로그 #102).
 *
 * ⚠️ **줄 수만 재면 되돌아오는 것을 못 잡는다.** 예산(`LayeringContractTest`)은 *"얼마나 큰가"*
 * 를 재고, 여기는 *"그것이 어디에 사는가"* 를 잰다. 둘 다 필요하다.
 */
class DeveloperTestSectionContractTest {

    private fun codeOnly(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val settings = codeOnly("src/main/java/com/worksoc/goaicoach/ui/SettingsScreen.kt")
    private val section = codeOnly("src/main/java/com/worksoc/goaicoach/ui/DeveloperTestSection.kt")

    @Test
    fun theSectionReproducesTheSpacingItsParentUsedToGiveIt() {
        // ⚠️ 원래 이 자식들은 설정 화면 `Column(spacedBy(12.dp))`의 **직계**였다. 하나로 묶는
        // 순간 그 간격이 사라지므로 안쪽에서 같은 값을 다시 준다. **이 줄이 없으면 섹션 전체가
        // 붙어 버린다** — 컴파일도 테스트도 통과하는 채로 화면만 망가지는 종류다.
        assertTrue(
            "개발자 섹션이 부모가 주던 간격을 재현하지 않는다 — 항목들이 붙어서 그려진다(#102).",
            "verticalArrangement = Arrangement.spacedBy(12.dp)" in section,
        )
    }

    @Test
    fun theSectionsOwnStateDoesNotLeakBackIntoTheSettingsShell() {
        // #102의 목적은 분리 자체가 아니라 *"조립만 하는 셸은 상태를 소유하지 않는다"* 를
        // 역할 단위로 세우는 것이다. 아래가 하나라도 설정 화면에 다시 나타나면 그 원칙이 깨진다.
        val leaked = listOf(
            "buildInfoTapCount",
            "LocalConsumableUiState",
            "LocalBotCharacterUiState",
            "LocalPremiumUiState",
            "AttendanceStore",
        ).filter { marker -> marker in settings }

        assertTrue(
            "개발자 섹션이 쓰던 상태가 설정 화면으로 되돌아왔다(#102):\n" +
                leaked.joinToString("\n") +
                "\n⚠️ 새 개발자 컨트롤은 DeveloperTestSection.kt로 갈 것.",
            leaked.isEmpty(),
        )
    }

    @Test
    fun theShellStillOwnsTurningDeveloperModeOnAndOff() {
        // ⚠️ 반대 방향의 실수도 막는다 — 켜고 끄는 자리까지 섹션으로 넘기면 **꺼져 있을 때
        // 되돌릴 길이 사라진다**(섹션 자체가 안 그려지므로). 진입은 버전 탭, 해제 팝업은 셸이다.
        assertTrue(
            "개발자 모드 진입/해제가 설정 화면에서 사라졌다 — 꺼진 상태에서 되돌릴 길이 없어진다.",
            "showDeveloperModeOptIn" in settings && "showDeveloperModeOptOut" in settings,
        )
    }
}
