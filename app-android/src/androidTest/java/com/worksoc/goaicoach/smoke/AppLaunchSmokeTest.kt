package com.worksoc.goaicoach.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.worksoc.goaicoach.MainActivity
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Skeleton for M-04. Enable after stable test tags and a deterministic engine bootstrap path exist.")
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesMainActivityAndShowsInitialSurface() {
        composeRule.onNodeWithText("Go AI Coach POC")
            .assertIsDisplayed()
    }
}
