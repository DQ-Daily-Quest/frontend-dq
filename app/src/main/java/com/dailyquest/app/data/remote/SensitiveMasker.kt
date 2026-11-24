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

    // 요청 본문에는 비밀번호 재설정의 인증 코드와 이메일도 있으므로 응답의 오류 code와 구분해 요청에서만 추가로 가린다.
    private val requestOnlyField = Regex("\"(code|email)\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"", RegexOption.IGNORE_CASE)

    fun maskJson(text: String): String = jsonField.replace(text) { "\"${it.groupValues[1]}\":\"***\"" }

    fun maskRequestJson(text: String): String =
        requestOnlyField.replace(maskJson(text)) { "\"${it.groupValues[1]}\":\"***\"" }
}
