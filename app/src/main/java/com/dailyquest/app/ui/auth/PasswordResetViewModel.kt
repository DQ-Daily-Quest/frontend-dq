package com.dailyquest.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.auth.PasswordResetMessages
import com.dailyquest.app.data.auth.PasswordResetRepository
import com.dailyquest.app.data.auth.PasswordResetValidator
import com.dailyquest.app.data.auth.ResetResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResetStep { EMAIL, CODE, PASSWORD }

/**
 * 인증 코드·비밀번호는 이 상태에만 두며 SavedStateHandle이나 저장소에 쓰지 않는다.
 * 화면(백스택 항목)이 사라지면 ViewModel과 함께 폐기되고, 프로세스가 종료되면 처음 단계부터 다시 시작한다.
 */
data class PasswordResetUiState(
    val step: ResetStep = ResetStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val requesting: Boolean = false,
    val verifying: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    /** 코드를 요청한(또는 요청했을 수 있는) 이메일. 이메일이 바뀌면 비운다. */
    val codeEmail: String? = null,
    /** 이 시각(단조 시계, ms) 전에는 다시 보낼 수 없다. */
    val resendAvailableAtMs: Long = 0,
    /** 현재 입력된 [code]가 verify에 성공했다. 코드를 수정하거나 재발송하면 해제된다. */
    val verified: Boolean = false,
    /** 코드 요청 응답이 유실돼 메일이 발송됐을 수 있다. */
    val codeMayBeSent: Boolean = false,
    /** confirm 응답이 유실돼 변경 여부를 알 수 없다. */
    val confirmUnknown: Boolean = false,
    val done: Boolean = false,
) {
    val busy: Boolean get() = requesting || verifying || saving

    fun resendRemainingSeconds(nowMs: Long): Int =
        if (nowMs >= resendAvailableAtMs) 0 else (((resendAvailableAtMs - nowMs) + 999) / 1000).toInt()
}

class PasswordResetViewModel(
    private val repo: PasswordResetRepository,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val _ui = MutableStateFlow(PasswordResetUiState())
    val ui: StateFlow<PasswordResetUiState> = _ui.asStateFlow()

    /** 요청 시점의 세대. 이메일 변경·완료로 세대가 바뀌면 이전 요청의 늦은 응답은 버린다. */
    private var generation = 0

    fun now(): Long = clock()

    /** 동시에 하나의 요청만 시작되도록 원자적으로 검사하고 표시한다. */
    private fun begin(mark: (PasswordResetUiState) -> PasswordResetUiState): Boolean {
        var started = false
        _ui.update { if (it.busy) it else { started = true; mark(it) } }
        return started
    }

    fun onEmail(value: String) {
        val v = PasswordResetValidator.normalizeEmail(value)
        _ui.update {
            when {
                it.busy || it.step != ResetStep.EMAIL -> it
                v == it.email -> it.copy(error = null)
                else -> {
                    generation++
                    PasswordResetUiState(email = v)
                }
            }
        }
    }

    fun submitEmail() {
        val s = _ui.value
        if (s.busy || s.step != ResetStep.EMAIL) return
        PasswordResetValidator.email(s.email)?.let { msg -> _ui.update { it.copy(error = msg) }; return }
        // 같은 이메일로 방금 보낸 코드가 있으면 새로 요청하지 않고(서버도 1분 안의 재요청은 무시한다) 코드 입력으로 이동한다.
        if (s.codeEmail == s.email && clock() < s.resendAvailableAtMs) {
            _ui.update { it.copy(step = ResetStep.CODE, error = null, notice = null) }
            return
        }
        sendCode(resend = false)
    }

    fun resend() {
        val s = _ui.value
        if (s.step != ResetStep.CODE || clock() < s.resendAvailableAtMs) return
        sendCode(resend = true)
    }

    private fun sendCode(resend: Boolean) {
        if (!begin { it.copy(requesting = true, error = null, notice = null) }) return
        val email = _ui.value.email
        val gen = generation
        viewModelScope.launch {
            val result = repo.request(email)
            if (gen != generation) return@launch
            val cooldown = clock() + RESEND_COOLDOWN_MS
            _ui.update { cur ->
                when (result) {
                    ResetResult.Success -> cur.copy(
                        requesting = false, step = ResetStep.CODE, codeEmail = email, resendAvailableAtMs = cooldown,
                        code = "", verified = false, codeMayBeSent = false,
                        notice = if (resend) PasswordResetMessages.RESENT else PasswordResetMessages.SENT,
                    )
                    is ResetResult.Failed -> cur.copy(requesting = false, error = PasswordResetMessages.forRequest(result.error))
                    // 새 코드가 발급됐는지 알 수 없다. 이전 코드를 유효하다고 보지 않고, 같은 계정의 서버 대기시간만큼 쉰다.
                    is ResetResult.Unconfirmed -> cur.copy(
                        requesting = false, codeEmail = email, resendAvailableAtMs = cooldown, codeMayBeSent = true, verified = false,
                        error = if (resend) PasswordResetMessages.RESEND_UNCONFIRMED else PasswordResetMessages.SEND_UNCONFIRMED,
                    )
                }
            }
        }
    }

    /** 코드가 이미 도착했을 수 있는 경우(요청 응답 유실) 코드 입력 단계로 이동한다. */
    fun goToCodeEntry() = _ui.update {
        if (it.busy || it.step != ResetStep.EMAIL || !it.codeMayBeSent) it else it.copy(step = ResetStep.CODE, error = null, notice = null)
    }

    fun onCode(value: String) = _ui.update {
        if (it.verifying || it.step != ResetStep.CODE) it
        else it.copy(code = PasswordResetValidator.sanitizeCode(value), verified = false, error = null)
    }

    fun verify() {
        val s = _ui.value
        if (s.busy || s.step != ResetStep.CODE) return
        PasswordResetValidator.code(s.code)?.let { msg -> _ui.update { it.copy(error = msg) }; return }
        if (!begin { it.copy(verifying = true, error = null, notice = null) }) return
        val email = s.codeEmail ?: s.email
        val code = s.code
        val gen = generation
        viewModelScope.launch {
            val result = repo.verify(email, code)
            if (gen != generation) return@launch
            _ui.update { cur ->
                when (result) {
                    ResetResult.Success -> cur.copy(verifying = false, verified = true, step = ResetStep.PASSWORD, error = null, confirmUnknown = false)
                    is ResetResult.Failed -> cur.copy(
                        verifying = false, verified = false,
                        error = if (PasswordResetMessages.isInvalidCode(result.error) || result.error.httpCode == "VALIDATION_ERROR")
                            PasswordResetMessages.CODE_INVALID else AuthErrorMessages.forApi(result.error),
                    )
                    is ResetResult.Unconfirmed -> cur.copy(verifying = false, verified = false, error = PasswordResetMessages.VERIFY_UNCONFIRMED)
                }
            }
        }
    }

    fun onNewPassword(v: String) = _ui.update {
        if (it.saving || it.step != ResetStep.PASSWORD) it else it.copy(newPassword = v.take(MAX_INPUT), error = null)
    }

    fun onConfirm(v: String) = _ui.update {
        if (it.saving || it.step != ResetStep.PASSWORD) it else it.copy(confirm = v.take(MAX_INPUT), error = null)
    }

    fun submitPassword() {
        val s = _ui.value
        if (s.busy || s.step != ResetStep.PASSWORD || !s.verified) return
        PasswordResetValidator.newPassword(s.newPassword, s.confirm)?.let { msg -> _ui.update { it.copy(error = msg) }; return }
        if (!begin { it.copy(saving = true, error = null, notice = null) }) return
        val email = s.codeEmail ?: s.email
        val code = s.code
        val password = s.newPassword
        val gen = generation
        viewModelScope.launch {
            val result = repo.confirm(email, code, password)
            if (gen != generation) return@launch
            _ui.update { cur ->
                when (result) {
                    // 입력했던 이메일·코드·비밀번호를 모두 비운다. 자동 로그인은 하지 않는다.
                    ResetResult.Success -> PasswordResetUiState(done = true)
                    is ResetResult.Failed ->
                        if (PasswordResetMessages.isInvalidCode(result.error)) {
                            if (cur.confirmUnknown) cur.copy(saving = false, error = PasswordResetMessages.CONFIRM_MAYBE_DONE)
                            else cur.copy(
                                saving = false, step = ResetStep.CODE, code = "", verified = false, newPassword = "", confirm = "",
                                error = PasswordResetMessages.CONFIRM_CODE_GONE,
                            )
                        } else cur.copy(saving = false, error = PasswordResetMessages.forConfirm(result.error))
                    is ResetResult.Unconfirmed -> cur.copy(saving = false, confirmUnknown = true, error = PasswordResetMessages.CONFIRM_UNCONFIRMED)
                }
            }
        }
    }

    /** 화면 안의 뒤로가기. 처리했으면 true, 첫 단계라 화면을 닫아야 하면 false. 처리 중에는 무시한다. */
    fun back(): Boolean {
        var handled = true
        _ui.update {
            when {
                it.step == ResetStep.EMAIL -> { handled = false; it }
                it.busy -> it
                it.step == ResetStep.PASSWORD -> it.copy(step = ResetStep.CODE, newPassword = "", confirm = "", error = null, notice = null)
                else -> it.copy(step = ResetStep.EMAIL, error = null, notice = null)
            }
        }
        return handled
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PasswordResetViewModel(c.passwordResetRepository) as T
    }

    companion object {
        const val RESEND_COOLDOWN_MS = 60_000L
        private const val MAX_INPUT = 128
    }
}
