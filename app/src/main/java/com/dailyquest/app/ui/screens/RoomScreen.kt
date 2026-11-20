package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
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
import com.dailyquest.app.ui.components.RoomInterior
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.room.ROLE_OWNER
import com.dailyquest.app.ui.room.RoomDetailFailure
import com.dailyquest.app.ui.room.RoomDetailViewModel
import com.dailyquest.app.ui.room.RoomManageEvent
import com.dailyquest.app.ui.room.RoomManageViewModel
import com.dailyquest.app.ui.theme.DQColors

private val TABS = listOf("피드", "미션", "랭킹")

/** 03 · 방 내부 — 서버의 방 상세·멤버 데이터만 사용한다. Mock 방은 [MockRoomScreen]에서만 열린다. */
@Composable
fun RoomScreen(roomId: Long, viewModel: RoomDetailViewModel, manage: RoomManageViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var tab by remember(roomId) { mutableStateOf("피드") }
    var notice by remember(roomId) { mutableStateOf<String?>(null) }
    val manageUi by manage.ui.collectAsStateWithLifecycle()
    val detail = ui.detail

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

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader(
                detail.name,
                right = {
                    Row(
                        Modifier.height(22.dp).background(DQColors.Soft, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!detail.isPublic) DqIcon(R.drawable.ic_lock_green, 12.dp)
                        Txt(if (detail.isPublic) "공개" else "우리만", size = 10.5f, weight = Bold, color = DQColors.Green)
                    }
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

                RoomManageSection(ui, manage)

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
                            Txt(
                                "멤버 아바타는 임시 이미지예요.", size = 11f, color = DQColors.Muted,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            )
                            MemberRow(
                                members = ui.members.map {
                                    Member(it.nickname, avatar = R.drawable.ic_users_lg, badge = if (it.role == ROLE_OWNER) "방장" else null)
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

                RoomInterior(
                    height = 252f,
                    avatars = emptyList(),
                    avatarSize = 52.dp,
                    decoX = 265f,
                    decoY = 213f,
                    decoWidth = 82.dp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                ) {
                    notice = "방 꾸미기는 아직 서버와 연결되지 않았어요. 곧 사용할 수 있어요."
                }

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

                val mission = detail.todayMission
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    when (tab) {
                        "피드" -> Txt("피드는 아직 서버와 연결되지 않았어요. 준비 중이에요.", size = 14f, color = DQColors.Muted)
                        "미션" ->
                            if (ui.isMember && mission != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Txt("오늘의 미션", size = 12f, color = DQColors.Muted)
                                    Txt(mission.content, size = 15f, weight = Bold, modifier = Modifier.padding(top = 6.dp))
                                    Txt("인증·보상은 준비 중이에요.", size = 11f, color = DQColors.Muted, modifier = Modifier.padding(top = 6.dp))
                                }
                            } else {
                                Txt("미션 목록 준비 중", size = 14f, color = DQColors.Muted)
                            }
                        else -> Txt("랭킹 정보 준비 중", size = 14f, color = DQColors.Muted)
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
                notice = "미션 보내기는 아직 서버와 연결되지 않았어요. 곧 사용할 수 있어요."
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
