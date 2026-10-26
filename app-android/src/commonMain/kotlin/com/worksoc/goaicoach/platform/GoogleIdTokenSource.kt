package com.worksoc.goaicoach.platform

/**
 * 구글 로그인 토큰을 받아 오는 쪽의 표지(백로그 #294). 안드로이드는 Credential Manager(`GoogleCredentialManagerClient`)이고,
 * 화면 문맥이 있어야 불리므로 메서드는 안드로이드 쪽에만 있다 — 공용 화면은 이것을 들고 다니기만 하고 계정 절 자리
 * (`AccountSettingsSlot`)에 넘긴다. 로그인은 지금 꺼져 있다(`FeatureFlags.isLoginEnabled`).
 */
interface GoogleIdTokenSource
