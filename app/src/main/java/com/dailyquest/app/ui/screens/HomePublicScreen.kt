
package com.dailyquest.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyquest.app.R
import com.dailyquest.app.data.model.Room
import com.dailyquest.app.data.mock.MockRoomData
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.HomeHeader
import com.dailyquest.app.ui.components.Scope
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.NotoSansKr

// 현재 백엔드에서 지원하는 정렬 방식
private val FILTERS = listOf("인기", "신규")

// 랭킹 임시 데이터
private data class Rank(
    val rank: Int,
    val name: String,
    val count: Int,
    val color: Color
)

private val RANKS = listOf(
    Rank(1, "새벽 러닝클럽", 128, Color(0xFFF2C14E)),
    Rank(2, "하늘 보는 사람들", 96, Color(0xFFC9CED6)),
    Rank(3, "맛집 탐험대", 71, Color(0xFFD99A6C)),
)

// 공개방 카드 전용 UI 데이터
private data class PublicRoom(
    val room: Room,
    @DrawableRes val house: Int,
    @DrawableRes val owner: Int,
    val story: Boolean
)

// 공통 Room 데이터를 공개방 카드 데이터로 변환
private fun createPublicRooms(
    rooms: List<Room>
): List<PublicRoom> {

    return rooms.map { room ->

        when (room.roomId) {
            5L -> PublicRoom(
                room,
                R.drawable.img_house_1,
                R.drawable.img_avatar_story_sm_1,
                true
            )

            6L -> PublicRoom(
                room,
                R.drawable.img_house_2,
                R.drawable.img_avatar_boy,
                false
            )

            7L -> PublicRoom(
                room,
                R.drawable.img_house_3,
                R.drawable.img_avatar_story_sm_2,
                true
            )

            8L -> PublicRoom(
                room,
                R.drawable.img_house_4,
                R.drawable.img_avatar_boy,
                false
            )

            else -> PublicRoom(
                room,
                R.drawable.img_house_1,
                R.drawable.img_avatar_boy,
                false
            )
        }
    }
}

/** 02 · 홈 · 공개 (다른 마을 구경) */
@Composable
fun HomePublicScreen() {

    val nav = LocalNav.current

    var filter by remember {
        mutableStateOf("인기")
    }

    var query by remember {
        mutableStateOf("")
    }

    // 미참여 방을 선택했을 때 표시할 미리보기
    var selectedRoom by remember {
        mutableStateOf<Room?>(null)
    }

    // 공개방 목록 조회
    val publicRooms = createPublicRooms(
        MockRoomData.publicRooms
    )

    // 검색 및 정렬 처리
    val filteredRooms = publicRooms
        .filter { item ->
            query.isBlank() ||
                    item.room.name.contains(
                        query.trim(),
                        ignoreCase = true
                    )
        }
        .let { rooms ->
            when (filter) {

                // 현재 백엔드의 임시 인기 기준과 동일하게
                // 멤버 수 기준으로 정렬
                "인기" -> rooms.sortedByDescending {
                    it.room.memberCount
                }

                // 현재 Mock 데이터에서는 ID를
                // 임시 생성 순서로 사용
                "신규" -> rooms.sortedByDescending {
                    it.room.roomId
                }

                else -> rooms
            }
        }

    DqScreen(statusBarPadding = false) {

        BoxWithConstraints(Modifier.fillMaxSize()) {

            val bandH = (220 * maxWidth.value / 390f).dp

            DqImage(
                R.drawable.img_village_map,
                Modifier
                    .fillMaxWidth()
                    .height(bandH)
            )

            HomeHeader(scope = Scope.Public)

            Column(Modifier.fillMaxSize()) {

                Spacer(
                    Modifier.height(bandH - 24.dp)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            DQColors.White,
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp
                            )
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 20.dp,
                            bottom = 24.dp
                        ),
                ) {

                    // 검색창
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                DQColors.Soft,
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DqIcon(
                            R.drawable.ic_search,
                            20.dp
                        )

                        Box(Modifier.weight(1f)) {

                            if (query.isEmpty()) {
                                Txt(
                                    "마을, 방 이름 검색",
                                    size = 13.5f,
                                    color = DQColors.Muted
                                )
                            }

                            BasicTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = NotoSansKr,
                                    fontSize = 13.5.sp,
                                    color = DQColors.Ink
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // 정렬 필터
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FILTERS.forEach { f ->

                            Chip(
                                f,
                                f == filter
                            ) {
                                filter = f
                            }
                        }
                    }

                    // 기존 챌린지 랭킹 UI
                    // 추후 실제 랭킹 API 연동
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 14.dp,
                                start = 2.dp,
                                end = 2.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DqIcon(
                            R.drawable.ic_trophy,
                            20.dp
                        )

                        Txt(
                            "이번 주 챌린지 랭킹",
                            size = 16f,
                            weight = Bold,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .weight(1f)
                        )

                        Txt(
                            "전체보기 ›",
                            size = 12f,
                            color = DQColors.Muted
                        )
                    }

                    Row(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RANKS.forEach { r ->

                            val shape = RoundedCornerShape(16.dp)

                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(118.dp)
                                    .background(
                                        DQColors.White,
                                        shape
                                    )
                                    .border(
                                        1.dp,
                                        DQColors.Line,
                                        shape
                                    )
                                    .padding(9.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .background(
                                            r.color,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Txt(
                                        "${r.rank}",
                                        size = 13f,
                                        weight = Black,
                                        color = DQColors.White
                                    )
                                }

                                DqImage(
                                    R.drawable.img_house_rank,
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(65.dp, 48.dp)
                                )

                                Column(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 56.dp)
                                ) {
                                    Txt(
                                        r.name,
                                        size = 12f,
                                        weight = Bold,
                                        maxLines = 1
                                    )

                                    Txt(
                                        "챌린지 ${r.count}회",
                                        size = 11f,
                                        color = DQColors.Muted,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 공개방 목록
                    Txt(
                        "다른 마을 구경하기",
                        size = 16f,
                        weight = Bold,
                        modifier = Modifier.padding(
                            top = 16.dp,
                            start = 4.dp
                        )
                    )

                    // 검색 결과가 없는 경우
                    if (filteredRooms.isEmpty()) {

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Txt(
                                "검색 결과가 없습니다.",
                                size = 14f,
                                color = DQColors.Muted
                            )
                        }

                    } else {

                        filteredRooms.chunked(2).forEach { row ->

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                row.forEach { item ->

                                    PublicRoomCard(
                                        item,
                                        Modifier.weight(1f)
                                    ) {
                                        if (item.room.joined) {

                                            // 이미 참여한 방은 바로 입장
                                            nav.navigate(
                                                Routes.room(
                                                    item.room.roomId
                                                )
                                            )

                                        } else {

                                            // 미참여 방은 미리보기 표시
                                            selectedRoom = item.room
                                        }
                                    }
                                }

                                // 홀수 개의 방이 있어도 카드 크기 유지
                                if (row.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                TabBar(Tab.Home)
            }
        }
    }

    // 공개방 상세 미리보기
    selectedRoom?.let { room ->

        AlertDialog(
            onDismissRequest = {
                selectedRoom = null
            },

            title = {
                Text(room.name)
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Text("공개 마을")

                    Text(
                        "멤버 ${room.memberCount}/${room.maxMembers}명"
                    )

                    Text(
                        room.description
                            ?.takeIf { it.isNotBlank() }
                            ?: "친구들과 함께 미션을 수행하는 마을입니다."
                    )

                    Text(
                        if (room.memberCount >= room.maxMembers.toLong()) {
                            "현재 정원이 가득 찼습니다."
                        } else {
                            "방 참여 기능은 API 연동 후 이용할 수 있습니다."
                        }
                    )
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        selectedRoom = null
                    }
                ) {
                    Text("확인")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        selectedRoom = null
                    }
                ) {
                    Text("닫기")
                }
            },

            containerColor = DQColors.White
        )
    }
}

/** 공개방 카드 */
@Composable
private fun PublicRoomCard(
    r: PublicRoom,
    modifier: Modifier,
    onClick: () -> Unit
) {

    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier
            .height(110.dp)
            .shadow(
                6.dp,
                shape,
                spotColor = DQColors.Shadow.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(DQColors.Cream)
            .border(
                1.dp,
                DQColors.Line,
                shape
            )
            .clickable(onClick = onClick),
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFDCEFF7),
                            Color(0xFFCDE6B2)
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            DqImage(
                r.house,
                Modifier
                    .padding(top = 10.dp)
                    .size(71.5.dp, 52.8.dp)
            )

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                if (r.story) {

                    DqImage(
                        r.owner,
                        Modifier.size(30.dp)
                    )

                } else {

                    DqAvatar(
                        r.owner,
                        30.dp
                    )
                }
            }

            // 인기방 표시
            if (r.room.isHot) {

                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            Color(0xFFFFE6D5),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 3.dp
                        )
                ) {
                    Txt(
                        "HOT",
                        size = 9f,
                        weight = Bold,
                        color = DQColors.Orange
                    )
                }
            }
        }

        // 방 이름
        Txt(
            r.room.name,
            size = 13f,
            weight = Bold,
            maxLines = 1,
            modifier = Modifier.padding(
                start = 11.dp,
                top = 5.dp
            )
        )

        // 멤버 수 및 참여 여부
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Txt(
                "멤버 ${r.room.memberCount}/${r.room.maxMembers}",
                size = 11f,
                color = DQColors.Muted,
                modifier = Modifier.weight(1f)
            )

            Txt(
                if (r.room.joined) {
                    "참여 중"
                } else if (
                    r.room.memberCount >= r.room.maxMembers.toLong()
                ) {
                    "만원"
                } else {
                    "미참여"
                },
                size = 10.5f,
                weight = Bold,
                color = if (r.room.joined) {
                    DQColors.Green
                } else {
                    DQColors.Muted
                }
            )
        }
    }
}
