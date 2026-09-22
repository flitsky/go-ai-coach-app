package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.persistence.UserProfileStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 마이 페이지의 「나」 줄이 지켜야 할 것(백로그 #165).
 *
 * ⚠️ **전부 어겨도 컴파일은 되고 화면도 뜬다** — 그래서 소스 계약과 순수 함수 둘 다로 든다.
 */
class UserProfileContractTest {

    private fun source(path: String): String =
        File(path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n") { it.substringBefore("//") }

    private val store = source("src/main/java/com/worksoc/goaicoach/persistence/UserProfileStore.kt")
    private val myPage = source("src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt")

    /**
     * ⚠️ **`UserPreferencesSnapshot`에 넣으면 조용히 사라진다.** 그 저장소는 저장할 때마다
     * 스냅샷을 통째로 다시 만들어서, 배선을 한 군데라도 빠뜨리면 그 필드가 **초기값으로
     * 되돌아간다**(함정 2). 이 저장소가 실제로 그 사고를 낸 자리다.
     */
    @Test
    fun theNicknameLivesInItsOwnStore() {
        assertFalse(
            "닉네임이 `UserPreferences`에 얹혔다 — 자동저장이 조용히 지운다(함정 2).",
            store.contains("UserPreferencesSnapshot") || store.contains("UserPreferencesStore("),
        )
        assertTrue(
            "prefs 이름이 `go_ai_coach_`로 시작하지 않는다 — 개발자 모드 초기화가 이 값을 건너뛴다.",
            store.contains("\"go_ai_coach_user_profile\""),
        )
    }

    /**
     * ⚠️ **저장 뒤 되읽지 않으면 화면이 옛 이름을 그대로 보여 준다**(함정 13 — 지급 경로마다
     * 되읽기 콜백을 두라는 그 규칙이 여기에도 걸린다).
     */
    @Test
    fun theScreenReReadsTheStoreAfterSaving() {
        assertTrue(
            "저장만 하고 되읽지 않는다 — 방금 지은 이름이 화면에 안 뜬다.",
            myPage.contains("profileStore.saveNickname(") && myPage.contains("nickname = profileStore.nickname()"),
        )
    }

    /**
     * ⚠️ **팝업은 별도 파일이어야 한다.** 화면 안에 인라인으로 두면 `FirstDolGuideContractTest`의
     * 자동 그물 밖이 되어, 팝업이 떠 있는 동안 첫돌이 가이드가 뒤에 깔린 채 **조용히 소진**된다.
     */
    @Test
    fun theNicknameDialogIsItsOwnFile() {
        assertTrue(
            "`UserNicknameDialog.kt`가 없다 — 팝업을 화면 안에 인라인으로 두면 가이드 그물 밖이다.",
            File("src/main/java/com/worksoc/goaicoach/ui/UserNicknameDialog.kt").exists(),
        )
        assertFalse(
            "마이 페이지가 `AlertDialog`를 직접 그린다 — 팝업은 별도 파일로 낼 것.",
            myPage.contains("AlertDialog("),
        )
    }

    /**
     * ⚠️ **맨 위 자리는 구독 카드의 것이다**(2026-09-18 사용자 확정, #159). 해지 경로가 찾기
     * 어려우면 그 자체가 정책 문제라는 사유가 붙어 있다 — 「나」 줄이 그 위로 올라가면 그
     * 결정을 말없이 뒤집게 된다.
     */
    @Test
    fun theProfileRowDoesNotTakeTheTopSpotFromTheSubscriptionCard() {
        val premium = myPage.indexOf("PremiumSubscriptionCard()")
        val profile = myPage.indexOf("UserProfileRow(")
        assertTrue("두 조각을 다 찾지 못했다 — 그물이 헛돌고 있다.", premium >= 0 && profile >= 0)
        assertTrue(
            "「나」 줄이 구독 카드보다 위에 있다 — 2026-09-18 결정을 뒤집었다(#159).",
            premium < profile,
        )
    }

    /** 자르는 규칙은 하나여야 한다 — 화면이 보여 주는 길이와 저장이 남기는 길이가 다르면 안 된다. */
    @Test
    fun theNicknameIsTrimmedAndCapped() {
        assertEquals("가짓", UserProfileStore.sanitizeNickname("  가짓  "))
        assertEquals(
            UserProfileStore.NicknameMaxLength,
            UserProfileStore.sanitizeNickname("가".repeat(40))?.length,
        )
        assertEquals("빈 이름은 지우는 것으로 친다.", null, UserProfileStore.sanitizeNickname("   "))
    }

    /**
     * ⚠️ **이모지를 `first()`로 자르면 반 토막이 난다**(surrogate pair) — 원 안에 두부(￭)가
     * 그려진다. 닉네임에 이모지를 넣는 사람은 반드시 있다.
     */
    @Test
    fun theAvatarInitialSurvivesSurrogatePairs() {
        assertEquals("가", avatarInitialOf("가나다"))
        assertEquals("R", avatarInitialOf("ryan"))
        assertEquals("🐼", avatarInitialOf("🐼 판다"))
        assertEquals("이름이 없으면 물음표가 아니라 점 하나다 — 물음표는 오류처럼 읽힌다.", "·", avatarInitialOf(null))
        assertEquals("·", avatarInitialOf("   "))
    }

    /** ⚠️ 색이 실행마다 바뀌면 "내 아바타"라는 느낌이 무너진다 — 난수를 쓰면 안 된다. */
    @Test
    fun theAvatarColorIsStableForTheSameName() {
        assertEquals(avatarColorOf("바둑이"), avatarColorOf("바둑이"))
        assertEquals(avatarColorOf(null), avatarColorOf(""))
        assertNotEquals(
            "이름이 있는 아바타가 「이름 없음」과 같은 회색이다 — 지었는지가 색으로 안 보인다.",
            avatarColorOf(null),
            avatarColorOf("바둑이"),
        )
    }
}
