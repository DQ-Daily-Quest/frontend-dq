package com.dailyquest.app.ui.screens

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.navigation.backOrCatalog
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.BottomCta
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.Chip
import com.dailyquest.app.ui.components.DecisionNote
import com.dailyquest.app.ui.components.DqAvatar
import com.dailyquest.app.ui.components.DqIcon
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.DqTextArea
import com.dailyquest.app.ui.components.FieldLabel
import com.dailyquest.app.ui.components.FormBody
import com.dailyquest.app.ui.components.InfoNote
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.MissionInput
import com.dailyquest.app.ui.components.MissionSummary
import com.dailyquest.app.ui.components.NavHeader
import com.dailyquest.app.ui.components.Person
import com.dailyquest.app.ui.components.RecipientPicker
import com.dailyquest.app.ui.components.ToggleCard
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.UploadArea
import com.dailyquest.app.ui.components.VoteChip
import com.dailyquest.app.ui.theme.DQColors

/** 05 · 미션 인증하기 */
@Composable
fun MissionVerifyScreen() {
    val nav = LocalNav.current
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("미션 인증하기", divider = true)
            FormBody {
                MissionSummary(R.drawable.ic_cloud_lg, "오늘의 하늘 찍기", "앱 미션 · 보상 랜덤 꾸미기 아이템 1개 · 오늘 자정까지")
                UploadArea()
                FieldLabel("한마디")
                DqTextArea("오늘 하늘은 어땠어? (선택)")
                ToggleCard("스토리에도 올리기", "멤버들 화면에서 내 프로필에 링이 생겨요")
                InfoNote("멤버 과반수가 \"통과\"하면 보상이 지급돼요")
            }
        }
        BottomCta {
            Btn("인증 올리기", Modifier.weight(1f).height(54.dp), size = 16f) { nav.navigate(Routes.ROOM) }
        }
    }
}

private data class Vote(val name: String, @DrawableRes val avatar: Int, val pass: Boolean)

/** 06 · 인증 확인 (팀원 투표) */
@Composable
fun VerifyCheckScreen() {
    val nav = LocalNav.current
    val votes = listOf(
        Vote("지수", R.drawable.img_avatar_girl, true),
        Vote("서연", R.drawable.img_avatar_boy, true),
        Vote("나", R.drawable.img_avatar_girl, false),
    )
    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("인증 확인하기", divider = true)
            FormBody(top = 14.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(160.dp).height(26.dp).background(DQColors.Soft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Txt("확인 대기 3개 중 1번째", size = 11.5f, weight = Bold, color = DQColors.Green)
                    }
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DqImage(R.drawable.img_avatar_story_42_boy, Modifier.size(42.dp))
                    Column {
                        Txt("민수", size = 14f, weight = Bold)
                        Txt("지수가 보낸 미션 · 10분 동안 산책하기", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(256.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFBFE0F2), Color(0xFFCDE6B2)))),
                ) {
                    DqImage(R.drawable.img_park, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(160.dp), ContentScale.FillBounds)
                    Box(
                        Modifier.padding(12.dp).width(76.dp).height(24.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Txt("00:12 영상", size = 11f, weight = Bold, color = Color.White) }
                }
                Txt("동네 한 바퀴 돌고 옴! 12분 걸었어요", size = 14f, weight = Medium, modifier = Modifier.padding(start = 4.dp, top = 14.dp))

                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt("투표 현황", size = 14f, weight = Bold, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.width(104.dp).height(26.dp).background(DQColors.Soft, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DqIcon(R.drawable.ic_coin_sm, 18.dp)
                        Txt("+100 보상", size = 12f, weight = Bold, color = DQColors.Gold)
                    }
                }
                Column(Modifier.padding(top = 14.dp)) {
                    votes.forEachIndexed { i, v ->
                        Row(
                            Modifier.fillMaxWidth().then(
                                if (i > 0) Modifier.drawBehind { drawLine(DQColors.Soft, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier
                            ).padding(top = if (i == 0) 0.dp else 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DqAvatar(v.avatar, 36.dp)
                            Txt(v.name, size = 14f, weight = Medium, modifier = Modifier.weight(1f))
                            VoteChip(v.pass)
                        }
                    }
                }
                Txt("과반수 통과 시 자동 완료 · 반려 시 사유를 남겨주세요", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 14.dp))
            }
        }
        BottomCta {
            Btn("반려", Modifier.width(118.dp).height(54.dp), style = BtnStyle.Outline, size = 16f)
            Btn("통과!", Modifier.weight(1f).height(54.dp), size = 16f) { nav.backOrCatalog() }
        }
    }
}

/** 07 · 미션 보내기 */
@Composable
fun SendMissionScreen() {
    val nav = LocalNav.current
    var method by remember { mutableStateOf("사진") }
    var due by remember { mutableStateOf("오늘") }
    var coin by remember { mutableIntStateOf(100) }
    val balance = 320

    DqScreen {
        Column(Modifier.fillMaxSize()) {
            NavHeader("미션 보내기", divider = true)
            FormBody {
                FieldLabel("누구에게 보낼까?", first = true)
                RecipientPicker(
                    people = listOf(
                        Person("지수", R.drawable.img_avatar_boy),
                        Person("민수", R.drawable.img_avatar_girl),
                        Person("서연", R.drawable.img_avatar_boy),
                    ),
                    initial = setOf("지수", "서연"),
                    withAll = true,
                )
                FieldLabel("미션 내용")
                MissionInput(listOf("산책 10분", "물 2L 마시기", "하늘 찍기", "랜덤 추천"))

                FieldLabel("인증 방식")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("사진", "동영상", "글").forEach { m -> Chip(m, m == method) { method = m } }
                }
                FieldLabel("기한")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("오늘", "3일", "일주일").forEach { m -> Chip(m, m == due) { due = m } }
                }

                FieldLabel("보상 코인")
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().height(60.dp).background(DQColors.White, shape).border(1.dp, DQColors.Line, shape).padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(DQColors.White).border(1.dp, DQColors.Line, CircleShape)
                            .clickable { coin = (coin - 10).coerceAtLeast(0) },
                        contentAlignment = Alignment.Center,
                    ) { DqIcon(R.drawable.ic_minus, 16.dp) }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        DqIcon(R.drawable.ic_coin_22, 22.dp)
                        Txt("$coin", size = 22f, weight = Black)
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(DQColors.Primary).clickable { coin = (coin + 10).coerceAtMost(balance) },
                        contentAlignment = Alignment.Center,
                    ) { DqIcon(R.drawable.ic_plus_white, 16.dp) }
                }
                Txt("내 코인 $balance → ${balance - coin} (미션 완료 시 차감)", size = 11.5f, color = DQColors.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

                DecisionNote("기획 결정 필요 · 보상 재원", "보낸 사람 코인 차감 vs 앱이 고정 지급")
            }
        }
        BottomCta {
            Btn("미션 보내기", Modifier.weight(1f).height(54.dp), size = 16f) { nav.backOrCatalog() }
        }
    }
}
