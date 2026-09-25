package com.worksoc.goaicoach.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [UserProfileStore]가 **무엇을 기기에 남기는가**의 고정표(refactor backlog #85).
 *
 * 닉네임 규칙(앞뒤 공백 걷기·12자 상한·빈 값은 지우기)을 어댑터에서 `shared`로 올릴 때, 옮기기
 * **전** 코드에서 이 표를 먼저 초록으로 돌리고 옮긴 뒤에도 **한 글자도 고치지 않은 채** 초록인지
 * 본다 — 같은 입력이 같은 저장값을 내는지가 "동작 불변"의 뜻이다. 그래서 기대값은 규칙 함수를
 * 불러 만들지 않고 **글자 그대로** 적는다(규칙을 부르면 규칙이 바뀌어도 표가 따라 바뀐다).
 *
 * ⚠️ 저장 키 `"nickname"`도 글자 그대로 적는다 — 키가 바뀌면 이미 저장된 이름이 업데이트 한 번에
 * 사라진다.
 */
class UserProfileStoreTest {

    private val prefs = InMemorySharedPreferences()
    private val store = UserProfileStore(prefs)

    @Test
    fun theSameInputLeavesTheSameStoredValue() {
        val table = listOf(
            "가짓" to "가짓",
            "  가짓  " to "가짓",
            "\t바둑이\n" to "바둑이",
            // 12자 상한 — 한글도 1로 센다. 정확히 12자는 그대로, 13자부터 자른다.
            "가".repeat(40) to "가".repeat(12),
            "123456789012" to "123456789012",
            "1234567890123" to "123456789012",
            // 걷기가 자르기보다 **먼저**다 — 앞 공백은 상한을 먹지 않는다.
            "  abcdefghijklmnop  " to "abcdefghijkl",
            // 자른 **뒤**에는 다시 걷지 않는다 — 12번째 글자까지 안쪽 공백이면 끝 공백이 남는다.
            "a" + " ".repeat(20) + "b" to "a" + " ".repeat(11),
            // ⚠️ 상한은 UTF-16 코드 단위로 센다 — 이모지 하나가 2다. 의도라기보다 지금 동작이고,
            //   12번째 단위가 서로게이트 쌍의 앞 반쪽이면 반 토막이 저장된다(바꾸는 것은 별도 일감).
            "🐼".repeat(7) to "🐼".repeat(6),
            "a" + "🐼".repeat(6) to "a" + "🐼".repeat(5) + "\uD83D",
        )
        table.forEach { (input, expected) ->
            store.saveNickname(input)
            assertEquals("저장값 — 입력 \"$input\"", expected, prefs.rawString("nickname"))
            assertEquals("되읽기 — 입력 \"$input\"", expected, store.nickname())
        }
    }

    /** 빈 이름은 빈 문자열로 남기지 않고 **지운다** — 원 안에 아무것도 없는 아바타가 남으면 안 된다. */
    @Test
    fun aBlankInputRemovesTheStoredName() {
        listOf("", "   ", "\t\n").forEach { blank ->
            store.saveNickname("바둑이")
            store.saveNickname(blank)
            assertFalse("빈 입력 \"$blank\"이 키를 남겼다.", prefs.contains("nickname"))
            assertNull(store.nickname())
        }
    }

    /** 다른 경로로 공백뿐인 값이 들어와 있어도 읽기는 「이름 없음」으로 접는다. */
    @Test
    fun aBlankStoredValueReadsAsNoName() {
        val stale = UserProfileStore(InMemorySharedPreferences(mapOf("nickname" to "   ")))
        assertNull(stale.nickname())
    }
}
