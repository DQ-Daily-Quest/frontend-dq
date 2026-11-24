package com.dailyquest.app.data.auth

import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.auth.social.SocialSignInResult
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import java.util.Locale

/** 서버 검증 규칙(이메일 형식, 비밀번호 8~64자·72바이트 이하, 닉네임 2~12자·"탈퇴회원" 접두어 금지)과 같은 기준의 클라이언트 사전 검증. */
object AuthValidator {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(value: String): String? =
        if (EMAIL.matches(value.trim())) null else "올바른 이메일 형식이 아니에요."

    /** 서버가 저장·조회에 쓰는 형태. 이메일은 대소문자를 구분하지 않고 서버가 소문자로 저장한다. */
    fun normalizeEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 64
    /** BCrypt 한도. 글자 수가 아니라 UTF-8 바이트 수다(영문·숫자 64자, 한글 24자). */
    const val PASSWORD_MAX_BYTES = 72

    /** 서버 규칙: 8~64자이면서 UTF-8로 72바이트 이하. */
    fun passwordError(value: String, label: String = "비밀번호"): String? = when {
        value.length !in PASSWORD_MIN..PASSWORD_MAX -> "${label}는 ${PASSWORD_MIN}~${PASSWORD_MAX}자로 입력해 주세요."
        value.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> "${label}가 너무 길어요. 영문·숫자는 64자, 한글은 24자까지 쓸 수 있어요."
        else -> null
    }

    fun password(value: String): String? = passwordError(value)

    const val WITHDRAWN_NICKNAME_PREFIX = "탈퇴회원"

    /** 서버는 앞뒤 공백을 제거한 뒤 2~12자를 확인하고 "탈퇴회원"으로 시작하는 닉네임을 거절한다. */
    fun nickname(value: String): String? {
        val v = value.trim()
        return when {
            v.length !in 2..12 -> "닉네임은 2~12자로 입력해 주세요."
            v.startsWith(WITHDRAWN_NICKNAME_PREFIX) -> "사용할 수 없는 닉네임이에요."
            else -> null
        }
    }
}

/** 서버 오류를 사용자 안내 문구로 변환한다. 분기는 message가 아닌 code 기준이다. */
object AuthErrorMessages {
    fun forApi(e: ApiException): String = when (e) {
        is ApiException.Network ->
            if (e.isTlsFailure) "안전한 연결을 확인할 수 없어요. 네트워크를 확인해 주세요."
            else "네트워크에 연결할 수 없어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
        is ApiException.Unexpected -> "알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
        is ApiException.Http -> when {
            e.isRateLimited -> rateLimited(e.retryAfterSeconds)
            e.code == ApiErrorCode.INVALID_CREDENTIALS -> "이메일 또는 비밀번호가 올바르지 않아요."
            e.code == ApiErrorCode.INVALID_OAUTH_TOKEN -> "소셜 계정 인증에 실패했어요. 다시 시도해 주세요."
            e.code == ApiErrorCode.DUPLICATE_EMAIL -> "이미 가입된 이메일이에요."
            e.code == ApiErrorCode.DUPLICATE_NICKNAME -> "이미 사용 중인 닉네임이에요."
            e.code == ApiErrorCode.VALIDATION_ERROR -> "입력한 정보를 다시 확인해 주세요."
            e.status >= 500 -> "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요."
            else -> "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}

/** 429 안내. 자동으로 다시 시도하지 않으며, Retry-After가 유효하면 대기시간을 함께 알려 준다. */
fun rateLimited(retryAfterSeconds: Long?): String {
    val base = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
    val wait = retryAfterSeconds?.takeIf { it > 0 } ?: return base
    val text = if (wait < 60) "${wait}초" else "약 ${(wait + 59) / 60}분"
    return "$base ($text 후)"
}

/** 소셜 SDK 단계의 실패를 사용자 안내 문구로 변환한다. 취소는 오류로 표시하지 않는다(null). */
object SocialErrorMessages {
    fun forResult(provider: SocialProvider, r: SocialSignInResult): String? {
        val name = if (provider == SocialProvider.GOOGLE) "Google" else "카카오"
        return when (r) {
            is SocialSignInResult.Token, SocialSignInResult.Cancelled -> null
            SocialSignInResult.NoAccount ->
                if (provider == SocialProvider.GOOGLE) "기기에 Google 계정이 없어요. 계정을 추가한 뒤 다시 시도해 주세요."
                else "사용할 수 있는 $name 계정을 찾지 못했어요."
            SocialSignInResult.Network -> "네트워크에 연결할 수 없어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
            SocialSignInResult.MissingIdToken ->
                if (provider == SocialProvider.KAKAO) "카카오에서 인증 정보(ID 토큰)를 받지 못했어요. 앱의 OpenID Connect 설정을 확인해 주세요."
                else "Google에서 인증 정보를 받지 못했어요. 다시 시도해 주세요."
            SocialSignInResult.NotConfigured -> "$name 로그인이 설정되지 않은 빌드예요."
            SocialSignInResult.Failed -> "$name 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
