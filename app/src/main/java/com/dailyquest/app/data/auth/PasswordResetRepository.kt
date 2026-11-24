package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest
import com.dailyquest.app.data.remote.dto.PasswordResetRequest
import com.dailyquest.app.data.remote.dto.PasswordResetVerifyRequest

/**
 * 비밀번호 재설정 요청의 결과. [Unconfirmed]는 서버가 처리했는지 알 수 없는 경우(응답 유실·타임아웃·서버 오류)이며
 * 성공으로도 실패로도 단정하지 않는다.
 */
sealed interface ResetResult {
    data object Success : ResetResult
    data class Failed(val error: ApiException) : ResetResult
    data class Unconfirmed(val error: ApiException) : ResetResult
}

private val KNOWN_RESET_CODES = setOf(
    ApiErrorCode.INVALID_RESET_CODE, ApiErrorCode.VALIDATION_ERROR, ApiErrorCode.MAIL_UNAVAILABLE,
)

private fun ApiException.isOutcomeUnknown(): Boolean = when (this) {
    is ApiException.Http -> status >= 500 && code !in KNOWN_RESET_CODES
    else -> true
}

/**
 * 로그인 없이 쓰는 공개 API(이메일 인증 코드 비밀번호 재설정). 세션과 토큰을 사용하지 않으며 자동 재전송도 하지 않는다.
 * 이메일·코드·비밀번호는 기록하지 않는다.
 */
class PasswordResetRepository(private val api: DqApi) {
    suspend fun request(email: String): ResetResult = call { api.requestPasswordReset(PasswordResetRequest(PasswordResetValidator.normalizeEmail(email))) }

    suspend fun verify(email: String, code: String): ResetResult =
        call { api.verifyPasswordReset(PasswordResetVerifyRequest(PasswordResetValidator.normalizeEmail(email), code)) }

    suspend fun confirm(email: String, code: String, newPassword: String): ResetResult =
        call { api.confirmPasswordReset(PasswordResetConfirmRequest(PasswordResetValidator.normalizeEmail(email), code, newPassword)) }

    private suspend fun call(block: suspend () -> Unit): ResetResult = when (val r = apiCall { block() }) {
        is ApiResult.Success -> ResetResult.Success
        is ApiResult.Failure -> if (r.error.isOutcomeUnknown()) ResetResult.Unconfirmed(r.error) else ResetResult.Failed(r.error)
    }
}

/** 서버 계약에 맞춘 입력 검증. 이메일은 서버가 trim 후 소문자로 정규화하므로 같은 방식으로 보낸다. */
object PasswordResetValidator {
    const val CODE_LENGTH = 6
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64

    fun normalizeEmail(value: String): String = AuthValidator.normalizeEmail(value)

    fun email(value: String): String? = AuthValidator.email(value)

    /** 숫자만 남기고 6자리로 자른다. 문자열로 다루므로 앞자리 0이 보존된다. */
    fun sanitizeCode(value: String): String = value.filter { it in '0'..'9' }.take(CODE_LENGTH)

    fun code(value: String): String? =
        if (value.length == CODE_LENGTH && value.all { it in '0'..'9' }) null else "인증 코드 6자리를 입력해 주세요."

    fun newPassword(password: String, confirm: String): String? = when {
        AuthValidator.passwordError(password) != null -> AuthValidator.passwordError(password)
        password != confirm -> "새 비밀번호가 일치하지 않아요."
        else -> null
    }
}

object PasswordResetMessages {
    const val SENT = "가입된 이메일이라면 인증 코드를 보냈어요."
    const val RESENT = "가입된 이메일이라면 새 인증 코드를 보냈어요. 이전 코드는 사용할 수 없어요."
    const val CODE_INVALID = "인증 코드가 올바르지 않거나 만료되었어요."
    const val CODE_HINT = "인증 코드는 10분 동안 유효하고, 5회 틀리면 폐기되어 다시 받아야 해요."
    const val SPAM_HINT = "메일이 오지 않으면 스팸함을 확인해 주세요."
    const val DONE = "비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요."
    const val SEND_UNCONFIRMED = "인증 코드 요청 결과를 확인하지 못했어요. 메일함과 스팸함을 확인하고, 오지 않으면 잠시 후 다시 요청해 주세요."
    const val RESEND_UNCONFIRMED = "새 인증 코드가 발급됐는지 확인하지 못했어요. 새 코드가 도착했다면 그 코드를 입력하고, 아니면 잠시 후 다시 요청해 주세요."
    const val VERIFY_UNCONFIRMED = "인증 코드를 확인하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요."
    const val CONFIRM_UNCONFIRMED = "비밀번호 변경 결과를 확인하지 못했어요. 새 비밀번호로 로그인해 보고, 로그인되지 않으면 처음부터 다시 진행해 주세요."
    const val CONFIRM_MAYBE_DONE = "이미 비밀번호가 변경됐을 수 있어요. 새 비밀번호로 로그인해 보고, 안 되면 처음부터 다시 진행해 주세요."
    const val CONFIRM_CODE_GONE = "인증 코드가 만료되었거나 더 이상 유효하지 않아요. 인증 코드를 다시 받아 주세요."

    fun forRequest(e: ApiException): String = when {
        e is ApiException.Http && e.code == ApiErrorCode.VALIDATION_ERROR -> "올바른 이메일 형식이 아니에요."
        e is ApiException.Http && e.code == ApiErrorCode.MAIL_UNAVAILABLE -> "메일을 보낼 수 없어요. 잠시 후 다시 시도해 주세요."
        else -> AuthErrorMessages.forApi(e)
    }

    fun forConfirm(e: ApiException): String = when {
        e is ApiException.Http && e.code == ApiErrorCode.VALIDATION_ERROR -> "비밀번호는 ${PasswordResetValidator.PASSWORD_MIN}~${PasswordResetValidator.PASSWORD_MAX}자(한글은 24자까지)로 입력해 주세요."
        else -> AuthErrorMessages.forApi(e)
    }

    fun isInvalidCode(e: ApiException) = e is ApiException.Http && e.code == ApiErrorCode.INVALID_RESET_CODE
}
