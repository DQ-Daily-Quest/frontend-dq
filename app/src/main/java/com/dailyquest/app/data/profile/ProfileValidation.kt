package com.dailyquest.app.data.profile

import com.dailyquest.app.data.auth.AuthValidator

/** 서버 검증 규칙(닉네임 2~12자, 비밀번호 8~64자, 새 비밀번호는 현재와 달라야 함)과 같은 기준의 사전 검증. */
object ProfileValidator {
    const val NICKNAME_MIN = 2
    const val NICKNAME_MAX = 12
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64

    /** 서버로 보낼 닉네임. 앞뒤 공백은 제거한다. */
    fun normalizeNickname(value: String): String = value.trim()

    fun nickname(value: String, current: String?): String? {
        val v = normalizeNickname(value)
        return when {
            v.length !in NICKNAME_MIN..NICKNAME_MAX -> "닉네임은 $NICKNAME_MIN~${NICKNAME_MAX}자로 입력해 주세요."
            v.startsWith(AuthValidator.WITHDRAWN_NICKNAME_PREFIX) -> "사용할 수 없는 닉네임이에요."
            v == current -> "현재 닉네임과 같아요."
            else -> null
        }
    }

    fun currentPassword(value: String): String? =
        if (value.isEmpty()) "현재 비밀번호를 입력해 주세요." else null

    fun newPassword(value: String, current: String): String? = when {
        AuthValidator.passwordError(value, "새 비밀번호") != null -> AuthValidator.passwordError(value, "새 비밀번호")
        value == current -> "새 비밀번호는 현재 비밀번호와 달라야 해요."
        else -> null
    }

    fun confirmation(newPassword: String, confirm: String): String? =
        if (newPassword == confirm) null else "새 비밀번호가 서로 일치하지 않아요."

    /** 첫 번째로 발견한 오류. 없으면 null. */
    fun passwordChange(current: String, new: String, confirm: String): String? =
        currentPassword(current) ?: newPassword(new, current) ?: confirmation(new, confirm)
}
