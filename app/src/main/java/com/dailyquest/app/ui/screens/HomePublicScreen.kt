
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyquest.app.R
import com.dailyquest.app.data.model.Room
import com.dailyquest.app.data.room.RoomSort
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.ui.room.JOIN_UNKNOWN_MESSAGE
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.ui.room.LocalPublicRooms
import com.dailyquest.app.ui.room.RoomDetailViewModel
import com.dailyquest.app.ui.room.RoomJoinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.ui.avatar.ProfileFace
import com.dailyquest.app.ui.avatar.rememberRoomOwnerFaces
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

// 공개방 카드 전용 UI 데이터
private data class PublicRoom(
    val room: Room,
    @DrawableRes val house: Int,
)

private val HOUSES = listOf(
    R.drawable.img_house_1, R.drawable.img_house_2, R.drawable.img_house_3, R.drawable.img_house_4
)

// 서버가 집 이미지를 내려주지 않으므로 roomId로 기본 집 이미지를 고른다.
// 방장 얼굴은 내가 참여한 방만 보여준다(프로필 얼굴 7단계, rememberRoomOwnerFaces).
private fun createPublicRooms(rooms: List<Room>): List<PublicRoom> =
    rooms.map { room ->
        PublicRoom(room, HOUSES[Math.floorMod(room.roomId, HOUSES.size.toLong()).toInt()])
    }

/** 02 · 홈 · 공개 (다른 마을 구경) */
@Composable
fun HomePublicScreen() {

    val nav = LocalNav.current

    val vm = LocalPublicRooms.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filter = if (ui.sort == RoomSort.NEWEST) "신규" else "인기"
    val query = ui.keyword

    // 미참여 방을 선택했을 때 표시할 미리보기 (서버 목록 데이터만 사용)
    var selectedRoom by remember { mutableStateOf<PublicRoom?>(null) }

    val publicRooms = createPublicRooms(ui.rooms)
    // 참여한 방의 방장 얼굴. 참여하지 않은 방은 서버가 멤버를 주지 않아 얼굴을 띄우지 않는다.
    val ownerFaces = rememberRoomOwnerFaces(ui.rooms)

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
                                onValueChange = vm::setKeyword,
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

                    // 정렬 필터와 방 생성
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FILTERS.forEach { f ->
                                Chip(f, f == filter) { vm.setSort(if (f == "신규") RoomSort.NEWEST else RoomSort.POPULARITY) }
                            }
                        }
                        Btn("+ 방 만들기", Modifier.height(34.dp).width(104.dp), size = 12f) {
                            nav.navigate(Routes.createRoom(isPublic = true))
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

                    Column(Modifier.fillMaxWidth().testTag("publicRoomList")) {
                    when {
                        ui.initialLoading -> StatusText("공개방을 불러오는 중이에요.")

                        ui.error != null && publicRooms.isEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Txt(ui.error ?: "", size = 14f, color = DQColors.Muted)
                            Btn("다시 시도", Modifier.height(36.dp).width(110.dp), size = 12f) { vm.retry() }
                        }

                        ui.isEmptyForSearch -> StatusText("검색 결과가 없습니다.")

                        ui.isEmptyOverall -> StatusText("아직 공개된 방이 없어요.")

                        else -> {
                            ui.error?.let { msg ->
                                Txt(msg, size = 12f, color = DQColors.Orange, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                            }
                            publicRooms.chunked(2).forEach { row ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { item ->
                                        // 내가 참여한 방이면 팝업 없이 바로 들어가고, 아니면 미리보기 팝업(QA 디자인 6단계).
                                        PublicRoomCard(item, ownerFaces, Modifier.weight(1f)) {
                                            if (item.room.joined) nav.navigate(Routes.room(item.room.roomId)) { launchSingleTop = true }
                                            else selectedRoom = item
                                        }
                                    }
                                    // 홀수 개의 방이 있어도 카드 크기 유지
                                    if (row.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            when {
                                ui.loadingMore -> StatusText("불러오는 중…")
                                ui.loadMoreError != null -> Column(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Txt(ui.loadMoreError ?: "", size = 12f, color = DQColors.Orange)
                                    Btn("다시 시도", Modifier.height(34.dp).width(110.dp), size = 12f) { vm.loadMore() }
                                }
                                ui.hasNext -> Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                                    Btn("더 보기", Modifier.height(36.dp).width(110.dp), size = 12f) { vm.loadMore() }
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

    // 공개방 상세 미리보기
    selectedRoom?.let { item ->
        RoomPreviewDialog(item.room, item.house, onDismiss = { selectedRoom = null })
    }
}

/**
 * 참여하지 않은 공개방 미리보기 팝업 (QA 디자인 6단계). 상세는 서버(GET /rooms/{id})로 갱신하며 참여 여부는 서버의 myRole로 판단한다.
 * - 입장하기: 참여한 뒤 방으로 들어간다(이미 멤버로 확인되면 참여 요청 없이 들어간다).
 * - 둘러보기: 참여하지 않고 방 화면을 본다(방 그림·공개 정보만, 멤버·미션은 참여 후).
 * - 닫기.
 * 내가 참여한 방은 목록에서 바로 들어가므로 이 팝업이 뜨지 않는다.
 */
@Composable
private fun RoomPreviewDialog(room: Room, @DrawableRes house: Int, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val vm: RoomDetailViewModel = viewModel(
        key = "preview-${room.roomId}",
        factory = RoomDetailViewModel.Factory(room.roomId, LocalAppContainer.current, withMembers = false),
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (ui.detail != null) vm.refresh() }
    val detail = ui.detail
    val myRooms = LocalMyRooms.current
    val publicRooms = LocalPublicRooms.current
    val join: RoomJoinViewModel = viewModel(
        key = "join-${room.roomId}",
        factory = RoomJoinViewModel.Factory(
            LocalAppContainer.current,
            onChanged = { myRooms.refresh(); publicRooms.refresh() },
            onOutcomeUnknown = { myRooms.refresh() },
        ),
    )
    val joinUi by join.ui.collectAsStateWithLifecycle()
    LaunchedEffect(joinUi.joinedRoomId) {
        val id = joinUi.joinedRoomId ?: return@LaunchedEffect
        join.consumeJoined()
        onDismiss()
        nav.navigate(Routes.room(id)) { launchSingleTop = true }
    }
    val enter = { onDismiss(); nav.navigate(Routes.room(room.roomId)) { launchSingleTop = true } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().widthIn(max = 420.dp)
                .background(DQColors.White, RoundedCornerShape(24.dp)).padding(20.dp).testTag("publicPreview"),
        ) {
            // 방 그림: 공개 목록 카드와 같은 집 그림과 하늘색 바탕.
            Box(
                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFDCEFF7), Color(0xFFCDE6B2)))),
                contentAlignment = Alignment.Center,
            ) { DqImage(house, Modifier.size(130.dp, 96.dp)) }

            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(detail?.name ?: room.name, size = 19f, weight = Bold, color = DQColors.InkGreen, maxLines = 1, modifier = Modifier.weight(1f))
                Row(
                    Modifier.height(26.dp).border(1.dp, DQColors.Line, RoundedCornerShape(13.dp))
                        .background(DQColors.White, RoundedCornerShape(13.dp)).padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DqIcon(R.drawable.ic_globe, 13.dp)
                    Txt("공개", size = 11.5f, weight = Bold, color = DQColors.Green)
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DqIcon(R.drawable.ic_users, 15.dp)
                val count = "멤버 ${detail?.memberCount ?: room.memberCount}/${detail?.maxMembers ?: room.maxMembers}명"
                Txt(detail?.owner?.nickname?.let { "$count · 방장 $it" } ?: count, size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("publicPreviewMembers"))
            }
            Txt(
                (detail?.description ?: room.description)?.takeIf { it.isNotBlank() } ?: "친구들과 함께 미션을 수행하는 마을입니다.",
                size = 13.5f, modifier = Modifier.padding(top = 10.dp),
            )
            val notes = listOfNotNull(
                ui.error,
                "방 정보를 불러오는 중이에요…".takeIf { detail == null && ui.error == null },
                joinUi.error,
                JOIN_UNKNOWN_MESSAGE.takeIf { joinUi.outcomeUnknown },
            )
            notes.forEach { Txt(it, size = 12.5f, color = if (it == ui.error || it == joinUi.error) DQColors.Danger else DQColors.Muted, modifier = Modifier.padding(top = 6.dp)) }

            val shape = RoundedCornerShape(14.dp)
            val retry = ui.error != null && detail == null
            val busy = joinUi.submitting
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(50.dp).clip(shape)
                    .background(if (busy) DQColors.Line else DQColors.InkGreen)
                    .clickable(enabled = !busy, role = Role.Button) {
                        when {
                            retry -> vm.retry()
                            ui.isMember -> enter()
                            else -> join.joinPublic(room.roomId)
                        }
                    }
                    .testTag("publicJoin"),
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    when { retry -> "다시 시도"; busy -> "입장 중…"; else -> "입장하기" },
                    size = 15f, weight = Bold, color = if (busy) DQColors.Muted else DQColors.White,
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(48.dp).clip(shape).border(1.dp, DQColors.PrimaryStrong, shape)
                        .clickable(enabled = !busy, role = Role.Button, onClick = enter).testTag("publicBrowse"),
                    contentAlignment = Alignment.Center,
                ) { Txt("둘러보기", size = 14.5f, weight = Bold, color = DQColors.InkGreen) }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(shape).border(1.dp, DQColors.Line, shape)
                        .clickable(enabled = !busy, role = Role.Button, onClick = onDismiss).testTag("publicClose"),
                    contentAlignment = Alignment.Center,
                ) { Txt("닫기", size = 14.5f, weight = Bold, color = DQColors.Muted) }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Txt(text, size = 14f, color = DQColors.Muted)
    }
}

/** 공개방 카드 */
@Composable
private fun PublicRoomCard(
    r: PublicRoom,
    ownerFaces: Map<Long, PersonFace?>,
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

            // 방장 얼굴: 내가 참여한 방만(목록에 있음). 참여하지 않은 방은 비워 둔다.
            if (r.room.roomId in ownerFaces) {
                ProfileFace(
                    ownerFaces[r.room.roomId], 30.dp,
                    Modifier.align(Alignment.TopStart).padding(8.dp).testTag("publicRoomOwner-${r.room.roomId}"),
                    contentDescription = "방장 아바타",
                )
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
