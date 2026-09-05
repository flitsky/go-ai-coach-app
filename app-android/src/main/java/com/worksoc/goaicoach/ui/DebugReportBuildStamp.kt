package com.worksoc.goaicoach.ui

import android.os.Build
import com.worksoc.goaicoach.BuildConfig

/**
 * 진단 리포트 머리말의 한 줄 — **"이거 어느 빌드, 어느 기기예요?"** 에 답한다(백로그 #92).
 *
 * ## 왜 `shared`가 아니라 여기인가
 * `BuildConfig`도 [Build]도 안드로이드 플랫폼 API다. 리포트를 조립하는 곳은
 * `shared/commonMain`이라 그 자리에서는 읽을 수 없다 — 그래서 `hapticDiagnostic`(#36)과 **똑같이**
 * app-android가 만들어 람다로 넘긴다. ⚠️ 새 값을 넣고 싶으면 `shared`에서 `BuildConfig`를 부르려
 * 하지 말고 **이 함수에 더할 것.**
 *
 * ## ⚠️ `SUPPORTED_ABIS`를 왜 넣는가
 * 엔진은 **arm64-v8a로만 빌드된다**(`scripts/build-katago-android-spike.sh`). 다른 ABI 기기에
 * 깔리면 **조용히 스텁 AI**로 떨어지고, 그 상태의 리포트는 겉보기에 멀쩡하다(#104·#91 ⓐ가
 * `abiFilters`로 막은 그 문제다). 스텁 의심 리포트가 왔을 때 **가장 먼저 볼 값**이라 넣는다.
 *
 * ## ⚠️ 개발자 행과 값이 겹치지만 합치지 않았다
 * `DeveloperTestSection`의 빌드 정보 행이 앞의 넷을 같은 식으로 조립한다. 거기는 **좁은 화면 한 줄**
 * 이라 짧아야 하고 여기는 **붙여넣기용**이라 길어도 된다 — 목적이 달라 표현을 공유하면 한쪽이
 * 반드시 진다. 대신 값의 출처(`BuildConfig`)는 하나이므로 어긋날 여지는 없다.
 */
internal fun debugReportBuildStamp(): String = buildString {
    // ⚠️ 구분자가 ` · `인 이유: `BUILD_TIME`("2026-09-06 00:31")과 `MODEL`("SM-S911N")에 **공백이
    // 들어 있어** 공백으로만 가르면 어디까지가 한 값인지 읽을 수 없다. 붙여넣은 리포트를 사람이
    // 눈으로 읽는 것이 이 줄의 용도이므로 눈에 띄는 구분자를 쓴다.
    append("build=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    append(" · ${BuildConfig.BUILD_TYPE}")
    append(" · ${if (BuildConfig.USE_TEST_ADS) "test-ads" else "REAL-ADS"}")
    append(" · builtAt=${BuildConfig.BUILD_TIME}")
    appendLine()
    append("device=${Build.MANUFACTURER} ${Build.MODEL}")
    append(" · android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
    append(" · abi=${Build.SUPPORTED_ABIS.joinToString(",")}")
}
