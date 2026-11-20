package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.SignupRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** MockWebServer로 실제 서버 토큰을 조작하지 않고 만료·갱신 시나리오를 재현한다. */
class AuthNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: SessionTokens
    private lateinit var session: SessionManager
    private lateinit var api: DqApi
    private var cleaned = 0

    private val me = """{"userId":1,"email":"a@b.c","nickname":"n","coins":0,"avatar":{},"streak":{"current":0,"longest":0},"stats":{"roomCount":0,"completedMissionCount":0},"createdAt":"2025-01-01T00:00:00Z"}"""
    private fun tokenBody(a: String, r: String) =
        """{"accessToken":"$a","refreshToken":"$r","expiresIn":1800,"user":{"userId":1,"nickname":"n","avatarUrl":null,"coins":0},"isNewUser":false}"""
    private fun err(code: String, status: Int) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("""{"code":"$code","message":"m"}""")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = SessionTokens(InMemoryTokenStore())
        session = SessionManager(tokens, listOf({ cleaned++ }))
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
    }

    @After fun tearDown() = server.shutdown()

    private fun dispatch(f: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = f(request) }
    }

    @Test fun loginSuccessSendsNoAuthHeaderAndUsesRelativePath() = runBlocking {
        tokens.set(TokenPair("stale", "stale"))
        dispatch { MockResponse().setBody(tokenBody("A", "R")).setHeader("Content-Type", "application/json") }
        val r = api.login(LoginRequest("a@b.c", "pw"))
        assertEquals("A", r.accessToken)
        val req = server.takeRequest()
        assertEquals("/api/v1/auth/login", req.path)
        assertNull(req.getHeader("Authorization")); assertNull(req.getHeader(DqApi.NO_AUTH_HEADER))
    }

    @Test fun invalidCredentialsDoesNotTriggerRefresh() = runBlocking {
        tokens.set(TokenPair("A", "R"))
        dispatch { err("INVALID_CREDENTIALS", 401) }
        val r = apiCall { api.login(LoginRequest("a@b.c", "bad")) }
        val e = (r as ApiResult.Failure).error as ApiException.Http
        assertEquals("INVALID_CREDENTIALS", e.code); assertEquals(401, e.status)
        assertEquals(1, server.requestCount)
        assertEquals("A", tokens.get()?.accessToken)
        assertFalse(e.report.contains("bad"))
    }

    @Test fun duplicateEmailAndNicknameParsed() = runBlocking {
        dispatch { err(if (it.requestUrl!!.queryParameter("x") == null) "DUPLICATE_EMAIL" else "DUPLICATE_NICKNAME", 409) }
        val r = apiCall { api.signup(SignupRequest("a@b.c", "password1", "nn")) } as ApiResult.Failure
        assertEquals("DUPLICATE_EMAIL", (r.error as ApiException.Http).code)
    }

    @Test fun logout204HandledWithoutBody() = runBlocking {
        tokens.set(TokenPair("A", "R"))
        dispatch { MockResponse().setResponseCode(204) }
        assertTrue(apiCall { api.logout() } is ApiResult.Success)
        assertEquals("Bearer A", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun expiredTokenRefreshesAndRetriesOnce() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        dispatch { req ->
            when {
                req.path!!.endsWith("auth/refresh") -> MockResponse().setBody(tokenBody("NEW", "R2")).setHeader("Content-Type", "application/json")
                req.getHeader("Authorization") == "Bearer NEW" -> MockResponse().setBody(me).setHeader("Content-Type", "application/json")
                else -> err("TOKEN_EXPIRED", 401)
            }
        }
        assertEquals(1L, api.me().userId)
        assertEquals(TokenPair("NEW", "R2"), tokens.get())
        assertEquals(3, server.requestCount)
        server.takeRequest(); val refresh = server.takeRequest()
        assertTrue(refresh.body.readUtf8().contains("R1"))
        assertNull(refresh.getHeader("Authorization"))
    }

    @Test fun concurrentExpiredRequestsRefreshOnlyOnceAndRotateToken() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        val refreshCalls = AtomicInteger()
        dispatch { req ->
            when {
                req.path!!.endsWith("auth/refresh") -> {
                    refreshCalls.incrementAndGet()
                    // 이전 Refresh Token 재사용은 서버가 거절한다.
                    if (req.body.readUtf8().contains("R1")) MockResponse().setBody(tokenBody("NEW", "R2")).setHeader("Content-Type", "application/json")
                        .setBodyDelay(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                    else err("INVALID_REFRESH_TOKEN", 401)
                }
                req.getHeader("Authorization") == "Bearer NEW" -> MockResponse().setBody(me).setHeader("Content-Type", "application/json")
                else -> err("TOKEN_EXPIRED", 401)
            }
        }
        (1..6).map { async(kotlinx.coroutines.Dispatchers.IO) { api.me() } }.awaitAll()
        assertEquals(1, refreshCalls.get())
        assertEquals(TokenPair("NEW", "R2"), tokens.get())
    }

    @Test fun invalidRefreshTokenExpiresSession() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        session.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        dispatch { req -> if (req.path!!.endsWith("auth/refresh")) err("INVALID_REFRESH_TOKEN", 401) else err("TOKEN_EXPIRED", 401) }
        val r = apiCall { api.me() } as ApiResult.Failure
        assertEquals("TOKEN_EXPIRED", (r.error as ApiException.Http).code)
        assertNull(tokens.get())
        assertEquals(SessionState.Unauthenticated(LogoutReason.EXPIRED), session.state.value)
        assertEquals(1, cleaned)
    }

    @Test fun networkErrorDuringRefreshKeepsSession() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        session.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        dispatch { req ->
            if (req.path!!.endsWith("auth/refresh")) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
            else err("TOKEN_EXPIRED", 401)
        }
        val r = apiCall { api.me() }
        assertTrue((r as ApiResult.Failure).error is ApiException.Network)
        assertEquals(TokenPair("OLD", "R1"), tokens.get())
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun serverErrorDuringRefreshKeepsSession() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        session.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        dispatch { req -> if (req.path!!.endsWith("auth/refresh")) MockResponse().setResponseCode(503) else err("TOKEN_EXPIRED", 401) }
        assertTrue((apiCall { api.me() } as ApiResult.Failure).error is ApiException.Network)
        assertEquals(TokenPair("OLD", "R1"), tokens.get())
    }

    @Test fun noInfiniteRetryWhenRetryStillExpired() = runBlocking {
        tokens.set(TokenPair("OLD", "R1"))
        val refreshCalls = AtomicInteger()
        dispatch { req ->
            if (req.path!!.endsWith("auth/refresh")) { refreshCalls.incrementAndGet(); MockResponse().setBody(tokenBody("NEW", "R2")).setHeader("Content-Type", "application/json") }
            else err("TOKEN_EXPIRED", 401)
        }
        assertTrue(apiCall { api.me() } is ApiResult.Failure)
        assertEquals(1, refreshCalls.get())
        assertEquals(3, server.requestCount)
    }

    @Test fun unauthorizedOnCurrentTokenExpiresSessionButStaleTokenDoesNot() = runBlocking {
        tokens.set(TokenPair("A", "R"))
        session.markAuthenticated(UserProfile(1, "e", "n", 0, 0, 0, 0, 0))
        dispatch { err("UNAUTHORIZED", 401) }
        apiCall { api.me() }
        assertEquals(SessionState.Unauthenticated(LogoutReason.EXPIRED), session.state.value)

    }

    @Test fun lateUnauthorizedForReplacedTokenDoesNotEndNewSession() = runBlocking {
        tokens.set(TokenPair("A", "R"))
        session.markAuthenticated(UserProfile(2, "e2", "n2", 0, 0, 0, 0, 0))
        // 요청이 서버에 도달한 사이 다른 사용자의 로그인으로 토큰이 교체된 상황
        dispatch { tokens.set(TokenPair("B", "RB")); err("UNAUTHORIZED", 401) }
        apiCall { api.me() }
        assertTrue(session.state.value is SessionState.Authenticated)
        assertEquals("B", tokens.get()?.accessToken)
    }

    @Test fun repositoryLoginThenProfileAndLogoutClearsEverything() = runBlocking {
        dispatch { req ->
            when {
                req.path!!.endsWith("auth/login") -> MockResponse().setBody(tokenBody("A", "R")).setHeader("Content-Type", "application/json")
                req.path!!.endsWith("users/me") -> MockResponse().setBody(me).setHeader("Content-Type", "application/json")
                else -> MockResponse().setResponseCode(204)
            }
        }
        val repo = AuthRepository(api, tokens, session)
        assertTrue(repo.login("a@b.c", "pw") is ApiResult.Success)
        assertTrue(session.state.value is SessionState.Authenticated)
        assertEquals("A", tokens.get()?.accessToken)
        repo.logout()
        assertNull(tokens.get())
        assertEquals(SessionState.Unauthenticated(LogoutReason.USER), session.state.value)
        assertEquals(1, cleaned)
    }

    @Test fun repositoryLoginRollsBackWhenProfileFails() = runBlocking {
        dispatch { req ->
            when {
                req.path!!.endsWith("auth/login") -> MockResponse().setBody(tokenBody("A", "R")).setHeader("Content-Type", "application/json")
                req.path!!.endsWith("users/me") -> MockResponse().setResponseCode(500)
                else -> MockResponse().setResponseCode(204)
            }
        }
        val repo = AuthRepository(api, tokens, session)
        assertTrue(repo.login("a@b.c", "pw") is ApiResult.Failure)
        assertNull(tokens.get())
        assertFalse(session.state.value is SessionState.Authenticated)
    }

    @Test fun bootstrapDistinguishesNoTokenNetworkAndAuthFailure() = runBlocking {
        val repo = AuthRepository(api, tokens, session)
        repo.bootstrap()
        assertEquals(SessionState.Unauthenticated(LogoutReason.NONE), session.state.value)

        tokens.set(TokenPair("A", "R"))
        dispatch { err("UNAUTHORIZED", 401) }
        repo.bootstrap()
        assertEquals(SessionState.Unauthenticated(LogoutReason.EXPIRED), session.state.value)
        assertNull(tokens.get())
    }

    @Test fun bootstrapNetworkFailureKeepsTokens() = runBlocking {
        tokens.set(TokenPair("A", "R"))
        server.shutdown()
        AuthRepository(api, tokens, session).bootstrap()
        assertEquals(SessionState.BootstrapFailed(isNetwork = true), session.state.value)
        assertNotNull(tokens.get())
    }

    @Test fun protectedCallWithoutTokenSendsNoBearer() = runBlocking {
        dispatch { err("UNAUTHORIZED", 401) }
        apiCall { api.me() }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
