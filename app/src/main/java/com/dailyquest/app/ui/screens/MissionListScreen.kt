package com.dailyquest.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.mission.MissionState
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Tab
import com.dailyquest.app.ui.components.TabBar
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.LocalMyMissions
import com.dailyquest.app.ui.mission.RoomMission
import com.dailyquest.app.ui.theme.DQColors

/**
 * 하단 미션 탭 — 서버의 내 미션(GET /missions/mine)을 방별 라벨과 함께 상태로 나눠 보여준다.
 * 서버는 진행 중인 미션만 내려주므로 마감된 미션은 각 방의 미션 탭(마감 필터)에서 확인한다.
 */
@Composable
fun MissionListScreen() {
    val nav = LocalNav.current
    val vm = LocalMyMissions.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val now = rememberNowMs(ui.groups)

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("missionList")) {
            NavHeader("미션", divider = true)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Txt(
                    "오늘 ${MissionTime.kstDateLabel(System.currentTimeMillis())}",
                    size = 12f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                when {
                    ui.initialLoading || (!ui.loaded && ui.error == null) ->
                        MissionEmptyNote("미션을 불러오는 중이에요.")
                    ui.error != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(ui.error.orEmpty(), size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f).testTag("missionListError"))
                        Btn("다시 시도", Modifier.width(88.dp).height(34.dp), style = BtnStyle.Outline, size = 12f) {
                            if (ui.loaded) vm.refresh() else vm.retry()
                        }
                    }
                }
                if (ui.loaded) {
                    when {
                        ui.groups.isEmpty() -> MissionEmptyNote("참여한 방이 없어요. 방에 참여하면 오늘의 미션이 보여요.", Modifier.testTag("missionListNoRooms"))
                        ui.isEmpty -> MissionEmptyNote("진행 중인 미션이 없어요.", Modifier.testTag("missionListEmpty"))
                        else -> {
                            val order = listOf(
                                MissionState.DOABLE to "수행 가능한 미션",
                                MissionState.PENDING_REVIEW to "인증 대기 중인 미션",
                                MissionState.DONE to "완료한 미션",
                                MissionState.NOT_APPLICABLE to "내가 보낸 미션",
                                MissionState.PROOF_OTHER to "인증 제출한 미션",
                            )
                            order.forEach { (state, title) ->
                                val items = ui.byState(state, now)
                                if (items.isNotEmpty()) MissionSection(title, items, ui.userId, now) { rm ->
                                    nav.navigate(Routes.missionDetail(rm.mission.missionId, rm.roomId))
                                }
                            }
                            Txt(
                                "마감된 미션은 각 방의 미션 탭에서 볼 수 있어요.",
                                size = 11f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                            )
                        }
                    }
                }
            }
            TabBar(Tab.Mission)
        }
    }
}

@Composable
private fun MissionSection(
    title: String,
    items: List<RoomMission>,
    userId: Long?,
    now: Long,
    onClick: (RoomMission) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, size = 15f, weight = Bold)
        Txt("  ${items.size}", size = 12f, color = DQColors.Muted)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { rm -> MissionCard(rm.mission, rm.roomName, userId, now) { onClick(rm) } }
    }
}
