package com.dailyquest.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.room.RoomValidation
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.DqTextArea
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.RoomDetailUiState
import com.dailyquest.app.ui.room.RoomManageViewModel
import com.dailyquest.app.ui.theme.DQColors

private sealed interface Dialog {
    data object Edit : Dialog
    data object Delete : Dialog
    data object Leave : Dialog
    data object LeaveBlocked : Dialog
    data object Regenerate : Dialog
    data class Kick(val member: RoomMemberDto) : Dialog
    data class Transfer(val member: RoomMemberDto) : Dialog
}

/** 현재 사용자가 수정할 때 서버에 보낼 변경 필드. 바뀌지 않은 필드는 null로 두어 보내지 않는다. */
internal data class RoomEdit(val name: String?, val description: String?, val isPublic: Boolean?, val maxMembers: Int?) {
    val isEmpty get() = name == null && description == null && isPublic == null && maxMembers == null
}

internal fun diffRoomEdit(d: RoomDetailDto, name: String, description: String, isPublic: Boolean, maxMembers: Int): RoomEdit = RoomEdit(
    name = name.trim().takeIf { it != d.name },
    // 설명을 지우려면 빈 문자열을 보낸다(서버가 공백을 null로 저장). 원래 비어 있었다면 보내지 않는다.
    description = description.trim().takeIf { it != d.description.orEmpty() },
    isPublic = isPublic.takeIf { it != d.isPublic },
    maxMembers = maxMembers.takeIf { it != d.maxMembers },
)

/** 방 관리 버튼·멤버 관리·확인 다이얼로그. 표시 여부는 서버가 준 myRole 기준이며 최종 검사는 서버가 한다. */
@Composable
fun RoomManageSection(ui: RoomDetailUiState, manage: RoomManageViewModel) {
    val detail = ui.detail ?: return
    if (!ui.isMember) return
    val m by manage.ui.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val clipboard = LocalClipboardManager.current
    val busy = m.busy != null

    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).testTag("roomManage")) {
        ui.visibleInviteCode?.let { code ->
            Txt(
                "초대코드 복사", size = 12f, weight = Bold, color = DQColors.Green,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(code)) }.padding(vertical = 4.dp).testTag("copyInviteCode"),
            )
        }
        m.error?.let { Txt(it, size = 11.5f, color = DQColors.Orange, modifier = Modifier.padding(vertical = 4.dp).testTag("manageError")) }
        if (busy) Txt("처리 중이에요…", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(vertical = 4.dp))

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.isOwner) {
                Btn("방 설정", Modifier.weight(1f).height(38.dp), style = BtnStyle.Outline, size = 12f, enabled = !busy) { dialog = Dialog.Edit }
                if (!detail.isPublic) {
                    Btn("코드 재발급", Modifier.weight(1f).height(38.dp), style = BtnStyle.Outline, size = 12f, enabled = !busy) { dialog = Dialog.Regenerate }
                }
            }
            Btn("방 나가기", Modifier.weight(1f).height(38.dp), style = BtnStyle.Outline, size = 12f, enabled = !busy) {
                dialog = if (ui.isOwner && detail.memberCount > 1) Dialog.LeaveBlocked else Dialog.Leave
            }
            if (ui.isOwner) {
                Btn("방 삭제", Modifier.weight(1f).height(38.dp), style = BtnStyle.Outline, size = 12f, enabled = !busy) { dialog = Dialog.Delete }
            }
        }

        if (ui.isOwner && ui.membersLoaded) {
            val others = ui.members.filter { it.userId != m.myUserId }
            if (others.isNotEmpty()) {
                Txt("멤버 관리", size = 12.5f, weight = Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                others.forEach { mem ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(mem.nickname, size = 12.5f, modifier = Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { dialog = Dialog.Transfer(mem) }) { Text("방장 위임") }
                        TextButton(enabled = !busy, onClick = { dialog = Dialog.Kick(mem) }) { Text("내보내기") }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.Edit -> EditRoomDialog(detail, onDismiss = { dialog = null }) { e ->
            dialog = null
            if (!e.isEmpty) manage.update(e.name, e.description, e.isPublic, e.maxMembers)
        }
        Dialog.Delete -> ConfirmDialog(
            "방 삭제", "‘${detail.name}’ 방을 삭제할까요? 모든 멤버가 방을 잃고 되돌릴 수 없어요.", "삭제",
            onDismiss = { dialog = null },
        ) { dialog = null; manage.delete() }
        Dialog.Leave -> ConfirmDialog(
            "방 나가기",
            if (detail.memberCount <= 1) "마지막 멤버예요. 나가면 방이 삭제돼요. 나갈까요?" else "‘${detail.name}’ 방에서 나갈까요?",
            "나가기", onDismiss = { dialog = null },
        ) { dialog = null; manage.leave() }
        Dialog.LeaveBlocked -> AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("확인") } },
            text = { Text("방장은 다른 멤버가 있으면 나갈 수 없어요. 먼저 방장을 위임하거나 방을 삭제해 주세요.") },
            containerColor = DQColors.White,
        )
        Dialog.Regenerate -> ConfirmDialog(
            "초대코드 재발급", "새 초대코드를 발급할까요? 기존 코드는 즉시 사용할 수 없게 돼요.", "재발급",
            onDismiss = { dialog = null },
        ) { dialog = null; manage.regenerateInviteCode() }
        is Dialog.Kick -> ConfirmDialog(
            "멤버 내보내기", "‘${d.member.nickname}’ 님을 내보낼까요?", "내보내기", onDismiss = { dialog = null },
        ) { dialog = null; manage.kick(d.member.userId) }
        is Dialog.Transfer -> ConfirmDialog(
            "방장 위임", "‘${d.member.nickname}’ 님에게 방장을 넘길까요? 나는 일반 멤버가 되고 방장 기능을 쓸 수 없어요.", "위임",
            onDismiss = { dialog = null },
        ) { dialog = null; manage.transferOwner(d.member.userId) }
    }

    m.notice?.let {
        AlertDialog(
            onDismissRequest = { manage.dismissMessages() },
            confirmButton = { TextButton(onClick = { manage.dismissMessages() }) { Text("확인") } },
            text = { Text(it, modifier = Modifier.testTag("manageNotice")) },
            containerColor = DQColors.White,
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onOk, modifier = Modifier.testTag("confirmAction")) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        containerColor = DQColors.White,
    )
}

@Composable
private fun EditRoomDialog(detail: RoomDetailDto, onDismiss: () -> Unit, onSave: (RoomEdit) -> Unit) {
    var name by rememberSaveable(detail.roomId) { mutableStateOf(detail.name) }
    var description by rememberSaveable(detail.roomId) { mutableStateOf(detail.description.orEmpty()) }
    var isPublic by rememberSaveable(detail.roomId) { mutableStateOf(detail.isPublic) }
    var capacity by rememberSaveable(detail.roomId) { mutableStateOf(detail.maxMembers.toString()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var descError by remember { mutableStateOf<String?>(null) }
    var capError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방 설정") },
        text = {
            Column(Modifier.testTag("editRoomDialog")) {
                DqTextField(name, { name = it; nameError = null }, "방 이름 (2~20자)", maxLength = 20)
                nameError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(top = 4.dp)) }
                Column(Modifier.padding(top = 8.dp)) {
                    DqTextArea(description, { description = it; descError = null }, "방 설명 (선택)", height = 96.dp)
                }
                descError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(top = 4.dp)) }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("우리만 · 비공개", !isPublic) { isPublic = false }
                    Chip("공개", isPublic) { isPublic = true }
                }
                if (isPublic != detail.isPublic) {
                    Txt(
                        if (isPublic) "공개로 바꾸면 초대코드가 사라지고 공개 목록에 표시돼요." else "비공개로 바꾸면 새 초대코드가 발급되고 공개 목록에서 사라져요.",
                        size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                DqTextField(capacity, { capacity = it; capError = null }, "최대 인원 (2~30명)", keyboardType = KeyboardType.Number, maxLength = 3,
                    modifier = Modifier.padding(top = 10.dp))
                capError?.let { Txt(it, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                nameError = RoomValidation.nameError(name)
                descError = RoomValidation.descriptionError(description)
                capError = RoomValidation.maxMembersError(capacity)
                    ?: capacity.trim().toInt().takeIf { it < detail.memberCount }?.let { "현재 멤버 수(${detail.memberCount}명)보다 적게 설정할 수 없어요." }
                if (nameError == null && descError == null && capError == null) {
                    onSave(diffRoomEdit(detail, name, description, isPublic, capacity.trim().toInt()))
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        containerColor = DQColors.White,
    )
}
