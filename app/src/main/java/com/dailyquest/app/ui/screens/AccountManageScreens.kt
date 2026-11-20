package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.profile.NicknameEditViewModel
import com.dailyquest.app.ui.profile.PasswordChangeViewModel
import com.dailyquest.app.data.profile.ProfileValidator
import com.dailyquest.app.ui.profile.WithdrawViewModel
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.theme.DQColors

@Composable
private fun Msg(text: String?, tag: String) {
    if (text != null) Txt(text, size = 12.5f, weight = Medium, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 10.dp).testTag(tag))
}

@Composable
private fun Label(text: String) = Txt(text, size = 13f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))

/** 닉네임 수정. 이메일은 서버가 수정을 지원하지 않아 읽기 전용으로만 보여 준다. */
@Composable
fun ProfileEditScreen() {
    val nav = LocalNav.current
    val vm: NicknameEditViewModel = viewModel(factory = NicknameEditViewModel.Factory(LocalAppContainer.current))
    val ui by vm.ui.collectAsStateWithLifecycle()
    val email = (LocalAppContainer.current.session.state.value as? com.dailyquest.app.data.auth.SessionState.Authenticated)?.profile?.email
    LaunchedEffect(ui.saved) { if (ui.saved) { vm.consumeSaved(); nav.popBackStack() } }
    DqScreen {
        Column(Modifier.fillMaxSize().imePadding().testTag("profileEditScreen")) {
            NavHeader("프로필 수정", divider = true)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Label("닉네임")
                DqTextField(ui.input, vm::onInput, "닉네임 (${ProfileValidator.NICKNAME_MIN}~${ProfileValidator.NICKNAME_MAX}자)", modifier = Modifier.testTag("nicknameInput"), radius = 16.dp, shadow = true)
                Msg(ui.error, "nicknameError")
                Label("이메일")
                Txt(email ?: "등록된 이메일이 없어요", size = 14f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp).testTag("emailReadOnly"))
                Txt("이메일과 프로필 사진은 변경할 수 없어요.", size = 12f, color = DQColors.Muted2, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                Btn(
                    if (ui.saving) "저장 중…" else "저장",
                    Modifier.padding(top = 24.dp).fillMaxWidth().height(54.dp).testTag("nicknameSave"),
                    size = 16f, enabled = !ui.saving,
                ) { vm.save() }
            }
        }
    }
}

@Composable
fun PasswordChangeScreen() {
    val vm: PasswordChangeViewModel = viewModel(factory = PasswordChangeViewModel.Factory(LocalAppContainer.current))
    val ui by vm.ui.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    DqScreen {
        Column(Modifier.fillMaxSize().imePadding().testTag("passwordScreen")) {
            NavHeader("비밀번호 변경", divider = true)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (ui.unsupported) {
                    Txt(
                        "소셜 로그인으로 가입한 계정은 비밀번호를 변경할 수 없어요.",
                        size = 14f, color = DQColors.Muted, modifier = Modifier.padding(top = 8.dp).testTag("passwordUnsupported"),
                    )
                } else {
                    Label("현재 비밀번호")
                    DqTextField(ui.current, vm::onCurrent, "현재 비밀번호", modifier = Modifier.testTag("currentPasswordInput"), radius = 16.dp, secure = !show, keyboardType = KeyboardType.Password, shadow = true)
                    Label("새 비밀번호")
                    DqTextField(ui.new, vm::onNew, "새 비밀번호 (${ProfileValidator.PASSWORD_MIN}~${ProfileValidator.PASSWORD_MAX}자)", modifier = Modifier.testTag("newPasswordInput"), radius = 16.dp, secure = !show, keyboardType = KeyboardType.Password, shadow = true)
                    Label("새 비밀번호 확인")
                    DqTextField(ui.confirm, vm::onConfirm, "새 비밀번호 확인", modifier = Modifier.testTag("confirmPasswordInput"), radius = 16.dp, secure = !show, keyboardType = KeyboardType.Password, shadow = true)
                    Row(Modifier.padding(top = 10.dp).clickable { show = !show }.testTag("passwordToggle"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(show, { show = it }, colors = CheckboxDefaults.colors(checkedColor = DQColors.Primary))
                        Txt("비밀번호 표시", size = 13f)
                    }
                    Msg(ui.error, "passwordError")
                    Txt("변경하면 이 기기에서 바로 로그아웃돼요. 다른 기기는 저장된 로그인이 해제되어, 현재 로그인 유지 시간(최대 약 30분)이 지나면 새 비밀번호로 다시 로그인해야 해요.", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                    Btn(
                        if (ui.saving) "변경 중…" else "비밀번호 변경",
                        Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).testTag("passwordSubmit"),
                        size = 16f, enabled = !ui.saving,
                    ) { vm.submit() }
                }
            }
        }
    }
}

@Composable
fun WithdrawScreen() {
    val nav = LocalNav.current
    val vm: WithdrawViewModel = viewModel(factory = WithdrawViewModel.Factory(LocalAppContainer.current))
    val ui by vm.ui.collectAsStateWithLifecycle()
    DqScreen {
        Column(Modifier.fillMaxSize().imePadding().testTag("withdrawScreen")) {
            NavHeader("회원 탈퇴", divider = true)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Txt("탈퇴하기 전에 확인해 주세요", size = 18f, weight = Bold)
                Txt(
                    "• 탈퇴하면 계정을 복구할 수 없어요.\n" +
                        "• 참여 중인 방에서 나가게 되고, 닉네임과 이메일 등 개인정보는 삭제돼요.\n" +
                        "• 이미 남긴 인증 기록은 익명으로 남을 수 있어요.\n" +
                        "• 같은 소셜 계정으로 다시 가입하면 새 계정으로 시작해요.\n" +
                        "• 방장인 방이 있으면 먼저 방장을 위임해야 해요.",
                    size = 13.5f, color = DQColors.Muted, lineHeight = 1.5f, modifier = Modifier.padding(top = 12.dp),
                )
                Label("비밀번호 확인")
                DqTextField(ui.password, vm::onPassword, "이메일 가입 계정은 비밀번호를 입력해 주세요", modifier = Modifier.testTag("withdrawPasswordInput"), radius = 16.dp, secure = true, keyboardType = KeyboardType.Password, shadow = true)
                Row(Modifier.padding(top = 12.dp).clickable { vm.onAgreed(!ui.agreed) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ui.agreed, { vm.onAgreed(it) }, modifier = Modifier.testTag("withdrawAgree"), colors = CheckboxDefaults.colors(checkedColor = DQColors.Primary))
                    Txt("위 내용을 확인했고 탈퇴에 동의해요", size = 13f)
                }
                Msg(ui.error, "withdrawError")
                if (ui.ownerBlocked) {
                    Txt(
                        "방장인 방이 있어 탈퇴할 수 없어요. 홈에서 방 관리로 이동해 방장을 먼저 위임해 주세요.",
                        size = 13f, color = DQColors.Ink, modifier = Modifier.padding(top = 10.dp).testTag("withdrawOwnerNotice"),
                    )
                    Btn("방 관리하러 가기", Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).testTag("withdrawGoRooms"), style = BtnStyle.Outline) {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true }
                    }
                }
                Btn(
                    if (ui.saving) "처리 중…" else "회원 탈퇴",
                    Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).testTag("withdrawSubmit"),
                    size = 16f, enabled = !ui.saving,
                ) { vm.requestConfirm() }
            }
        }
    }
    if (ui.showConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            title = { Txt("정말 탈퇴할까요?", size = 17f, weight = Bold) },
            text = { Txt("탈퇴하면 되돌릴 수 없어요.", size = 14f) },
            confirmButton = { TextButton(onClick = vm::confirm, modifier = Modifier.testTag("withdrawConfirm")) { Txt("탈퇴", color = DQColors.Orange, weight = Bold) } },
            dismissButton = { TextButton(onClick = vm::dismissConfirm, modifier = Modifier.testTag("withdrawCancel")) { Txt("취소") } },
        )
    }
}
