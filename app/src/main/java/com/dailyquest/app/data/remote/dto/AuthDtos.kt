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
data class OAuthLoginRequest(val idToken: String) {
    override fun toString() = "OAuthLoginRequest(idToken=***)"
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

/** POST /auth/password-reset/request (로그인 없이 호출, 성공 204). 이메일 가입 여부와 무관하게 항상 204다. */
@Serializable
data class PasswordResetRequest(val email: String) {
    override fun toString() = "PasswordResetRequest(email=***)"
}

/** POST /auth/password-reset/verify (성공 204). 코드는 소모되지 않는다. code는 앞자리 0을 보존하는 문자열이다. */
@Serializable
data class PasswordResetVerifyRequest(val email: String, val code: String) {
    override fun toString() = "PasswordResetVerifyRequest(email=***, code=***)"
}

/** POST /auth/password-reset/confirm (성공 204). 성공하면 서버가 코드를 폐기하고 Refresh Token을 모두 폐기한다. */
@Serializable
data class PasswordResetConfirmRequest(val email: String, val code: String, val newPassword: String) {
    override fun toString() = "PasswordResetConfirmRequest(email=***, code=***, newPassword=***)"
}
