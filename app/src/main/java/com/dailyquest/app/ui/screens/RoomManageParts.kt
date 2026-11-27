package com.dailyquest.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.R
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.room.RoomValidation
import com.dailyquest.app.ui.avatar.ProfileFace
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.DqConfirmDialog
import com.dailyquest.app.ui.components.DqDialogButton
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqNameChip
import com.dailyquest.app.ui.components.DqPopup
import com.dailyquest.app.ui.components.DqTextArea
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.ROLE_OWNER
import com.dailyquest.app.ui.room.RoomDetailUiState
import com.dailyquest.app.ui.room.RoomManageViewModel
import com.dailyquest.app.ui.theme.DQColors

internal sealed interface RoomDialog {
    data object Edit : RoomDialog
    data object Delete : RoomDialog
    data object Leave : RoomDialog
    data object LeaveBlocked : RoomDialog
    data object Regenerate : RoomDialog
    data object Members : RoomDialog
    data class Kick(val member: RoomMemberDto) : RoomDialog
    data class Transfer(val member: RoomMemberDto) : RoomDialog
}

/** 방 관리 창 상태. 헤더의 ⋮ 메뉴([RoomManageMenu])가 열고, 본문([RoomManageSection])이 창을 그린다. */
@Stable
class RoomManageState {
    internal var dialog by mutableStateOf<RoomDialog?>(null)
}

@Composable
fun rememberRoomManageState(): RoomManageState = remember { RoomManageState() }

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

/**
 * 방 헤더의 ⋮ 메뉴 (QA 디자인): 아이콘 + 방 설정 · 멤버 관리 · (코드 재발급) · 방 나가기 · 방 삭제(빨강).
 * 표시 여부는 서버가 준 myRole 기준이며 최종 검사는 서버가 한다. 멤버가 아니면 메뉴가 없다.
 */
@Composable
fun RoomManageMenu(ui: RoomDetailUiState, manage: RoomManageViewModel, state: RoomManageState) {
    val detail = ui.detail ?: return
    if (!ui.isMember) return
    val m by manage.ui.collectAsStateWithLifecycle()
    val busy = m.busy != null
    var open by remember { mutableStateOf(false) }
    val others = ui.members.filter { it.userId != m.myUserId }

    Box {
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }.testTag("roomMenu")
                .semantics { contentDescription = "방 메뉴" },
            contentAlignment = Alignment.Center,
        ) { DqIcon(R.drawable.ic_more, 22.dp, Modifier.rotate(90f)) }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            containerColor = DQColors.White, shape = RoundedCornerShape(16.dp),
        ) {
            val item: @Composable (Int, String, String, Boolean, () -> RoomDialog) -> Unit = { icon, label, tag, danger, next ->
                val color = if (danger) DQColors.Danger else DQColors.Ink
                DropdownMenuItem(
                    text = { Txt(label, size = 14.5f, weight = Bold, color = color) },
                    leadingIcon = { Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (danger) DQColors.Danger else DQColors.InkGreen)) },
                    onClick = { open = false; state.dialog = next() },
                    enabled = !busy,
                    modifier = Modifier.testTag(tag),
                )
            }
            if (ui.isOwner) {
                item(R.drawable.ic_settings, "방 설정", "menuEdit", false) { RoomDialog.Edit }
                if (ui.membersLoaded && others.isNotEmpty()) item(R.drawable.ic_users, "멤버 관리", "menuMembers", false) { RoomDialog.Members }
                if (!detail.isPublic) item(R.drawable.ic_refresh, "코드 재발급", "menuRegenerate", false) { RoomDialog.Regenerate }
            }
            item(R.drawable.ic_logout, "방 나가기", "menuLeave", false) { if (ui.isOwner && detail.memberCount > 1) RoomDialog.LeaveBlocked else RoomDialog.Leave }
            if (ui.isOwner) item(R.drawable.ic_delete, "방 삭제", "menuDelete", true) { RoomDialog.Delete }
        }
    }
}

/**
 * 방 본문의 관리 안내(오류·처리 중)와 ⋮ 메뉴가 연 팝업들 (QA 디자인: 모두 가운데 팝업).
 * @param faceOf 멤버 관리 팝업의 얼굴(방 화면이 멤버 목록으로 계산한 것)
 */
@Composable
fun RoomManageSection(
    ui: RoomDetailUiState, manage: RoomManageViewModel, state: RoomManageState,
    faceOf: (Long) -> PersonFace? = { null },
) {
    val detail = ui.detail ?: return
    if (!ui.isMember) return
    val m by manage.ui.collectAsStateWithLifecycle()
    val busy = m.busy != null
    var dialog by state::dialog

    Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp).testTag("roomManage")) {
        m.error?.let { Txt(it, size = 11.5f, color = DQColors.Orange, modifier = Modifier.padding(vertical = 4.dp).testTag("manageError")) }
        if (busy) Txt("처리 중이에요…", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(vertical = 4.dp))
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        RoomDialog.Edit -> EditRoomDialog(detail, onDismiss = close) { e ->
            dialog = null
            if (!e.isEmpty) manage.update(e.name, e.description, e.isPublic, e.maxMembers)
        }
        RoomDialog.Delete -> DqConfirmDialog(
            title = "이 방을 삭제할까요?",
            body = "모든 멤버가 방에서 나가게 되며,\n삭제한 방은 복구할 수 없어요.",
            confirmLabel = "방 삭제", danger = true, icon = R.drawable.ic_delete, confirmTag = "confirmAction",
            onConfirm = { dialog = null; manage.delete() }, onDismiss = close,
            content = { DqNameChip(detail.name) },
        )
        RoomDialog.Leave -> DqConfirmDialog(
            title = "마을에서 나갈까요?",
            body = if (detail.memberCount <= 1) "마지막 멤버예요.\n나가면 이 마을이 삭제돼요." else "나가면 이 마을의 멤버에서 제외돼요.",
            confirmLabel = "나가기", danger = true, icon = R.drawable.ic_logout, confirmTag = "confirmAction",
            onConfirm = { dialog = null; manage.leave() }, onDismiss = close,
            content = { DqNameChip(detail.name) },
        )
        RoomDialog.LeaveBlocked -> DqConfirmDialog(
            title = "먼저 방장을 위임해 주세요",
            body = "다른 멤버가 남아 있어 바로 나갈 수 없어요.\n멤버 관리에서 새 방장을 정해 주세요.",
            confirmLabel = "멤버 관리", icon = R.drawable.ic_crown, dismissLabel = "닫기", confirmTag = "leaveBlockedMembers",
            onConfirm = { dialog = RoomDialog.Members }, onDismiss = close,
        )
        RoomDialog.Regenerate -> DqConfirmDialog(
            title = "초대코드를 새로 발급할까요?",
            body = "기존 코드는 즉시 사용할 수 없게 돼요.",
            confirmLabel = "재발급", icon = R.drawable.ic_refresh, confirmTag = "confirmAction",
            onConfirm = { dialog = null; manage.regenerateInviteCode() }, onDismiss = close,
        )
        RoomDialog.Members -> MembersDialog(
            members = ui.members, myUserId = m.myUserId, isOwner = ui.isOwner,
            count = detail.memberCount, max = detail.maxMembers, busy = busy, faceOf = faceOf,
            onDismiss = close,
            onTransfer = { dialog = RoomDialog.Transfer(it) },
            onKick = { dialog = RoomDialog.Kick(it) },
        )
        is RoomDialog.Kick -> DqConfirmDialog(
            title = "${d.member.nickname}님을 내보낼까요?",
            body = "내보내면 이 마을의 멤버에서 제외돼요.",
            confirmLabel = "내보내기", danger = true, icon = R.drawable.ic_logout, confirmTag = "confirmAction",
            onConfirm = { dialog = null; manage.kick(d.member.userId) }, onDismiss = { dialog = RoomDialog.Members },
            content = { DqNameChip(detail.name) },
        )
        is RoomDialog.Transfer -> DqConfirmDialog(
            title = "${d.member.nickname}님에게 방장을 넘길까요?",
            body = "나는 일반 멤버가 되고\n방장 기능을 쓸 수 없어요.",
            confirmLabel = "위임하기", icon = R.drawable.ic_crown, confirmTag = "confirmAction",
            onConfirm = { dialog = null; manage.transferOwner(d.member.userId) }, onDismiss = { dialog = RoomDialog.Members },
            content = { DqNameChip(detail.name) },
        )
    }

    m.notice?.let {
        DqConfirmDialog(
            title = "알림", body = it, confirmLabel = "확인", dismissLabel = null, icon = R.drawable.ic_info, bodyTag = "manageNotice",
            onConfirm = { manage.dismissMessages() }, onDismiss = { manage.dismissMessages() },
        )
    }
}

/**
 * 멤버 관리 팝업 (QA 디자인): "멤버 관리 2/10", 안내 문구, 멤버마다 얼굴·이름·방장/나 표시.
 * 방장이면 다른 멤버 줄에 방장 위임(초록 테두리)·내보내기(빨강 테두리). 누르면 확인 팝업으로 넘어간다.
 */
@Composable
private fun MembersDialog(
    members: List<RoomMemberDto>, myUserId: Long?, isOwner: Boolean, count: Long, max: Int, busy: Boolean,
    faceOf: (Long) -> PersonFace?, onDismiss: () -> Unit,
    onTransfer: (RoomMemberDto) -> Unit, onKick: (RoomMemberDto) -> Unit,
) {
    DqPopup(onDismiss, tag = "membersDialog") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("멤버 관리", size = 19f, weight = Bold, color = DQColors.InkGreen)
            Txt("$count/$max", size = 15f, weight = Bold, color = DQColors.Green, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "닫기" }.testTag("membersClose"),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.ic_close), null, Modifier.size(22.dp)) }
        }
        Txt("함께하는 멤버와 권한을 관리해요.", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(top = 2.dp))
        // 방장 → 나 → 나머지 순서.
        val sorted = members.sortedWith(compareBy({ it.role != ROLE_OWNER }, { it.userId != myUserId }))
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sorted.forEach { mem ->
                val me = mem.userId == myUserId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, DQColors.Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileFace(faceOf(mem.userId), 44.dp, contentDescription = "${mem.nickname} 아바타")
                    Column(Modifier.weight(1f)) {
                        Txt(mem.nickname, size = 15f, weight = Bold, maxLines = 1)
                        if (mem.role == ROLE_OWNER || me) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (mem.role == ROLE_OWNER) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(DQColors.Gold.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Image(painterResource(R.drawable.ic_crown), null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(DQColors.Gold))
                                    Txt("방장", size = 11.5f, weight = Bold, color = DQColors.Gold)
                                }
                            }
                            if (me) Txt(if (mem.role == ROLE_OWNER) " · 나" else "나", size = 11.5f, weight = Bold, color = DQColors.Muted)
                        }
                    }
                    if (isOwner && !me) {
                        SmallOutlineButton("방장 위임", DQColors.Green, enabled = !busy) { onTransfer(mem) }
                        SmallOutlineButton("내보내기", DQColors.Danger, enabled = !busy) { onKick(mem) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallOutlineButton(label: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(32.dp).clip(shape).border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 12.5f, weight = Bold, color = color) }
}

/**
 * 방 설정 팝업 (QA 디자인): "방 설정" + X, 방 이름, 방 소개(100자), 공개 설정(공개/비공개 큰 카드), 최대 인원(− N명 +), "변경사항 저장".
 * 최대 인원은 2~30명이고 지금 멤버 수보다 적게 내릴 수 없다(− 버튼이 멈춘다). 바뀐 필드만 서버에 보낸다([diffRoomEdit]).
 */
@Composable
private fun EditRoomDialog(detail: RoomDetailDto, onDismiss: () -> Unit, onSave: (RoomEdit) -> Unit) {
    var name by rememberSaveable(detail.roomId) { mutableStateOf(detail.name) }
    var description by rememberSaveable(detail.roomId) { mutableStateOf(detail.description.orEmpty()) }
    var isPublic by rememberSaveable(detail.roomId) { mutableStateOf(detail.isPublic) }
    var capacity by rememberSaveable(detail.roomId) { mutableIntStateOf(detail.maxMembers) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var descError by remember { mutableStateOf<String?>(null) }
    var capError by remember { mutableStateOf<String?>(null) }
    val minCapacity = maxOf(MIN_ROOM_CAPACITY, detail.memberCount.toInt())

    DqPopup(onDismiss, tag = "editRoomDialog") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("방 설정", size = 19f, weight = Bold, color = DQColors.InkGreen, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "닫기" }.testTag("editRoomClose"),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.ic_close), null, Modifier.size(22.dp)) }
        }
        FieldTitle("방 이름")
        DqTextField(name, { name = it; nameError = null }, "방 이름 (2~20자)", maxLength = 20)
        nameError?.let { Txt(it, size = 12f, color = DQColors.Danger, modifier = Modifier.padding(top = 4.dp)) }
        FieldTitle("방 소개")
        DqTextArea(description, { description = it; descError = null }, "방 소개 (선택)", height = 96.dp)
        descError?.let { Txt(it, size = 12f, color = DQColors.Danger, modifier = Modifier.padding(top = 4.dp)) }

        FieldTitle("공개 설정")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VisibilityCard(R.drawable.ic_globe, "공개", isPublic, Modifier.weight(1f).testTag("editPublic")) { isPublic = true }
            VisibilityCard(R.drawable.ic_lock, "비공개", !isPublic, Modifier.weight(1f).testTag("editPrivate")) { isPublic = false }
        }
        Txt(
            when {
                isPublic != detail.isPublic && isPublic -> "공개로 바꾸면 초대코드가 사라지고 공개 목록에 표시돼요."
                isPublic != detail.isPublic -> "비공개로 바꾸면 새 초대코드가 발급되고 공개 목록에서 사라져요."
                isPublic -> "누구나 마을을 찾고 참여할 수 있어요."
                else -> "초대코드를 받은 친구만 참여할 수 있어요."
            },
            size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 6.dp),
        )

        FieldTitle("최대 인원")
        Row(
            Modifier.fillMaxWidth().height(50.dp).border(1.dp, DQColors.Line, RoundedCornerShape(14.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(R.drawable.ic_minus, "인원 줄이기", capacity > minCapacity, "capacityMinus") { capacity--; capError = null }
            Txt("${capacity}명", size = 16f, weight = Bold, align = TextAlign.Center, modifier = Modifier.weight(1f).testTag("capacityValue"))
            StepButton(R.drawable.ic_plus, "인원 늘리기", capacity < MAX_ROOM_CAPACITY, "capacityPlus") { capacity++; capError = null }
        }
        if (detail.memberCount > MIN_ROOM_CAPACITY) {
            Txt("지금 멤버 수(${detail.memberCount}명)보다 적게 설정할 수 없어요.", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        capError?.let { Txt(it, size = 12f, color = DQColors.Danger, modifier = Modifier.padding(top = 4.dp)) }

        DqDialogButton("변경사항 저장", Modifier.padding(top = 20.dp).fillMaxWidth().testTag("editRoomSave")) {
            nameError = RoomValidation.nameError(name)
            descError = RoomValidation.descriptionError(description)
            capError = RoomValidation.maxMembersError(capacity.toString())
                ?: capacity.takeIf { it < detail.memberCount.toInt() }?.let { "현재 멤버 수(${detail.memberCount}명)보다 적게 설정할 수 없어요." }
            if (nameError == null && descError == null && capError == null) {
                onSave(diffRoomEdit(detail, name, description, isPublic, capacity))
            }
        }
    }
}

private const val MIN_ROOM_CAPACITY = 2
private const val MAX_ROOM_CAPACITY = 30

@Composable
private fun FieldTitle(text: String) = Txt(text, size = 13.5f, weight = Bold, color = DQColors.Ink, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))

/** 공개/비공개 선택 카드: 고르면 초록 테두리·연한 초록 바탕·오른쪽 위 체크. */
@Composable
private fun VisibilityCard(icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(64.dp).clip(shape)
            .background(if (selected) DQColors.Soft else DQColors.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) DQColors.Primary else DQColors.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" },
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (selected) DQColors.InkGreen else DQColors.Muted))
            Txt(label, size = 14f, weight = Bold, color = if (selected) DQColors.InkGreen else DQColors.Muted, modifier = Modifier.padding(top = 3.dp))
        }
        if (selected) Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).clip(CircleShape).background(DQColors.Primary),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(R.drawable.ic_check_white), null, Modifier.size(10.dp)) }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (enabled) DQColors.Soft else DQColors.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(if (enabled) DQColors.InkGreen else DQColors.Line))
    }
}
