package com.dailyquest.app.data.remote

/** 백엔드 팀에 전달하는 오류 리포트와 로그에서 인증 정보를 가린다. */
object SensitiveMasker {
    private val SENSITIVE_KEYS = listOf(
        "password", "currentPassword", "newPassword", "accessToken", "refreshToken", "idToken", "token",
    )
    private val jsonField = Regex(
        "\"(${SENSITIVE_KEYS.joinToString("|")})\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"",
        RegexOption.IGNORE_CASE,
    )

    fun maskJson(text: String): String = jsonField.replace(text) { "\"${it.groupValues[1]}\":\"***\"" }
}
