package com.dailyquest.app.data.profile

import com.dailyquest.app.data.auth.AuthRepository
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.auth.toProfile
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.ChangePasswordRequest
import com.dailyquest.app.data.remote.dto.UpdateMeRequest
import com.dailyquest.app.data.remote.dto.WithdrawRequest

/** 상태 변경 요청의 결과. [Unconfirmed]는 서버가 처리했는지 알 수 없는 경우(응답 유실·타임아웃·서버 오류)다. */
sealed interface AccountResult {
    data object Success : AccountResult
    data class Failed(val error: ApiException, val verifiedNotApplied: Boolean = false) : AccountResult
    data class Unconfirmed(val error: ApiException) : AccountResult
}

private fun ApiException.isOutcomeUnknown(): Boolean = this !is ApiException.Http || status >= 500

class ProfileRepository(
    private val api: DqApi,
    private val session: SessionManager,
    private val auth: AuthRepository,
) {
    /**
     * 닉네임을 수정하고 서버가 돌려준 사용자 정보로 세션을 갱신한다. 서버 응답이 정본이다.
     * 요청 중 다른 계정으로 바뀌었다면 응답은 세션에 반영하지 않는다.
     */
    suspend fun updateNickname(nickname: String): ApiResult<UserProfile> {
        val userId = session.currentUserId() ?: return ApiResult.Failure(ApiException.Unexpected(IllegalStateException("no session")))
        return when (val r = apiCall { api.updateMe(UpdateMeRequest(ProfileValidator.normalizeNickname(nickname))) }) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val profile = r.value.toProfile()
                session.applyProfileIfCurrent(userId, profile)
                ApiResult.Success(profile)
            }
        }
    }

    /** 성공하면 서버가 모든 Refresh Token을 폐기하므로 로컬 세션도 끝내고 다시 로그인하게 한다. */
    suspend fun changePassword(current: String, new: String): AccountResult {
        val userId = session.currentUserId()
        return when (val r = apiCall { api.changePassword(ChangePasswordRequest(current, new)) }) {
            is ApiResult.Success -> {
                if (session.currentUserId() == userId) auth.endSession(LogoutReason.PASSWORD_CHANGED)
                AccountResult.Success
            }
            is ApiResult.Failure ->
                if (r.error.isOutcomeUnknown()) AccountResult.Unconfirmed(r.error) else AccountResult.Failed(r.error)
        }
    }

    /**
     * 회원 탈퇴. 응답을 알 수 없으면 성공 여부를 추정하지 않고 GET /users/me로 확인한다.
     * 정상 응답이면 미처리로 확정한다. 401은 탈퇴한 계정뿐 아니라 만료·폐기된 토큰에도 같은 UNAUTHORIZED로 내려와
     * 구별할 수 없으므로 탈퇴 성공으로 보지 않고 확인 불가로 처리한다(서버에 삭제 여부를 직접 묻는 API가 없다).
     */
    suspend fun withdraw(password: String?): AccountResult {
        val userId = session.currentUserId()
        val body = WithdrawRequest(password?.takeIf { it.isNotEmpty() })
        return when (val r = apiCall { api.withdraw(body) }) {
            is ApiResult.Success -> finishWithdraw(userId)
            is ApiResult.Failure -> {
                if (!r.error.isOutcomeUnknown()) return AccountResult.Failed(r.error)
                when (val check = apiCall { api.me() }) {
                    is ApiResult.Success -> AccountResult.Failed(r.error, verifiedNotApplied = true)
                    is ApiResult.Failure -> {
                        // 401 응답은 세션 가드가 이미 로그아웃시켰을 수 있다. 그 경우에도 이유를 "결과 확인 불가"로 남긴다.
                        if (session.currentUserId() == null) auth.endSession(LogoutReason.WITHDRAW_UNCONFIRMED)
                        AccountResult.Unconfirmed(r.error)
                    }
                }
            }
        }
    }

    private suspend fun finishWithdraw(userId: Long?): AccountResult {
        if (session.currentUserId() == userId || session.currentUserId() == null) auth.endSession(LogoutReason.WITHDRAWN)
        return AccountResult.Success
    }
}
