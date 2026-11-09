package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Member
import com.dailyquest.app.ui.components.MemberRow
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.PlacedAvatar
import com.dailyquest.app.ui.components.RoomInterior
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

private val TABS = listOf("피드", "미션", "랭킹")

/** 03 · 방 내부 (우리들의 아지트) */
@Composable
fun RoomScreen() {
    val nav = LocalNav.current
    var tab by remember { mutableStateOf("피드") }

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("우리들의 아지트", right = {
                Row(
                    Modifier.height(22.dp).background(DQColors.Soft, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    DqIcon(R.drawable.ic_lock_green, 12.dp)
                    Txt("우리만", size = 10.5f, weight = Bold, color = DQColors.Green)
                }
                Box(Modifier.padding(start = 8.dp).clickable { }) { DqIcon(R.drawable.ic_more, 24.dp) }
            })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
                MemberRow(
                    members = listOf(
                        Member("지수", story = R.drawable.img_avatar_story_lg_1),
                        Member("민수", story = R.drawable.img_avatar_story_lg_2),
                        Member("서연", avatar = R.drawable.img_avatar_girl),
                        Member("나", avatar = R.drawable.img_avatar_boy),
                    ),
                    size = 52.dp, inviteSize = 44.dp, showInviteLabel = true,
                    onStory = { nav.navigate(Routes.STORY) },
                    modifier = Modifier.padding(start = 18.dp),
                )
                RoomInterior(
                    height = 252f,
                    avatars = listOf(
                        PlacedAvatar(R.drawable.img_avatar_girl, 95f, 149f),
                        PlacedAvatar(R.drawable.img_avatar_boy, 167f, 165f),
                        PlacedAvatar(R.drawable.img_avatar_girl, 227f, 145f),
                    ),
                    avatarSize = 52.dp, decoX = 265f, decoY = 213f, decoWidth = 82.dp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                ) { nav.navigate(Routes.SHOP) }

                // 피드 / 미션 / 랭킹 탭
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).drawBehind {
                        drawLine(DQColors.Soft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
                ) {
                    TABS.forEach { t ->
                        val on = t == tab
                        Box(Modifier.weight(1f).clickable { tab = t }.padding(bottom = 10.dp), contentAlignment = Alignment.TopCenter) {
                            Txt(t, size = 15f, weight = if (on) Bold else Medium, color = if (on) DQColors.Ink else DQColors.Muted)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    TABS.forEach { t ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (t == tab) Box(Modifier.padding(top = 0.dp).size(60.dp, 3.dp).background(DQColors.Primary, RoundedCornerShape(2.dp)))
                        }
                    }
                }

                // 피드 게시물
                DqCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), radius = 16.dp, soft = true) {
                    Column(Modifier.padding(9.dp)) {
                        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            DqImage(R.drawable.img_avatar_story_md_1, Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Txt("지수", size = 14f, weight = Bold)
                                Txt("오늘의 하늘 찍기 · 1시간 전", size = 11f, color = DQColors.Muted, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                Modifier.height(24.dp).widthIn(min = 84.dp).background(DQColors.Soft, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Txt("검토 중 2/3", size = 11f, weight = Bold, color = DQColors.Orange) }
                        }
                        DqImage(R.drawable.img_photo_sky, Modifier.padding(top = 10.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)))
                        Row(Modifier.padding(top = 13.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            DqIcon(R.drawable.ic_heart_outline, 18.dp)
                            Txt("5", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
                            DqIcon(R.drawable.ic_chat, 18.dp)
                            Txt("3", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp).weight(1f))
                            Txt("통과 투표하기 ›", size = 13f, weight = Bold, color = DQColors.Green, modifier = Modifier.clickable { nav.navigate(Routes.VERIFY_CHECK) })
                        }
                    }
                }
            }
        }
        // 플로팅 버튼
        Btn(
            "+ 미션 보내기",
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 24.dp).height(50.dp)
                .shadow(8.dp, RoundedCornerShape(25.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                .width(148.dp),
            radius = 25.dp,
        ) { nav.navigate(Routes.SEND_MISSION) }
    }
}
