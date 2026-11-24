package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.FcmTokenRequest
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest
import com.dailyquest.app.data.remote.dto.PasswordResetRequest
import com.dailyquest.app.data.remote.dto.PasswordResetVerifyRequest
import com.dailyquest.app.data.remote.dto.RefreshRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 백엔드 v0.8: 공개 API는 Authorization 없이, 로그아웃·FCM 토큰 등록은 현재 Access Token으로 호출한다. 실서버는 호출하지 않는다. */
class LogoutAndPublicApiAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: SessionTokens
    private lateinit var session: SessionManager
    private lateinit var api: DqApi

    private val json = "application/json"
    private fun tokenBody(a: String, r: String) =
        """{"accessToken":"$a","refreshToken":"$r","expiresIn":1800,"user":{"userId":1,"nickname":"n","avatarUrl":null,"coins":0},"isNewUser":false}"""
    private fun err(code: String, status: Int) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", json).setBody("""{"code":"$code","message":"m"}""")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = SessionTokens(InMemoryTokenStore())
        session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
    }

    @After fun tearDown() = server.shutdown()

    private fun dispatch(f: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = f(request) }
    }

    @Test fun publicEndpointsNeverCarryAuthorizationEvenWithStoredTokens() = runBlocking {
        tokens.set(TokenPair("ACCESS", "REFRESH"))
        dispatch { req ->
            when {
                req.path!!.contains("auth/password-reset") -> MockResponse().setResponseCode(204)
                req.path!!.endsWith("auth/signup") -> MockResponse().setResponseCode(201).setHeader("Content-Type", json)
                    .setBody("""{"userId":1,"email":"a@b.c","nickname":"n"}""")
                else -> MockResponse().setHeader("Content-Type", json).setBody(tokenBody("N", "R"))
            }
        }
        runCatching { api.signup(SignupRequest("a@b.c", "pw123456", "n")) }
        api.login(LoginRequest("a@b.c", "pw"))
        api.oauthLogin("google", OAuthLoginRequest("id"))
        api.refresh(RefreshRequest("REFRESH"))
        api.requestPasswordReset(PasswordResetRequest("a@b.c"))
        api.verifyPasswordReset(PasswordResetVerifyRequest("a@b.c", "000123"))
        api.confirmPasswordReset(PasswordResetConfirmRequest("a@b.c", "000123", "N3wP@ssword"))
        val paths = mutableListOf<String>()
        repeat(server.requestCount) {
            val r = server.takeRequest()
            paths += r.path!!
            assertNull("${r.path} must be public", r.getHeader("Authorization"))
            assertNull(r.getHeader(DqApi.NO_AUTH_HEADER))
            assertNull(r.getHeader("X-DQ-No-Retry"))
        }
        assertEquals(7, paths.size)
    }

    @Test fun logoutSendsPostWithCurrentAccessToken() = runBlocking {
        tokens.set(TokenPair("CURRENT", "R"))
        dispatch { MockResponse().setResponseCode(204) }
        assertTrue(apiCall { api.logout() } is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/auth/logout", req.path)
        assertEquals("Bearer CURRENT", req.getHeader("Authorization"))
    }

    @Test fun logoutWithExpiredAccessTokenRefreshesOnceThenRetriesWithNewToken() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        dispatch { req ->
            when {
                req.path!!.endsWith("auth/refresh") -> MockResponse().setHeader("Content-Type", json).setBody(tokenBody("NEW", "R2"))
                req.getHeader("Authorization") == "Bearer NEW" -> MockResponse().setResponseCode(204)
                else -> err("TOKEN_EXPIRED", 401)
            }
        }
        val repo = AuthRepository(api, tokens, session)
        repo.logout()
        val first = server.takeRequest(); val refresh = server.takeRequest(); val retry = server.takeRequest()
        assertEquals("Bearer OLD", first.getHeader("Authorization"))
        assertTrue(refresh.body.readUtf8().contains("R1"))
        assertEquals("Bearer NEW", retry.getHeader("Authorization"))
        assertEquals("/api/v1/auth/logout", retry.path)
        assertEquals(3, server.requestCount)
        assertNull(tokens.get())
        assertEquals(SessionState.Unauthenticated(LogoutReason.USER), session.state.value)
    }

    @Test fun logoutStillClearsLocalSessionWhenRefreshIsRejected() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        dispatch { req ->
            if (req.path!!.endsWith("auth/refresh")) err("TOKEN_INVALID", 401) else err("TOKEN_EXPIRED", 401)
        }
        AuthRepository(api, tokens, session).logout()
        assertNull(tokens.get())
        assertTrue(session.state.value is SessionState.Unauthenticated)
    }

    @Test fun fcmTokenRegistrationUsesCurrentAccountBearerAndHandles204() = runBlocking {
        tokens.set(TokenPair("ACCOUNT-B", "R"))
        dispatch { MockResponse().setResponseCode(204) }
        api.updateFcmToken(FcmTokenRequest("device-token"))
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/v1/users/me/fcm-token", req.path)
        assertEquals("Bearer ACCOUNT-B", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("device-token"))
    }
}
