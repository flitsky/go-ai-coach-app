package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable

/**
 * 이 앱의 스토어 페이지를 여는 동작(백로그 #294 — S14). 안드로이드는 화면의 액티비티 문맥으로 `openStoreListing`(market:// → https)을
 * 부른다 — 앱 문맥으로는 새 작업 플래그 없이 화면을 띄울 수 없어 조용히 실패한다. 갈 스토어 페이지가 없으면 `null`이고(iOS — App Store 등록 전, #298),
 * 화면은 그 단추 · 링크를 그리지 않는다(눌러도 아무 일 없는 링크를 두지 않는다).
 */
@Composable
expect fun rememberStoreListingLauncher(): (() -> Unit)?
