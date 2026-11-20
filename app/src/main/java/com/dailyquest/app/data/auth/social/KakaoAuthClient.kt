package com.dailyquest.app.data.auth.social

import android.content.Context
import com.kakao.sdk.auth.model.Prompt
import com.kakao.sdk.common.model.AuthErrorCause
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.model.AuthError
import com.kakao.sdk.user.UserApiClient
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Kakao SDK로 OIDC ID Token을 얻는다. 백엔드는 Access Token이 아닌 ID Token만 검증한다.
 * 카카오톡이 설치돼 있으면 앱 로그인, 없으면 카카오계정(브라우저) 로그인을 사용한다.
 */
class KakaoAuthClient(private val nativeAppKey: String) : SocialAuthClient {

    override suspend fun signIn(context: Context): SocialSignInResult {
        if (nativeAppKey.isBlank()) return SocialSignInResult.NotConfigured
        val client = UserApiClient.instance
        if (client.isKakaoTalkLoginAvailable(context)) {
            val viaTalk = request { cb -> client.loginWithKakaoTalk(context, callback = cb) }
            // 사용자가 카카오톡 화면에서 취소한 경우 브라우저로 넘기지 않는다. 그 외 실패만 계정 로그인으로 전환한다.
            if (viaTalk !is Outcome.Error || viaTalk.cancelled) return viaTalk.toResult()
        }
        return request { cb ->
            client.loginWithKakaoAccount(context, prompts = listOf(Prompt.SELECT_ACCOUNT), callback = cb)
        }.toResult()
    }

    override suspend fun signOut() {
        // 로컬 토큰만 삭제한다. unlink(연결 끊기)는 호출하지 않는다.
        suspendCancellableCoroutine<Unit> { cont ->
            runCatching { UserApiClient.instance.logout { if (cont.isActive) cont.resume(Unit) } }
                .onFailure { if (cont.isActive) cont.resume(Unit) }
        }
    }

    private sealed interface Outcome {
        data class Success(val idToken: String?) : Outcome
        data class Error(val cancelled: Boolean, val network: Boolean) : Outcome
    }

    private fun Outcome.toResult(): SocialSignInResult = when (this) {
        is Outcome.Success ->
            if (idToken.isNullOrBlank()) SocialSignInResult.MissingIdToken else SocialSignInResult.Token(idToken)
        is Outcome.Error -> when {
            cancelled -> SocialSignInResult.Cancelled
            network -> SocialSignInResult.Network
            else -> SocialSignInResult.Failed
        }
    }

    private suspend fun request(
        start: (callback: (com.kakao.sdk.auth.model.OAuthToken?, Throwable?) -> Unit) -> Unit,
    ): Outcome = suspendCancellableCoroutine { cont ->
        start { token, error ->
            if (!cont.isActive) return@start
            cont.resume(
                when {
                    error != null -> Outcome.Error(
                        cancelled = (error is ClientError && error.reason == ClientErrorCause.Cancelled) ||
                            (error is AuthError && error.reason == AuthErrorCause.AccessDenied),
                        network = error is IOException,
                    )
                    token != null -> Outcome.Success(token.idToken)
                    else -> Outcome.Error(cancelled = false, network = false)
                },
            )
        }
    }
}
