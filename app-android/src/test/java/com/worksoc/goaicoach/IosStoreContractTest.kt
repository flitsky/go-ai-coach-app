package com.worksoc.goaicoach

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 구독 상품 ID가 앱 코드(`IosStore.kt`)와 시뮬레이터 시험 파일(`app-ios/StoreKit/GoCoach.storekit`)에서 같은지 · 스킴이 그 파일을 가리키는지(백로그 #296).
 * 한쪽만 고치면 Xcode 실행에서 상품이 조용히 사라진다(가격 줄이 「결제 화면에서 확인」으로 물러나고 구독 단추가 「쓸 수 없음」으로 끝난다).
 */
class IosStoreContractTest {
    private val productId = Regex("""const val IosPremiumProductId = "([^"]+)"""")
        .find(RepoPaths.root.resolve("app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ios/IosStore.kt").readContractSource())
        ?.groupValues?.get(1)

    @Test
    fun theStoreKitTestFileSellsTheProductTheAppAsksFor() {
        val storekit = RepoPaths.root.resolve("app-ios/StoreKit/GoCoach.storekit").readContractSource()
        val sold = Regex(""""productID" : "([^"]+)"""").findAll(storekit).map { it.groupValues[1] }.toList()
        assertTrue("IosStore.kt에서 IosPremiumProductId를 찾지 못했다", productId != null)
        assertEquals("GoCoach.storekit의 상품이 앱이 묻는 ID와 다르다(#296)", listOf(productId), sold)
    }

    @Test
    fun theSchemeRunsWithTheStoreKitTestFile() {
        val scheme = RepoPaths.root.resolve("app-ios/GoCoach.xcodeproj/xcshareddata/xcschemes/GoCoach.xcscheme").readContractSource()
        assertTrue(
            "GoCoach 스킴의 실행이 StoreKit 시험 파일을 가리키지 않는다(#296)",
            scheme.contains("""identifier = "../../StoreKit/GoCoach.storekit""""),
        )
    }
}
