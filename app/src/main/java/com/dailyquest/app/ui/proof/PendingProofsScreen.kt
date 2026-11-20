package com.dailyquest.app.ui.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.proof.PendingProof
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

/** 방의 승인 대기 인증 목록 — 서버가 "내가 아직 투표하지 않았고 승인할 수 있는" 인증만 내려준다. */
@Composable
fun PendingProofsScreen(roomId: Long, viewModel: PendingProofsViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    DqScreen {
        Column(Modifier.fillMaxSize().testTag("pendingProofs")) {
            NavHeader("승인 대기 인증", divider = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    ui.error != null -> {
                        Txt(ui.error.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("pendingProofsError"))
                        if (!ui.gone) Btn("다시 시도", Modifier.width(140.dp).height(44.dp)) { viewModel.retry() }
                        else Btn("돌아가기", Modifier.width(140.dp).height(44.dp)) { nav.backOrHome() }
                    }
                    !ui.loaded -> Txt("승인 대기 인증을 불러오는 중이에요.", size = 13f, color = DQColors.Muted)
                    ui.items.isEmpty() -> Txt("승인을 기다리는 인증이 없어요.", size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("pendingProofsEmpty"))
                }
                ui.items.forEach { p -> PendingRow(p) { nav.navigate(Routes.proofDetail(p.proofId, roomId)) } }
                if (ui.hasNext) Btn("더 보기", Modifier.fillMaxWidth().height(40.dp), size = 13f, enabled = !ui.loadingMore) { viewModel.loadMore() }
            }
        }
    }
}

@Composable
private fun PendingRow(p: PendingProof, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DQColors.Soft).clickable(onClick = onClick).padding(12.dp).testTag("pendingProof-${p.proofId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (p.mediaType == ProofMediaType.TEXT) {
            Txt("글", size = 14f, weight = Bold, modifier = Modifier.size(56.dp).padding(top = 16.dp))
        } else {
            RemoteImage(p.thumbnailUrl, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(DQColors.White), placeholder = if (p.mediaType == ProofMediaType.VIDEO) "영상" else "사진")
        }
        Column(Modifier.weight(1f)) {
            Txt(p.missionContent, size = 14f, weight = Bold, maxLines = 1)
            Txt("${p.user.nickname}님의 인증" + (p.createdAtMs?.let { " · ${MissionTime.kstDateTimeLabel(it)}" } ?: ""), size = 11.5f, color = DQColors.Muted)
        }
        Txt("확인하기", size = 12f, weight = Bold, color = DQColors.PrimaryStrong)
    }
}
