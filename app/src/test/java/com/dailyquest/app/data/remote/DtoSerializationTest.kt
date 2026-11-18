package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class DtoSerializationTest {
    private val json = NetworkModule.json

    @Test fun loginResponseParsesRealShapeIncludingIsNewUser() {
        val body = """{"accessToken":"a","refreshToken":"r","expiresIn":1800,
            "user":{"userId":10,"nickname":"qa","avatarUrl":null,"coins":0},"isNewUser":true,"extra":1}"""
        val r = json.decodeFromString<TokenResponse>(body)
        assertEquals("a", r.accessToken); assertEquals(1800, r.expiresIn)
        assertEquals(10L, r.user.userId); assertNull(r.user.avatarUrl); assertTrue(r.isNewUser)
    }

    @Test fun swaggerNewUserNameIsNotAccepted() {
        val body = """{"accessToken":"a","refreshToken":"r","expiresIn":1,"user":{"userId":1,"nickname":"n"},"newUser":true}"""
        assertFalse(json.decodeFromString<TokenResponse>(body).isNewUser)
    }

    @Test fun signupResponseHasNoTokensAndNullableEmail() {
        val r = json.decodeFromString<SignupResponse>("""{"userId":1,"email":null,"nickname":"n"}""")
        assertNull(r.email); assertEquals(1L, r.userId)
    }

    @Test fun userMeParsesAvatarSlotsWithNull() {
        val body = """{"userId":1,"email":"a@b.c","nickname":"n","coins":5,
            "avatar":{"hair":1,"eyes":2,"skin":3,"top":4,"bottom":5,"shoes":6,"hat":null},
            "streak":{"current":2,"longest":3},"stats":{"roomCount":1,"completedMissionCount":2},"createdAt":"2025-01-01T00:00:00Z"}"""
        val r = json.decodeFromString<UserMeResponse>(body)
        assertEquals(3L, r.avatar.skin); assertNull(r.avatar.hat); assertEquals(3, r.streak.longest)
    }

    @Test fun errorWithoutTimestampParses() {
        val e = parseErrorBody("""{"code":"UNAUTHORIZED","message":"인증이 필요합니다."}""")!!
        assertEquals("UNAUTHORIZED", e.code); assertNull(e.timestamp)
        assertNull(parseErrorBody("not json")); assertNull(parseErrorBody(""))
    }

    @Test fun requestFieldNames() {
        val o = Json.parseToJsonElement(json.encodeToString(SignupRequest("e@x.com", "pw", "nick"))).jsonObject
        assertEquals(setOf("email", "password", "nickname"), o.keys)
        val l = Json.parseToJsonElement(json.encodeToString(LoginRequest("e@x.com", "pw"))).jsonObject
        assertEquals(setOf("email", "password"), l.keys)
        val f = Json.parseToJsonElement(json.encodeToString(RefreshRequest("t"))).jsonObject
        assertEquals(setOf("refreshToken"), f.keys)
    }

    @Test fun dtoToStringNeverLeaksSecrets() {
        assertFalse(SignupRequest("e", "secretpw", "n").toString().contains("secretpw"))
        assertFalse(LoginRequest("e", "secretpw").toString().contains("secretpw"))
        assertFalse(RefreshRequest("rtok").toString().contains("rtok"))
        val t = TokenResponse("atok", "rtok", 1, UserSummaryDto(1, "n")).toString()
        assertFalse(t.contains("atok") || t.contains("rtok"))
    }

    @Test fun maskerHidesSensitiveJsonFields() {
        val masked = SensitiveMasker.maskJson("""{"email":"a@b.c","password":"pw1234567","accessToken":"abc","refreshToken":"def"}""")
        assertTrue(masked.contains("a@b.c"))
        assertFalse(masked.contains("pw1234567") || masked.contains("abc") || masked.contains("def"))
    }
}
