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
 * 로그인 상태. 플랫폼(Firebase Auth SDK)에 의존하지 않는 순수 로직으로 설계해,
 * 추후 iOS 쪽 인증 SDK를 추가할 때 이 타입 자체는 재사용할 수 있게 한다.
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
