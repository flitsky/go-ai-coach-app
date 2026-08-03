package com.worksoc.goaicoach.application.auth

/**
 * 4계층(External Integration) α — 실제 인증 SDK(Firebase Auth 등) 호출을 감싸는 순수 포트.
 * 실제 어댑터는 `ui/AndroidAuthClient.kt`(플랫폼 계층)에 둔다. 이번 단계에는 익명 로그인만
 * 필요하므로 그 메서드만 둔다 — Google/이메일 로그인을 실제로 연동할 때 추가한다.
 */
internal interface AuthClientPort {
    suspend fun signInAnonymously(): Result<AuthState>
}
