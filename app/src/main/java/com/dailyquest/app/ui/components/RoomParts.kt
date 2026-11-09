package com.dailyquest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.ui.theme.DQColors

/** 멤버: story 가 있으면 핑크 링(새 인증) 아바타 */
data class Member(val name: String, @DrawableRes val story: Int? = null, @DrawableRes val avatar: Int? = null)

/** 방 멤버 줄 (03 · 13) — 멤버 + 초대 버튼 */
@Composable
fun MemberRow(members: List<Member>, size: Dp, inviteSize: Dp, showInviteLabel: Boolean, onStory: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        members.forEach { m ->
            Column(Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.story != null) {
                    DqImage(m.story, Modifier.size(size).clip(CircleShape).clickable(onClick = onStory))
                } else {
                    DqAvatar(m.avatar!!, size)
                }
                Txt(
                    m.name, size = 11.5f,
                    weight = if (m.story != null) Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = if (m.story != null) DQColors.Ink else DQColors.Muted,
                )
            }
        }
        Column(Modifier.width(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
