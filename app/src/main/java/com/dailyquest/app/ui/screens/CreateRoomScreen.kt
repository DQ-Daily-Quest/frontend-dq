package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.mock.MockRoomData
import com.dailyquest.app.navigation.*
import com.dailyquest.app.ui.components.*
import com.dailyquest.app.ui.theme.DQColors

@Composable
fun CreateRoomScreen(initialIsPublic: Boolean = false) {
    val nav = LocalNav.current
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable(initialIsPublic) { mutableStateOf(initialIsPublic) }
    var capacity by rememberSaveable { mutableStateOf("10") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    DqScreen {
        Column {
            NavHeader("방 만들기")
            FormBody {
                FieldLabel("방 이름", first = true)
                DqTextField(name, { name = it; error = null }, "2~20자", maxLength = 20)
                FieldLabel("방 설명 (선택)")
                DqTextArea(description, { description = it; error = null }, "어떤 방인지 소개해 주세요", height = 110.dp)
                FieldLabel("공개 설정")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("우리만 · 비공개", !isPublic) { isPublic = false }
                    Chip("공개", isPublic) { isPublic = true }
                }
                InfoNote(if (isPublic) "누구나 공개방 목록에서 참여할 수 있어요." else "초대코드로만 참여할 수 있어요.")
                FieldLabel("최대 인원")
                DqTextField(capacity, { capacity = it; error = null }, "2~30명", keyboardType = KeyboardType.Number, maxLength = 3)
                error?.let { Txt(it, color = DQColors.Orange, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
        BottomCta {
            Btn("방 만들기", Modifier.fillMaxWidth().height(50.dp)) {
                if (!submitted) {
                    val count = capacity.toIntOrNull()
                    if (count == null || count !in 2..30) {
                        error = "최대 인원은 2~30명이어야 합니다."
                    } else {
                        try {
                            val room = MockRoomData.createRoom(name, description, isPublic, count)
                            submitted = true
                            nav.navigate(Routes.mockRoom(room.roomId)) {
                                popUpTo(Routes.CREATE_ROOM_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        } catch (e: IllegalArgumentException) {
                            error = e.message
                        }
                    }
                }
            }
        }
    }
}
