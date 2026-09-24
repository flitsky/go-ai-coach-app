package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **자동 백업 복원이 참인 이유를 지키는 그물**(백로그 #129 → **#186에서 방향이 뒤집혔다**, 함정 37).
 *
 * ## ⚠️ 2026-09-22에 이 계약의 **방향이 반대가 됐다**
 * 예전에는 *"백업이 되살아나면 방침이 거짓이 된다"* 를 막는 트립와이어였다. 매니페스트는
 * `allowBackup="true"`인데 제외 규칙이 없었고, **백업은 `filesDir`의 KataGo 모델 93MB가 쿼터
 * 25MB를 넘겨 통째로 실패**하고 있었다 — 즉 소실 고지는 설계가 아니라 **파일 크기 덕분에** 참이었다.
 *
 * **이제 그 무게를 덜어냈다.** 제외 규칙 두 벌(`backup_rules.xml` API 30 이하 ·
 * `data_extraction_rules.xml` API 31 이상)이 `files/katago`를 빼서 백업 크기가 **약 2MB**가 됐다.
 * 실측(2026-09-22, Pixel 7 에뮬레이터): `bmgr backupnow` → `Success`, 앱을 지우고 다시 깐 뒤
 * **닉네임·소모품(29/38/2)·기보 8건이 전부 복원**됐고 엔진 모델만 빠졌다.
 *
 * ## 그래서 지금 지키는 것
 * · 제외 규칙이 **둘 다** 있을 것. `minSdk = 26`이라 하나만 있으면 한쪽 API 대역에서 조용히 무효다.
 * · 그 규칙이 **엔진 모델을 실제로 빼고 있을 것.** 경로가 틀려도 빌드는 통과하고 백업만 조용히
 *   다시 쿼터를 넘긴다 — 그때 사용자에게 보이는 것은 아무것도 없다.
 * · 앱 문구가 소실을 **단정하지 않을 것**(이제 거짓이다), 그러나 **안전하다고도 하지 않을 것**.
 *
 * ## ⚠️ 이 저장소 밖에 있는 것 — 개인정보처리방침
 * 형제 저장소 `rezen.dev`의 `src/pages/go-ai-coach/privacy/index.astro`가 그 문장을 든다.
 * 2026-09-22에 **자동 백업을 언급하는 조건형으로 고치고 시행일자를 올렸다**(커밋 `d0fe7c9`).
 * ⚠️ **그 문서가 배포된 뒤에 앱을 내보내야 한다** — 순서가 뒤집히면 배포된 방침이 앱의 동작과
 * 어긋나 그 자체로 위반이다(함정 60). 이 테스트는 거기 닿지 못하므로 **그 순서는 사람이 지킨다.**
 */
class LocalOnlyDataNoticeContractTest {

    /**
     * refactor backlog #63: `File(".")` 상향 탐색을 자체로 다시 하지 않는다 — 이 저장소는
     * 워크트리를 여러 개 두고 세션이 나눠 쓰므로, 실행 디렉터리가 속한 트리로 검사 대상이
     * 미끄러질 수 있다(`RepoPaths.root`의 KDoc 참고). `RepoPaths.root`는 Gradle이
     * `-Drepo.root=<rootDir>`로 못박아 주입한 값을 먼저 보고, 없을 때만 이 상향 탐색으로
     * 폴백한다 — 그 주입을 받지 못하던 이 파일만의 재탐색을 없애고 흡수한다.
     */
    private val repoRoot = RepoPaths.root

    private fun source(path: String): String = File(repoRoot, path).readContractSource()

    /**
     * ⚠️ **제외 규칙이 둘 다 있어야 한다 — `minSdk = 26`이다.**
     * `dataExtractionRules`는 **API 31+**에만 걸리고 그 아래는 `fullBackupContent`가 본다.
     * 하나만 두면 **한쪽 API 대역에서 아무것도 달라지지 않는데, 그 사실은 최신 에뮬레이터
     * 실기에서 보이지 않는다.**
     */
    @Test
    fun bothBackupRuleFilesAreDeclaredBecauseMinSdkSpansTheApiChange() {
        val manifest = source("app-android/src/main/AndroidManifest.xml")
        assertTrue(
            "`allowBackup`이 꺼졌다 \u2014 백업 복원(백로그 #186)이 통째로 사라진다. " +
                "끄는 것이 옳은 판단이라면 마이 페이지 문구와 개인정보처리방침을 함께 되돌릴 것.",
            manifest.contains("android:allowBackup=\"true\""),
        )
        assertTrue(
            "`android:fullBackupContent`가 없다 \u2014 API 30 이하에서 94MB 모델이 그대로 백업에 들어가 " +
                "쿼터(25MB)를 넘기고, 백업이 조용히 통째로 실패한다.",
            manifest.contains("android:fullBackupContent"),
        )
        assertTrue(
            "`android:dataExtractionRules`가 없다 \u2014 API 31 이상에서 같은 일이 일어난다.",
            manifest.contains("android:dataExtractionRules"),
        )
    }

    /**
     * ⚠️ **경로가 틀려도 빌드는 통과하고, 백업만 조용히 다시 쿼터를 넘긴다.**
     * 그때 사용자에게 보이는 것은 아무것도 없다 — 그래서 경로를 문자로 못박는다.
     */
    @Test
    fun theEngineModelIsExcludedFromEveryBackupPath() {
        val legacy = source("app-android/src/main/res/xml/backup_rules.xml")
        val modern = source("app-android/src/main/res/xml/data_extraction_rules.xml")
        val katagoRule = "<exclude domain=\"file\" path=\"katago\" />"

        assertTrue(
            "`backup_rules.xml`이 `files/katago`를 빼지 않는다 \u2014 94MB가 쿼터를 넘긴다.",
            legacy.contains(katagoRule),
        )
        assertEquals(
            "`data_extraction_rules.xml`은 `cloud-backup`과 `device-transfer` **둘 다** 빼야 한다 \u2014 " +
                "하나만 빼면 새 폰으로 직접 옮길 때 94MB가 그대로 따라간다.",
            2,
            Regex(Regex.escape(katagoRule)).findAll(modern).count(),
        )
    }

    /**
     * ⚠️ **문구가 소실을 단정하면 이제 거짓이다.** 재설치·기기 교체에서 실제로 되살아난다
     * (2026-09-22 실측). 반대로 *"안전합니다"* 로 단정하는 것도 거짓이다 — 자동 백업은 24시간
     * 주기이고 Wi-Fi·충전·유휴 조건이 있으며 사용자가 끌 수 있다. **조건을 말해야 한다.**
     */
    @Test
    fun theNoticeNeitherDeniesNorGuaranteesRecovery() {
        val forbidden = mapOf(
            UiLanguage.Korean to listOf("되돌릴 수 없", "되돌려 드릴 방법이 없"),
            UiLanguage.English to listOf("bring it back"),
            UiLanguage.Japanese to listOf("元に戻せません", "元に戻すことはできません"),
            UiLanguage.ChineseSimplified to listOf("无法恢复", "无法为您恢复"),
        )
        val conditionMarker = mapOf(
            UiLanguage.Korean to "백업",
            UiLanguage.English to "backup",
            UiLanguage.Japanese to "バックアップ",
            UiLanguage.ChineseSimplified to "备份",
        )
        UiLanguage.entries.forEach { language ->
            val body = UiStrings.forLanguage(language).localOnlyDataNoticeBody
            forbidden.getValue(language).forEach { phrase ->
                assertTrue(
                    "${language.name}의 고지가 아직 소실을 단정한다 \u2014 백로그 #186 이후 거짓이다: \"$body\"",
                    !body.contains(phrase),
                )
            }
            assertTrue(
                "${language.name}의 고지가 백업이라는 **조건**을 말하지 않는다 \u2014 조건 없이 " +
                    "복원을 약속하면 백업을 꺼 둔 사용자에게 거짓이 된다: \"$body\"",
                body.contains(conditionMarker.getValue(language)),
            )
        }
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
     * ⚠️ **이 문장의 참·거짓은 `PremiumProductType` 한 줄에 달려 있다.**
     *
     * 2026-09-22 이전 이 그물은 정반대를 지켰다 — *"구독 복원은 `INAPP`으로 조회해 조용히
     * 실패하니 구독 이야기를 쓰지 말라"*(#87 「앱이 안 하는 것을 문구가 약속했다」의 재판을
     * 막으려던 것). **그 전제가 사라졌다**: `PremiumPurchaseGlue.kt`가 이제 `SUBS`로 조회한다.
     * 그래서 같은 위험을 **반대편에서** 지킨다 — 그 한 줄이 `INAPP`으로 돌아가는 순간
     * 마이 페이지의 *"앱이 구독을 확인해 모든 기능을 다시 열어 드립니다"* 가 거짓이 된다.
     *
     * ⚠️ 옛 게이트(`isBotCharacterPurchaseEnabled`)도 함께 걷어냈다 — **캐릭터 개별 판매가
     * 2026-09-18에 폐기**돼(#160·#161·#18) 그 플래그는 영영 켜지지 않고, 문구가 죽어 있었다.
     * 이제 **모두에게 보인다**: 구독자에게는 안심이고, 아직 아닌 사람에게는 *"구독하면 이것도
     * 해결된다"* 는 정보다.
     */
    @Test
    fun theRestoreLineIsOnlyHonestWhilePremiumIsQueriedAsASubscription() {
        val glue = RepoPaths.compositionFile("PremiumPurchaseGlue.kt").readContractSource()
        assertTrue(
            "`PremiumProductType`이 `SUBS`가 아니다 — 구독 조회가 조용한 미소유로 끝나고, " +
                "마이 페이지의 복원 문장이 거짓이 된다(#87의 재판).",
            glue.contains("BillingClient.ProductType.SUBS"),
        )

        val myPage = source("app-android/src/main/java/com/worksoc/goaicoach/ui/MyPageScreen.kt")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
        assertTrue(
            "복원 문장이 다시 플래그 뒤로 숨었다 — 아직 구독하지 않은 사람이 " +
                "\"구독하면 이것도 해결된다\"를 영영 못 보게 된다.",
            myPage.contains("strings.localOnlyDataNoticePaidRestoreLine") &&
                !myPage.contains("isBotCharacterPurchaseEnabled"),
        )
    }

}
