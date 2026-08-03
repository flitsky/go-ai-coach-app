package com.worksoc.goaicoach.application.auth

/**
 * 로그인 수단. [Google]/[Email]은 UI에 배치만 되어 있고 아직 실제로 연동되지 않았다
 * (버튼을 누르면 "준비 중" 안내만 표시됨) — 실제로 발급되는 값은 현재 [Anonymous]뿐이다.
 */
internal enum class AuthProvider {
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
internal data class AuthState(
    val isSignedIn: Boolean = false,
    val provider: AuthProvider? = null,
    val uid: String? = null,
) {
    companion object {
        fun signedIn(provider: AuthProvider, uid: String): AuthState =
            AuthState(isSignedIn = true, provider = provider, uid = uid)
    }
}
