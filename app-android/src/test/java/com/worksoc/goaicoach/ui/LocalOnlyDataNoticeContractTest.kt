package com.worksoc.goaicoach.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **"앱을 지우면 사라진다"는 말이 참인 이유를 지키는 그물**(백로그 #129, 함정 37번).
 *
 * ## ⚠️ 이 계약이 지키는 것은 코드가 아니라 **문구와 방침**이다
 *
 * 매니페스트는 `android:allowBackup="true"`이고 **제외 규칙이 하나도 없다.** 그러면 안드로이드
 * 자동 백업이 `shared_prefs`를 Google 계정으로 백업해 **재설치 때 되살린다** — 그것이 기본 동작이다.
 *
 * 그런데 지금은 백업이 **통째로 실패한다**: `filesDir`의 KataGo 모델이 93MB인데 자동 백업 쿼터는
 * 25MB다. 2026-09-05에 처음 실측되고(백로그 #93 → #100, 커밋 `0ba78021`) 2026-09-08에 다시 확인했다
 * (`adb shell bmgr backupnow` → `Package … with result: Size quota exceeded`).
 *
 * ⚠️ **즉 이 앱의 소실 고지는 설계가 아니라 파일 크기 덕분에 참이다.** 모델을 `noBackupFilesDir`로
 * 옮기면(재시딩 가능한 자산에는 그쪽이 올바른 자리라 언젠가 후보로 올라온다) 백업이 되살아나고,
 * 그 순간 **아무 테스트도 빨개지지 않은 채** 두 가지가 거짓이 된다:
 * · 마이페이지의 소실 고지 — 다만 #129가 주어를 *"앱이 되돌려 드릴 방법이 없어요"* 로 좁혀
 *   **앱 문구는 그래도 참으로 남는다**(그 완충이 의도된 것이다).
 * · ⚠️ **개인정보처리방침은 무조건 단정한다** — *"앱을 삭제하는 즉시 함께 파기됩니다"*.
 *   형제 저장소 `rezen.dev`의 `src/pages/go-ai-coach/privacy/index.astro`이고 **이 저장소의
 *   테스트가 닿지 못한다.** 그래서 이 그물이 지키는 진짜 대상은 그 문장이다.
 *
 * ## ⚠️ #100이 *"트립와이어는 남긴다"* 고 적었는데 실제로 남지 않았다
 *
 * 남은 것은 봉인 문서의 한 줄(*"트립와이어만 남김"*)뿐이고 **감시 대상이 무엇인지는 git 히스토리에만**
 * 있었다. 세대 인수 때 활성 백로그의 「함정」 절로 이월되지도 않았다. 문서에 적은 트립와이어는
 * 그렇게 유실된다 — 그래서 #129가 그것을 **테스트로** 옮겼다.
 */
class LocalOnlyDataNoticeContractTest {

    private val repoRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun source(path: String): String = File(repoRoot, path).readText()

    /**
     * 백업이 되살아나는 **두 갈래**를 함께 본다. 둘 중 하나라도 열리면 방침의 단정이 거짓이 될 수 있다.
     *
     * ⓐ 백업 대상에서 앱 데이터가 빠지는 길 — `allowBackup="false"`나 제외 규칙이 생기면
     *    **이 그물은 더 이상 필요 없다**(그때는 설계로 참이 된다). 그래서 그 경우는 통과시킨다.
     * ⓑ 쿼터를 넘기던 무게가 사라지는 길 — 모델이 `noBackupFilesDir`로 가면 백업이 성공하기
     *    시작한다. **이것이 이 단언이 실제로 잡는 것이다.**
     */
    @Test
    fun theDataLossNoticeKeepsItsGroundOrTheChangeIsMadeDeliberately() {
        val manifest = source("app-android/src/main/AndroidManifest.xml")
        val backupIsDeclaredOn = manifest.contains("""android:allowBackup="true"""")
        val hasExclusionRules = manifest.contains("android:dataExtractionRules") ||
            manifest.contains("android:fullBackupContent")

        if (!backupIsDeclaredOn || hasExclusionRules) return

        val bootstrap = source("app-android/src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt")
        assertTrue(
            "⚠️ `allowBackup=\"true\"`인데 제외 규칙이 없고, 엔진 자산이 `noBackupFilesDir`로 옮겨졌다.\n" +
                "그러면 자동 백업이 쿼터(25MB)를 넘기지 않게 되어 **되살아나고**, 재설치가 " +
                "`shared_prefs`를 복원한다.\n" +
                "그 순간 개인정보처리방침의 *\"앱을 삭제하는 즉시 함께 파기됩니다\"* 가 거짓이 된다 " +
                "(형제 저장소 `rezen.dev`, 이 저장소 테스트가 닿지 못하는 문장이다).\n" +
                "→ 옮기는 것이 옳은 판단일 수 있다. 그렇다면 **함께** 하라: " +
                "① 방침 문장을 조건형으로 고치고 ② `allowBackup`을 끄거나 제외 규칙을 넣어 " +
                "설계로 참이 되게 하고 ③ 백로그 함정 37번과 이 테스트를 갱신할 것.",
            !bootstrap.contains("noBackupFilesDir"),
        )
    }

    /**
     * ⚠️ **고지 세 조각은 함께 있어야 한다.** 유료 조각만 남고 본문이 사라지거나 그 반대가 되면
     * 카드가 말을 뒤집는다 — 본문이 *"되돌릴 방법이 없다"* 고 하고 유료 조각이 *"다시 열어 준다"*
     * 고 하는 구조라, 둘 중 하나만 그려지는 순간 안내가 한쪽으로 기운다.
     */
    @Test
    fun allThreePiecesOfTheNoticeAreRenderedTogether() {
        val myPage = source("app-android/src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt")
        listOf(
            "localOnlyDataNoticeTitle",
            "localOnlyDataNoticeBody",
            "localOnlyDataNoticePaidRestoreLine",
        ).forEach { field ->
            assertTrue(
                "`MyPageScreen.kt`가 `$field`를 그리지 않는다 — 고지 세 조각은 함께 있어야 한다(#129).",
                myPage.contains(field),
            )
        }
    }

    /**
     * ⚠️ **유료 조각의 게이트는 `isBotCharacterPurchaseEnabled` 하나여야 한다.**
     *
     * `isPurchaseEnabled`를 OR로 묶으면 #26(월 구독)이 그것을 켜는 날 문장이 함께 노출되는데,
     * 구독 복원은 `PremiumPurchaseGlue.kt`가 `AndroidBillingClient`의 기본값 `INAPP`으로 조회하고
     * **`SUBS`를 넘기는 호출부가 저장소에 없다** — 구독자에게는 조용한 미소유로 끝나
     * *"앱이 다시 열어 준다"* 가 아무 일도 하지 않는다. #87(앱이 안 하는 것을 문구가 약속했다)의 재판이다.
     *
     * 되돌리기 쉬운 만큼(OR 한 글자다) 그물을 단다.
     */
    @Test
    fun thePaidLineIsGatedOnTheCharacterPurchaseFlagAlone() {
        val myPage = source("app-android/src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n") { it.substringBefore("//") }

        val gate = myPage.lines()
            .indexOfFirst { it.contains("localOnlyDataNoticePaidRestoreLine") }
            .let { at -> myPage.lines().take(at).last { it.contains("if (") } }

        assertTrue(
            "유료 조각의 게이트가 `isBotCharacterPurchaseEnabled`가 아니다: \"${gate.trim()}\" (#129)",
            gate.contains("isBotCharacterPurchaseEnabled"),
        )
        assertTrue(
            "유료 조각의 게이트가 `isPurchaseEnabled`를 함께 본다 — 구독 복원은 `INAPP`으로 조회해 " +
                "조용히 실패하므로, 그 플래그가 켜지는 날 이 문장은 거짓이 된다(#129). " +
                "구독용 문장은 `SUBS` 전달이 고쳐진 뒤 별도 필드로 붙일 것: \"${gate.trim()}\"",
            !gate.contains("isPurchaseEnabled"),
        )
    }
}
