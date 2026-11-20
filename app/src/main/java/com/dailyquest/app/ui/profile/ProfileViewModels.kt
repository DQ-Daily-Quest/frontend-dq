package com.dailyquest.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.auth.AuthRepository
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.profile.AccountResult
import com.dailyquest.app.data.profile.ProfileErrorMessages
import com.dailyquest.app.data.profile.ProfileRepository
import com.dailyquest.app.data.profile.ProfileValidator
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 동시에 하나의 요청만 시작되도록 [flag]를 원자적으로 검사하고 올린다. */
private inline fun <S> MutableStateFlow<S>.beginOnce(isBusy: (S) -> Boolean, mark: (S) -> S): Boolean {
    var started = false
    update { if (isBusy(it)) it else { started = true; mark(it) } }
    return started
}

// ---------- 마이페이지 ----------

data class ProfileUiState(
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/** 마이페이지. 값은 세션(/users/me)이 단일 출처이며, 화면 진입과 재시도 때 서버에서 다시 읽는다. */
class ProfileViewModel(
    private val auth: AuthRepository,
    val session: StateFlow<SessionState>,
) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()
    private var generation = 0

    fun refresh() {
        val gen = ++generation
        _ui.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val r = auth.refreshProfile()
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> ProfileUiState(loaded = true)
                    // 이미 표시 중인 값은 유지하고 갱신 실패만 알린다. 401은 세션 처리 로직이 로그인 화면으로 보낸다.
                    is ApiResult.Failure -> it.copy(refreshing = false, error = AuthErrorMessages.forApi(r.error))
                }
            }
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(c.authRepository, c.session.state) as T
    }
}

// ---------- 닉네임 수정 ----------

data class NicknameUiState(
    val input: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class NicknameEditViewModel(
    private val repo: ProfileRepository,
    private val session: StateFlow<SessionState>,
) : ViewModel() {
    private val current: String? get() = (session.value as? SessionState.Authenticated)?.profile?.nickname
    private val _ui = MutableStateFlow(NicknameUiState(input = current.orEmpty()))
    val ui: StateFlow<NicknameUiState> = _ui.asStateFlow()

    /** 입력 중에는 서버 길이 제한까지만 자유롭게 편집할 수 있고, 검증 오류는 저장할 때 보여 준다. */
    fun onInput(value: String) = _ui.update { if (it.saving) it else it.copy(input = value.take(ProfileValidator.NICKNAME_MAX + 8), error = null) }

    /** 입력 오류. 저장 버튼 활성화 판단에도 쓴다. */
    fun validationError(): String? = ProfileValidator.nickname(_ui.value.input, current)

    fun save() {
        validationError()?.let { msg -> _ui.update { it.copy(error = msg) }; return }
        if (!_ui.beginOnce({ it.saving }) { it.copy(saving = true, error = null) }) return
        val nickname = _ui.value.input
        viewModelScope.launch {
            when (val r = repo.updateNickname(nickname)) {
                is ApiResult.Success -> _ui.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> _ui.update { it.copy(saving = false, error = ProfileErrorMessages.forNickname(r.error)) }
            }
        }
    }

    fun consumeSaved() = _ui.update { it.copy(saved = false) }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NicknameEditViewModel(c.profileRepository, c.session.state) as T
    }
}

// ---------- 비밀번호 변경 ----------

data class PasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    /** 소셜 로그인 전용 계정. 서버가 NO_PASSWORD_SET으로 알려 준 뒤에만 true가 된다(이메일 형식 등으로 추정하지 않는다). */
    val unsupported: Boolean = false,
    /** 직전 요청의 결과를 알 수 없었다. 이후 현재 비밀번호 불일치는 "이미 변경됐을 수 있음"으로 안내한다. */
    val outcomeUnknown: Boolean = false,
)

class PasswordChangeViewModel(private val repo: ProfileRepository) : ViewModel() {
    private val _ui = MutableStateFlow(PasswordUiState())
    val ui: StateFlow<PasswordUiState> = _ui.asStateFlow()

    fun onCurrent(v: String) = _ui.update { if (it.saving) it else it.copy(current = v.take(128), error = null) }
    fun onNew(v: String) = _ui.update { if (it.saving) it else it.copy(new = v.take(128), error = null) }
    fun onConfirm(v: String) = _ui.update { if (it.saving) it else it.copy(confirm = v.take(128), error = null) }

    fun submit() {
        val s = _ui.value
        if (s.saving || s.unsupported) return
        ProfileValidator.passwordChange(s.current, s.new, s.confirm)?.let { msg -> _ui.update { it.copy(error = msg) }; return }
        if (!_ui.beginOnce({ it.saving }) { it.copy(saving = true, error = null) }) return
        viewModelScope.launch {
            when (val r = repo.changePassword(s.current, s.new)) {
                // 성공하면 세션이 종료되고 로그인 화면으로 이동한다. 입력값은 즉시 비운다.
                AccountResult.Success -> _ui.value = PasswordUiState()
                is AccountResult.Unconfirmed -> _ui.update { it.copy(saving = false, outcomeUnknown = true, error = ProfileErrorMessages.PASSWORD_UNCONFIRMED) }
                is AccountResult.Failed -> {
                    val code = (r.error as? ApiException.Http)?.code
                    _ui.update {
                        when {
                            code == com.dailyquest.app.data.remote.ApiErrorCode.NO_PASSWORD_SET ->
                                it.copy(saving = false, unsupported = true, error = ProfileErrorMessages.NO_PASSWORD)
                            code == com.dailyquest.app.data.remote.ApiErrorCode.INVALID_CREDENTIALS && it.outcomeUnknown ->
                                it.copy(saving = false, error = ProfileErrorMessages.PASSWORD_MAYBE_CHANGED)
                            else -> it.copy(saving = false, error = ProfileErrorMessages.forPasswordChange(r.error))
                        }
                    }
                }
            }
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PasswordChangeViewModel(c.profileRepository) as T
    }
}

// ---------- 회원 탈퇴 ----------

data class WithdrawUiState(
    val password: String = "",
    val agreed: Boolean = false,
    val showConfirm: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** 방장 권한 때문에 탈퇴할 수 없다. 사용자가 방 관리에서 직접 위임·삭제해야 한다. */
    val ownerBlocked: Boolean = false,
)

class WithdrawViewModel(private val repo: ProfileRepository) : ViewModel() {
    private val _ui = MutableStateFlow(WithdrawUiState())
    val ui: StateFlow<WithdrawUiState> = _ui.asStateFlow()

    fun onPassword(v: String) = _ui.update { if (it.saving) it else it.copy(password = v.take(128), error = null) }
    fun onAgreed(v: Boolean) = _ui.update { if (it.saving) it else it.copy(agreed = v, error = null) }

    /** 동의한 경우에만 최종 확인 단계로 넘어간다. 확인 없이 탈퇴 요청이 나가지 않는다. */
    fun requestConfirm() = _ui.update {
        if (it.saving) it
        else if (!it.agreed) it.copy(error = "안내 내용에 동의해 주세요.")
        else it.copy(showConfirm = true, error = null)
    }

    fun dismissConfirm() = _ui.update { if (it.saving) it else it.copy(showConfirm = false) }

    fun confirm() {
        val s = _ui.value
        if (!s.agreed || !s.showConfirm) return
        if (!_ui.beginOnce({ it.saving }) { it.copy(saving = true, showConfirm = false, error = null, ownerBlocked = false) }) return
        viewModelScope.launch {
            when (val r = repo.withdraw(s.password)) {
                AccountResult.Success -> _ui.value = WithdrawUiState()
                is AccountResult.Unconfirmed -> _ui.update { it.copy(saving = false, error = ProfileErrorMessages.WITHDRAW_UNCONFIRMED) }
                is AccountResult.Failed -> _ui.update {
                    it.copy(
                        saving = false,
                        ownerBlocked = (r.error as? ApiException.Http)?.code == com.dailyquest.app.data.remote.ApiErrorCode.OWNER_MUST_TRANSFER,
                        error = if (r.verifiedNotApplied) ProfileErrorMessages.WITHDRAW_NOT_APPLIED else ProfileErrorMessages.forWithdraw(r.error),
                    )
                }
            }
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WithdrawViewModel(c.profileRepository) as T
    }
}
