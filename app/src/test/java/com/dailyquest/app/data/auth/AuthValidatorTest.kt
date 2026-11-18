package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiException
import org.junit.Assert.*
import org.junit.Test

class AuthValidatorTest {
    @Test fun email() {
        assertNull(AuthValidator.email("a@b.co")); assertNotNull(AuthValidator.email("a@b")); assertNotNull(AuthValidator.email(""))
    }
    @Test fun passwordBoundaries() {
        assertNotNull(AuthValidator.password("a".repeat(7))); assertNull(AuthValidator.password("a".repeat(8)))
        assertNull(AuthValidator.password("a".repeat(64))); assertNotNull(AuthValidator.password("a".repeat(65)))
    }
    @Test fun nicknameBoundaries() {
        assertNotNull(AuthValidator.nickname("a")); assertNull(AuthValidator.nickname("ab"))
        assertNull(AuthValidator.nickname("a".repeat(12))); assertNotNull(AuthValidator.nickname("a".repeat(13)))
    }
    @Test fun errorMessagesBranchOnCode() {
        fun http(status: Int, code: String?) = ApiException.Http(status, code, "ignored", "")
        assertTrue(AuthErrorMessages.forApi(http(401, "INVALID_CREDENTIALS")).contains("올바르지"))
        assertTrue(AuthErrorMessages.forApi(http(409, "DUPLICATE_EMAIL")).contains("이메일"))
        assertTrue(AuthErrorMessages.forApi(http(409, "DUPLICATE_NICKNAME")).contains("닉네임"))
        assertTrue(AuthErrorMessages.forApi(http(500, null)).contains("서버"))
        assertTrue(AuthErrorMessages.forApi(ApiException.Network(java.io.IOException())).contains("네트워크"))
    }
}
