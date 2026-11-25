package com.dailyquest.app.ui.feed

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.proof.RemoteImage
import com.dailyquest.app.ui.theme.DQColors

/**
 * 방 인증 피드. 서버가 내려주는 최근 24시간의 인증만 보여 주며, 미디어 원본과 리액션은 항목을 눌러 인증 상세에서 확인한다.
 * 별도 스토리 API는 없으므로 상단 띠도 같은 피드 데이터를 미리보기로 쓸 뿐이다.
 */
@Composable
fun FeedScreen(roomId: Long, viewModel: FeedViewModel) {
    DqScreen {
        Column(Modifier.fillMaxSize().testTag("feed")) {
            NavHeader("인증 피드", divider = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                FeedContent(roomId, viewModel)
            }
        }
    }
}

/** 피드 목록 본문(로딩·오류·빈 상태·더 보기·새로고침). 인증 피드 화면과 방 상세의 피드 탭이 함께 쓴다. */
@Composable
fun FeedContent(roomId: Long, viewModel: FeedViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val open = { p: PendingProof -> nav.navigate(Routes.proofDetail(p.proofId, roomId)) }
    Column(Modifier.fillMaxWidth().testTag("feedContent"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            ui.error != null -> {
                Txt(ui.error.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("feedError"))
                if (!ui.gone) Btn("다시 시도", Modifier.width(140.dp).height(44.dp).testTag("feedRetry")) { if (ui.loaded) viewModel.refresh() else viewModel.retry() }
                else Btn("돌아가기", Modifier.width(140.dp).height(44.dp)) { nav.backOrHome() }
            }
            !ui.loaded -> Txt("인증 피드를 불러오는 중이에요.", size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("feedLoading"))
            ui.items.isEmpty() -> Txt("최근 24시간 동안 올라온 인증이 없어요.", size = 13f, color = DQColors.Muted, modifier = Modifier.testTag("feedEmpty"))
        }
        if (ui.items.isNotEmpty()) {
            Txt("최근 24시간 인증", size = 12f, weight = Bold, color = DQColors.Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("feedStrip"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.items.forEach { p -> StripItem(p) { open(p) } }
            }
            ui.items.forEach { p -> FeedRow(p) { open(p) } }
        }
        if (ui.hasNext) Btn("더 보기", Modifier.fillMaxWidth().height(40.dp).testTag("feedMore"), size = 13f, enabled = !ui.loadingMore) { viewModel.loadMore() }
        if (ui.loaded && !ui.gone) Btn("새로고침", Modifier.fillMaxWidth().height(40.dp).testTag("feedRefresh"), BtnStyle.Outline, size = 13f, enabled = !ui.loading) { viewModel.refresh() }
    }
}

@Composable
private fun StripItem(p: PendingProof, onClick: () -> Unit) {
    Column(Modifier.width(60.dp).clickable(onClick = onClick).testTag("feedStrip-${p.proofId}"), horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteImage(p.thumbnailUrl, Modifier.size(52.dp).clip(CircleShape).background(DQColors.Soft), placeholder = mediaLabel(p.mediaType))
        Txt(p.user.nickname, size = 10.5f, maxLines = 1, color = DQColors.Muted)
    }
}

private fun mediaLabel(t: ProofMediaType) = when (t) { ProofMediaType.VIDEO -> "영상"; ProofMediaType.IMAGE -> "사진"; ProofMediaType.TEXT -> "글" }

@Composable
private fun FeedRow(p: PendingProof, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DQColors.Soft).clickable(onClick = onClick).padding(12.dp).testTag("feedItem-${p.proofId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (p.mediaType == ProofMediaType.TEXT) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Txt("글", size = 14f, weight = Bold) }
        } else {
            RemoteImage(p.thumbnailUrl, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(DQColors.White), placeholder = mediaLabel(p.mediaType))
        }
        Column(Modifier.weight(1f)) {
            Txt(p.missionContent, size = 14f, weight = Bold, maxLines = 1)
            Txt("${p.user.nickname}님의 인증" + (p.createdAtMs?.let { " · ${MissionTime.kstDateTimeLabel(it)}" } ?: ""), size = 11.5f, color = DQColors.Muted)
        }
        if (p.mediaType == ProofMediaType.VIDEO) Txt("영상", size = 11.5f, weight = Bold, color = DQColors.PrimaryStrong)
    }
}
