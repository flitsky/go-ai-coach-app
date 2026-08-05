package com.worksoc.goaicoach.application.auth

/**
 * 4계층(External Integration) α — 실제 인증 SDK(Firebase Auth 등) 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidAuthClient.kt`(플랫폼 계층)에 둔다.
 *
 * 익명(Anonymous) 로그인 메서드는 없다 — 파이어베이스 Auth의 익명 로그인 활성화는
 * 이 프로젝트에서 켜지 않기로 확정된 상태다(재설치마다 새 익명 계정이 쌓여 허수 유저가
 * 늘어나는 구조적 한계, `auth-onboarding/README.md` 3장 참고). 게스트 기능("계정 없이
 * 시작하기")은 `DeviceIdentityStorePort`의 로컬 UUID로 별도 동작하며 이 포트와 무관하다.
 */
internal interface AuthClientPort {
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

    /**
     * 익명 세션이 없는 상태에서의 이메일+비밀번호 로그인. 가입/로그인을 사용자가 직접
     * 구분해서 고르지 않는다 — 계정이 없으면(이메일 중복 없음) 그 자리에서 신규 가입으로
     * 대체하고, 있으면 그 계정으로 로그인한다.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthState>

    /**
     * 현재 익명 세션을 이 이메일+비밀번호 계정으로 승격한다(UID 유지, 데이터 유실 없음).
     * 호출 시점에 익명 세션이 없으면 [signInWithEmail]과 동일하게 동작하고, 이 이메일이
     * 이미 다른 사용자에 연결되어 있으면(충돌) 그 기존 계정으로 로그인한다.
     */
    suspend fun linkEmailCredential(email: String, password: String): Result<AuthState>

    /** 지금 이 기기의 로그인 상태를 동기적으로 읽는다 — 승격 여부 판단에 쓰인다. */
    fun currentAuthState(): AuthState
}
