package com.dailyquest.app.data.auth.social

import android.content.Context

enum class SocialProvider(val path: String) { GOOGLE("google"), KAKAO("kakao") }

sealed interface SocialSignInResult {
    data class Token(val idToken: String) : SocialSignInResult {
        override fun toString() = "Token(idToken=***)"
    }

    /** 사용자가 직접 취소 */
    data object Cancelled : SocialSignInResult

    /** 기기에 사용 가능한 계정이 없음 (Google) */
    data object NoAccount : SocialSignInResult

    data object Network : SocialSignInResult

    /** SDK 로그인은 성공했지만 ID Token이 없음 (예: Kakao OpenID Connect 비활성) */
    data object MissingIdToken : SocialSignInResult

    /** 클라이언트 ID/앱 키가 빌드에 주입되지 않음 */
    data object NotConfigured : SocialSignInResult

    data object Failed : SocialSignInResult
}

/** 제공자 SDK로 ID Token만 얻는다. Daily Quest 세션은 서버가 발급한 JWT로만 관리한다. */
interface SocialAuthClient {
    suspend fun signIn(context: Context): SocialSignInResult

    /** 앱 로그아웃 시 SDK의 로컬 인증 상태만 정리한다. 계정 연결 해제(unlink)는 하지 않는다. */
    suspend fun signOut()
}

object NotConfiguredSocialClient : SocialAuthClient {
    override suspend fun signIn(context: Context) = SocialSignInResult.NotConfigured
    override suspend fun signOut() = Unit
}
