package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.navigation.*
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.room.JOIN_UNKNOWN_MESSAGE
import com.dailyquest.app.ui.room.RoomJoinViewModel
import com.dailyquest.app.ui.theme.DQColors

/** 초대코드 참여 — 서버(POST /rooms/join)로 참여한다. */
@Composable
fun JoinRoomScreen(viewModel: RoomJoinViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // 성공 이벤트는 한 번만 처리하고 참여 화면은 back stack에서 제거한다.
    LaunchedEffect(ui.joinedRoomId) {
        val roomId = ui.joinedRoomId ?: return@LaunchedEffect
        viewModel.consumeJoined()
        nav.navigate(Routes.room(roomId)) {
            popUpTo(Routes.JOIN_ROOM) { inclusive = true }
            launchSingleTop = true
        }
    }

    DqScreen {
        Column(Modifier.testTag("realJoinRoom")) {
            NavHeader("초대코드 참여")
            FormBody {
                FieldLabel("초대코드", first = true)
                DqTextField(ui.code, viewModel::onCodeChange, "초대받은 코드를 입력해 주세요", maxLength = 30)
                InfoNote("코드를 확인한 뒤 해당 방에 참여해요. 이미 참여한 방은 우리만 탭에서 확인할 수 있어요.")
                (ui.codeError ?: ui.error)?.let { Txt(it, color = DQColors.Orange, modifier = Modifier.padding(top = 16.dp)) }
                if (ui.outcomeUnknown) {
                    Txt(JOIN_UNKNOWN_MESSAGE, size = 12.5f, color = DQColors.Orange,
                        modifier = Modifier.padding(top = 16.dp).testTag("joinOutcomeUnknown"))
                }
            }
        }
        BottomCta {
            Btn(
                if (ui.submitting) "참여 중…" else "참여하기",
                Modifier.fillMaxWidth().height(50.dp),
                enabled = !ui.submitting,
            ) { viewModel.joinByCode() }
        }
    }
}
