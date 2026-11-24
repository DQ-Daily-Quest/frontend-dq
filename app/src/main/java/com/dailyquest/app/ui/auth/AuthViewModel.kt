package com.dailyquest.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.AuthErrorMessages
import android.content.Context
import com.dailyquest.app.data.auth.AuthValidator
import com.dailyquest.app.data.auth.SocialErrorMessages
import com.dailyquest.app.data.auth.social.NotConfiguredSocialClient
import com.dailyquest.app.data.auth.social.SocialProvider
import com.dailyquest.app.data.auth.social.SocialSignInResult
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignupOutcome {
    /** 가입 후 자동 로그인까지 성공 */
    LOGGED_IN,

    /** 가입은 성공했지만 자동 로그인에 실패해 로그인 화면에서 직접 로그인해야 함 */
    NEEDS_LOGIN,
}

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val signupOutcome: SignupOutcome? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.authRepository

    val session: StateFlow<SessionState> = container.session.state
    val startAfterAuth: String get() = container.startAfterAuth

    /** 신규 소셜 가입자는 로그인 직후 아바타 선택 화면으로, 그 외에는 [startAfterAuth]로 보낸다. */
    fun consumePostLoginRoute(): String =
        if (container.session.consumeAvatarSetup()) com.dailyquest.app.navigation.Routes.SIGNUP_AVATAR else startAfterAuth

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { repo.bootstrap() }
    }

    fun retryBootstrap() {
        container.session.markLoading()
        viewModelScope.launch { repo.bootstrap() }
    }

    /** 요청 중에는 추가 입력을 무시해 연속 클릭으로 인한 중복 제출을 막는다. */
    private fun beginRequest(): Boolean {
        var started = false
        _ui.update { if (it.loading) it else { started = true; it.copy(loading = true, error = null, notice = null) } }
        return started
    }

    fun login(email: String, password: String) {
        AuthValidator.email(email)?.let { return fail(it) }
        if (password.isEmpty()) return fail("비밀번호를 입력해 주세요.")
        if (!beginRequest()) return
        viewModelScope.launch {
            when (val r = repo.login(email.trim(), password)) {
                is ApiResult.Success -> _ui.update { it.copy(loading = false) }
                is ApiResult.Failure -> _ui.update { it.copy(loading = false, error = AuthErrorMessages.forApi(r.error)) }
            }
        }
    }

    /** SDK로 ID Token을 받아 백엔드에 전달한다. 진행 중에는 다른 로그인 요청도 무시한다. */
    fun socialLogin(provider: SocialProvider, context: Context) {
        if (!beginRequest()) return
        viewModelScope.launch {
            val client = container.socialClients[provider] ?: NotConfiguredSocialClient
            val sdk = client.signIn(context)
            if (sdk !is SocialSignInResult.Token) {
                _ui.update { it.copy(loading = false, error = SocialErrorMessages.forResult(provider, sdk)) }
                return@launch
            }
            when (val r = repo.socialLogin(provider, sdk.idToken)) {
                is ApiResult.Success -> _ui.update { it.copy(loading = false) }
                is ApiResult.Failure -> _ui.update { it.copy(loading = false, error = AuthErrorMessages.forApi(r.error)) }
            }
        }
    }

    /** 가입 응답에는 JWT가 없으므로, 가입 성공 후 같은 자격 증명으로 로그인을 한 번 더 요청한다. */
    fun signup(email: String, password: String, nickname: String) {
        AuthValidator.email(email)?.let { return fail(it) }
        AuthValidator.password(password)?.let { return fail(it) }
        AuthValidator.nickname(nickname)?.let { return fail(it) }
        if (!beginRequest()) return
        viewModelScope.launch {
            when (val r = repo.signup(email.trim(), password, nickname.trim())) {
                is ApiResult.Failure -> _ui.update { it.copy(loading = false, error = AuthErrorMessages.forApi(r.error)) }
                is ApiResult.Success -> when (repo.login(email.trim(), password)) {
                    is ApiResult.Success -> _ui.update { it.copy(loading = false, signupOutcome = SignupOutcome.LOGGED_IN) }
                    is ApiResult.Failure -> _ui.update {
                        it.copy(
                            loading = false, signupOutcome = SignupOutcome.NEEDS_LOGIN,
                            notice = "가입이 완료됐어요. 로그인해 주세요.",
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    fun refreshProfile() {
        viewModelScope.launch { repo.refreshProfile() }
    }

    fun consumeSignupOutcome() = _ui.update { it.copy(signupOutcome = null) }
    /** 다른 화면(비밀번호 재설정 완료 등)에서 로그인 화면에 안내를 남긴다. */
    fun showNotice(message: String) = _ui.update { it.copy(error = null, notice = message) }
    fun clearMessages() = _ui.update { it.copy(error = null, notice = null) }

    private fun fail(message: String) = _ui.update { it.copy(error = message, notice = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(container) as T
    }
}

val LocalAuth = staticCompositionLocalOf<AuthViewModel> { error("AuthViewModel not provided") }
