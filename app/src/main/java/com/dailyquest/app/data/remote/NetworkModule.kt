package com.dailyquest.app.data.remote

import com.dailyquest.app.data.auth.AuthInterceptor
import com.dailyquest.app.data.auth.SessionGuardInterceptor
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenAuthenticator
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = false
    }

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        // 기본 TLS 인증서 검증을 그대로 사용한다(TrustManager/HostnameVerifier 커스터마이징 금지).
        // 요청·응답 본문에 토큰과 비밀번호가 있으므로 HttpLoggingInterceptor는 사용하지 않는다.

    /** 이 헤더가 붙은 요청만 OkHttp의 연결 실패 자동 재시도를 끈다(방 생성처럼 멱등하지 않은 요청). 서버로는 전송되지 않는다. */
    const val NO_RETRY_HEADER = "X-DQ-No-Retry"

    /**
     * 기본 클라이언트는 그대로 쓰고, [NO_RETRY_HEADER] 요청만 retryOnConnectionFailure=false 클라이언트로 보낸다.
     * 두 클라이언트는 연결 풀·Authenticator·인터셉터를 공유하므로 TOKEN_EXPIRED 갱신 동작은 같다.
     */
    private class RetryAwareCallFactory(private val client: OkHttpClient) : Call.Factory {
        private val noRetryClient = client.newBuilder().retryOnConnectionFailure(false).build()
        override fun newCall(request: Request): Call =
            if (request.header(NO_RETRY_HEADER) != null) {
                noRetryClient.newCall(request.newBuilder().removeHeader(NO_RETRY_HEADER).build())
            } else {
                client.newCall(request)
            }
    }

    private fun retrofit(client: OkHttpClient, baseUrl: String, noRetryAware: Boolean = false) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .let { if (noRetryAware) it.callFactory(RetryAwareCallFactory(client)) else it.client(client) }
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun createApi(
        tokens: SessionTokens,
        onSessionExpired: () -> Unit,
        baseUrl: String = ApiConfig.BASE_URL,
    ): DqApi {
        // 갱신 전용: Authenticator·인터셉터가 없어 갱신 중 재귀 호출이 일어나지 않는다.
        val refreshApi = retrofit(baseClient().build(), baseUrl).create(DqApi::class.java)
        val client = baseClient()
            .addInterceptor(AuthInterceptor(tokens))
            .addInterceptor(SessionGuardInterceptor(tokens, onSessionExpired))
            .authenticator(TokenAuthenticator(tokens, refreshApi, onSessionExpired))
            .build()
        return retrofit(client, baseUrl, noRetryAware = true).create(DqApi::class.java)
    }
}
