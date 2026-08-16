package com.worksoc.goaicoach.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.MainActivity
import com.worksoc.goaicoach.ui.UiLanguage
import com.worksoc.goaicoach.ui.UiStrings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M-04 smoke coverage: real [MainActivity] -> real [com.worksoc.goaicoach.engine.createEngineBootstrap]
 * -> real [com.worksoc.goaicoach.ui.GoCoachApp] composition, end to end. Distinct from
 * [NewGameBoardTapSmokeTest], which bypasses MainActivity/engine bootstrap entirely with a fake
 * engine client.
 *
 * Two things make this non-trivial to assert on, found by running against a real emulator rather
 * than assuming:
 * - `createEngineBootstrap` is synchronous and always resolves (falling back to a stub engine
 *   when KataGo assets aren't seeded), but on a device without `make seed-engine`, it first
 *   copies the bundled model out of assets/ before falling back -- multiple real seconds of I/O,
 *   not instant. `waitUntil` below covers that instead of assuming the default Compose idle sync
 *   catches it (it doesn't track that IO-bound `LaunchedEffect` as a pending recomposition).
 * - [FeatureFlags.isLoginEnabled] is `false`, so [initialDestination] skips Onboarding entirely
 *   and always lands on [ScreenDestination.Home] -- confirmed against the real app, not assumed
 *   from [NewGameBoardTapSmokeTest]'s onboarding-first flow (that test predates the 2026-08-09
 *   login-disable decision and may itself now be stale).
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val strings = UiStrings.forLanguage(UiLanguage.Korean)

    // Same rationale as NewGameBoardTapSmokeTest: the instrumented test APK shares
    // process/storage with the app under test, so leftover SharedPreferences would otherwise
    // make which screen appears first non-deterministic.
    @Before
    fun clearPersistedAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefsDir = context.filesDir.resolveSibling("shared_prefs")
        prefsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun launchesMainActivityAndShowsInitialSurface() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(strings.appTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(strings.appTitle)
            .assertIsDisplayed()
    }
}
