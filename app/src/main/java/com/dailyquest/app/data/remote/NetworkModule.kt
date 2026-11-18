package com.dailyquest.app.data.remote

import com.dailyquest.app.data.auth.AuthInterceptor
import com.dailyquest.app.data.auth.SessionGuardInterceptor
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenAuthenticator
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    private fun retrofit(client: OkHttpClient, baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
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
        return retrofit(client, baseUrl).create(DqApi::class.java)
    }
}
