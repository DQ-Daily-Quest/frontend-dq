package com.dailyquest.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrCatalog
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextArea
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.HomeHeader
import com.dailyquest.app.ui.components.Member
import com.dailyquest.app.ui.components.MemberRow
import com.dailyquest.app.ui.components.MissionInput
import com.dailyquest.app.ui.components.MissionSummary
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Person
import com.dailyquest.app.ui.components.PlacedAvatar
import com.dailyquest.app.ui.components.RecipientPicker
import com.dailyquest.app.ui.components.RoomInterior
import com.dailyquest.app.ui.components.Scope
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.UploadArea
import com.dailyquest.app.ui.components.VoteChip
import com.dailyquest.app.ui.components.softShadow
import com.dailyquest.app.ui.theme.DQColors

private data class RoomCardData(val name: String, val members: Int, @DrawableRes val story: Int)

private val MY_ROOMS = listOf(
    RoomCardData("우리들의 아지트", 4, R.drawable.img_avatar_story_34_1),
    RoomCardData("맛집 탐험대", 3, R.drawable.img_avatar_story_34_2),
    RoomCardData("하늘 보는 사람들", 4, R.drawable.img_avatar_story_34_3),
    RoomCardData("운동하는 우리", 3, R.drawable.img_avatar_story_34_4),
)

/** 12 · B1 홈 */
@Composable
fun B1HomeScreen() {
    val nav = LocalNav.current
    DqScreen(statusBarPadding = false) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bgH = (530 * maxWidth.value / 390f).dp
            DqImage(R.drawable.img_village_map, Modifier.fillMaxWidth().height(bgH))
            Column(Modifier.fillMaxSize()) {
                HomeHeader(Scope.Private, variantB = true)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("우리 방", size = 17f, weight = Bold, modifier = Modifier.weight(1f))
                        Txt("+ 방 만들기", size = 13f, weight = Bold, color = DQColors.Green, modifier = Modifier.clickable { })
                    }
                    MY_ROOMS.chunked(2).forEachIndexed { i, row ->
                        Row(Modifier.padding(top = if (i == 0) 7.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { r -> MyRoomCard(r, Modifier.weight(1f)) { nav.navigate(Routes.ROOM_DETAIL) } }
                        }
                    }

                    // 오늘의 미션
                    DqCard(Modifier.padding(top = 26.dp).fillMaxWidth().height(84.dp), radius = 16.dp, soft = true) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).background(DQColors.Soft, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                DqIcon(R.drawable.ic_cloud_28, 28.dp)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Txt("오늘의 미션", size = 11.5f, weight = Bold, color = DQColors.Green)
                                Txt("오늘의 하늘 찍기", size = 16f, weight = Bold, modifier = Modifier.padding(top = 1.dp))
                                Txt("보상 · 랜덤 꾸미기 아이템", size = 11f, color = DQColors.Muted, modifier = Modifier.padding(top = 1.dp))
                            }
                            Btn("인증하기", Modifier.size(82.dp, 44.dp), size = 14f) { nav.navigate(Routes.B3_MISSION_VERIFY) }
                        }
                    }

                    // 친구 미션 배너
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().height(64.dp)
                            .softShadow(RoundedCornerShape(16.dp)).background(DQColors.Soft, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp)).clickable { nav.navigate(Routes.B4_SEND_MISSION) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(66.dp)) {
                            DqAvatar(R.drawable.img_avatar_girl, 40.dp)
                            DqAvatar(R.drawable.img_avatar_boy, 40.dp, Modifier.offset(x = 26.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Txt("친구 미션 2개 도착!", size = 15f, weight = Bold)
                            Txt("지수 · 민수가 미션을 보냈어요", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 3.dp))
                        }
                        DqIcon(R.drawable.ic_chevron_right_20, 20.dp)
                    }
                }
                TabBar(Tab.Home, set = "b")
            }
        }
    }
}

@Composable
private fun MyRoomCard(r: RoomCardData, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.height(150.dp).softShadow(shape).background(DQColors.Cream, shape).clip(shape).clickable(onClick = onClick).padding(8.dp)) {
        DqImage(R.drawable.img_room_interior, Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)))
        DqImage(r.story, Modifier.offset(2.dp, 2.dp).size(34.dp))
        DqAvatar(R.drawable.img_avatar_boy, 26.dp, Modifier.offset(30.dp, 6.dp))
        Column(Modifier.padding(start = 6.dp, top = 98.dp)) {
            Txt(r.name, size = 14f, weight = Bold, maxLines = 1)
            Txt("멤버 ${r.members}명", size = 11.5f, color = DQColors.Muted)
        }
    }
}

/** 13 · B2 방 내부 */
@Composable
fun RoomDetailScreen() {
    val nav = LocalNav.current
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("우리들의 아지트")
            FormBody(top = 6.dp) {
                RoomInterior(
                    height = 220f,
                    avatars = listOf(
                        PlacedAvatar(R.drawable.img_avatar_girl, 99f, 127f),
                        PlacedAvatar(R.drawable.img_avatar_boy, 159f, 139f),
                        PlacedAvatar(R.drawable.img_avatar_girl, 219f, 123f),
                    ),
                    avatarSize = 50.dp, decoX = 265f, decoY = 179f, decoWidth = 80.dp,
                ) { nav.navigate(Routes.B5_SHOP) }

                Row(Modifier.padding(start = 4.dp, top = 18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt("멤버", size = 15f, weight = Bold)
                    Txt("4명", size = 14f, color = DQColors.Muted)
                }
                MemberRow(
                    members = listOf(
                        Member("지수", story = R.drawable.img_avatar_story_48_1),
                        Member("민수", story = R.drawable.img_avatar_story_48_2),
                        Member("서연", avatar = R.drawable.img_avatar_boy),
                        Member("나", avatar = R.drawable.img_avatar_girl),
                    ),
                    size = 48.dp, inviteSize = 40.dp, showInviteLabel = false,
                    onStory = { nav.navigate(Routes.STORY) },
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )

                Txt("오늘의 인증", size = 15f, weight = Bold, modifier = Modifier.padding(start = 4.dp, top = 22.dp))
                ProofItem("지수", "오늘의 하늘 찍기", listOf(Color(0xFF7FB8E6), Color(0xFFE3F2FA)), Modifier.padding(top = 12.dp)) {
                    Btn("투표하기", Modifier.width(82.dp).height(38.dp), size = 13f) { nav.navigate(Routes.VERIFY_CHECK) }
                }
                ProofItem("민수", "10분 동안 산책하기", listOf(Color(0xFFCDE6B2), Color(0xFF8CC07A)), Modifier.padding(top = 10.dp)) {
                    VoteChip(pass = true, width = 76.dp)
                }
            }
        }
        BottomCta {
            Btn("+ 미션 보내기", Modifier.weight(1f).height(54.dp), size = 16f) { nav.navigate(Routes.B4_SEND_MISSION) }
        }
    }
}

@Composable
private fun ProofItem(name: String, desc: String, gradient: List<Color>, modifier: Modifier, action: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(72.dp).softShadow(shape).background(DQColors.White, shape).border(1.dp, DQColors.Line, shape)
            .padding(start = 9.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(Brush.verticalGradient(gradient), RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(name, size = 14f, weight = Bold)
            Txt(desc, size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 3.dp))
        }
        action()
    }
}

/** 14 · B3 미션 인증 */
@Composable
fun B3MissionVerifyScreen() {
    val nav = LocalNav.current
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("미션 인증")
            FormBody(top = 8.dp) {
                MissionSummary(R.drawable.ic_cloud_24, "오늘의 하늘 찍기", "보상 · 랜덤 꾸미기 아이템", small = true)
                UploadArea(large = true)
                FieldLabel("한마디")
                DqTextArea("오늘 하늘은 어땠어? (선택)", height = 90.dp, counter = false)
            }
        }
        BottomCta {
            Btn("인증 올리기", Modifier.weight(1f).height(54.dp), size = 16f) { nav.navigate(Routes.ROOM_DETAIL) }
        }
    }
}

/** 15 · B4 미션 보내기 */
@Composable
fun B4SendMissionScreen() {
    val nav = LocalNav.current
    var coin by remember { mutableIntStateOf(100) }
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("미션 보내기")
            FormBody {
                FieldLabel("누구에게?", first = true, size = 15f, bottom = 10.dp)
                RecipientPicker(
                    people = listOf(
                        Person("지수", R.drawable.img_avatar_boy),
                        Person("민수", R.drawable.img_avatar_girl),
                        Person("서연", R.drawable.img_avatar_boy),
                    ),
                    initial = setOf("지수"),
                )
                FieldLabel("어떤 미션?", size = 15f, top = 24.dp, bottom = 10.dp)
                MissionInput(listOf("산책 10분", "물 2L", "하늘 찍기", "랜덤"), height = 54.dp, fontSize = 15f)

                FieldLabel("보상 코인", size = 15f, top = 24.dp, bottom = 10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200).forEach { c ->
                        val on = c == coin
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier.weight(1f).height(56.dp).clip(shape).background(if (on) DQColors.Soft else DQColors.White)
                                .border(if (on) 2.dp else 1.dp, if (on) DQColors.PrimaryStrong else DQColors.Line, shape)
                                .clickable { coin = c },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        ) {
                            DqIcon(R.drawable.ic_coin_22, 22.dp)
                            Txt("$c", size = 17f, weight = Black)
                        }
                    }
                }
                Txt("내 코인 320 · 미션 완료 시 차감돼요", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
        }
        BottomCta {
            Btn("미션 보내기", Modifier.weight(1f).height(54.dp), size = 16f) { nav.backOrCatalog() }
        }
    }
}

