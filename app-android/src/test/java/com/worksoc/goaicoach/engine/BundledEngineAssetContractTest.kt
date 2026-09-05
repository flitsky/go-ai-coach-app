package com.worksoc.goaicoach.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번들에 **넣는 이름**과 앱이 **여는 이름**을 묶어 두는 계약(백로그 #104).
 *
 * ## ⚠️ 두 이름은 같지 않다 — `.gz` 하나만큼 다르고, 그게 맞다
 * **AGP는 에셋의 `.gz`를 패키징하면서 풀고 확장자를 뗀다.** 2026-09-05 실측:
 * 소스에 `model.bin.gz`(97,898,094B)뿐인데 병합 결과는 `model.bin`(105,532,578B)이었고
 * 그 값이 `gzip -dc | wc -c`와 **정확히 같았다.** 그래서 빌드는 `model.bin.gz`를 넣고 앱은
 * `model.bin`을 여는 것이 **정상**이다.
 *
 * ## ⚠️ 이 변환을 모르면 "고치다가" 망가뜨린다 — 실제로 한 번 그랬다
 * 2026-09-05에 두 이름이 어긋난 것을 결함으로 읽고 여는 쪽을 `.gz`로 바꿨다가, **APK에 없는
 * 이름**을 열게 되어 스텁으로 떨어뜨렸다(같은 날 실기 설치 직전에 APK를 뜯어보고 발견).
 * `assets.open()`의 실패는 `seedAssetIfMissing`이 삼키므로(stderr 한 줄) **아무것도 빨개지지
 * 않은 채** 그렇게 된다.
 *
 * ## 왜 "파일이 있나"를 재지 않는가
 * `app-android/src/friend/assets/katago/`는 **gitignore**다(`.gitignore:26`). 그 디렉터리의
 * 내용은 저장소 안에서 확인 가능한 사실이 아니므로 계약이 될 수 없다. 반면 *"빌드가 넣는 이름"*
 * 과 *"앱이 여는 이름"* 은 둘 다 커밋돼 있어 잴 수 있다(함정 20번).
 */
class BundledEngineAssetContractTest {

    private val bootstrap = File("src/main/java/com/worksoc/goaicoach/engine/EngineBootstrap.kt").readText()
    private val makefile = File("../Makefile").readText()

    /** 앱이 번들에서 꺼내려고 시도하는 파일 이름들. */
    private fun assetNamesTheAppOpens(): Set<String> =
        Regex("assetPath = \"katago/([^\"]+)\"").findAll(bootstrap)
            .map { it.groupValues[1] }
            .toSet()

    /**
     * `prepare-friend-assets`가 넣은 것이 **APK 안에서 갖게 되는** 이름들.
     * ⚠️ `.gz`를 떼는 것이 이 함수의 요점이다 — AGP가 패키징하면서 풀기 때문이다(머리말 참고).
     */
    private fun assetNamesInsideTheApk(): Set<String> =
        Regex("""\$\(FRIEND_ASSET_DIR\)/([^"\s]+)""").findAll(makefile)
            .map { it.groupValues[1].removeSuffix(".gz") }
            .toSet()

    @Test
    fun theAppOpensExactlyTheNamesThatEndUpInsideTheApk() {
        val opened = assetNamesTheAppOpens()
        val packaged = assetNamesInsideTheApk()

        assertTrue("EngineBootstrap에서 에셋 이름을 찾지 못했다 — 이 계약의 전제가 무너졌다.", opened.isNotEmpty())
        assertTrue("Makefile에서 번들 대상 이름을 찾지 못했다 — 전제가 무너졌다.", packaged.isNotEmpty())

        assertEquals(
            "APK 안의 이름과 앱이 여는 이름이 어긋났다 — 앱은 조용히 스텁으로 떨어진다(#104).\n" +
                "⚠️ 빌드 쪽 `.gz`는 AGP가 떼므로, 앱은 확장자 **없는** 이름을 열어야 한다.",
            packaged.sorted(),
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

    @Test
    fun theBuildStillBundlesTheCompressedModel() {
        // ⚠️ 빌드 쪽이 `.gz`를 그만 넣으면 위 계약은 여전히 통과하지만(양쪽이 같아지므로)
        // **AAB가 8MB 커진다.** 압축본을 넣는 선택 자체를 못 박아 둔다.
        assertTrue(
            "빌드가 모델을 압축본으로 넣지 않는다 — 번들이 커진다(#104).",
            "\$(FRIEND_ASSET_DIR)/model.bin.gz" in makefile,
        )
    }
}
