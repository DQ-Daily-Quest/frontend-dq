package com.dailyquest.app

import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.UserMeResponse
import com.dailyquest.app.navigation.Routes

/** 기존 Mock UI 테스트용: 가짜 토큰과 가짜 API로 로그인된 상태에서 시작하고 개발용 Catalog로 진입한다. */
class TestDQApplication : DQApplication() {
    override fun createContainer() = AppContainer(
        tokenStore = InMemoryTokenStore(TokenPair("test-access", "test-refresh")),
        apiFactory = { _, _ -> FakeApi },
        startAfterAuth = Routes.CATALOG,
    )

    private object FakeApi : DqApi {
        override suspend fun signup(body: SignupRequest): SignupResponse = error("not used")
        override suspend fun login(body: LoginRequest): TokenResponse = error("not used")
        override suspend fun refresh(body: RefreshRequest): TokenResponse = error("not used")
        override suspend fun logout() = Unit
        override suspend fun me() = UserMeResponse(userId = 1, email = "ui-test@example.com", nickname = "uitest")
    }
}
