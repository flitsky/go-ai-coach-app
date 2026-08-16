package com.worksoc.goaicoach.application.auth

/** 로그인 수단. 셋 다 실제로 발급된다(Apple은 아직 UI 스텁만 있고 이 enum엔 없음). */
enum class AuthProvider {
    Anonymous,
    Google,
    Email,
}

/**
 * 6계층(Session & Continuity) — 로그인 상태. 플랫폼(Firebase Auth SDK)에 의존하지 않는
 * 순수 로직으로 설계해, 추후 iOS 쪽 인증 SDK를 추가할 때 이 타입 자체는 재사용할 수 있게 한다.
 * [AuthClientPort]와 같은 패키지에 있지만 계층이 다르다 — 이 타입은 "누구의 세션인가"를
 * 나타내는 상태(6계층)이고, [AuthClientPort]는 그 상태를 얻어오는 외부 SDK 포트(4계층)다.
 */
data class AuthState(
    val isSignedIn: Boolean = false,
    val provider: AuthProvider? = null,
    val uid: String? = null,
) {
    companion object {
        fun signedIn(provider: AuthProvider, uid: String): AuthState =
            AuthState(isSignedIn = true, provider = provider, uid = uid)
    }
}

/**
 * 지금 이 상태가 "승격 가능한 익명 세션"인지 — Google 로그인 시도 시 [AuthClientPort]의
 * `signInWithGoogle`(신규)과 `linkGoogleCredential`(승격) 중 어느 쪽을 호출할지는 이
 * 판단 하나로 결정된다. 이 판단 자체를 SDK 어댑터(`AndroidAuthClient`) 안에 묻지 않고
 * 여기 순수 함수로 분리해, "언제 승격할지"가 raw SDK 기능이 아니라 유스케이스 판단으로
 * 남도록 한다(`auth-onboarding/README.md`의 "계층 배치 참고" 표 Step 2 참고).
 */
val AuthState.isPromotableAnonymousSession: Boolean
    get() = isSignedIn && provider == AuthProvider.Anonymous
