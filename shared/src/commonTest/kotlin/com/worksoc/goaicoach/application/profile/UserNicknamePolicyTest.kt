package com.worksoc.goaicoach.application.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [UserNicknamePolicy]의 고정표(refactor backlog #85).
 *
 * 규칙을 어댑터에서 올리면서 동작을 바꾸지 않았음을 지키는 표다 — 기대값은 옮기기 전 코드
 * (저장: `raw.trim().take(12).takeIf { it.isNotBlank() }`, 입력 중: `next.take(12)`)가 내던 값을
 * **글자 그대로** 적었다. 어댑터 쪽 같은 표는 `UserProfileStoreTest`(app-android)에 있다.
 */
class UserNicknamePolicyTest {

    /** 상한은 12다 — 한 줄에 들어가야 한다는 제품 규칙이다(함정 21). 바꾸면 마이 페이지를 네 언어로 다시 볼 것. */
    @Test
    fun theLimitIsTwelve() {
        assertEquals(12, UserNicknamePolicy.MaxLength)
    }

    @Test
    fun savingTrimsFirstThenCaps() {
        val table = listOf(
            "가짓" to "가짓",
            "  가짓  " to "가짓",
            "\t바둑이\n" to "바둑이",
            "가".repeat(40) to "가".repeat(12),
            "123456789012" to "123456789012",
            "1234567890123" to "123456789012",
            // 걷기가 먼저다 — 앞 공백은 상한을 먹지 않는다.
            "  abcdefghijklmnop  " to "abcdefghijkl",
            // 자른 뒤에는 다시 걷지 않는다.
            "a" + " ".repeat(20) + "b" to "a" + " ".repeat(11),
            // ⚠️ UTF-16 코드 단위로 센다(의도라기보다 지금 동작 — [UserNicknamePolicy.MaxLength]).
            "🐼".repeat(7) to "🐼".repeat(6),
            "a" + "🐼".repeat(6) to "a" + "🐼".repeat(5) + "\uD83D",
        )
        table.forEach { (input, expected) ->
            assertEquals(expected, UserNicknamePolicy.sanitize(input), "입력 \"$input\"")
        }
    }

    /** 빈 이름은 `null` — 저장소가 그것을 지우라는 뜻으로 받는다. */
    @Test
    fun aBlankNameMeansRemove() {
        listOf("", " ", "   ", "\t\n").forEach { blank ->
            assertNull(UserNicknamePolicy.sanitize(blank), "입력 \"$blank\"")
        }
    }

    /**
     * 입력 중에는 **걷지 않고 자르기만** 한다 — 걷으면 끝에 친 띄어쓰기가 곧바로 사라져 이름 가운데
     * 띄어쓰기를 칠 수 없다. 상한은 저장과 같다.
     */
    @Test
    fun typingOnlyCapsAndKeepsSpaces() {
        val table = listOf(
            "" to "",
            "바둑 " to "바둑 ",
            "   " to "   ",
            "가".repeat(40) to "가".repeat(12),
            "1234567890123" to "123456789012",
            "  abcdefghijklmnop" to "  abcdefghij",
            "a" + "🐼".repeat(6) to "a" + "🐼".repeat(5) + "\uD83D",
        )
        table.forEach { (input, expected) ->
            assertEquals(expected, UserNicknamePolicy.capInput(input), "입력 \"$input\"")
        }
    }
}
