package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.dto.UserMeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 서버 /users/me 기준의 현재 사용자. Mock 데이터와 섞이지 않도록 별도 모델로 둔다. */
data class UserProfile(
    val userId: Long,
    val email: String?,
    val nickname: String,
    val coins: Int,
    val streakCurrent: Int,
    val streakLongest: Int,
    val roomCount: Long,
    val completedMissionCount: Long,
    val createdAt: String? = null,
)

fun UserMeResponse.toProfile() = UserProfile(
    userId = userId, email = email, nickname = nickname, coins = coins,
    streakCurrent = streak.current, streakLongest = streak.longest,
    roomCount = stats.roomCount, completedMissionCount = stats.completedMissionCount,
    createdAt = createdAt,
)

/** [PASSWORD_CHANGED]: 서버가 Refresh Token을 모두 폐기했다. [WITHDRAWN]: 회원 탈퇴가 확정됐다. */
enum class LogoutReason { NONE, USER, EXPIRED, PASSWORD_CHANGED, WITHDRAWN }

sealed interface SessionState {
    data object Loading : SessionState
    data class Unauthenticated(val reason: LogoutReason = LogoutReason.NONE) : SessionState
    data class Authenticated(val profile: UserProfile) : SessionState

    /** 토큰은 있으나 서버 확인에 실패(일시적 오류). 로그아웃하지 않고 재시도를 안내한다. */
    data class BootstrapFailed(val isNetwork: Boolean) : SessionState
}

/**
 * 로그인 상태의 단일 출처. 로그아웃·세션 만료·계정 변경 시 등록된 cleaner로
 * 이전 사용자의 메모리 데이터(Mock 등)를 초기화한다.
 */
class SessionManager(
    private val tokens: SessionTokens,
    private val cleaners: List<() -> Unit> = emptyList(),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var avatarSetupPending = false

    /** 신규 소셜 가입자를 로그인 직후 아바타 선택 화면으로 보내기 위한 1회성 표시. */
    fun requestAvatarSetup() {
        avatarSetupPending = true
    }

    fun consumeAvatarSetup(): Boolean {
        val pending = avatarSetupPending
        avatarSetupPending = false
        return pending
    }

    fun markAuthenticated(profile: UserProfile) {
        val previous = (_state.value as? SessionState.Authenticated)?.profile?.userId
        if (previous != null && previous != profile.userId) runCleaners()
        _state.value = SessionState.Authenticated(profile)
    }

    /** 서버가 응답한 잔액(구매 응답의 remainingCoins)을 현재 사용자 프로필에 반영한다. 사용자가 바뀐 뒤의 늦은 응답은 [expectedUserId]로 막는다. */
    fun applyServerCoins(expectedUserId: Long, coins: Int) {
        _state.update { cur ->
            if (cur is SessionState.Authenticated && cur.profile.userId == expectedUserId) cur.copy(profile = cur.profile.copy(coins = coins)) else cur
        }
    }

    /** 프로필 수정 응답처럼 현재 사용자의 새 정보를 반영한다. 다른 사용자로 바뀐 뒤 도착한 늦은 응답은 무시한다. */
    fun applyProfileIfCurrent(expectedUserId: Long, profile: UserProfile): Boolean {
        var applied = false
        _state.update { cur ->
            if (cur is SessionState.Authenticated && cur.profile.userId == expectedUserId && profile.userId == expectedUserId) {
                applied = true
                SessionState.Authenticated(profile)
            } else cur
        }
        return applied
    }

    fun currentUserId(): Long? = (_state.value as? SessionState.Authenticated)?.profile?.userId

    fun markLoading() {
        _state.value = SessionState.Loading
    }

    fun markBootstrapFailed(isNetwork: Boolean) {
        _state.value = SessionState.BootstrapFailed(isNetwork)
    }

    fun markUnauthenticated() {
        _state.value = SessionState.Unauthenticated(LogoutReason.NONE)
    }

    /** 토큰을 지우고 로그아웃 상태로 전환한다. 여러 번 호출해도 안전하다. */
    fun expire(reason: LogoutReason) {
        tokens.clear()
        avatarSetupPending = false
        val wasUnauth = _state.value is SessionState.Unauthenticated
        if (!wasUnauth) runCleaners()
        _state.value = SessionState.Unauthenticated(reason)
    }

    private fun runCleaners() = cleaners.forEach { runCatching(it) }
}
