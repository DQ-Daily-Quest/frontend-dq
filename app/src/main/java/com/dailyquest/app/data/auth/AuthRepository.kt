package com.dailyquest.app.data.auth

import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.auth.social.SocialAuthClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.remote.dto.LoginRequest
import com.dailyquest.app.data.remote.dto.OAuthLoginRequest
import com.dailyquest.app.data.remote.dto.TokenResponse
import com.dailyquest.app.data.remote.dto.SignupRequest
import com.dailyquest.app.data.remote.dto.SignupResponse
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepository(
    private val api: DqApi,
    private val tokens: SessionTokens,
    private val session: SessionManager,
    private val socialClients: Map<SocialProvider, SocialAuthClient> = emptyMap(),
) {
    // 로그인/로그아웃/부트스트랩이 겹쳐 토큰과 상태가 엇갈리지 않도록 직렬화한다.
    private val mutex = Mutex()

    private companion object {
        const val BOOTSTRAP_TIMEOUT_MS = 45_000L
    }

    /** 앱 시작 시 저장된 토큰으로 로그인 상태를 복원한다. */
    suspend fun bootstrap() = mutex.withLock {
        if (tokens.get() == null) {
            session.markUnauthenticated()
            return@withLock
        }
        // 개별 타임아웃과 별개로 시작 화면이 무한 로딩되지 않도록 전체 상한을 둔다.
        val result = withTimeoutOrNull(BOOTSTRAP_TIMEOUT_MS) { apiCall { api.me() } }
        if (result == null) {
            session.markBootstrapFailed(isNetwork = true)
            return@withLock
        }
        when (result) {
            is ApiResult.Success -> session.markAuthenticated(result.value.toProfile())
            is ApiResult.Failure -> {
                val e = result.error
                if (e is ApiException.Http && e.status == 401) session.expire(LogoutReason.EXPIRED)
                else session.markBootstrapFailed(isNetwork = e is ApiException.Network)
            }
        }
    }

    /** 회원가입은 JWT를 발급하지 않으므로 로그인 상태가 되지 않는다. */
    suspend fun signup(email: String, password: String, nickname: String): ApiResult<SignupResponse> =
        apiCall { api.signup(SignupRequest(email, password, nickname)) }

    /** 로그인 후 /users/me까지 성공해야 인증 완료로 처리한다. 실패 시 저장된 토큰을 되돌린다. */
    suspend fun login(email: String, password: String): ApiResult<Unit> = mutex.withLock {
        when (val r = apiCall { api.login(LoginRequest(email, password)) }) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> completeLogin(r.value)
        }
    }

    /** 제공자 ID Token을 Daily Quest 백엔드에 보내 JWT를 발급받는다. 이후 세션 처리는 이메일 로그인과 동일하다. */
    suspend fun socialLogin(provider: SocialProvider, idToken: String): ApiResult<Unit> = mutex.withLock {
        when (val r = apiCall { api.oauthLogin(provider.path, OAuthLoginRequest(idToken)) }) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> completeLogin(r.value)
        }
    }

    private suspend fun completeLogin(token: TokenResponse): ApiResult<Unit> {
        tokens.set(TokenPair(token.accessToken, token.refreshToken))
        return when (val me = apiCall { api.me() }) {
            is ApiResult.Success -> {
                if (token.isNewUser) session.requestAvatarSetup()
                session.markAuthenticated(me.value.toProfile())
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> {
                apiCall { api.logout() }
                tokens.clear()
                me
            }
        }
    }

    /** 서버 로그아웃은 실패해도 로컬 인증 정보와 사용자 데이터는 항상 삭제한다. */
    suspend fun logout() = mutex.withLock {
        if (tokens.get() != null) apiCall { api.logout() }
        // SDK 로컬 인증 상태를 정리해 다음 로그인에서 다른 계정을 고를 수 있게 한다. 계정 연결은 해제하지 않는다.
        socialClients.values.forEach { runCatching { it.signOut() } }
        session.expire(LogoutReason.USER)
    }

    suspend fun refreshProfile(): ApiResult<UserProfile> = when (val r = apiCall { api.me() }) {
        is ApiResult.Success -> {
            val profile = r.value.toProfile()
            session.markAuthenticated(profile)
            ApiResult.Success(profile)
        }
        is ApiResult.Failure -> r
    }
}
