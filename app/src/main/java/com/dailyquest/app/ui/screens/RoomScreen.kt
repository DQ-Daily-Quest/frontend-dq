package com.dailyquest.app.ui.screens

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
import androidx.compose.ui.platform.testTag
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
import com.dailyquest.app.ui.components.MemberRow
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
                }
            )

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp).testTag("roomDetail")
            ) {
                if (ui.error != null) {
                    Txt(
                        ui.error.orEmpty(), size = 11f, color = DQColors.Orange,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
                Txt(
                    "멤버 ${detail.memberCount}/${detail.maxMembers}명 · 방장 ${detail.owner.nickname}",
                    size = 12f, color = DQColors.Muted,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
                )
                if (!detail.description.isNullOrBlank()) {
                    Txt(
                        detail.description, size = 12.5f,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
                ui.visibleInviteCode?.let {
                    Txt(
                        "초대코드: $it", size = 11.5f, weight = Bold, color = DQColors.Green,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).testTag("roomInviteCode"),
                    )
                }

                RoomManageSection(ui, manage, manageState)

                // 멤버 영역: 멤버 전용 API이므로 비멤버에게는 호출하지도 표시하지도 않는다.
                Column(Modifier.testTag("roomMembers")) {
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
                            val me = (session as? SessionState.Authenticated)?.profile
                            val avatars = ui.members.map { m -> if (m.userId == me?.userId && me.avatar != null) me.avatar else m.avatar }
                            val faces = rememberPersonFaces(avatars)
                            MemberRow(
                                members = ui.members.mapIndexed { i, it ->
                                    Member(it.nickname, badge = if (it.role == ROLE_OWNER) "방장" else null, face = faces[i], showsFace = true)
                                },
                                size = 52.dp,
                                inviteSize = 44.dp,
                                showInviteLabel = false,
                                showInvite = false,
                                onStory = {},
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp),
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

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).drawBehind {
                        drawLine(DQColors.Soft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
                ) {
                    TABS.forEach { t ->
                        val on = t == tab
                        Box(
                            Modifier.weight(1f).clickable { tab = t }.padding(bottom = 10.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Txt(t, size = 15f, weight = if (on) Bold else Medium, color = if (on) DQColors.Ink else DQColors.Muted)
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

                when (tab) {
                    "피드" ->
                        if (ui.isMember) {
                            val feedVm: FeedViewModel = viewModel(key = "room-feed-$roomId", factory = FeedViewModel.Factory(roomId, LocalAppContainer.current))
                            Box(Modifier.fillMaxWidth().padding(16.dp)) { FeedContent(roomId, feedVm) }
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
