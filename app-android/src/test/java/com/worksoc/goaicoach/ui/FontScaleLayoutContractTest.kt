package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **고정 `dp` 높이가 폰트 배율을 따라가지 않아 글자가 세로로 잘린다** — 이 저장소가 네 번 밟은
 * 함정의 소스 계약(백로그 #64).
 *
 * ⚠️ **일반 단위 테스트로는 절대 안 잡힌다.** 잘린 글자는 측정 결과이지 값이 아니라서,
 * `UiStrings`도 도메인도 전부 정상인 채로 화면에서만 반토막이 난다. 실제로 #64의 셋은 전부
 * **사용자가 눈으로** 찾아냈다(2026-09-01). 그래서 값 대신 **처방을 소스에 못박는다.**
 *
 * ⚠️ 주석은 걷어내고 본다([codeOnly]). 이 파일이 지키는 처방들은 그것을 설명하는 KDoc에도
 * 그대로 적혀 있어서, 걷어내지 않으면 **코드를 되돌려도 주석만 보고 통과한다.**
 *
 * 되돌리고 싶어지면 먼저 볼 것: 배율 1.3배·2.0배에서 마이 페이지 출석 현황과 상대 고르기 픽커.
 */
class FontScaleLayoutContractTest {

    private val attendanceBoard = codeOnly(sourceOf("AttendanceBoardView.kt"))
    private val settingsGrid = codeOnly(sourceOf("CompactScoringAndBoardSettingsPanel.kt"))
    private val botPicker = codeOnly(sourceOf("BotCharacterUiState.kt"))

    /**
     * #64 ⓐ — 도장판 칸. 고정 높이일 때 배율 1.3배에서 좁은 칸의 개수 줄(`30`·`3`)과 넓은 칸의
     * 셋째 보상 줄(`▶| 3`)이 아래 절반부터 잘렸다.
     */
    @Test
    fun theStampCellHeightIsAFloorRatherThanAFixedValue() {
        assertTrue(
            "칸 높이가 `heightIn(min = …)`이 아니다 — 고정 높이로 돌아가면 배율 1.3배에서 개수 줄이 잘린다(#64 ⓐ).",
            attendanceBoard.contains("heightIn(min = if (compact) CompactCellMinHeight else WideCellMinHeight)"),
        )
        assertFalse(
            "칸이 다시 고정 높이(`height(...)`)를 쓴다 — #64 ⓐ가 그대로 돌아온다.",
            attendanceBoard.contains(".height(if (compact) CompactCellMinHeight else WideCellMinHeight)"),
        )
    }

    /**
     * #64 ⓐ의 나머지 절반. 바닥값만 두고 행을 묶지 않으면 글리프 칸(1~4일차)만 자라고 얼굴 칸
     * (5·6일차)은 바닥값에 머물러 **한 행의 칸 높이가 들쭉날쭉해진다** — #57이 빈 줄까지 남겨
     * 가며 맞춰 둔 정렬이 거기서 무너진다.
     */
    @Test
    fun theStampRowTiesEveryCellInTheRowToTheSameHeight() {
        assertTrue(
            "행이 `IntrinsicSize.Min`으로 묶여 있지 않다 — 칸마다 높이가 갈린다(#64 ⓐ).",
            attendanceBoard.contains("height(IntrinsicSize.Min)"),
        )
        assertTrue(
            "칸이 행 높이를 채우지 않는다 — 가장 높은 칸에만 맞고 나머지는 짧아진다(#64 ⓐ).",
            attendanceBoard.contains("fillMaxHeight()"),
        )
    }

    /**
     * #64와 함께 고친 넷째 사례. 인장의 원은 고정 `dp`인데 안의 `✓`만 배율을 따라 커져,
     * 배율 2.0배에서 체크가 통째로 잘리고 **빈 동그라미만** 남았다.
     *
     * ⚠️ **원을 키우는 것으로 고치지 말 것** — 한 번 그렇게 고쳤다가 32dp 원이 20dp 글리프를
     * 덮어 1·3·5·6일차의 **보상 그림이 통째로 가려졌다**(2026-09-01 실기 확인). 표시 쪽을
     * `dp`에 묶는 것이 답이다.
     */
    @Test
    fun theStampMarkIsPinnedToDpSoItNeverOutgrowsItsCircle() {
        assertTrue(
            "인장 표시가 `dp`에 묶여 있지 않다 — 배율 2.0배에서 체크가 원 밖으로 잘린다(#64).",
            attendanceBoard.contains("(if (compact) CompactMarkSize else WideMarkSize).toSp()"),
        )
        assertFalse(
            "인장의 원이 배율을 따라 커진다 — 보상 그림을 덮는다(#57 KDoc이 경고한 사고).",
            attendanceBoard.contains("WideSealSize) * scale"),
        )
    }

    /**
     * #64 ⓑ — 캐릭터 카드. `232.dp` 고정이라 배율이 오르면 이름·설명이 커지면서 **맨 아래 해금
     * 힌트에 한 줄만 남아** 말줄임됐다. 하필 잘린 것이 *"무엇을 하면 열리는가"* 였다.
     *
     * ⚠️ **도장판처럼 `IntrinsicSize.Min`으로 고칠 수 없다** — 캐러셀은 페이지를 한 장씩만 재므로
     * 다른 카드의 높이를 알 방법이 없다. 그래서 배율에서 **계산**한다.
     */
    @Test
    fun theCharacterCardHeightFollowsTheFontScale() {
        assertFalse(
            "카드가 다시 고정 높이를 쓴다 — 배율 1.3배에서 해금 힌트가 한 줄로 눌린다(#64 ⓑ).",
            botPicker.contains(".height(232.dp)"),
        )
        assertTrue(
            "카드 높이가 배율에서 계산되지 않는다(#64 ⓑ).",
            botPicker.contains("height(botCharacterCardHeight())"),
        )
        assertTrue(
            "카드 높이가 `fontScale`을 안 본다 — 계산이 배율과 무관해졌다(#64 ⓑ).",
            botPicker.contains("LocalDensity.current.fontScale.coerceAtLeast(1f)"),
        )
    }

    /**
     * #64 ⓑ의 나머지 절반이자 **더 중요한 쪽**. [Column]은 가중치 없는 자식을 먼저 재므로,
     * 소개에만 `weight`가 걸려 있어야 아바타·이름·힌트가 제 높이를 온전히 가져간다.
     * 가중치가 사라지거나 힌트로 옮겨 가면 배율이 오를 때 **힌트가 다시 눌린다.**
     */
    @Test
    fun onlyTheDescriptionYieldsSpaceSoTheUnlockHintIsNeverSqueezed() {
        assertTrue(
            "소개 줄에 `weight`가 없다 — 자리가 모자라면 힌트가 대신 눌린다(#64 ⓑ).",
            botPicker.contains("Modifier.weight(1f, fill = false)"),
        )
        val weights = Regex("""Modifier\.weight\(""").findAll(botPicker).count()
        assertTrue(
            "카드 안 가중치가 하나가 아니다(${weights}개) — 소개 하나만 양보해야 힌트가 안전하다(#64 ⓑ).",
            weights == 1,
        )
    }

    /**
     * 설정·로비의 2×2 드롭다운 격자(백로그 #107).
     *
     * ⚠️ **이 화면은 2026-09-05에 관객이 바뀌었다.** 1.3배는 예전부터 있었지만 개발자 도구 뒤에
     * 있어서 **개발자만** 봤다. #106이 글꼴 크기를 정식 설정으로 승격하면서 일반 사용자가 그
     * 배율에 닿게 됐고, 실기에서 `바둑판 (13…` 으로 **값이 잘렸다.**
     */
    @Test
    fun theSettingsGridWrapsInsteadOfCuttingTheValueOff() {
        assertTrue(
            "격자 칸이 한 줄로 고정돼 있다 — 큰 글꼴에서 값이 잘린다(#107).",
            settingsGrid.contains("maxLines = 2"),
        )
        assertTrue(
            "한 줄 고정이 남아 있다 — 어느 칸인가는 여전히 잘린다(#107).",
            !settingsGrid.contains("maxLines = 1"),
        )
    }

    @Test
    fun theSettingsGridRowTiesBothCellsToTheSameHeight() {
        // ⚠️ 접히는 순간 그 칸만 높아진다 — 짝이 어긋나 보인다. 출석판과 같은 처방이다(#64 ⓐ).
        assertTrue(
            "격자 행이 `IntrinsicSize.Min`으로 묶여 있지 않다 — 접힌 칸만 커진다(#107).",
            settingsGrid.contains("height(IntrinsicSize.Min)"),
        )
        assertTrue(
            "격자 칸이 행 높이를 채우지 않는다 — 배경이 짝짝이가 된다(#107).",
            settingsGrid.contains("fillMaxHeight()"),
        )
    }

    private fun sourceOf(fileName: String): String =
        File("src/main/java/com/worksoc/goaicoach/ui/$fileName").readText()

    /**
     * 주석을 걷어낸 코드만 남긴다.
     *
     * ⚠️ **여러 줄 KDoc을 반드시 지워야 한다.** #63에서 같은 종류의 계약 테스트가 **거짓 통과**를
     * 한 적이 있다 — 줄 단위로만 걷어내던 헬퍼가 여러 줄 주석을 못 지워, 코드를 되돌렸는데도
     * 주석에 남은 이름을 코드로 오인해 통과했다.
     */
    private fun codeOnly(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}
