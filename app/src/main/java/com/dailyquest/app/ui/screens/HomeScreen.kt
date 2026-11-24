
package com.dailyquest.app.ui.screens

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
import com.dailyquest.app.data.avatar.MapFaces
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.data.model.Room
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.avatar.MapRoomFaces
import com.dailyquest.app.ui.avatar.rememberMapFaces
import com.dailyquest.app.ui.avatar.rememberMapPersonFaces
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
    val label: Pair<Float, Float>,
)

// 마을 지도 전용 이미지 및 배치 정보
private data class MapRoomSlot(
    val label: Pair<Float, Float>,
)

// 기존 마을 지도 디자인 유지. 방 이름표 자리(그 밑에 멤버 얼굴 줄이 붙는다).
// 아래 두 자리는 이름표+얼굴(약 103dp)이 바텀시트(490)에 가리지 않게 원래보다 위로 올렸다(420→372, 430→380).
private val MAP_ROOM_SLOTS = listOf(
    MapRoomSlot(
        33f to 240f
    ),
    MapRoomSlot(
        251f to 250f
    ),
    MapRoomSlot(
        31.5f to 372f
    ),
    MapRoomSlot(
        245f to 380f
    )
)

// 서버 방 목록을 마을 지도 UI 데이터로 변환
private fun createMapRooms(rooms: List<Room>): List<MapRoom> {
    return rooms.zip(MAP_ROOM_SLOTS).map { (room, slot) ->
        MapRoom(
            room = room,
            label = slot.label
        )
    }
}

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
    // 프로필 얼굴 6단계: 핀에 실제 멤버 얼굴. 보이는 방들의 멤버를 받아 오고, 얼굴 정보는 한 번에 받는다.
    val mapFaces = rememberMapFaces(ui.pageRooms)
    val personFaces = rememberMapPersonFaces(mapFaces)

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
                    MapRoomPin(r, mapFaces[r.room.roomId], personFaces) {
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
                                nav.navigate(Routes.createRoom(isPublic = false))
                            }

                            Btn(
                                "초대코드 참여",
                                Modifier.weight(1f).height(44.dp),
                                style = BtnStyle.Outline,
                                size = 14f
                            ) {
                                nav.navigate(Routes.JOIN_ROOM)
                            }
                        }
                        HomeMissionSection()
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
    faces: MapFaces?,
    personFaces: Map<Long, PersonFace?>,
    onClick: () -> Unit
) {
    val wood = RoundedCornerShape(10.dp)

    // 방 이름표와 그 바로 밑 멤버 얼굴 줄을 한 묶음으로, 가운데 맞춤(프로필 얼굴: 사용자 요청으로 이름표 밑으로 옮김).
    Column(
        Modifier.at(r.label.first, r.label.second),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            Modifier
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

        MapRoomFaces(
            r.room.roomId, r.room.memberCount, faces, personFaces,
            Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
internal fun SheetHead(
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
