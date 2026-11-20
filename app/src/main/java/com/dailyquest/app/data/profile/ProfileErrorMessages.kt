package com.dailyquest.app.data.profile

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 프로필·계정 관리 서버 오류를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object ProfileErrorMessages {
    fun forNickname(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.DUPLICATE_NICKNAME -> "이미 사용 중인 닉네임이에요."
            ApiErrorCode.VALIDATION_ERROR -> "닉네임은 2~12자로 입력해 주세요."
            else -> AuthErrorMessages.forApi(e)
        }
    }

    fun forPasswordChange(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.INVALID_CREDENTIALS -> "현재 비밀번호가 일치하지 않아요."
            ApiErrorCode.NO_PASSWORD_SET -> NO_PASSWORD
            ApiErrorCode.VALIDATION_ERROR -> "새 비밀번호는 8~64자여야 하고 현재 비밀번호와 달라야 해요."
            else -> AuthErrorMessages.forApi(e)
        }
    }

    fun forWithdraw(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.INVALID_CREDENTIALS -> "비밀번호가 일치하지 않아요."
            ApiErrorCode.VALIDATION_ERROR -> "이메일로 가입한 계정은 비밀번호를 입력해야 탈퇴할 수 있어요."
            ApiErrorCode.OWNER_MUST_TRANSFER ->
                "방장으로 있는 방이 있어 탈퇴할 수 없어요. 방장을 다른 멤버에게 위임하거나 방을 삭제한 뒤 다시 시도해 주세요."
            else -> AuthErrorMessages.forApi(e)
        }
    }

    const val NO_PASSWORD = "소셜 로그인으로 가입한 계정은 비밀번호가 없어 변경할 수 없어요."

    /** 비밀번호 변경 요청의 결과를 알 수 없을 때(응답 유실·타임아웃·서버 오류). */
    const val PASSWORD_UNCONFIRMED =
        "변경 결과를 확인하지 못했어요. 새 비밀번호로 로그인되는지 확인하고, 안 되면 기존 비밀번호를 사용해 주세요."

    /** 위 안내 이후 현재 비밀번호가 맞지 않다고 나오는 경우: 이전 요청이 이미 반영됐을 수 있다. */
    const val PASSWORD_MAYBE_CHANGED =
        "이전 요청에서 비밀번호가 이미 변경됐을 수 있어요. 로그아웃한 뒤 새 비밀번호로 로그인해 주세요."

    const val WITHDRAW_UNCONFIRMED =
        "탈퇴 처리 결과를 확인하지 못했어요. 네트워크를 확인한 뒤 앱을 다시 열어 로그인 상태를 확인해 주세요."
    const val WITHDRAW_NOT_APPLIED = "탈퇴가 처리되지 않았어요. 잠시 후 다시 시도해 주세요."
}
