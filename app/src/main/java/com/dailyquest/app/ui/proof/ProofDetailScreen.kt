package com.dailyquest.app.ui.proof

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.proof.ProofDetail
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.data.proof.ProofStatus
import com.dailyquest.app.data.proof.proofStatusLabel
import com.dailyquest.app.data.proof.VoteChoice
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

fun ProofStatus.label(): String = proofStatusLabel(this)

/** 인증 상세 — 서버 GET /proofs/{id}가 주는 정보만 표시하고, 승인·거절 버튼은 서버가 허용할 때만 보인다. */
@Composable
fun ProofDetailScreen(viewModel: ProofDetailViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val d = ui.detail
    var confirm by remember { mutableStateOf<VoteChoice?>(null) }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("proofDetail")) {
            NavHeader("인증 상세", divider = true)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                when {
                    d == null && ui.error == null -> Txt("인증을 불러오는 중이에요.", size = 13f, color = DQColors.Muted)
                    d == null -> {
                        Txt(ui.error.orEmpty(), size = 13f, color = DQColors.Orange, modifier = Modifier.testTag("proofDetailError"))
                        if (!ui.gone) Btn("다시 시도", Modifier.padding(top = 16.dp).width(140.dp).height(44.dp)) { viewModel.retry() }
                        Btn("돌아가기", Modifier.padding(top = 10.dp).width(140.dp).height(44.dp)) { nav.backOrHome() }
                    }
                    else -> Body(d, ui.userId, ui.error)
                }
                ui.voteNotice?.let {
                    Txt(it, size = 13f, weight = Bold, color = DQColors.PrimaryStrong, modifier = Modifier.padding(top = 14.dp).testTag("proofVoteNotice"))
                }
                ui.voteError?.let {
                    Txt(it, size = 12.5f, color = DQColors.Orange, modifier = Modifier.padding(top = 14.dp).testTag("proofVoteError"))
                }
                if (d != null && ui.canVote) {
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn("거절하기", Modifier.weight(1f).height(50.dp).testTag("proofReject"), BtnStyle.Outline, enabled = !ui.voting) { confirm = VoteChoice.REJECT }
                        Btn("승인하기", Modifier.weight(1f).height(50.dp).testTag("proofApprove"), enabled = !ui.voting) { confirm = VoteChoice.APPROVE }
                    }
                } else if (d != null && ui.voting) {
                    Txt("처리 중…", size = 12.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }

    val pick = confirm
    if (pick != null && d != null) {
        val approve = pick == VoteChoice.APPROVE
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (approve) "이 인증을 승인할까요?" else "이 인증을 거절할까요?") },
            text = { Text("${d.proof.user.nickname}님의 인증입니다. " + if (approve) "승인이 확정되면 제출자에게 보상이 지급돼요." else "거절이 확정되면 제출자가 다시 인증할 수 있어요.") },
            confirmButton = {
                TextButton(
                    onClick = { confirm = null; if (approve) viewModel.approve() else viewModel.reject() },
                    modifier = Modifier.testTag("proofVoteConfirm"),
                ) { Text(if (approve) "승인" else "거절") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun Body(d: ProofDetail, myUserId: Long?, error: String?) {
    val p = d.proof
    val context = LocalContext.current
    Txt("${d.roomName} · ${if (d.missionType == MissionType.APP) "앱 미션" else "친구 미션"}", size = 12f, color = DQColors.Muted)
    Txt(d.missionContent, size = 18f, weight = Bold, modifier = Modifier.padding(top = 6.dp))
    Txt(
        "${if (d.isMine(myUserId)) "내 인증" else "${p.user.nickname}님의 인증"}" + (p.createdAtMs?.let { " · ${MissionTime.kstDateTimeLabel(it)}" } ?: ""),
        size = 12.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 6.dp),
    )

    when (p.mediaType) {
        ProofMediaType.TEXT -> Unit
        ProofMediaType.IMAGE -> RemoteImage(
            p.mediaUrl, Modifier.padding(top = 14.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).background(DQColors.Soft).testTag("proofImage"),
        )
        ProofMediaType.VIDEO -> VideoProofView(
            p.mediaUrl, Modifier.padding(top = 14.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)),
            fallbackThumbUrl = p.thumbnailUrl, tag = "proofVideo",
        )
    }
    if (!p.text.isNullOrBlank()) {
        DqCard(Modifier.padding(top = 12.dp).fillMaxWidth(), radius = 14.dp, soft = true) {
            Txt(p.text, size = 13.5f, modifier = Modifier.padding(12.dp).testTag("proofText"))
        }
    }

    DqCard(Modifier.padding(top = 14.dp).fillMaxWidth(), radius = 16.dp, soft = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.isAutoApproved) {
                Txt("자동 승인 완료", size = 14f, weight = Bold, modifier = Modifier.testTag("proofStatus"))
                Txt("혼자 참여 중인 방의 오늘의 미션은 자동으로 승인돼요.", size = 12.5f, modifier = Modifier.testTag("proofAutoApproved"))
            } else {
                Txt(p.status.label(), size = 14f, weight = Bold, modifier = Modifier.testTag("proofStatus"))
                // 필요 승인 수는 아직 투표가 진행 중일 때만 의미가 있어, 확정된 인증에서는 생략한다.
                val needed = if (p.status == ProofStatus.PENDING) " · 필요 승인 ${p.approval.required}명" else ""
                Txt("승인 ${p.approval.approved} · 거절 ${p.approval.rejected}$needed", size = 12.5f, modifier = Modifier.testTag("proofCounts"))
            }
            p.approval.myVote?.let {
                Txt("내 투표: ${if (it == VoteChoice.APPROVE) "승인" else "거절"} (투표 완료)", size = 12.5f, color = DQColors.Muted, modifier = Modifier.testTag("proofMyVote"))
            }
            if (p.status == ProofStatus.REJECTED && d.isMine(myUserId)) Txt("미션이 마감 전이면 다시 인증할 수 있어요.", size = 12f, color = DQColors.Muted)
        }
    }
    if (p.approval.voters.isNotEmpty()) {
        FieldLabel("투표 현황")
        p.approval.voters.forEach {
            Txt("${it.nickname} · ${when (it.vote) { VoteChoice.APPROVE -> "승인"; VoteChoice.REJECT -> "거절"; null -> "대기" }}", size = 12.5f, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
    }
    if (error != null) Txt(error, size = 11.5f, color = DQColors.Orange, modifier = Modifier.padding(top = 10.dp))
}
