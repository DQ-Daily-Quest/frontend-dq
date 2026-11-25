package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.R
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.switchTo
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.avatar.ProfileFace
import com.dailyquest.app.ui.avatar.rememberUserFaces
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.LocalMyMissions
import com.dailyquest.app.ui.mission.RoomMission
import com.dailyquest.app.ui.theme.DQColors

private const val HOME_FRIEND_LIMIT = 3

/** 홈 하단 시트의 미션 영역 — 서버의 내 미션(방별) 데이터만 사용한다. 인증 제출은 STEP 5 이후 연결한다. */
@Composable
fun HomeMissionSection() {
    val nav = LocalNav.current
    val vm = LocalMyMissions.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(ui.groups)
    val open = { rm: RoomMission -> nav.navigate(Routes.missionDetail(rm.mission.missionId, rm.roomId)) }

    SheetHead {
        Txt("오늘의 미션", size = 17f, weight = Bold)
        Txt(MissionTime.kstDateLabel(System.currentTimeMillis()), size = 12f, color = DQColors.Muted)
    }
    Column(Modifier.padding(top = 8.dp).testTag("homeMissions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            ui.initialLoading || (!ui.loaded && ui.error == null) -> MissionEmptyNote("미션을 불러오는 중이에요.")
            ui.error != null && !ui.loaded -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(ui.error.orEmpty(), size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f).testTag("homeMissionsError"))
                Btn("다시 시도", Modifier.width(88.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) { vm.retry() }
            }
            ui.groups.isEmpty() -> MissionEmptyNote("참여한 방이 없어요. 방에 참여하면 오늘의 미션이 보여요.", Modifier.testTag("homeMissionsNoRooms"))
            ui.appMissions.isEmpty() -> MissionEmptyNote("오늘의 미션이 아직 없어요.", Modifier.testTag("homeMissionsEmpty"))
            else -> ui.appMissions.forEach { rm -> MissionCard(rm.mission, rm.roomName, ui.userId, now) { open(rm) } }
        }
        if (ui.error != null && ui.loaded) Txt(ui.error.orEmpty(), size = 11.5f, color = DQColors.Orange)
    }

    val friends = ui.friendMissions
    // 프로필 얼굴 7단계: 미션을 보낸 사람의 실제 아바타 얼굴(보낸 사람 정보에는 아바타가 없어 따로 받는다).
    val senderFaces = rememberUserFaces(friends.take(HOME_FRIEND_LIMIT).mapNotNull { it.mission.createdBy?.userId })
    SheetHead(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("친구가 보낸 미션", size = 15f, weight = Bold)
            if (friends.isNotEmpty()) {
                Box(Modifier.size(20.dp).background(DQColors.Primary, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Txt("${friends.size}", size = 11f, weight = Bold, color = DQColors.White, modifier = Modifier.testTag("homeFriendCount"))
                }
            }
        }
        if (friends.size > HOME_FRIEND_LIMIT) {
            Txt(
                "더보기 ›", size = 12f, color = DQColors.Muted,
                modifier = Modifier.clickable { nav.switchTo(Routes.MISSIONS) },
            )
        }
    }
    if (ui.loaded && friends.isEmpty()) {
        MissionEmptyNote("받은 미션이 없어요.", Modifier.testTag("homeFriendEmpty"))
        return
    }
    if (friends.isEmpty()) return
    DqCard(Modifier.padding(top = 8.dp).fillMaxWidth().testTag("homeFriendMissions"), radius = 16.dp, soft = true) {
        Column(Modifier.padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp)) {
            friends.take(HOME_FRIEND_LIMIT).forEachIndexed { i, rm ->
                val m = rm.mission
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { open(rm) }
                        .then(
                            if (i > 0) Modifier.drawBehind {
                                drawLine(DQColors.Soft, Offset(4.dp.toPx(), 0f), Offset(size.width, 0f), 1.dp.toPx())
                            } else Modifier,
                        ).testTag("friendMission-${m.missionId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileFace(m.createdBy?.userId?.let { senderFaces[it] }, 34.dp, contentDescription = m.createdBy?.nickname?.let { "$it 아바타" })
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Txt("${m.createdBy?.nickname ?: "멤버"}님이 보낸 미션 · ${rm.roomName}", size = 10f, color = DQColors.Muted, maxLines = 1)
                        Txt(m.content, size = 12.5f, weight = Bold, maxLines = 1)
                    }
                    Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DqIcon(R.drawable.ic_coin_sm, 18.dp)
                        Txt("+${m.rewardCoins}", size = 12.5f, weight = Bold)
                    }
                    Btn("보기", Modifier.size(56.dp, 30.dp), style = BtnStyle.Soft, size = 12f) { open(rm) }
                }
            }
        }
    }
}
