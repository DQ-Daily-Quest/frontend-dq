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
import com.dailyquest.app.R
import com.dailyquest.app.data.mission.MissionDetail
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.MissionDetailViewModel
import com.dailyquest.app.ui.theme.DQColors

/**
 * 미션 상세 — 서버 GET /missions/{id}. 인증 제출·승인은 STEP 5에서 연결하므로 지금은 안내만 보여주며,
 * 그때 필요한 missionId·roomId·유형·보상·상태는 이 화면의 경로 인자와 서버 응답으로 이미 유지된다.
 */
@Composable
fun MissionDetailScreen(roomId: Long, viewModel: MissionDetailViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val detail = ui.detail
    val now = rememberNowMs(detail)

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("missionDetail")) {
            NavHeader("미션 상세", divider = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                when {
                    detail == null && ui.error == null -> Txt("미션을 불러오는 중이에요.", size = 13f, color = DQColors.Muted)
                    detail == null -> {
                        Txt(ui.error.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("missionDetailError"))
                        if (!ui.gone) Btn("다시 시도", Modifier.padding(top = 16.dp).width(140.dp).height(44.dp)) { viewModel.retry() }
                        Btn("돌아가기", Modifier.padding(top = 10.dp).width(140.dp).height(44.dp)) { nav.backOrHome() }
                    }
                    else -> DetailBody(detail, roomId, ui.userId, now, ui.error)
                }
            }
        }
    }
}

@Composable
private fun DetailBody(d: MissionDetail, routeRoomId: Long, myUserId: Long?, now: Long, error: String?) {
    val m = d.mission
    val state = m.state(myUserId, now)
    Txt(m.originLabel(myUserId), size = 12f, color = DQColors.Muted)
    Txt(m.content, size = 20f, weight = Bold, modifier = Modifier.padding(top = 6.dp))
    DqCard(Modifier.padding(top = 16.dp).fillMaxWidth(), radius = 16.dp, soft = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DqIcon(R.drawable.ic_coin_sm, 18.dp)
                Txt("보상 +${m.rewardCoins}코인", size = 14f, weight = Bold)
                if (m.streakBonus > 0) Txt(" (연속 보너스 +${m.streakBonus})", size = 12f, color = DQColors.Muted)
            }
            Txt("대상: ${m.targetLabel()}", size = 12.5f)
            Txt(m.dueLabel(now), size = 12.5f)
            Txt("내 상태: ${state.label()}", size = 12.5f, weight = Bold, modifier = Modifier.testTag("missionDetailState"))
            Txt("제출된 인증 ${m.proofCount}건", size = 12.5f, color = DQColors.Muted)
        }
    }
    if (d.proofs.isNotEmpty()) {
        FieldLabel("인증 현황")
        d.proofs.forEach {
            Txt("${it.user.nickname} · ${it.status}", size = 12.5f, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
    }
    if (error != null) Txt(error, size = 11.5f, color = DQColors.Orange, modifier = Modifier.padding(top = 10.dp))
    Txt(
        "미션 인증 제출과 승인은 아직 서버와 연결되지 않았어요. 곧 사용할 수 있어요.",
        size = 12f, color = DQColors.Muted, modifier = Modifier.padding(top = 16.dp).testTag("missionProofNotice"),
    )
}
