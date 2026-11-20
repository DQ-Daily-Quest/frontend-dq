
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.ui.room.RoomEntryGate
import com.dailyquest.app.ui.room.mapAvatarCount
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.HomeHeader
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Scaled
import com.dailyquest.app.ui.components.Scope
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.at
import com.dailyquest.app.ui.theme.DQColors

// 마을 지도에서 사용할 방 데이터
private data class MapRoom(
    val room: Room,
    @DrawableRes val story: Int,
    val avatars: List<Int>,
    val pin: Pair<Float, Float>,
    val label: Pair<Float, Float>,
)

// 마을 지도 전용 이미지 및 배치 정보
private data class MapRoomSlot(
    @DrawableRes val story: Int,
    val avatars: List<Int>,
    val pin: Pair<Float, Float>,
    val label: Pair<Float, Float>,
)

// 기존 마을 지도 디자인 유지
private val MAP_ROOM_SLOTS = listOf(
    MapRoomSlot(
        R.drawable.img_avatar_story_1,
        listOf(
            R.drawable.img_avatar_boy,
            R.drawable.img_avatar_girl,
            R.drawable.img_avatar_boy
        ),
        34f to 170f,
        33f to 240f
    ),
    MapRoomSlot(
        R.drawable.img_avatar_story_2,
        listOf(
            R.drawable.img_avatar_boy,
            R.drawable.img_avatar_girl
        ),
        240f to 182f,
        251f to 250f
    ),
    MapRoomSlot(
        R.drawable.img_avatar_story_3,
        listOf(
            R.drawable.img_avatar_girl,
            R.drawable.img_avatar_boy
        ),
        34f to 326f,
        31.5f to 420f
    ),
    MapRoomSlot(
        R.drawable.img_avatar_story_4,
        listOf(
            R.drawable.img_avatar_boy
        ),
        240f to 336f,
        245f to 430f
    )
)

// 서버 방 목록을 마을 지도 UI 데이터로 변환
private fun createMapRooms(rooms: List<Room>): List<MapRoom> {
    return rooms.zip(MAP_ROOM_SLOTS).map { (room, slot) ->
        MapRoom(
            room = room,
            story = slot.story,
            // 멤버 상세는 STEP 3-4 전이므로 임시 캐릭터만 쓰고 실제 멤버 수보다 많이 그리지 않는다.
            avatars = slot.avatars.take(mapAvatarCount(room.memberCount, slot.avatars.size)),
            pin = slot.pin,
            label = slot.label
        )
    }
}

// 친구 미션 임시 데이터
private data class FriendMission(
    val from: String,
    @DrawableRes val avatar: Int,
    val title: String,
    val coin: Int
)

private val FRIEND_MISSIONS = listOf(
    FriendMission(
        "지수",
        R.drawable.img_avatar_girl,
        "오늘 커피 한 잔 마시기",
        50
    ),
    FriendMission(
        "민수",
        R.drawable.img_avatar_boy,
        "10분 동안 산책하기",
        100
    ),
)

/** 01 · 홈 · 우리만 (마을 지도) */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current

    val vm = LocalMyRooms.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val rooms = ui.rooms
    val currentPage = ui.currentPage
    val pageCount = ui.pageCount
    val mapRooms = createMapRooms(ui.pageRooms)

    // 방 상세·생성·참여는 서버 연동 전이므로 안내만 표시한다. 서버 roomId를 Mock 화면으로 넘기지 않는다.
    var notice by remember { mutableStateOf<String?>(null) }

    DqScreen(statusBarPadding = false) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val s = maxWidth.value / 390f

            // 마을 지도 (390 x 520 디자인 캔버스)
            Scaled(390f, 520f) {
                DqImage(
                    R.drawable.img_village_map,
                    Modifier.size(390.dp, 520.dp)
                )

                mapRooms.forEach { r ->
                    MapRoomPin(r) {
                        nav.navigate(Routes.room(r.room.roomId))
                    }
                }
            }

            HomeHeader(scope = Scope.Private)

            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height((490 * s).dp))

                // 바텀시트
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .shadow(
                            10.dp,
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp
                            ),
                            spotColor = Color.Black.copy(alpha = 0.2f)
                        )
                        .background(
                            DQColors.White,
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp
                            )
                        ),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 9.dp)
                            .size(40.dp, 4.dp)
                            .background(
                                DQColors.Handle,
                                RoundedCornerShape(2.dp)
                            )
                    )

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 24.dp,
                                bottom = 24.dp
                            )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Btn("‹ 이전", Modifier.width(78.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) {
                                vm.prevPage()
                            }
                            Txt("${currentPage + 1} / $pageCount 페이지 · ${rooms.size}개 방", size = 12f, weight = Medium,
                                modifier = Modifier.testTag("myRoomsPageInfo"))
                            Btn("다음 ›", Modifier.width(78.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) {
                                vm.nextPage()
                            }
                        }
                        when {
                            ui.initialLoading -> Txt("참여한 방을 불러오는 중이에요.", size = 12f,
                                modifier = Modifier.padding(bottom = 12.dp))
                            ui.error != null -> Row(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Txt(ui.error ?: "", size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f))
                                Btn("다시 시도", Modifier.width(88.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) {
                                    if (ui.loaded) vm.refresh() else vm.retry()
                                }
                            }
                            ui.isEmpty -> Txt("첫 번째 방을 만들거나 초대코드로 참여해 보세요.", size = 12f,
                                modifier = Modifier.padding(bottom = 12.dp))
                        }
                        // 방 만들기 및 초대코드 참여 버튼
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Btn(
                                "+ 방 만들기",
                                Modifier.weight(1f).height(44.dp),
                                size = 14f
                            ) {
                                nav.navigate(if (RoomEntryGate.mockRoomActionsEnabled) Routes.mockCreateRoom(isPublic = false) else Routes.createRoom(isPublic = false))
                            }

                            Btn(
                                "초대코드 참여",
                                Modifier.weight(1f).height(44.dp),
                                style = BtnStyle.Outline,
                                size = 14f
                            ) {
                                if (RoomEntryGate.mockRoomActionsEnabled) nav.navigate(Routes.JOIN_ROOM)
                                else notice = "초대코드 참여는 아직 서버와 연결되지 않았어요. 곧 사용할 수 있어요."
                            }
                        }
                        SheetHead {
                            Txt(
                                "오늘의 미션",
                                size = 17f,
                                weight = Bold
                            )

                            Txt(
                                "4월 17일 (목)",
                                size = 12f,
                                color = DQColors.Muted
                            )
                        }

                        TodayMissionCard {
                            nav.navigate(Routes.MISSION_VERIFY)
                        }

                        SheetHead(Modifier.padding(top = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Txt(
                                    "친구가 보낸 미션",
                                    size = 15f,
                                    weight = Bold
                                )

                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .background(
                                            DQColors.Primary,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Txt(
                                        "2",
                                        size = 11f,
                                        weight = Bold,
                                        color = DQColors.White
                                    )
                                }
                            }

                            Txt(
                                "더보기 ›",
                                size = 12f,
                                color = DQColors.Muted
                            )
                        }

                        DqCard(
                            Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            radius = 16.dp,
                            soft = true
                        ) {
                            Column(
                                Modifier.padding(
                                    start = 7.dp,
                                    end = 9.dp,
                                    top = 2.dp,
                                    bottom = 2.dp
                                )
                            ) {
                                FRIEND_MISSIONS.forEachIndexed { i, m ->

                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(
                                                if (i == 0) 38.dp else 40.dp
                                            )
                                            .then(
                                                if (i > 0) {
                                                    Modifier.drawBehind {
                                                        drawLine(
                                                            DQColors.Soft,
                                                            Offset(
                                                                4.dp.toPx(),
                                                                0f
                                                            ),
                                                            Offset(
                                                                size.width,
                                                                0f
                                                            ),
                                                            1.dp.toPx()
                                                        )
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .padding(
                                                top = if (i > 0) 2.dp else 0.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        DqAvatar(
                                            m.avatar,
                                            34.dp
                                        )

                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .padding(start = 8.dp)
                                        ) {
                                            Txt(
                                                "${m.from}가 보낸 미션",
                                                size = 10f,
                                                color = DQColors.Muted
                                            )

                                            Txt(
                                                m.title,
                                                size = 12.5f,
                                                weight = Bold
                                            )
                                        }

                                        Row(
                                            Modifier.width(72.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            DqIcon(
                                                R.drawable.ic_coin_sm,
                                                18.dp
                                            )

                                            Txt(
                                                "+${m.coin}",
                                                size = 12.5f,
                                                weight = Bold
                                            )
                                        }

                                        Btn(
                                            "도전하기",
                                            Modifier.size(72.dp, 30.dp),
                                            style = BtnStyle.Soft,
                                            size = 12f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TabBar(Tab.Home)
            }
        }
    }

    notice?.let { msg ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("확인") } },
            containerColor = DQColors.White
        )
    }
}

@Composable
private fun MapRoomPin(
    r: MapRoom,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .at(r.pin.first, r.pin.second)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DqImage(
            r.story,
            Modifier.size(44.dp)
        )

        r.avatars.forEachIndexed { i, a ->
            DqAvatar(
                a,
                32.dp,
                Modifier.offset(
                    x = (if (i == 0) -2 else -2 - 5 * i).dp
                )
            )
        }
    }

    val wood = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .at(r.label.first, r.label.second)
            .background(DQColors.Wood, wood)
            .border(1.dp, DQColors.WoodLine, wood)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val pill = RoundedCornerShape(14.dp)

        Box(
            Modifier
                .background(DQColors.WoodPill, pill)
                .border(1.5.dp, DQColors.WoodPillLine, pill)
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Txt(
                r.room.name,
                size = 13f,
                weight = Bold,
                color = DQColors.InkGreen,
                maxLines = 1
            )
        }

        Row(
            Modifier
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            DqIcon(
                R.drawable.ic_users,
                13.dp
            )

            Txt(
                "${r.room.memberCount}명",
                size = 11f,
                weight = Bold,
                color = DQColors.InkGreen
            )
        }
    }
}

@Composable
private fun SheetHead(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        content()
    }
}

@Composable
private fun TodayMissionCard(
    onVerify: () -> Unit
) {
    DqCard(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(90.dp)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DqImage(
                R.drawable.img_photo_sky,
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DqIcon(
                        R.drawable.ic_cloud,
                        20.dp
                    )

                    Txt(
                        "오늘의 하늘 찍기",
                        size = 16f,
                        weight = Bold
                    )
                }

                Txt(
                    "지금 이 순간, 너의 하늘을 보여줘!",
                    size = 11.5f,
                    color = DQColors.Muted,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    DqIcon(
                        R.drawable.ic_gift,
                        15.dp
                    )

                    Txt(
                        "보상",
                        size = 11.5f,
                        weight = Bold
                    )

                    Box(
                        Modifier
                            .padding(start = 3.dp)
                            .height(22.dp)
                            .background(
                                DQColors.Soft,
                                RoundedCornerShape(11.dp)
                            )
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Txt(
                            "랜덤 꾸미기 아이템 1개",
                            size = 10f,
                            weight = Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Btn(
                "인증하기",
                Modifier.size(88.dp, 54.dp),
                onClick = onVerify
            )
        }
    }
}
