package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.navigation.*
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.room.CreateRoomViewModel
import com.dailyquest.app.ui.room.OUTCOME_UNKNOWN_MESSAGE
import com.dailyquest.app.ui.theme.DQColors

/** 방 만들기 — 서버(POST /rooms)에 생성한다. 초기 공개 여부는 진입한 탭이 정하고 화면에서 바꿀 수 있다. */
@Composable
fun CreateRoomScreen(viewModel: CreateRoomViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // 성공 이벤트는 한 번만 처리한다. 이동 후 비우고, 생성 화면은 back stack에서 제거해 뒤로가기로 재생성되지 않는다.
    LaunchedEffect(ui.created) {
        val created = ui.created ?: return@LaunchedEffect
        viewModel.consumeCreated()
        nav.navigate(Routes.room(created.roomId)) {
            popUpTo(Routes.CREATE_ROOM_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }

    DqScreen {
        Column(Modifier.testTag("realCreateRoom")) {
            NavHeader("방 만들기")
            FormBody {
                FieldLabel("방 이름", first = true)
                DqTextField(ui.name, viewModel::onNameChange, "2~20자", maxLength = 20)
                FieldError(ui.nameError)
                FieldLabel("방 설명 (선택)")
                DqTextArea(ui.description, viewModel::onDescriptionChange, "어떤 방인지 소개해 주세요", height = 110.dp)
                FieldError(ui.descriptionError)
                FieldLabel("공개 설정")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("우리만 · 비공개", !ui.isPublic) { viewModel.onPublicChange(false) }
                    Chip("공개", ui.isPublic) { viewModel.onPublicChange(true) }
                }
                InfoNote(if (ui.isPublic) "누구나 공개방 목록에서 참여할 수 있어요." else "초대코드로만 참여할 수 있어요.")
                FieldLabel("최대 인원")
                DqTextField(ui.maxMembers, viewModel::onMaxMembersChange, "2~30명", keyboardType = KeyboardType.Number, maxLength = 3)
                FieldError(ui.maxMembersError)
                FieldError(ui.serverError)
                if (ui.outcomeUnknown) {
                    Txt(
                        OUTCOME_UNKNOWN_MESSAGE, size = 12.5f, color = DQColors.Orange,
                        modifier = Modifier.padding(top = 16.dp).testTag("createOutcomeUnknown"),
                    )
                }
            }
        }
        BottomCta {
            Btn(
                if (ui.submitting) "만드는 중…" else "방 만들기",
                Modifier.fillMaxWidth().height(50.dp),
                enabled = !ui.submitting,
            ) { viewModel.submit() }
        }
    }
}

@Composable
private fun FieldError(message: String?) {
    if (message != null) Txt(message, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}
