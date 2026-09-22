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
     * ⚠️ **「나」 줄은 지금 꺼져 있다**(2026-09-22 사용자 결정) — 기능이 덜 돼서가 아니라
     * **쓸 데가 없어서**다(#165가 노출을 마이 페이지 안으로만 한정한 결과, U-53).
     *
     * 그물이 지키는 것은 *꺼져 있다*가 아니라 **「게이트를 거쳐서만 그려진다」**이다 —
     * 게이트를 지우고 줄을 되살리는 것은 **결정을 뒤집는 일**이라 눈에 띄어야 한다.
     * 켜는 것 자체는 상수 한 줄이면 되고, 그때 이 테스트는 그대로 통과한다.
     */
    @Test
    fun theProfileRowIsDrawnOnlyThroughItsGate() {
        assertTrue(
            "`ShowUserProfileRow` 게이트가 사라졌다 — 「나」 줄이 결정 없이 다시 노출된다(U-53).",
            myPage.contains("private const val ShowUserProfileRow"),
        )
        assertTrue(
            "「나」 줄이 게이트 밖에서 그려진다.",
            myPage.contains("if (ShowUserProfileRow) {"),
        )
        assertTrue(
            "닉네임 팝업이 게이트 밖에서 뜰 수 있다 — 줄이 없는데 팝업만 뜨는 길이 남는다.",
            myPage.contains("if (ShowUserProfileRow && isEditingNickname)"),
        )
    }

    /**
     * ⚠️ **구독 카드가 「나」 줄 **바로 아래**여야 한다**(2026-09-22 사용자 지시로 순서가 뒤집혔다).
     *
     * 2026-09-18(#159)이 구독 카드를 맨 위에 둔 사유 — *"해지 경로가 찾기 어려우면 그 자체가
     * 정책 문제"* — 는 **지금도 유효하다.** 지키려던 것이 *맨 위*가 아니라 **「스크롤 없이
     * 닿는다」** 였을 뿐이라 한 줄짜리 프로필이 위에 와도 성립한다. 그래서 그물은 자리를
     * **둘째까지**로 묶는다 — 셋째로 밀리는 순간 그 전제가 깨진다.
     */
    @Test
    fun theSubscriptionCardStaysWithinReachAtTheTop() {
        val premium = myPage.indexOf("PremiumSubscriptionCard()")
        val profile = myPage.indexOf("UserProfileRow(")
        val attendance = myPage.indexOf("AttendanceBoardSection(")
        assertTrue("세 조각을 다 찾지 못했다 — 그물이 헛돌고 있다.", premium >= 0 && profile >= 0 && attendance >= 0)
        assertTrue(
            "「나」 줄이 맨 위가 아니다 — 2026-09-22 사용자 지시를 되돌렸다.",
            profile < premium,
        )
        assertTrue(
            "구독 카드가 출석 절 아래로 밀렸다 — 해지 경로가 스크롤 밖으로 나간다(#159).",
            premium < attendance,
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
