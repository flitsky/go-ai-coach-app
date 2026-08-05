package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.worksoc.goaicoach.R

/**
 * 4계층(External Integration) α의 SDK 의존이 무거운 부분만 전담 — Credential Manager/
 * Sign in with Google을 호출해 Google ID 토큰을 얻는다. Firebase Auth 호출([AndroidAuthClient])과
 * 별도 파일로 분리해, 두 SDK의 서로 다른 실패 유형(사용자 취소 vs 자격 증명 오류)이 섞이지
 * 않게 한다 — `auth-onboarding/README.md`의 "계층 배치 참고" 표가 명시한 파일 분리 기준.
 *
 * [R.string.default_web_client_id]는 `google-services.json`에 Google 로그인용 웹 OAuth
 * 클라이언트가 포함돼 있어야(Firebase 콘솔에서 Google 로그인 활성화 후 재다운로드) 생성된다.
 */
internal class GoogleCredentialManagerClient {
    suspend fun requestIdToken(context: Context): Result<String> {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (error: GetCredentialException) {
            // 사용자 취소(GetCredentialCancellationException)도 이 상위 타입으로 잡힌다 —
            // 호출부가 실패/취소를 구분하지 않고 동일하게 로그/토스트로 안내하기로 했으므로
            // (auth-onboarding 작업 규칙) 여기서 세분화하지 않고 그대로 Result.failure로 넘긴다.
            Result.failure(error)
        } catch (error: GoogleIdTokenParsingException) {
            Result.failure(error)
        }
    }
}
