package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import com.worksoc.goaicoach.platform.StoreReviewRequester

/**
 * 현재 화면의 플랫폼 문맥에 묶인 [StoreReviewRequester].
 * 안드로이드는 Activity를 풀어 Play In-App Review를 부르고, iOS는 활성 씬의 SKStoreReviewController를 부른다.
 */
@Composable
internal expect fun rememberStoreReviewRequester(): StoreReviewRequester
