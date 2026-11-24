package com.dailyquest.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.R
import com.dailyquest.app.data.auth.PasswordResetMessages
import com.dailyquest.app.data.auth.PasswordResetValidator
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.auth.PasswordResetViewModel
import com.dailyquest.app.ui.auth.ResetStep
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

/** 비밀번호 찾기: 이메일 → 인증 코드 → 새 비밀번호. 로그인 없이 쓰는 공개 화면이다. */
@Composable
fun PasswordResetScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val vm: PasswordResetViewModel = viewModel(factory = PasswordResetViewModel.Factory(LocalAppContainer.current))
    val ui by vm.ui.collectAsStateWithLifecycle()

    fun goToLogin() {
        if (!nav.popBackStack(Routes.LOGIN, false)) nav.navigate(Routes.LOGIN) { launchSingleTop = true }
    }

    // 재발송 대기시간은 ViewModel의 마감 시각 기준이라 화면이 다시 구성되어도 버튼이 먼저 켜지지 않는다.
    var nowMs by remember { mutableLongStateOf(vm.now()) }
    LaunchedEffect(ui.resendAvailableAtMs, ui.step) {
        while (true) {
            nowMs = vm.now()
            if (nowMs >= ui.resendAvailableAtMs) break
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(ui.done) {
        if (ui.done) {
            auth.showNotice(PasswordResetMessages.DONE)
            goToLogin()
        }
    }

    BackHandler(enabled = ui.step != ResetStep.EMAIL) { vm.back() }

    DqScreen {
        Column(Modifier.fillMaxSize().imePadding().testTag("resetScreen")) {
            NavHeader("비밀번호 찾기", divider = true, onBack = { if (!vm.back()) nav.popBackStack() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                when (ui.step) {
                    ResetStep.EMAIL -> EmailStep(vm, ui)
                    ResetStep.CODE -> CodeStep(vm, ui, ui.resendRemainingSeconds(nowMs))
                    ResetStep.PASSWORD -> PasswordStep(vm, ui, onLogin = ::goToLogin)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Txt(text, size = 13f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))

@Composable
private fun Hint(text: String, tag: String? = null) =
    Txt(text, size = 12.5f, color = DQColors.Muted, lineHeight = 1.5f, modifier = Modifier.padding(start = 4.dp, top = 8.dp).let { if (tag != null) it.testTag(tag) else it })

@Composable
private fun Feedback(error: String?, notice: String?) {
    when {
        error != null -> Txt(error, size = 12.5f, weight = Medium, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("resetError"))
        notice != null -> Txt(notice, size = 12.5f, weight = Medium, color = DQColors.Green, modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("resetNotice"))
    }
}

@Composable
private fun EmailStep(vm: PasswordResetViewModel, ui: com.dailyquest.app.ui.auth.PasswordResetUiState) {
    Txt("가입한 이메일을 입력해 주세요", size = 18f, weight = Bold)
    Hint("이메일로 6자리 인증 코드를 보내드려요.")
    Label("이메일")
    DqTextField(
        ui.email, vm::onEmail, "example@email.com", modifier = Modifier.testTag("resetEmailInput"), radius = 16.dp,
        keyboardType = KeyboardType.Email, leading = R.drawable.ic_mail, shadow = true,
    )
    Feedback(ui.error, ui.notice)
    Hint(PasswordResetMessages.SPAM_HINT)
    Btn(
        if (ui.requesting) "보내는 중…" else "인증 코드 보내기",
        Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).testTag("resetSendCode"), size = 16f, enabled = !ui.requesting,
    ) { vm.submitEmail() }
    if (ui.codeMayBeSent) {
        Btn(
            "인증 코드 입력하기", Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).testTag("resetEnterCode"),
            style = BtnStyle.Outline, enabled = !ui.requesting,
        ) { vm.goToCodeEntry() }
    }
}

@Composable
private fun CodeStep(vm: PasswordResetViewModel, ui: com.dailyquest.app.ui.auth.PasswordResetUiState, remaining: Int) {
    Txt("인증 코드를 입력해 주세요", size = 18f, weight = Bold)
    Hint("메일로 받은 숫자 6자리를 입력해 주세요.")
    DqTextField(
        ui.code, vm::onCode, "000000", modifier = Modifier.padding(top = 14.dp).testTag("resetCodeInput"), radius = 16.dp,
        keyboardType = KeyboardType.Number, maxLength = PasswordResetValidator.CODE_LENGTH, shadow = true,
    )
    Feedback(ui.error, ui.notice)
    Hint(PasswordResetMessages.CODE_HINT, "resetCodeHint")
    Hint(PasswordResetMessages.SPAM_HINT)
    Btn(
        if (ui.verifying) "확인 중…" else "인증 코드 확인",
        Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).testTag("resetVerify"), size = 16f, enabled = !ui.verifying,
    ) { vm.verify() }
    val canResend = remaining == 0 && !ui.busy
    Btn(
        when {
            ui.requesting -> "보내는 중…"
            remaining > 0 -> "다시 보내기 (${remaining}초)"
            else -> "다시 보내기"
        },
        Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).testTag("resetResend"),
        style = BtnStyle.Outline, enabled = canResend,
    ) { vm.resend() }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
        Txt(
            "이메일 변경", size = 13f, color = DQColors.Muted,
            modifier = Modifier.clickable(enabled = !ui.busy) { vm.back() }.testTag("resetChangeEmail"),
        )
    }
}

@Composable
private fun PasswordStep(vm: PasswordResetViewModel, ui: com.dailyquest.app.ui.auth.PasswordResetUiState, onLogin: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    Txt("새 비밀번호를 설정해 주세요", size = 18f, weight = Bold)
    Hint("${PasswordResetValidator.PASSWORD_MIN}~${PasswordResetValidator.PASSWORD_MAX}자로 입력해 주세요.")
    Label("새 비밀번호")
    DqTextField(
        ui.newPassword, vm::onNewPassword, "새 비밀번호", modifier = Modifier.testTag("resetNewPassword"), radius = 16.dp,
        secure = !show, keyboardType = KeyboardType.Password, shadow = true, leading = R.drawable.ic_lock_gray,
        trailing = { DqIcon(R.drawable.ic_eye, 20.dp, Modifier.clip(CircleShape).clickable { show = !show }.testTag("resetPasswordToggle")) },
    )
    Label("새 비밀번호 확인")
    DqTextField(
        ui.confirm, vm::onConfirm, "새 비밀번호 확인", modifier = Modifier.testTag("resetConfirmPassword"), radius = 16.dp,
        secure = !show, keyboardType = KeyboardType.Password, shadow = true, leading = R.drawable.ic_lock_gray,
    )
    Feedback(ui.error, ui.notice)
    Btn(
        if (ui.saving) "변경 중…" else "비밀번호 재설정",
        Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).testTag("resetSubmit"), size = 16f, enabled = !ui.saving,
    ) { vm.submitPassword() }
    if (ui.confirmUnknown) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Btn("로그인 화면으로", Modifier.fillMaxWidth().height(48.dp).testTag("resetGoLogin"), style = BtnStyle.Outline, enabled = !ui.saving) { onLogin() }
        }
    }
}
