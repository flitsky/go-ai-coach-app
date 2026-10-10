package com.worksoc.goaicoach.engine

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번들에 **넣는 이름**과 앱이 **여는 이름**을 묶어 두는 계약(백로그 #104, #245).
 *
 * ## 백로그 #245 PAD 전환
 * 2026-10-10부터 AAB 204MB의 주범이던 두 대용량 엔진 모델은 base APK가 아니라
 * **Play Asset Delivery(PAD)의 독립 에셋 팩(`katago_model_pack`, `katago_human_pack`)**에 실린다.
 * base APK에는 수 KB의 설정 둘(`gtp_learning.cfg`, `analysis_learning.cfg`)만 남는다.
 *
 * ## ⚠️ 에셋 팩 안의 이름은 `.gz`가 풀리지 않는다 (2026-10-10 실측)
 * base 에셋과 달리, AGP의 `com.android.asset-pack`은 `.gz` 압축을 풀지 않고 원본 파일명
 * 그대로(`model.bin.gz`, `human.bin.gz`) 패키징한다. KataGo는 `.bin.gz`를 압축 해제 없이
 * 직접 읽으므로, 앱도 에셋 팩에서 `.bin.gz`를 꺼내 `files/katago/`에 복사한다.
 */
class BundledEngineAssetContractTest {

    private val bootstrap = RepoPaths.appAndroid("engine/EngineBootstrap.kt").readContractSource()
    // refactor backlog #63: RepoPaths.root로 흡수
    private val makefile = RepoPaths.root.resolve("Makefile").readContractSource()

    /** 앱이 base 에셋에서 꺼내려고 시도하는 설정 파일 이름들. */
    private fun configAssetNamesTheAppOpens(): Set<String> =
        Regex("""seedAssetIfMissing\(\s*context = context,\s*assetPath = "katago/([^"]+)"""")
            .findAll(bootstrap)
            .map { it.groupValues[1] }
            .filter { it.endsWith(".cfg") }
            .toSet()

    /** `prepare-friend-assets`가 base 에셋(`FRIEND_ASSET_DIR`)에 넣는 이름들. */
    private fun configAssetNamesInsideTheBaseApk(): Set<String> =
        Regex("""\$\(FRIEND_ASSET_DIR\)/([^"\s]+)""").findAll(makefile)
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun theAppOpensExactlyTheConfigFilesInBaseApk() {
        val opened = configAssetNamesTheAppOpens()
        val packaged = configAssetNamesInsideTheBaseApk()

        assertTrue("EngineBootstrap에서 설정 에셋 이름을 찾지 못했다.", opened.isNotEmpty())
        assertTrue("Makefile에서 base 에셋 대상을 찾지 못했다.", packaged.isNotEmpty())

        assertEquals(
            "Base APK 안의 설정 이름과 앱이 여는 설정 이름이 어긋났다.",
            packaged.sorted(),
            opened.sorted(),
        )
    }

    @Test
    fun theModelsAreBundledInPadAssetPacks() {
        // 백로그 #245: 두 모델은 base 에셋이 아니라 PAD 에셋 팩 디렉터리에 복사된다.
        assertTrue("빌드가 주 모델을 PAD 팩에 넣지 않는다.", "\$(MODEL_PACK_ASSET_DIR)/model.bin.gz" in makefile)
        assertTrue("빌드가 사람 모델을 PAD 팩에 넣지 않는다.", "\$(HUMAN_PACK_ASSET_DIR)/human.bin.gz" in makefile)
    }

    @Test
    fun theAppSeedsBothModelsFromPadPacks() {
        // 앱이 PAD 에셋 팩에서 두 모델을 꺼내는지 검증
        assertTrue(
            "앱이 주 모델을 PAD 팩에서 풀지 않는다.",
            bootstrap.contains("packName = KatagoModelPackName") &&
                bootstrap.contains("""relativeSourcePath = "katago/model.bin.gz""""),
        )
        assertTrue(
            "앱이 사람 모델을 PAD 팩에서 풀지 않는다.",
            bootstrap.contains("packName = KatagoHumanPackName") &&
                bootstrap.contains("""relativeSourcePath = "katago/human.bin.gz""""),
        )
    }

    @Test
    fun thePadPackNamesMatchModuleDeclarations() {
        // EngineBootstrap의 팩 이름 상수가 settings.gradle.kts의 모듈 이름과 일치하는지 검증
        val settings = RepoPaths.root.resolve("settings.gradle.kts").readContractSource()
        assertTrue("katago_model_pack이 settings에 선언되지 않았다.", "include(\":katago_model_pack\")" in settings)
        assertTrue("katago_human_pack이 settings에 선언되지 않았다.", "include(\":katago_human_pack\")" in settings)
        assertTrue("EngineBootstrap의 주 모델 팩 이름이 일치하지 않는다.", "internal const val KatagoModelPackName = \"katago_model_pack\"" in bootstrap)
        assertTrue("EngineBootstrap의 사람 모델 팩 이름이 일치하지 않는다.", "internal const val KatagoHumanPackName = \"katago_human_pack\"" in bootstrap)
    }
}
