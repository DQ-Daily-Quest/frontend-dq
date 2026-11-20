package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.parseErrorBody
import com.dailyquest.app.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/** 인증이 필요한 요청에 Bearer를 붙인다. [DqApi.NO_AUTH_HEADER] 요청은 토큰 없이 보낸다. */
class AuthInterceptor(private val tokens: SessionTokens) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder().removeHeader(DqApi.NO_AUTH_HEADER)
        if (original.header(DqApi.NO_AUTH_HEADER) == null) {
            tokens.get()?.let { builder.header("Authorization", "Bearer ${it.accessToken}") }
        }
        return chain.proceed(builder.build())
    }
}

private fun Request.bearer(): String? = header("Authorization")?.removePrefix("Bearer ")?.trim()

private fun Response.errorCode(): String? =
    parseErrorBody(runCatching { peekBody(4096).string() }.getOrNull())?.code

private fun Response.attempts(): Int {
    var n = 1
    var prior = priorResponse
    while (prior != null) { n++; prior = prior.priorResponse }
    return n
}

/**
 * 401 TOKEN_EXPIRED일 때만 Refresh Token으로 갱신 후 원 요청을 한 번 재시도한다.
 * - 동시 요청은 lock으로 직렬화되며, 이미 다른 요청이 갱신했다면 새 토큰으로 재시도만 한다.
 * - Authorization이 없던 요청(로그인 등)과 다른 401 코드(INVALID_CREDENTIALS 등)는 건드리지 않는다.
 * - 갱신 요청은 Authenticator가 없는 별도 클라이언트([refreshApi])로 보내 재귀 호출을 막는다.
 * - Refresh Token이 거절(401)되면 세션을 만료시키고, 네트워크/서버 오류는 IOException으로 전달해 로그아웃하지 않는다.
 */
class TokenAuthenticator(
    private val tokens: SessionTokens,
    private val refreshApi: DqApi,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val sent = response.request.bearer() ?: return null
        if (response.code != 401 || response.attempts() >= 2) return null
        if (response.errorCode() != ApiErrorCode.TOKEN_EXPIRED) return null

        synchronized(tokens.lock) {
            val current = tokens.get() ?: return null
            if (current.accessToken != sent) {
                return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            }
            val result = runBlocking { apiCall { refreshApi.refresh(RefreshRequest(current.refreshToken)) } }
            when (result) {
                is ApiResult.Success -> {
                    val fresh = TokenPair(result.value.accessToken, result.value.refreshToken)
                    tokens.set(fresh)
                    return response.request.newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build()
                }
                is ApiResult.Failure -> {
                    val http = result.error as? com.dailyquest.app.data.remote.ApiException.Http
                    if (http != null && http.status == 401) {
                        onSessionExpired()
                        return null
                    }
                    throw IOException("token refresh failed (transient)")
                }
            }
        }
    }
}

/**
 * 최종 응답이 401 UNAUTHORIZED/TOKEN_EXPIRED이면 세션을 만료시킨다.
 * 요청에 쓰인 토큰이 여전히 현재 토큰일 때만 동작해, 이전 사용자의 늦은 응답이 새 로그인을 끊지 못한다.
 */
class SessionGuardInterceptor(
    private val tokens: SessionTokens,
    private val onSessionExpired: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            val sent = response.request.bearer()
            val code = response.errorCode()
            if (sent != null && (code == ApiErrorCode.UNAUTHORIZED || code == ApiErrorCode.TOKEN_EXPIRED)) {
                val current = tokens.get()
                if (current == null || current.accessToken == sent) onSessionExpired()
            }
        }
        return response
    }
}
