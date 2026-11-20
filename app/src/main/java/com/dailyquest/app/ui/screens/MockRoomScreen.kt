
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
import com.dailyquest.app.data.model.Room
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrCatalog
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

private val SAMPLE_ROOM = Room(1L, "우리들의 아지트", 4L, false, joined = true)

/** 03 · 방 내부 (Mock 전용). 실제 서버 roomId는 절대 전달하지 않는다. 게이트가 열린 Mock 생성·참여와 개발용 카탈로그에서만 진입한다. */
@Composable
fun MockRoomScreen(roomId: Long) {

    val nav = LocalNav.current

    // 서버와 무관한 고정 샘플 방. 전달받은 roomId는 조회에 쓰지 않는다.
    val room = SAMPLE_ROOM

    var tab by remember(roomId) {
        mutableStateOf("피드")
    }

    // 임시 멤버 데이터
    // 추후 GET /rooms/{roomId}/members API로 교체
    val mockMembers = listOf(
        Member(
            "지수",
            story = R.drawable.img_avatar_story_lg_1
        ),
        Member(
            "민수",
            story = R.drawable.img_avatar_story_lg_2
        ),
        Member(
            "서연",
            avatar = R.drawable.img_avatar_girl
        ),
        Member(
            "나",
            avatar = R.drawable.img_avatar_boy
        )
    )

    DqScreen {
        Column(Modifier.fillMaxSize()) {

            // 실제 방 이름 및 공개 여부 표시
            NavHeader(
                room.name,
                right = {
                    Row(
                        Modifier
                            .height(22.dp)
                            .background(
                                DQColors.Soft,
                                RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!room.isPublic) {
                            DqIcon(
                                R.drawable.ic_lock_green,
                                12.dp
                            )
                        }

                        Txt(
                            if (room.isPublic) "공개" else "우리만",
                            size = 10.5f,
                            weight = Bold,
                            color = DQColors.Green
                        )
                    }

                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clickable { }
                    ) {
                        DqIcon(
                            R.drawable.ic_more,
                            24.dp
                        )
                    }
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp)
            ) {

                // 방 멤버 수 표시
                Txt(
                    "멤버 ${room.memberCount}/${room.maxMembers}명",
                    size = 12f,
                    color = DQColors.Muted,
                    modifier = Modifier.padding(
                        start = 18.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                )

                Txt(
                    "샘플 방 · 서버와 연결되지 않은 미리보기",
                    size = 11f, color = DQColors.Muted,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
                Txt("멤버·아바타·피드는 공통 샘플 데이터입니다.", size = 11f,
                    color = DQColors.Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))

                // 방 멤버 목록
                MemberRow(
                    members = mockMembers,
                    size = 52.dp,
                    inviteSize = 44.dp,
                    showInviteLabel = true,
                    onStory = {
                        nav.navigate(Routes.STORY)
                    },
                    modifier = Modifier.padding(start = 18.dp),
                )

                // 방 내부 및 아바타
                RoomInterior(
                    height = 252f,
                    avatars = listOf(
                        PlacedAvatar(
                            R.drawable.img_avatar_girl,
                            95f,
                            149f
                        ),
                        PlacedAvatar(
                            R.drawable.img_avatar_boy,
                            167f,
                            165f
                        ),
                        PlacedAvatar(
                            R.drawable.img_avatar_girl,
                            227f,
                            145f
                        ),
                    ),
                    avatarSize = 52.dp,
                    decoX = 265f,
                    decoY = 213f,
                    decoWidth = 82.dp,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp
                    ),
                ) {
                    nav.navigate(Routes.SHOP)
                }

                // 피드 / 미션 / 랭킹 탭
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .drawBehind {
                            drawLine(
                                DQColors.Soft,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                1.dp.toPx()
                            )
                        },
                ) {
                    TABS.forEach { t ->

                        val on = t == tab

                        Box(
                            Modifier
                                .weight(1f)
                                .clickable {
                                    tab = t
                                }
                                .padding(bottom = 10.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Txt(
                                t,
                                size = 15f,
                                weight = if (on) Bold else Medium,
                                color = if (on) {
                                    DQColors.Ink
                                } else {
                                    DQColors.Muted
                                }
                            )
                        }
                    }
                }

                // 선택된 탭 표시
                Row(Modifier.fillMaxWidth()) {

                    TABS.forEach { t ->

                        Box(
                            Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (t == tab) {
                                Box(
                                    Modifier
                                        .size(60.dp, 3.dp)
                                        .background(
                                            DQColors.Primary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // 선택한 탭에 따른 콘텐츠 표시
                when (tab) {

                    "피드" -> {
                        DqCard(
                            Modifier
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp
                                )
                                .fillMaxWidth(),
                            radius = 16.dp,
                            soft = true
                        ) {
                            Column(Modifier.padding(9.dp)) {

                                Row(
                                    Modifier.height(40.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DqImage(
                                        R.drawable.img_avatar_story_md_1,
                                        Modifier.size(40.dp)
                                    )

                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp)
                                    ) {
                                        Txt(
                                            "지수",
                                            size = 14f,
                                            weight = Bold
                                        )

                                        Txt(
                                            "오늘의 하늘 찍기 · 1시간 전",
                                            size = 11f,
                                            color = DQColors.Muted,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    Box(
                                        Modifier
                                            .height(24.dp)
                                            .widthIn(min = 84.dp)
                                            .background(
                                                DQColors.Soft,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Txt(
                                            "승인 대기",
                                            size = 11f,
                                            weight = Bold,
                                            color = DQColors.Orange
                                        )
                                    }
                                }

                                DqImage(
                                    R.drawable.img_photo_sky,
                                    Modifier
                                        .padding(top = 10.dp)
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )

                                Row(
                                    Modifier.padding(
                                        top = 13.dp,
                                        start = 4.dp,
                                        end = 4.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DqIcon(
                                        R.drawable.ic_heart_outline,
                                        18.dp
                                    )

                                    Txt(
                                        "5",
                                        size = 13f,
                                        color = DQColors.Muted,
                                        modifier = Modifier.padding(
                                            start = 4.dp,
                                            end = 10.dp
                                        )
                                    )

                                    DqIcon(
                                        R.drawable.ic_chat,
                                        18.dp
                                    )

                                    Txt(
                                        "3",
                                        size = 13f,
                                        color = DQColors.Muted,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .weight(1f)
                                    )

                                    Txt(
                                        "인증 확인하기 ›",
                                        size = 13f,
                                        weight = Bold,
                                        color = DQColors.Green,
                                        modifier = Modifier.clickable {
                                            nav.navigate(Routes.VERIFY_CHECK)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "미션" -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Txt(
                                "미션 목록 준비 중",
                                size = 14f,
                                color = DQColors.Muted
                            )
                        }
                    }

                    "랭킹" -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Txt(
                                "랭킹 정보 준비 중",
                                size = 14f,
                                color = DQColors.Muted
                            )
                        }
                    }
                }
            }
        }

        // 미션 보내기 플로팅 버튼
        Btn(
            "+ 미션 보내기",
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = 24.dp
                )
                .height(50.dp)
                .shadow(
                    8.dp,
                    RoundedCornerShape(25.dp),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .width(148.dp),
            radius = 25.dp,
        ) {
            nav.navigate(Routes.SEND_MISSION)
        }
    }
}
