package com.dailyquest.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.R
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.avatar.rememberPersonFaces
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Member
import com.dailyquest.app.ui.components.LocalDqToast
import com.dailyquest.app.ui.components.RoomMemberStrip
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.decor.RoomDecorCard
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.RoomMissionsViewModel
import com.dailyquest.app.data.mission.MissionScope
import com.dailyquest.app.ui.room.ROLE_OWNER
import com.dailyquest.app.ui.room.RoomDetailFailure
import com.dailyquest.app.ui.room.RoomDetailViewModel
import com.dailyquest.app.ui.room.RoomManageEvent
import com.dailyquest.app.ui.room.RoomManageViewModel
import com.dailyquest.app.ui.feed.FeedContent
import com.dailyquest.app.ui.feed.FeedViewModel
import com.dailyquest.app.ui.feed.storyProofToOpen
import com.dailyquest.app.ui.feed.proofsOf
import com.dailyquest.app.ui.feed.storyRings
import com.dailyquest.app.ui.room.LocalAppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyquest.app.ui.theme.DQColors

private val TABS = listOf("피드", "미션", "랭킹")

/** 03 · 방 내부 — 서버의 방 상세·멤버 데이터만 사용한다. Mock 방은 [MockRoomScreen]에서만 열린다. */
@Composable
fun RoomScreen(roomId: Long, viewModel: RoomDetailViewModel, manage: RoomManageViewModel, missions: RoomMissionsViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var tab by remember(roomId) { mutableStateOf("피드") }
    var notice by remember(roomId) { mutableStateOf<String?>(null) }
    val manageUi by manage.ui.collectAsStateWithLifecycle()
    val detail = ui.detail
    val session by LocalAuth.current.session.collectAsStateWithLifecycle()

    // 관리 요청 결과는 한 번만 처리한다. 나가기·삭제는 홈으로, 그 외는 서버에서 상세·멤버를 다시 조회한다.
    LaunchedEffect(manageUi.event) {
        val event = manageUi.event ?: return@LaunchedEffect
        manage.consumeEvent()
        when (event) {
            is RoomManageEvent.Gone ->
                nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false } }
            else -> viewModel.refresh()
        }
    }

    if (detail == null) {
        DqScreen {
            Column(Modifier.fillMaxSize()) {
                NavHeader("방")
                Column(
                    Modifier.fillMaxSize().padding(24.dp).testTag("roomState"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (ui.failure == null) {
                        Txt("방 정보를 불러오는 중이에요…", size = 14f, color = DQColors.Muted)
                    } else {
                        Txt(
                            when (ui.failure) {
                                RoomDetailFailure.NOT_FOUND -> "방을 찾을 수 없어요."
                                RoomDetailFailure.FORBIDDEN -> "접근 권한이 없어요."
                                else -> "방 정보를 불러오지 못했어요."
                            },
                            size = 18f, weight = Bold,
                        )
                        Txt(
                            ui.error.orEmpty(), size = 13f, color = DQColors.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (ui.failure == RoomDetailFailure.OTHER) {
                            Btn("다시 시도", Modifier.padding(top = 20.dp).width(160.dp).height(44.dp)) { viewModel.retry() }
                        }
                        Btn("홈으로", Modifier.padding(top = 12.dp).width(160.dp).height(44.dp)) {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false } }
                        }
                    }
                }
            }
        }
        return
    }

    val manageState = rememberRoomManageState()
    // 방 피드: 피드 탭과 멤버 얼굴 테두리(스토리 링)가 함께 쓴다. 멤버만 받는다(QA 디자인 2-1단계).
    val feedVm: FeedViewModel? = if (ui.isMember) viewModel(key = "room-feed-$roomId", factory = FeedViewModel.Factory(roomId, LocalAppContainer.current)) else null
    val feedItems = feedVm?.ui?.collectAsStateWithLifecycle()?.value?.items.orEmpty()
    val rings = remember(feedItems) { storyRings(feedItems) }
    // 멤버마다 실제 아바타 얼굴(프로필 얼굴 5단계). 멤버 줄과 피드 카드가 함께 쓴다. 모르는 아이템은 멤버 전원 것을 한 번에 받는다.
    // 내 얼굴은 세션의 착용(방금 바꾼 옷)을 쓴다. 다른 사람은 방에 다시 들어오면 새 착용으로 바뀐다.
    val me = (session as? SessionState.Authenticated)?.profile
    val avatars = ui.members.map { m -> if (m.userId == me?.userId && me.avatar != null) me.avatar else m.avatar }
    val faces = rememberPersonFaces(avatars)
    val faceById = remember(ui.members, faces) { ui.members.mapIndexed { i, m -> m.userId to faces.getOrNull(i) }.toMap() }
    val toast = LocalDqToast.current
    val clipboard = LocalClipboardManager.current
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader(
                detail.name,
                // 오른쪽에 공개 배지 + ⋮ 메뉴가 있어 긴 방 이름이 겹치지 않게 양쪽을 넓게 비운다.
                titlePadding = 104.dp,
                right = {
                    Row(
                        Modifier.height(28.dp).border(1.dp, DQColors.Line, RoundedCornerShape(14.dp))
                            .background(DQColors.White, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp)
                            .testTag("roomVisibility"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DqIcon(if (detail.isPublic) R.drawable.ic_globe else R.drawable.ic_lock_green, 14.dp)
                        Txt(if (detail.isPublic) "공개" else "우리만", size = 12f, weight = Bold, color = DQColors.Green)
                    }
                    RoomManageMenu(ui, manage, manageState)
                    // 참여하지 않은 방은 ⋮ 메뉴가 없어 배지가 오른쪽 끝에 붙지 않게 여백을 둔다.
                    if (!ui.isMember) Spacer(Modifier.width(4.dp))
                }
            )

            // 위쪽(설명·멤버·방 그림)은 그대로 두고, 아래 흰 시트(피드·미션·랭킹)가 그 위로 올라와 가린다(QA 디자인).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sheetArea = this
            var topContentPx by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .onSizeChanged { topContentPx = it.height }.testTag("roomDetail")
            ) {
                if (ui.error != null) {
                    Txt(
                        ui.error.orEmpty(), size = 11f, color = DQColors.Orange,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
                if (!detail.description.isNullOrBlank()) {
                    Txt(
                        detail.description, size = 12.5f,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }

                RoomManageSection(ui, manage, manageState, faceOf = { id -> faceById[id] })

                // 멤버 영역: 멤버 전용 API이므로 비멤버에게는 호출하지도 표시하지도 않는다.
                Column(Modifier.testTag("roomMembers")) {
                    if (ui.isMember) {
                        Txt(
                            "함께하는 친구 ${detail.memberCount}/${detail.maxMembers}", size = 14f, weight = Bold, color = DQColors.InkGreen,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp).testTag("roomMemberCount"),
                        )
                    }
                    when {
                        !ui.isMember -> Txt(
                            "참여하지 않은 공개방이에요. 멤버와 미션은 참여 후 볼 수 있어요.",
                            size = 11.5f, color = DQColors.Muted,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                        ui.membersError != null -> Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(ui.membersError.orEmpty(), size = 11.5f, color = DQColors.Orange, modifier = Modifier.weight(1f))
                            Txt(
                                "멤버 다시 불러오기", size = 12f, weight = Bold, color = DQColors.Green,
                                modifier = Modifier.clickable { viewModel.retryMembers() }.padding(8.dp),
                            )
                        }
                        !ui.membersLoaded -> Txt(
                            "멤버를 불러오는 중이에요…", size = 11.5f, color = DQColors.Muted,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                        else -> {
                            // 멤버마다 실제 아바타 얼굴(프로필 얼굴 5단계). 모르는 아이템은 멤버 전원 것을 한 번에 받는다.
                            // 내 얼굴은 세션의 착용(방금 바꾼 옷)을 쓴다. 다른 사람은 방에 다시 들어오면 새 착용으로 바뀐다.
                            val all = ui.members.mapIndexed { i, it ->
                                it.userId to Member(
                                    it.nickname, badge = if (it.role == ROLE_OWNER) "방장" else null, face = faces[i], showsFace = true,
                                    id = it.userId, ring = rings[it.userId],
                                )
                            }
                            // 내 얼굴을 맨 앞에 두고 세로선으로 다른 멤버와 나눈다(QA 디자인 2단계).
                            val inviteCode = ui.visibleInviteCode
                            RoomMemberStrip(
                                me = all.firstOrNull { it.first == me?.userId }?.second,
                                others = all.filter { it.first != me?.userId }.map { it.second },
                                inviteState = when {
                                    detail.isPublic -> "공개방"
                                    inviteCode != null -> "초대코드 $inviteCode"
                                    else -> "초대코드 없음"
                                },
                                onInvite = {
                                    when {
                                        detail.isPublic -> toast.show("공개방은 초대코드 없이 누구나 참여할 수 있어요")
                                        inviteCode != null -> {
                                            clipboard.setText(AnnotatedString(inviteCode))
                                            toast.show("초대코드($inviteCode)를 복사했어요")
                                        }
                                        else -> toast.show("초대코드를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.", isError = true)
                                    }
                                },
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp),
                                // 테두리 있는 얼굴을 누르면 목록 없이 바로 그 사람의 인증 상세(QA 디자인).
                                // 안 본 인증이 있으면 그중 가장 최근 것, 다 봤으면 가장 최근 것.
                                onMember = { who ->
                                    storyProofToOpen(proofsOf(feedItems, who.id ?: -1L))?.let { p ->
                                        feedVm?.markViewed(p.proofId)
                                        nav.navigate(Routes.proofDetail(p.proofId, roomId))
                                    }
                                },
                            )
                        }
                    }
                }

                // STEP 10: 고정 일러스트 대신 서버에 저장된 실제 방. "방 꾸미기"는 편집 화면으로 간다.
                RoomDecorCard(
                    roomId = roomId,
                    isMember = ui.isMember,
                    onDecorate = { nav.navigate(Routes.roomDecor(roomId)) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                Spacer(Modifier.height(14.dp))
            }
            // 아래로 당기면 새로고침: 방 정보·피드·(미션 탭이면) 미션을 다시 받는다.
            val feedLoading = feedVm?.ui?.collectAsStateWithLifecycle()?.value?.loading == true
            val missionsLoading = missions.ui.collectAsStateWithLifecycle().value.loading
            val busy by rememberUpdatedState(ui.refreshing || feedLoading || (tab == "미션" && missionsLoading))
            var pulling by remember { mutableStateOf(false) }
            LaunchedEffect(pulling) {
                if (!pulling) return@LaunchedEffect
                // 받기가 시작될 틈을 준 뒤, 모두 끝나면 당김 표시를 끈다.
                delay(400)
                snapshotFlow { busy }.first { !it }
                pulling = false
            }
            RoomTabSheet(
                area = sheetArea,
                collapsedTopPx = topContentPx,
                refreshing = pulling,
                onRefresh = {
                    pulling = true
                    viewModel.refresh()
                    feedVm?.refresh()
                    if (tab == "미션" && ui.isMember) missions.refresh()
                },
                header = {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).drawBehind {
                            drawLine(DQColors.Soft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        },
                    ) {
                        TABS.forEach { t ->
                            val on = t == tab
                            Box(
                                Modifier.weight(1f).clickable { tab = t }.padding(bottom = 10.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Txt(t, size = 15f, weight = if (on) Bold else Medium, color = if (on) Color(0xFF303A34) else Color(0xFF788078))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TABS.forEach { t ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (t == tab) Box(Modifier.size(60.dp, 3.dp).background(DQColors.Primary, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                },
            ) {
                    when (tab) {
                        "피드" ->
                            if (ui.isMember) {
                                Box(Modifier.fillMaxWidth().padding(16.dp)) { feedVm?.let { FeedContent(roomId, it, faceOf = { id -> faceById[id] }, showRefreshButton = false) } }
                            } else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Txt("참여 후 방의 인증 피드를 볼 수 있어요.", size = 14f, color = DQColors.Muted)
                            }
                        "미션" ->
                            if (ui.isMember) RoomMissionsTab(roomId, missions) { missionId ->
                                nav.navigate(Routes.missionDetail(missionId, roomId))
                            } else Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Txt("참여 후 방의 미션을 볼 수 있어요.", size = 14f, color = DQColors.Muted)
                            }
                        else -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Txt("랭킹 정보 준비 중", size = 14f, color = DQColors.Muted)
                        }
                    }
                    // 아래 "+ 미션 보내기" 버튼에 가리지 않게.
                    Spacer(Modifier.height(110.dp))
            }
            }
        }

        if (ui.isMember) {
            Btn(
                "+ 미션 보내기",
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 24.dp)
                    .height(50.dp)
                    .shadow(8.dp, RoundedCornerShape(25.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                    .width(148.dp),
                radius = 25.dp,
            ) {
                nav.navigate(Routes.sendRoomMission(roomId))
            }
        }

        notice?.let {
            AlertDialog(
                onDismissRequest = { notice = null },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("확인") } },
                text = { Text(it) },
            )
        }
    }
}

/** 시트 위 손잡이·탭 줄 높이(대략). 위쪽 내용이 아주 길어도 시트가 이만큼은 보이게 한다. */
private val ROOM_SHEET_MIN_VISIBLE = 160.dp

/**
 * 방 화면 아래 흰 시트 (QA 디자인): 상점 시트처럼 흰 바탕·둥근 윗모서리·그림자·손잡이.
 * - 처음에는 방 그림 바로 아래([collapsedTopPx])에 있고, 손잡이·탭 줄을 위로 끌면 위쪽 내용(멤버·방 그림)을 가리며 올라온다.
 *   위쪽 내용은 크기가 바뀌지 않고 가려지기만 한다. 처음 자리보다 아래로는 내려가지 않는다.
 * - 내용 맨 위에서 아래로 당기면 [onRefresh](새로고침 버튼 대신).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomTabSheet(
    area: BoxWithConstraintsScope,
    collapsedTopPx: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val fullPx = with(density) { area.maxHeight.toPx() }
    val minVisiblePx = with(density) { ROOM_SHEET_MIN_VISIBLE.toPx() }
    val lowest = collapsedTopPx.toFloat().coerceIn(0f, (fullPx - minVisiblePx).coerceAtLeast(0f))
    var raised by rememberSaveable { mutableStateOf(false) }
    var top by remember { mutableFloatStateOf(-1f) }
    // 위쪽 내용 높이가 정해지거나 바뀌면 지금 상태 자리로 맞춘다.
    LaunchedEffect(lowest) { top = if (raised) 0f else lowest }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf(false) }
    fun settle(up: Boolean, velocity: Float = 0f) {
        scope.launch {
            settling = true
            animate(top, if (up) 0f else lowest, velocity, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> top = v }
            raised = up
            settling = false
        }
    }
    if (top < 0f) return
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Column(
        Modifier
            .offset { IntOffset(0, top.roundToInt()) }
            .layout { measurable, constraints ->
                // 보이는 만큼만 높이를 준다(아래가 화면 밖으로 잘리지 않게 스크롤 끝까지 볼 수 있다).
                val h = (fullPx - top).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .shadow(10.dp, shape, spotColor = Color.Black.copy(alpha = 0.2f))
            .background(DQColors.White, shape)
            .testTag("roomSheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().draggable(
                orientation = Orientation.Vertical,
                enabled = !settling,
                state = rememberDraggableState { delta -> top = (top + delta).coerceIn(0f, lowest) },
                onDragStopped = { velocity ->
                    val up = when {
                        velocity < -1_000f -> true
                        velocity > 1_000f -> false
                        else -> top < lowest / 2
                    }
                    settle(up, velocity)
                },
            ),
        ) {
            Box(
                Modifier.fillMaxWidth().height(24.dp).clickable(role = Role.Button, enabled = !settling) { settle(!raised) }
                    .semantics { contentDescription = if (raised) "시트 내리기" else "시트 올리기" }
                    .testTag("roomSheetHandle"),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(40.dp, 4.dp).background(DQColors.Handle, RoundedCornerShape(2.dp))) }
            header()
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("roomSheetRefresh"),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
        }
    }
}

/** 방 미션 탭 — GET /rooms/{id}/missions 결과만 보여준다. 진행 중/마감 범위는 서버 status 필터다. */
@Composable
private fun RoomMissionsTab(roomId: Long, vm: RoomMissionsViewModel, onOpen: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(ui.missions)
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("roomMissions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.dailyquest.app.ui.components.Chip("진행 중", ui.scope == MissionScope.ACTIVE) { vm.selectScope(MissionScope.ACTIVE) }
            com.dailyquest.app.ui.components.Chip("마감", ui.scope == MissionScope.EXPIRED) { vm.selectScope(MissionScope.EXPIRED) }
        }
        when {
            ui.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(ui.error.orEmpty(), size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f).testTag("roomMissionsError"))
                Btn("다시 시도", Modifier.width(88.dp).height(34.dp), size = 12f) { if (ui.loaded) vm.refresh() else vm.retry() }
            }
            !ui.loaded -> MissionEmptyNote("미션을 불러오는 중이에요.")
            ui.missions.isEmpty() -> MissionEmptyNote(
                if (ui.scope == MissionScope.ACTIVE) "진행 중인 미션이 없어요." else "마감된 미션이 없어요.",
                Modifier.testTag("roomMissionsEmpty"),
            )
        }
        Btn("인증 피드 보기", Modifier.fillMaxWidth().height(40.dp).testTag("roomFeed"), com.dailyquest.app.ui.components.BtnStyle.Soft, size = 13f) {
            nav.navigate(Routes.feed(roomId))
        }
        Btn("승인 대기 인증 확인", Modifier.fillMaxWidth().height(40.dp).testTag("roomPendingProofs"), com.dailyquest.app.ui.components.BtnStyle.Soft, size = 13f) {
            nav.navigate(Routes.pendingProofs(roomId))
        }
        ui.missions.forEach { m -> MissionCard(m, null, ui.userId, now) { onOpen(m.missionId) } }
        if (ui.hasNext) Btn("더 보기", Modifier.fillMaxWidth().height(40.dp), size = 13f, enabled = !ui.loadingMore) { vm.loadMore() }
    }
}
