package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.data.mission.Mission
import com.dailyquest.app.data.mission.MissionState
import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.DqCard
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

/** 화면 구성 시점의 현재 시각. 마감 계산에만 쓰며 composition 동안 고정한다. */
@Composable
fun rememberNowMs(key: Any? = null): Long = remember(key) { System.currentTimeMillis() }

fun MissionState.label(): String = when (this) {
    MissionState.DOABLE -> "수행 가능"
    MissionState.PENDING_REVIEW -> "인증 대기 중"
    MissionState.DONE -> "완료"
    MissionState.EXPIRED -> "마감"
    MissionState.NOT_APPLICABLE -> "수행 대상 아님"
    MissionState.PROOF_OTHER -> "인증 제출됨"
}

/** "{닉네임}님이 보낸 미션" / "오늘의 미션" / "내가 보낸 미션" */
fun Mission.originLabel(myUserId: Long?): String = when {
    type == MissionType.APP -> "오늘의 미션"
    isCreatedBy(myUserId) -> "내가 보낸 미션"
    else -> "${createdBy?.nickname ?: "멤버"}님이 보낸 미션"
}

fun Mission.targetLabel(): String = when {
    type == MissionType.APP -> "방 전체 멤버"
    assignee != null -> "${assignee.nickname}님 대상"
    else -> "방 전체 멤버"
}

fun Mission.dueLabel(nowMs: Long): String {
    val due = dueAtMs ?: return "마감 시간 정보 없음"
    return "${MissionTime.kstDateTimeLabel(due)} 마감 · ${MissionTime.remainingLabel(due, nowMs)}"
}

/** 방 이름·유형·내용·보상·마감·상태를 보여주는 미션 카드. 여러 방의 미션을 구분하기 위해 방 이름을 항상 표시한다. */
@Composable
fun MissionCard(
    mission: Mission,
    roomName: String?,
    myUserId: Long?,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val state = mission.state(myUserId, nowMs)
    DqCard(
        modifier.fillMaxWidth().testTag("mission-${mission.missionId}"),
        radius = 16.dp, soft = true,
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (roomName != null) {
                    Box(Modifier.background(DQColors.Soft, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Txt(roomName, size = 10.5f, weight = Bold, color = DQColors.Green, maxLines = 1)
                    }
                }
                Txt(mission.originLabel(myUserId), size = 10.5f, color = DQColors.Muted, maxLines = 1)
            }
            Txt(mission.content, size = 15f, weight = Bold, modifier = Modifier.padding(top = 6.dp))
            Row(
                Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DqIcon(R.drawable.ic_coin_sm, 18.dp)
                Txt("+${mission.rewardCoins}코인", size = 12f, weight = Bold)
                Txt(" · ${state.label()}", size = 11.5f, weight = Medium, color = DQColors.Muted)
            }
            Txt(mission.dueLabel(nowMs), size = 11f, color = DQColors.Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun MissionEmptyNote(text: String, modifier: Modifier = Modifier) {
    Txt(text, size = 12.5f, color = DQColors.Muted, modifier = modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp))
}
