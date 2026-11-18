package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 실제 서버 응답으로 검증한 필드명을 @SerialName으로 고정한다. (Swagger의 newUser 등은 실제 응답과 다름)

@Serializable
data class SignupRequest(val email: String, val password: String, val nickname: String) {
    override fun toString() = "SignupRequest(email=$email, password=***, nickname=$nickname)"
}

@Serializable
data class SignupResponse(
    val userId: Long,
    val email: String? = null,
    val nickname: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String) {
    override fun toString() = "LoginRequest(email=$email, password=***)"
}

@Serializable
data class RefreshRequest(val refreshToken: String) {
    override fun toString() = "RefreshRequest(refreshToken=***)"
}

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 0,
    val user: UserSummaryDto,
    @SerialName("isNewUser") val isNewUser: Boolean = false,
) {
    override fun toString() = "TokenResponse(user=$user, isNewUser=$isNewUser, tokens=***)"
}

@Serializable
data class UserSummaryDto(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String? = null,
    val coins: Int = 0,
)
