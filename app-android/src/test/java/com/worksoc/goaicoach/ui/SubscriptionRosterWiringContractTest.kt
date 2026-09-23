package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.readContractSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구독이 로스터를 여는 **배선**을 소스에서 못박는다(백로그 #157).
 *
 * ⚠️ 도메인 판정은 초록인데 **배선만 빠지면** 구독자에게 캐릭터가 안 열린다 —
 * `isAvailable`의 기본값이 `false`라 아무도 오류를 내지 않고 조용히 옛 동작이 된다.
 */
class SubscriptionRosterWiringContractTest {

    private fun source(path: String): String = File(path).readContractSource()

    private val app = source("src/main/java/com/worksoc/goaicoach/ui/GoCoachApp.kt")
    private val bots = source("src/main/java/com/worksoc/goaicoach/ui/BotCharacterUiState.kt")
    private val setup = source("src/main/java/com/worksoc/goaicoach/ui/PlayerSetupPanel.kt")
    private val store = source("src/main/java/com/worksoc/goaicoach/persistence/BotCollectionStore.kt")

    /**
     * ⚠️ 봇 상태는 프리미엄 배선보다 **먼저** 만들어진다(구매 특전 판정에 컬렉션이 필요해서다).
     * 그래서 구독은 provider에서 얹어야 한다 — 빌더 안에서 읽으려 들면 아직 없는 값을 읽는다.
     */
    @Test
    fun theProviderHandsTheSubscriptionToTheBotState() {
        assertTrue(
            "구독이 봇 상태에 전달되지 않는다 — 구독자에게 캐릭터가 조용히 안 열린다(#157).",
            app.contains("LocalBotCharacterUiState provides botCharacterUiState.copy(subscriptionActive = premiumUiState.isPurchased)"),
        )
    }

    /** ⚠️ 강등 경로에도 같은 값을 줘야 한다 — 안 주면 구독을 사고도 상대가 약해진다. */
    @Test
    fun theLevelClampAlsoKnowsAboutTheSubscription() {
        assertTrue(
            "강등 판정이 구독을 모른다 — 구독자의 상대가 낮춰진다(#157).",
            setup.contains("clampToOwnedBotCharacter(side.playLevel, bots.collection, bots.subscriptionActive)"),
        )
    }

    /**
     * ⚠️ **픽커에 표시가 없으면 해지 순간 말없이 잠긴다** — 사용자에게는 "내 캐릭터가 사라졌다"다.
     * 백로그 #157이 따로 경고한 자리다.
     */
    @Test
    fun thePickerSaysWhenACharacterIsOnlyOpenBecauseOfTheSubscription() {
        assertTrue(
            "픽커가 구독으로 열린 자리를 표시하지 않는다 — 해지 시 말없이 잠긴다(#157).",
            bots.contains("viaSubscription = bots.isSubscriptionOnly(character)") &&
                bots.contains("strings.botUnlockedBySubscription"),
        )
    }

    /**
     * ⚠️ **스키마를 올리면 수집물이 통째로 날아간다**(백로그 #157). 구독은 저장할 것이 없으므로
     * 이 항목에서 번호가 바뀔 이유도 없다 — 바뀌었다면 무언가를 저장하기 시작한 것이다.
     */
    @Test
    fun theCollectionSchemaStaysAtOneBecauseTheSubscriptionStoresNothing() {
        assertEquals(
            "`BotCollectionStore`의 스키마 번호가 바뀌었다 — 구독을 저장소에 쓰기 시작했거나, " +
                "올리는 순간 이미 수집한 캐릭터가 통째로 날아간다(#157).",
            1,
            Regex("""CurrentSchemaVersion\s*=\s*(\d+)""").find(store)!!.groupValues[1].toInt(),
        )
    }
}
