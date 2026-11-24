package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.SensitiveMasker
import com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest
import com.dailyquest.app.data.remote.dto.PasswordResetRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 비로그인 상태의 비밀번호 재설정 API 계약을 MockWebServer로 검증한다(실제 서버는 호출하지 않는다). */
class PasswordResetRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: PasswordResetRepository
    private var expired = 0

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(null))
        val api = NetworkModule.createApi(tokens, { expired++ }, server.url("/api/v1/").toString())
        repo = PasswordResetRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun err(status: Int, code: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("""{"code":"$code","message":"m"}""")

    @Test fun requestSerializesEmailAndHandles204WithoutAuthHeader() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ResetResult.Success, repo.request("  user@example.com "))
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/api/v1/auth/password-reset/request", req.path)
        assertEquals("""{"email":"user@example.com"}""", req.body.readUtf8())
        assertNull(req.getHeader("Authorization")); assertNull(req.getHeader("X-DQ-No-Auth"))
        assertEquals(0, expired)
    }

    @Test fun verifyKeepsLeadingZeroAsString() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ResetResult.Success, repo.verify("a@b.com", "012345"))
        val req = server.takeRequest()
        assertEquals("/api/v1/auth/password-reset/verify", req.path)
        assertEquals("""{"email":"a@b.com","code":"012345"}""", req.body.readUtf8())
        assertNull(req.getHeader("Authorization"))
    }

    @Test fun confirmSendsSameCodeAndNewPassword() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ResetResult.Success, repo.confirm("a@b.com", "000123", "N3wP@ssword"))
        val req = server.takeRequest()
        assertEquals("/api/v1/auth/password-reset/confirm", req.path)
        assertEquals("""{"email":"a@b.com","code":"000123","newPassword":"N3wP@ssword"}""", req.body.readUtf8())
        assertNull(req.getHeader("Authorization"))
    }

    @Test fun invalidCodeIsDefiniteFailureNotSessionExpiry() = runBlocking {
        server.enqueue(err(400, "INVALID_RESET_CODE"))
        val r = repo.verify("a@b.com", "111111")
        assertTrue(r is ResetResult.Failed)
        assertTrue(PasswordResetMessages.isInvalidCode((r as ResetResult.Failed).error))
        assertEquals(0, expired)
    }

    @Test fun mailUnavailableIsDefiniteFailure() = runBlocking {
        server.enqueue(err(503, "MAIL_UNAVAILABLE"))
        val r = repo.request("a@b.com")
        assertTrue(r is ResetResult.Failed)
        assertEquals(PasswordResetMessages.forRequest((r as ResetResult.Failed).error), PasswordResetMessages.forRequest(r.error))
    }

    @Test fun validationErrorIsDefiniteFailure() = runBlocking {
        server.enqueue(err(400, "VALIDATION_ERROR"))
        assertTrue(repo.confirm("a@b.com", "123456", "short") is ResetResult.Failed)
    }

    @Test fun serverErrorIsUnconfirmed() = runBlocking {
        server.enqueue(err(500, "INTERNAL_ERROR"))
        assertTrue(repo.confirm("a@b.com", "123456", "N3wP@ssword") is ResetResult.Unconfirmed)
    }

    @Test fun lostResponseIsUnconfirmedAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.request("a@b.com") is ResetResult.Unconfirmed)
        assertEquals(1, server.requestCount)
    }

    @Test fun emailValidationAndNormalization() {
        assertNotNull(PasswordResetValidator.email(""))
        assertNotNull(PasswordResetValidator.email("not-an-email"))
        assertNull(PasswordResetValidator.email("user@example.com"))
        assertEquals("User@Example.com", PasswordResetValidator.normalizeEmail("  User@Example.com "))
    }

    @Test fun codeMustBeSixDigitsAndKeepsLeadingZeros() {
        assertEquals("012345", PasswordResetValidator.sanitizeCode("01-23 45 99"))
        assertEquals("", PasswordResetValidator.sanitizeCode("abc"))
        assertNotNull(PasswordResetValidator.code("12345"))
        assertNull(PasswordResetValidator.code("000000"))
    }

    @Test fun passwordLengthAndConfirmation() {
        assertNotNull(PasswordResetValidator.newPassword("short", "short"))
        assertNotNull(PasswordResetValidator.newPassword("a".repeat(65), "a".repeat(65)))
        assertNotNull(PasswordResetValidator.newPassword("N3wP@ssword", "different1"))
        assertNull(PasswordResetValidator.newPassword("N3wP@ssword", "N3wP@ssword"))
        assertNull(PasswordResetValidator.newPassword("a".repeat(64), "a".repeat(64)))
    }

    @Test fun dtoToStringMasksSecrets() {
        val s = PasswordResetConfirmRequest("a@b.com", "123456", "N3wP@ssword").toString() + PasswordResetRequest("a@b.com")
        assertFalse(s.contains("123456")); assertFalse(s.contains("N3wP@ssword")); assertFalse(s.contains("a@b.com"))
    }

    @Test fun maskerHidesRequestCodeAndEmailButKeepsResponseErrorCode() {
        val req = SensitiveMasker.maskRequestJson("""{"email":"a@b.com","code":"123456","newPassword":"x1234567"}""")
        assertFalse(req.contains("a@b.com")); assertFalse(req.contains("123456")); assertFalse(req.contains("x1234567"))
        assertTrue(SensitiveMasker.maskJson("""{"code":"INVALID_RESET_CODE"}""").contains("INVALID_RESET_CODE"))
    }
}
