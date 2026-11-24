package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.worksoc.goaicoach.platform.StoreReviewRequester
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene

@Composable
internal actual fun rememberStoreReviewRequester(): StoreReviewRequester =
    remember {
        StoreReviewRequester {
            val activeScene = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .firstOrNull { scene -> scene.activationState == UISceneActivationStateForegroundActive }
            if (activeScene != null) {
                SKStoreReviewController.requestReviewInScene(activeScene)
            }
        }
    }
