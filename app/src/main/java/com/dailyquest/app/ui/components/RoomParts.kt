package com.dailyquest.app.ui.components

import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.ui.avatar.ProfileFace
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors

/**
 * 멤버: story 가 있으면 핑크 링(새 인증) 아바타.
 * [showsFace]면 고정 그림([avatar]) 대신 그 사람의 아바타 얼굴([face], 프로필 얼굴)을 그린다. 아직 모르면 null.
 */
data class Member(
    val name: String,
    @DrawableRes val story: Int? = null,
    @DrawableRes val avatar: Int? = null,
    val badge: String? = null,
    val face: PersonFace? = null,
    val showsFace: Boolean = false,
)

/** 방 멤버 줄 (03 · 13) — 멤버 + 초대 버튼 */
@Composable
fun MemberRow(members: List<Member>, size: Dp, inviteSize: Dp, showInviteLabel: Boolean, onStory: () -> Unit, modifier: Modifier = Modifier, showInvite: Boolean = true) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        members.forEach { m ->
            Column(Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.story != null) {
                    DqImage(m.story, Modifier.size(size).clip(CircleShape).clickable(onClick = onStory))
                } else if (m.showsFace) {
                    ProfileFace(m.face, size, contentDescription = "${m.name} 아바타")
                } else {
                    DqAvatar(m.avatar!!, size)
                }
                Txt(
                    m.name, size = 11.5f, maxLines = 1,
                    weight = if (m.story != null) Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = if (m.story != null) DQColors.Ink else DQColors.Muted,
                )
                if (m.badge != null) Txt(m.badge, size = 10f, weight = Bold, color = DQColors.Green)
            }
        }
        if (showInvite) Column(Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.padding(vertical = (size - inviteSize) / 2).size(inviteSize).clip(CircleShape)
                    .background(DQColors.White)
                    .dashedBorder(Color(0xFFA9B5AC), 1.dp, inviteSize / 2, dash = 6f, gap = 5f)
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) { DqIcon(R.drawable.ic_plus, 20.dp) }
            if (showInviteLabel) Txt("초대", size = 11.5f, color = DQColors.Muted)
        }
    }
}

/**
 * 방 화면 멤버 줄 (QA 디자인 2단계): **내 얼굴을 맨 앞에**, 세로선(│)으로 나누고 다른 멤버, 맨 끝에 초대하기 버튼.
 * @param inviteState 초대하기 버튼의 상태 설명(보조 기능이 읽음). 우리만 방이면 "초대코드 ○○○", 공개방이면 "공개방".
 */
@Composable
fun RoomMemberStrip(
    me: Member?,
    others: List<Member>,
    inviteState: String,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        me?.let { MemberFace(it, size, Modifier.testTag("memberMe")) }
        if (me != null && others.isNotEmpty()) {
            Box(Modifier.padding(top = (size - 36.dp) / 2).width(1.dp).height(36.dp).background(DQColors.Line).testTag("memberDivider"))
        }
        others.forEach { MemberFace(it, size) }
        Column(Modifier.width(size + 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.size(size).clip(CircleShape).background(DQColors.White).border(1.dp, DQColors.Line, CircleShape)
                    .clickable(role = Role.Button, onClick = onInvite)
                    .testTag("roomInvite")
                    .semantics { contentDescription = "초대하기"; stateDescription = inviteState },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_plus), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(DQColors.Primary))
            }
            Txt("초대하기", size = 11.5f, color = DQColors.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun MemberFace(m: Member, size: Dp, modifier: Modifier = Modifier) {
    Column(modifier.width(size + 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProfileFace(m.face, size, contentDescription = "${m.name} 아바타")
        Txt(m.name, size = 11.5f, maxLines = 1, color = DQColors.Muted)
        if (m.badge != null) Txt(m.badge, size = 10f, weight = Bold, color = DQColors.Green)
    }
}

data class PlacedAvatar(@DrawableRes val res: Int, val x: Float, val y: Float)

/** 방 인테리어 일러스트 + 아바타 + 방 꾸미기 버튼 (358 x h 디자인 캔버스) */
@Composable
fun RoomInterior(
    height: Float,
    avatars: List<PlacedAvatar>,
    avatarSize: Dp,
    decoX: Float,
    decoY: Float,
    decoWidth: Dp,
    modifier: Modifier = Modifier,
    onDeco: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.clip(shape).border(1.dp, Color(0xFFE8D9BF), shape)) {
        Scaled(358f, height) {
            DqImage(R.drawable.img_room_interior, Modifier.size(358.dp, height.dp))
            avatars.forEach { DqAvatar(it.res, avatarSize, Modifier.at(it.x, it.y)) }
            Btn("방 꾸미기", Modifier.at(decoX, decoY).size(decoWidth, 30.dp), style = BtnStyle.Soft, size = 12f, onClick = onDeco)
        }
    }
}
