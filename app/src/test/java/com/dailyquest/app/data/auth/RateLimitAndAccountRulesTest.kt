package com.dailyquest.app.data.auth

import com.dailyquest.app.data.profile.ProfileValidator
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.parseRetryAfter
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 백엔드 429(TOO_MANY_REQUESTS, Retry-After)와 계정 입력 규칙(이메일 소문자, 비밀번호 72바이트, 닉네임 접두어). 실서버는 호출하지 않는다. */
class RateLimitAndAccountRulesTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DqApi
    private val msg = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore())
        val session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
    }

    @After fun tearDown() = server.shutdown()

    private fun tooMany(retryAfter: String?) = MockResponse().setResponseCode(429)
        .setHeader("Content-Type", "application/json")
        .apply { if (retryAfter != null) setHeader("Retry-After", retryAfter) }
        .setBody("""{"code":"TOO_MANY_REQUESTS","message":"server"}""")

    private fun loginFailure(retryAfter: String?): ApiException.Http = runBlocking {
        server.enqueue(tooMany(retryAfter))
        val r = apiCall { api.login(LoginRequest("a@b.c", "password1")) }
        ((r as ApiResult.Failure).error as ApiException.Http)
    }

    @Test fun rateLimitedLoginIsNotRetriedAndShowsRetryAfter() {
        val e = loginFailure("30")
        assertEquals(1, server.requestCount)
        assertTrue(e.isRateLimited)
        assertEquals(30L, e.retryAfterSeconds)
        assertEquals("$msg (30초 후)", AuthErrorMessages.forApi(e))
    }

    @Test fun rateLimitedWithoutOrWithInvalidRetryAfterUsesDefaultMessage() {
        for (h in listOf(null, "", "abc", "0", "-5", "Wed, 21 Oct 2026 07:28:00 GMT")) {
            val e = loginFailure(h)
            assertNull("header=$h", e.retryAfterSeconds)
            assertEquals(msg, AuthErrorMessages.forApi(e))
        }
        assertEquals(6, server.requestCount)
    }

    @Test fun retryAfterFormattingAndClamp() {
        assertEquals(90L, parseRetryAfter(" 90 "))
        assertEquals(86_400L, parseRetryAfter("999999"))
        assertEquals("$msg (약 10분 후)", rateLimited(600))
        assertEquals("$msg (약 2분 후)", rateLimited(61))
        assertEquals(msg, rateLimited(null))
    }

    @Test fun rateLimitedMapsToCommonMessageOnOtherScreens() {
        val e = loginFailure("5")
        assertEquals("$msg (5초 후)", com.dailyquest.app.data.proof.ProofErrorMessages.forApi(e))
        assertEquals("$msg (5초 후)", com.dailyquest.app.data.room.RoomErrorMessages.forApi(e))
        assertEquals("$msg (5초 후)", com.dailyquest.app.data.mission.MissionErrorMessages.forApi(e))
        assertEquals("$msg (5초 후)", com.dailyquest.app.data.auth.PasswordResetMessages.forRequest(e))
        assertEquals("$msg (5초 후)", com.dailyquest.app.data.auth.PasswordResetMessages.forConfirm(e))
    }

    @Test fun rateLimitedIsNotTreatedAsSessionExpiry() = runBlocking {
        server.enqueue(tooMany("1"))
        val r = apiCall { api.login(LoginRequest("a@b.c", "password1")) }
        assertNotNull((r as ApiResult.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test fun emailIsTrimmedAndLowercased() {
        assertEquals("user@example.com", AuthValidator.normalizeEmail("  User@Example.COM "))
        assertEquals("user@example.com", PasswordResetValidator.normalizeEmail(" USER@example.com"))
        assertNull(AuthValidator.email("User@Example.com"))
    }

    @Test fun passwordBoundaries() {
        assertNull(AuthValidator.password("a".repeat(8)))
        assertNotNull(AuthValidator.password("a".repeat(7)))
        assertNull(AuthValidator.password("a".repeat(64)))
        assertNotNull(AuthValidator.password("a".repeat(65)))
        assertNull(AuthValidator.password("가".repeat(24)))
        val tooLong = AuthValidator.password("가".repeat(25))
        assertTrue(tooLong!!.contains("한글은 24자"))
        assertNull(AuthValidator.password("가".repeat(8) + "a".repeat(48)))
        assertNotNull(AuthValidator.password("가".repeat(8) + "a".repeat(49)))
        assertNull(PasswordResetValidator.newPassword("가".repeat(24), "가".repeat(24)))
        assertNotNull(PasswordResetValidator.newPassword("가".repeat(25), "가".repeat(25)))
        assertNotNull(ProfileValidator.newPassword("가".repeat(25), "old"))
        assertNull(ProfileValidator.newPassword("가".repeat(24), "old"))
    }

    @Test fun nicknameTrimAndWithdrawnPrefix() {
        assertNull(AuthValidator.nickname("  ab  "))
        assertNotNull(AuthValidator.nickname(" a "))
        assertNotNull(AuthValidator.nickname("a".repeat(13)))
        assertNull(AuthValidator.nickname("a".repeat(12)))
        assertEquals("사용할 수 없는 닉네임이에요.", AuthValidator.nickname("탈퇴회원12"))
        assertEquals("사용할 수 없는 닉네임이에요.", AuthValidator.nickname("  탈퇴회원  "))
        assertNull(AuthValidator.nickname("내탈퇴회원"))
        assertEquals("사용할 수 없는 닉네임이에요.", ProfileValidator.nickname("탈퇴회원1", "old"))
        assertNull(ProfileValidator.nickname("  새닉네임 ", "old"))
        assertEquals("새닉네임", ProfileValidator.normalizeNickname("  새닉네임 "))
    }
}
