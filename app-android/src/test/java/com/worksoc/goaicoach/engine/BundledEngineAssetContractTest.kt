package com.worksoc.goaicoach.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번들에 **넣는 이름**과 앱이 **여는 이름**을 묶어 두는 계약(백로그 #104).
 *
 * ⚠️ **이 둘이 어긋나도 아무것도 빨개지지 않았다.** `assets.open()`이 던지는 `IOException`을
 * `seedAssetIfMissing`이 삼키기 때문에, 어긋난 채로 **조용히 스텁으로 강등된 앱**이 만들어진다.
 * 실제로 2026-06-05(`628e2e8`)부터 `make prepare-friend-assets`는 `model.bin.gz`를 넣고 있었고
 * 코드는 `model.bin`을 열고 있었다 — 3개월간 아무 신호가 없었다.
 *
 * ⚠️ **왜 아무도 몰랐나**: `app-android/src/friend/assets/katago/`는 **gitignore**다
 * (`.gitignore:26`). 지난 릴리스 빌드는 누군가 그 자리에 손으로 넣어둔 `model.bin` 덕에
 * 통과했고, **그 상태는 버전 관리되지 않는다.** 그래서 "파일이 있나"가 아니라
 * **"이름이 서로 맞나"** 를 재야 한다 — 그것만이 저장소 안에서 확인 가능한 사실이다.
 */
class BundledEngineAssetContractTest {

    private val bootstrap = File("src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt").readText()
    private val makefile = File("../Makefile").readText()

    /** 앱이 번들에서 꺼내려고 시도하는 파일 이름들. */
    private fun assetNamesTheAppOpens(): Set<String> =
        Regex("""assetPath = "katago/([^"]+)"""").findAll(bootstrap)
            .map { it.groupValues[1] }
            .toSet()

    /** `prepare-friend-assets`가 실제로 번들에 넣는 파일 이름들. */
    private fun assetNamesTheBuildBundles(): Set<String> =
        Regex("""\$\(FRIEND_ASSET_DIR\)/([^"\s]+)"""").findAll(makefile)
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun theAppOpensExactlyTheAssetsTheBuildBundles() {
        val opened = assetNamesTheAppOpens()
        val bundled = assetNamesTheBuildBundles()

        assertTrue("EngineBootstrap에서 에셋 이름을 찾지 못했다 — 이 계약의 전제가 무너졌다.", opened.isNotEmpty())
        assertTrue("Makefile에서 번들 대상 이름을 찾지 못했다 — 전제가 무너졌다.", bundled.isNotEmpty())

        // ⚠️ 한쪽만 바꾸면 여기서 깨진다. 그것이 이 테스트의 전부다.
        assertEquals(
            "번들에 넣는 이름과 앱이 여는 이름이 어긋났다 — 앱은 조용히 스텁으로 떨어진다(#104).",
            bundled.sorted(),
            opened.sorted(),
        )
    }

    @Test
    fun theModelIsAmongThem() {
        // 설정 둘은 어긋나면 기능이 눈에 띄게 빠지지만, 모델은 없으면 **스텁이 대신 수를 둔다** —
        // 가장 조용히 실패하는 쪽이라 따로 못 박아 둔다.
        assertTrue(
            "모델 에셋이 씨딩 목록에 없다 — 앱 데이터를 지운 기기가 스스로 복구하지 못한다(#104).",
            assetNamesTheAppOpens().any { it.startsWith("model.bin") },
        )
    }
}
