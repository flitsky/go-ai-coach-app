package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.platform.StoreReviewRequester
import com.worksoc.goaicoach.platform.requestPlayInAppReview

@Composable
internal actual fun rememberStoreReviewRequester(): StoreReviewRequester {
    val context = LocalContext.current
    return remember(context) {
        val activity = context.findActivity()
        StoreReviewRequester {
            if (activity != null) requestPlayInAppReview(activity)
        }
    }
}
