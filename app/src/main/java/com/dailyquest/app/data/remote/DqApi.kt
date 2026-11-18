package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.UserMeResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/** 경로는 Base URL 뒤에 붙으므로 앞에 '/'를 쓰지 않는다. */
interface DqApi {
    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): SignupResponse

    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @Headers("$NO_AUTH_HEADER: 1")
    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    /** 성공 204(body 없음). Unit 반환이면 Retrofit이 빈 본문을 허용한다. */
    @POST("auth/logout")
    suspend fun logout()

    @GET("users/me")
    suspend fun me(): UserMeResponse

    companion object {
        /** 이 헤더가 있는 요청에는 Bearer를 붙이지 않고 토큰 갱신도 하지 않는다. 전송 전에 제거된다. */
        const val NO_AUTH_HEADER = "X-DQ-No-Auth"
    }
}
