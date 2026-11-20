package com.dailyquest.app.data.auth

import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.auth.social.SocialSignInResult
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 서버 검증 규칙(이메일 형식, 비밀번호 8~64자, 닉네임 2~12자)과 같은 기준의 클라이언트 사전 검증. */
object AuthValidator {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(value: String): String? =
        if (EMAIL.matches(value.trim())) null else "올바른 이메일 형식이 아니에요."

    fun password(value: String): String? =
        if (value.length in 8..64) null else "비밀번호는 8~64자로 입력해 주세요."

    fun nickname(value: String): String? =
        if (value.trim().length in 2..12) null else "닉네임은 2~12자로 입력해 주세요."
}

/** 서버 오류를 사용자 안내 문구로 변환한다. 분기는 message가 아닌 code 기준이다. */
object AuthErrorMessages {
    fun forApi(e: ApiException): String = when (e) {
        is ApiException.Network ->
            if (e.isTlsFailure) "안전한 연결을 확인할 수 없어요. 네트워크를 확인해 주세요."
            else "네트워크에 연결할 수 없어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
        is ApiException.Unexpected -> "알 수 없는 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
        is ApiException.Http -> when {
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
