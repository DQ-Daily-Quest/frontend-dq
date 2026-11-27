package com.dailyquest.app.ui.feed

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
import androidx.compose.ui.graphics.Color
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
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.ui.avatar.ProfileFace
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

/**
 * 피드 목록 본문(로딩·오류·빈 상태·더 보기·새로고침). 인증 피드 화면과 방 상세의 피드 탭이 함께 쓴다.
 * @param faceOf 올린 사람의 프로필 얼굴(방 멤버 목록에서 찾음). 모르면 null → 기본 얼굴.
 * @param showRefreshButton 방 화면은 아래로 당겨 새로고침하므로 버튼을 숨긴다(QA 디자인).
 */
@Composable
fun FeedContent(roomId: Long, viewModel: FeedViewModel, faceOf: (Long) -> PersonFace? = { null }, showRefreshButton: Boolean = true) {
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
        // 위의 동그라미 줄("최근 24시간 인증")은 뺐다. 방 화면에서는 멤버 얼굴 테두리가 같은 역할을 한다(QA 디자인 4단계).
        ui.items.forEach { p -> FeedRow(p, faceOf(p.user.userId)) { open(p) } }
        if (ui.hasNext) Btn("더 보기", Modifier.fillMaxWidth().height(40.dp).testTag("feedMore"), size = 13f, enabled = !ui.loadingMore) { viewModel.loadMore() }
        if (showRefreshButton && ui.loaded && !ui.gone) Btn("새로고침", Modifier.fillMaxWidth().height(40.dp).testTag("feedRefresh"), BtnStyle.Outline, size = 13f, enabled = !ui.loading) { viewModel.refresh() }
    }
}

private fun mediaLabel(t: ProofMediaType) = when (t) { ProofMediaType.VIDEO -> "영상"; ProofMediaType.IMAGE -> "사진"; ProofMediaType.TEXT -> "글" }

/**
 * 피드 카드(QA 디자인 4단계): 왼쪽 프로필 얼굴, 이름(굵게)과 미션, 오른쪽 위 날짜, 그 아래 사진.
 * 인증 글귀는 피드 목록에 오지 않아(상세에서만) 미션 내용을 보여 준다. 글 인증은 사진 자리가 없다.
 */
@Composable
private fun FeedRow(p: PendingProof, face: PersonFace?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(DQColors.White).border(1.dp, DQColors.Line, shape)
            .clickable(onClick = onClick).padding(12.dp).testTag("feedItem-${p.proofId}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileFace(face, 44.dp, contentDescription = "${p.user.nickname} 아바타")
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(p.user.nickname, size = 14.5f, weight = Bold, maxLines = 1)
            Txt(p.missionContent, size = 13f, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.createdAtMs?.let { Txt(MissionTime.kstDateTimeLabel(it), size = 11f, color = DQColors.Muted) }
            if (p.mediaType != ProofMediaType.TEXT) {
                Box(contentAlignment = Alignment.Center) {
                    RemoteImage(
                        p.thumbnailUrl, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(DQColors.Soft),
                        placeholder = mediaLabel(p.mediaType),
                    )
                    if (p.mediaType == ProofMediaType.VIDEO && p.thumbnailUrl != null) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Txt("▶", size = 10f, color = DQColors.White)
                        }
                    }
                }
            }
        }
    }
}
