package com.worksoc.goaicoach.application.auth

/**
 * 4계층(External Integration) α — 실제 인증 SDK(Firebase Auth 등) 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidAuthClient.kt`(플랫폼 계층)에 둔다. 이메일 로그인은 아직
 * 필요 없으므로 메서드를 두지 않는다 — 실제로 연동할 때 추가한다.
 */
internal interface AuthClientPort {
    suspend fun signInAnonymously(): Result<AuthState>

    /**
     * 익명 세션이 없는 상태에서의 신규 Google 로그인. [idToken]은 Credential Manager/
     * Sign in with Google이 발급한 Google ID 토큰이다(`ui/GoogleCredentialManagerClient.kt`).
     */
    suspend fun signInWithGoogle(idToken: String): Result<AuthState>

    /**
     * 현재 익명 세션을 Google 계정으로 승격한다(UID 유지, 데이터 유실 없음). 호출 시점에
     * 익명 세션이 없으면 신규 로그인으로 대체되고, 이 Google 계정이 이미 다른 사용자에
     * 연결되어 있으면(충돌) 그 기존 계정으로 로그인한다 — 두 경우 모두 구현체가 흡수한다.
     */
    suspend fun linkGoogleCredential(idToken: String): Result<AuthState>

    /** 지금 이 기기의 로그인 상태를 동기적으로 읽는다 — 승격 여부 판단에 쓰인다. */
    fun currentAuthState(): AuthState
}
