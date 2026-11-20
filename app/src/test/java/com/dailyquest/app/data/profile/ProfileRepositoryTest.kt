package com.dailyquest.app.data.profile

import com.dailyquest.app.data.auth.AuthRepository
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 닉네임·비밀번호·탈퇴 API 요청 형태와 세션 처리를 MockWebServer로 검증한다(실제 서버는 호출하지 않는다). */
class ProfileRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var tokens: SessionTokens
    private lateinit var repo: ProfileRepository
    private var cleaned = 0

    private fun profile(id: Long = 1, nick: String = "tester") = UserProfile(id, "t@example.com", nick, 100, 2, 5, 1, 3)

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        session = SessionManager(tokens, listOf({ cleaned++ }))
        val api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
        repo = ProfileRepository(api, session, AuthRepository(api, tokens, session))
        session.markAuthenticated(profile())
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun err(status: Int, code: String) = json("""{"code":"$code","message":"m"}""", status)
    private val meBody = """{"userId":1,"email":"t@example.com","nickname":"새닉","coins":120,"streak":{"current":4,"longest":9},"stats":{"roomCount":2,"completedMissionCount":7},"createdAt":"2025-03-04T01:02:03Z"}"""

    private fun unauthenticatedReason() = (session.state.value as? SessionState.Unauthenticated)?.reason

    @Test fun nicknameSendsOnlyNicknameAndUpdatesSession() = runBlocking {
        server.enqueue(json(meBody))
        assertTrue(repo.updateNickname("  새닉 ") is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method); assertEquals("/api/v1/users/me", req.path)
        assertEquals("""{"nickname":"새닉"}""", req.body.readUtf8())
        val p = (session.state.value as SessionState.Authenticated).profile
        assertEquals("새닉", p.nickname); assertEquals(4, p.streakCurrent); assertEquals("2025-03-04T01:02:03Z", p.createdAt)
    }

    @Test fun duplicateNicknameKeepsSessionAndMapsMessage() = runBlocking {
        server.enqueue(err(409, "DUPLICATE_NICKNAME"))
        val r = repo.updateNickname("다른닉") as ApiResult.Failure
        assertTrue(ProfileErrorMessages.forNickname(r.error).contains("닉네임"))
        assertEquals("tester", (session.state.value as SessionState.Authenticated).profile.nickname)
    }

    @Test fun previousAccountProfileIsNotApplied() {
        val applied = session.applyProfileIfCurrent(99, profile(99, "other"))
        assertFalse(applied)
        assertEquals("tester", (session.state.value as SessionState.Authenticated).profile.nickname)
    }

    @Test fun passwordSuccessEndsSessionAndClearsTokens() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AccountResult.Success, repo.changePassword("oldpass12", "newpass12"))
        val req = server.takeRequest()
        assertEquals("PATCH", req.method); assertEquals("/api/v1/users/me/password", req.path)
        assertEquals(LogoutReason.PASSWORD_CHANGED, unauthenticatedReason())
        assertNull(tokens.get()); assertEquals(1, cleaned)
    }

    @Test fun passwordWrongCurrentKeepsSession() = runBlocking {
        server.enqueue(err(401, "INVALID_CREDENTIALS"))
        val r = repo.changePassword("bad", "newpass12") as AccountResult.Failed
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, (r.error as ApiException.Http).code)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun passwordSocialAccountReportsNoPasswordSet() = runBlocking {
        server.enqueue(err(409, "NO_PASSWORD_SET"))
        val r = repo.changePassword("x", "newpass12") as AccountResult.Failed
        assertEquals(ApiErrorCode.NO_PASSWORD_SET, (r.error as ApiException.Http).code)
    }

    @Test fun passwordLostResponseIsUnconfirmedAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.changePassword("oldpass12", "newpass12") is AccountResult.Unconfirmed)
        assertEquals(1, server.requestCount)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun withdrawSendsPasswordBodyAndEndsSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AccountResult.Success, repo.withdraw("pw123456"))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method); assertEquals("/api/v1/users/me", req.path)
        assertEquals("""{"password":"pw123456"}""", req.body.readUtf8())
        assertEquals(LogoutReason.WITHDRAWN, unauthenticatedReason())
        assertNull(tokens.get())
    }

    @Test fun withdrawSocialSendsEmptyObjectWithoutPassword() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        repo.withdraw("")
        assertEquals("""{}""", server.takeRequest().body.readUtf8())
    }

    @Test fun withdrawOmitsPasswordForSocialOnlyAccountEvenIfGiven() = runBlocking {
        session.markAuthenticated(profile().copy(hasPassword = false))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.withdraw("leftover")
        assertEquals("""{}""", server.takeRequest().body.readUtf8())
    }

    @Test fun withdrawSendsPasswordWhenHasPasswordTrue() = runBlocking {
        session.markAuthenticated(profile().copy(hasPassword = true))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.withdraw("pw123456")
        assertEquals("""{"password":"pw123456"}""", server.takeRequest().body.readUtf8())
    }

    @Test fun hasPasswordIsParsedAndKeptAfterNicknameUpdate() = runBlocking {
        server.enqueue(json(meBody.replace("\"nickname\"", "\"hasPassword\":false,\"nickname\"")))
        repo.updateNickname("새닉")
        assertEquals(false, (session.state.value as SessionState.Authenticated).profile.hasPassword)
    }

    @Test fun missingHasPasswordStaysUnknown() = runBlocking {
        server.enqueue(json(meBody))
        repo.updateNickname("새닉")
        assertNull((session.state.value as SessionState.Authenticated).profile.hasPassword)
    }

    @Test fun accountSwitchDoesNotKeepPreviousHasPassword() {
        session.markAuthenticated(profile(1).copy(hasPassword = false))
        session.markAuthenticated(profile(2))
        assertNull(session.currentHasPassword())
    }

    @Test fun withdrawOwnerBlockedKeepsSession() = runBlocking {
        server.enqueue(err(409, "OWNER_MUST_TRANSFER"))
        val r = repo.withdraw("pw123456") as AccountResult.Failed
        assertEquals(ApiErrorCode.OWNER_MUST_TRANSFER, (r.error as ApiException.Http).code)
        assertEquals(1, server.requestCount)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun withdrawLostResponseThen401IsNotConfirmedAsWithdrawn() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(err(401, "UNAUTHORIZED"))
        assertTrue(repo.withdraw("pw123456") is AccountResult.Unconfirmed)
        assertEquals(LogoutReason.WITHDRAW_UNCONFIRMED, unauthenticatedReason())
        assertEquals(2, server.requestCount)
    }

    @Test fun withdrawLostResponseThenExpiredTokenIsNotConfirmedAsWithdrawn() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(err(401, "TOKEN_EXPIRED"))
        server.enqueue(err(401, "INVALID_REFRESH_TOKEN"))
        assertTrue(repo.withdraw("pw123456") is AccountResult.Unconfirmed)
        assertEquals(LogoutReason.WITHDRAW_UNCONFIRMED, unauthenticatedReason())
    }

    @Test fun withdrawLostResponseVerifiedNotAppliedBy200() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json(meBody))
        val r = repo.withdraw("pw123456") as AccountResult.Failed
        assertTrue(r.verifiedNotApplied)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun withdrawLostResponseAndUnreachableVerificationIsUnconfirmed() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(repo.withdraw("pw123456") is AccountResult.Unconfirmed)
        assertTrue(session.state.value is SessionState.Authenticated)
    }
}
