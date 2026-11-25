package com.dailyquest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyquest.app.R
import com.dailyquest.app.data.mission.MissionValidation
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.backOrHome
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.data.avatar.PersonFace
import com.dailyquest.app.ui.avatar.ProfileFace
import com.dailyquest.app.ui.avatar.rememberPersonFaces
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextField
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.InfoNote
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.mission.CreateMissionViewModel
import com.dailyquest.app.ui.mission.MISSION_OUTCOME_UNKNOWN_MESSAGE
import com.dailyquest.app.ui.mission.MissionDueOption
import com.dailyquest.app.ui.room.RoomDetailViewModel
import com.dailyquest.app.ui.theme.DQColors

private val SUGGESTIONS = listOf("산책 10분", "물 2L 마시기", "하늘 찍기")

/**
 * 07 · 미션 보내기 — 서버(POST /rooms/{id}/missions)에 생성한다. 기존 화면 구성을 유지하되
 * 서버가 지원하지 않는 인증 방식·다중 선택·코인 차감 안내는 제거했다. 받는 사람은 실제 방 멤버(나 제외) 한 명 또는 전체다.
 */
@Composable
fun SendRoomMissionScreen(viewModel: CreateMissionViewModel, room: RoomDetailViewModel) {
    val nav = LocalNav.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val roomUi by room.ui.collectAsStateWithLifecycle()
    val recipients = roomUi.members.filter { it.userId != ui.myUserId }
    // 프로필 얼굴 7단계: 받는 사람마다 실제 아바타 얼굴. 얼굴 정보는 한 번에 받는다.
    val recipientFaces = rememberPersonFaces(recipients.map { it.avatar })

    // 성공 이벤트는 한 번만 처리한다. 이동 후 비우고 이 화면은 back stack에서 제거된다.
    LaunchedEffect(ui.created) {
        if (ui.created == null) return@LaunchedEffect
        viewModel.consumeCreated()
        nav.backOrHome()
    }

    DqScreen {
        Column(Modifier.fillMaxSize().testTag("sendRoomMission")) {
            NavHeader("미션 보내기", divider = true)
            FormBody {
                FieldLabel("누구에게 보낼까?", first = true)
                when {
                    roomUi.membersError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(roomUi.membersError.orEmpty(), size = 12f, color = DQColors.Orange, modifier = Modifier.weight(1f))
                        Txt(
                            "다시 불러오기", size = 12f, weight = Bold, color = DQColors.Green,
                            modifier = Modifier.clickable { room.retryMembers() }.padding(8.dp),
                        )
                    }
                    !roomUi.membersLoaded -> Txt("멤버를 불러오는 중이에요…", size = 12f, color = DQColors.Muted)
                    else -> Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Recipient("전체", null, everyone = true, on = ui.assigneeUserId == null, modifier = Modifier.testTag("recipient-all")) { viewModel.onAssigneeChange(null) }
                        recipients.forEachIndexed { i, m ->
                            Recipient(m.nickname, recipientFaces.getOrNull(i), on = ui.assigneeUserId == m.userId, modifier = Modifier.testTag("recipient-${m.userId}")) {
                                viewModel.onAssigneeChange(m.userId)
                            }
                        }
                    }
                }
                if (roomUi.membersLoaded && recipients.isEmpty()) {
                    InfoNote("방에 다른 멤버가 없어도 전체 대상으로 보낼 수 있어요. 멤버가 들어오면 알림이 가요.")
                }
                ui.assigneeError?.let { ErrorText(it) }

                FieldLabel("미션 내용")
                DqTextField(
                    ui.content, viewModel::onContentChange, "2~100자로 입력해 주세요",
                    height = 50.dp, fontSize = 14.5f, weight = FontWeight.Medium, maxLength = MissionValidation.CONTENT_MAX,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTIONS.forEach { s -> Chip(s, false) { viewModel.onContentChange(s) } }
                }
                ui.contentError?.let { ErrorText(it) }

                FieldLabel("기한")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MissionDueOption.entries.forEach { o -> Chip(o.label, o == ui.due) { viewModel.onDueChange(o) } }
                }

                FieldLabel("보상 코인")
                CoinStepper(
                    ui.rewardText,
                    onChange = viewModel::onRewardTextChange,
                    onMinus = { viewModel.stepReward(-MissionValidation.REWARD_STEP) },
                    onPlus = { viewModel.stepReward(MissionValidation.REWARD_STEP) },
                )
                val rewardError = ui.rewardError
                Txt(
                    rewardError ?: "${MissionValidation.REWARD_MIN}~${MissionValidation.REWARD_MAX}코인, ${MissionValidation.REWARD_STEP}코인 단위",
                    size = 11.5f, color = if (rewardError != null) DQColors.Orange else DQColors.Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp).testTag("rewardHint"),
                )
                InfoNote("보상은 인증이 승인되면 앱이 수행한 멤버에게 지급해요. 보내는 사람의 코인은 차감되지 않아요.")
                ui.serverError?.let { ErrorText(it, Modifier.testTag("sendMissionError")) }
                if (ui.outcomeUnknown) {
                    Txt(
                        MISSION_OUTCOME_UNKNOWN_MESSAGE, size = 12.5f, color = DQColors.Orange,
                        modifier = Modifier.padding(top = 16.dp).testTag("sendMissionOutcomeUnknown"),
                    )
                }
            }
        }
        BottomCta {
            Btn(
                if (ui.submitting) "보내는 중…" else "미션 보내기",
                Modifier.weight(1f).height(54.dp),
                size = 16f,
                enabled = !ui.submitting && ui.rewardError == null,
            ) { viewModel.submit() }
        }
    }
}

@Composable
private fun ErrorText(text: String, modifier: Modifier = Modifier) =
    Txt(text, size = 12f, color = DQColors.Orange, modifier = modifier.padding(start = 4.dp, top = 6.dp))

@Composable
private fun Recipient(name: String, face: PersonFace?, everyone: Boolean = false, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.width(56.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (!everyone) ProfileFace(face, 56.dp, contentDescription = "$name 아바타")
            else Box(Modifier.size(48.dp).background(DQColors.Soft, CircleShape).border(1.dp, DQColors.Line, CircleShape), contentAlignment = Alignment.Center) {
                DqIcon(R.drawable.ic_users_lg, 24.dp)
            }
            if (on) {
                DqIcon(R.drawable.ic_selected_ring, 52.dp, Modifier.offset(0.dp, 0.dp))
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(DQColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
                    DqIcon(R.drawable.ic_check_white, 12.dp)
                }
            }
        }
        Txt(name, size = 12f, weight = if (on) Bold else FontWeight.Normal, color = if (on) DQColors.Ink else DQColors.Muted, maxLines = 1)
    }
}

@Composable
private fun CoinStepper(coin: String, onChange: (String) -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(DQColors.White, shape).border(1.dp, DQColors.Line, shape).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(DQColors.White).border(1.dp, DQColors.Line, CircleShape)
                .clickable(onClick = onMinus).testTag("rewardMinus"),
            contentAlignment = Alignment.Center,
        ) { DqIcon(R.drawable.ic_minus, 16.dp) }
        Row(
            Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            DqIcon(R.drawable.ic_coin_22, 22.dp)
            androidx.compose.foundation.text.BasicTextField(
                value = coin,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = DQColors.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.width(88.dp).testTag("rewardValue"),
            )
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(DQColors.Primary).clickable(onClick = onPlus).testTag("rewardPlus"),
            contentAlignment = Alignment.Center,
        ) { DqIcon(R.drawable.ic_plus_white, 16.dp) }
    }
}
