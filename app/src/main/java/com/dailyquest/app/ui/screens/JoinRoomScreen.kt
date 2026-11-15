package com.dailyquest.app.ui.screens

import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.mock.MockRoomData
import com.dailyquest.app.navigation.*
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.theme.DQColors

@Composable
fun JoinRoomScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    DqScreen {
        Column {
            NavHeader("초대코드 참여")
            FormBody {
                FieldLabel("초대코드", first = true)
                DqTextField(code, { code = it; error = null }, "초대받은 코드를 입력해 주세요", maxLength = 30)
                InfoNote("코드를 확인한 뒤 해당 방에 참여해요. 이미 참여한 방은 바로 입장해요.")
                error?.let { Txt(it, color = DQColors.Orange, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
        BottomCta {
            Btn("참여하기", Modifier.fillMaxWidth().height(50.dp)) {
                if (!submitted) {
                    if (code.isBlank()) error = "초대코드를 입력해 주세요."
                    else when (val result = MockRoomData.joinByInviteCode(code)) {
                        is MockRoomData.JoinResult.Failure -> error = result.message
                        is MockRoomData.JoinResult.Success -> {
                            submitted = true
                            if (result.alreadyJoined) {
                                Toast.makeText(context, "이미 참여 중인 방입니다.", Toast.LENGTH_SHORT).show()
                            }
                            nav.navigate(Routes.room(result.room.roomId)) {
                                popUpTo(Routes.JOIN_ROOM) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }
}
