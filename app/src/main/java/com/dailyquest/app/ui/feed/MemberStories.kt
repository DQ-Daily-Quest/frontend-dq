package com.dailyquest.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.proof.PendingProof
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.proof.RemoteImage
import com.dailyquest.app.ui.theme.DQColors

/** 멤버 얼굴 테두리(스토리 링, QA 디자인 2-1단계). 최근 24시간 인증이 없으면 테두리 없음. */
enum class StoryRing {
    /** 내가 아직 안 본 인증이 있다(분홍). */
    NEW,
    /** 인증이 있지만 모두 봤다(연한 회색). */
    SEEN,
}

/**
 * 방 피드(최근 24시간 인증)로 멤버별 테두리를 정한다. 서버의 `viewed`(내가 상세를 연 적이 있는지)를 쓴다.
 * 피드는 한 번에 일정 개수씩 받으므로, 아직 받지 않은 뒤쪽 인증은 계산에 들어가지 않는다.
 */
fun storyRings(items: List<PendingProof>): Map<Long, StoryRing> =
    items.groupBy { it.user.userId }.mapValues { (_, proofs) -> if (proofs.any { !it.viewed }) StoryRing.NEW else StoryRing.SEEN }

/** [userId]가 올린 인증(피드 순서 = 최신순). */
fun proofsOf(items: List<PendingProof>, userId: Long): List<PendingProof> = items.filter { it.user.userId == userId }

/** 멤버 얼굴을 누르면 뜨는 그 사람의 최근 인증 목록. 항목을 누르면 인증 상세로 간다. */
@Composable
fun MemberProofsDialog(name: String, proofs: List<PendingProof>, onOpen: (PendingProof) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${name}님의 인증") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("memberProofs"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt("최근 24시간 · ${proofs.size}개", size = 12f, color = DQColors.Muted)
                proofs.forEach { p -> MemberProofRow(p) { onOpen(p) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = DQColors.White,
    )
}

@Composable
private fun MemberProofRow(p: PendingProof, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DQColors.Soft).clickable(onClick = onClick)
            .padding(10.dp).testTag("memberProof-${p.proofId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (p.mediaType == ProofMediaType.TEXT) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(DQColors.White), contentAlignment = Alignment.Center) {
                Txt("글", size = 13f, weight = Bold)
            }
        } else {
            RemoteImage(
                p.thumbnailUrl, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(DQColors.White),
                placeholder = if (p.mediaType == ProofMediaType.VIDEO) "영상" else "사진",
            )
        }
        Column(Modifier.weight(1f)) {
            Txt(p.missionContent, size = 13.5f, weight = Bold, maxLines = 1)
            p.createdAtMs?.let { Txt(MissionTime.kstDateTimeLabel(it), size = 11.5f, color = DQColors.Muted) }
        }
        if (!p.viewed) Box(Modifier.size(8.dp).clip(CircleShape).background(DQColors.StoryPink).testTag("memberProofNew-${p.proofId}"))
    }
}
