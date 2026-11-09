package com.dailyquest.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyquest.app.R
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Btn
import com.dailyquest.app.ui.components.BtnStyle
import com.dailyquest.app.ui.components.DqImage
import com.dailyquest.app.ui.components.DqProgress
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.components.rememberMediaPicker
import com.dailyquest.app.ui.theme.DQColors
import com.dailyquest.app.ui.theme.NotoSansKr

/* 17–19 세로 흐름형 화면 공통 */

@Composable
private fun FlowColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun FlowTitle(text: String) = Txt(text, size = 25f, weight = Bold)

@Composable
private fun FlowCard(soft: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (soft) DQColors.Soft else DQColors.White, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = content,
    )
}

@Composable
private fun FlowButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit = {}) =
    Btn(label, modifier.height(44.dp), style = BtnStyle.Flow, onClick = onClick)

/** 17 · Daily Quest */
@Composable
fun DailyQuestScreen() {
    val nav = LocalNav.current
    DqScreen {
        DqImage(R.drawable.img_village_map, Modifier.fillMaxSize().alpha(0.2f))
        FlowColumn {
            FlowTitle("Daily Quest")
            Txt("평범한 오늘도, 함께하면 미션!", size = 13f, color = DQColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowButton("Public", Modifier.weight(1f)) { nav.navigate(Routes.HOME_PUBLIC) }
                FlowButton("Private", Modifier.weight(1f)) { nav.navigate(Routes.HOME) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(
                    "진영" to R.drawable.img_avatar_48_boy, "지원" to R.drawable.img_avatar_48_girl,
                    "정원" to R.drawable.img_avatar_48_boy, "내 인증" to R.drawable.img_avatar_48_girl,
                ).forEach { (name, res) ->
                    Column(Modifier.width(66.dp).clickable { nav.navigate(Routes.STORY) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        DqImage(res, Modifier.size(48.dp))
                        Txt(name, size = 11f)
                    }
                }
            }
            FlowCard(soft = true) {
                Txt("DAILY QUEST  ·  오늘의 미션", size = 11f, weight = Bold, color = DQColors.Green)
                Txt("오늘의 하늘 찍기", size = 23f, weight = Bold)
                Txt("지금 이 순간, 너의 하늘을 보여줘!", size = 13f)
                Txt("보상  ·  랜덤 커스터마이징 아이템 1개", size = 12f, weight = Bold, color = DQColors.Green)
                FlowButton("사진 / 글로 인증하기") { nav.navigate(Routes.FLOW_MISSION_VERIFY) }
            }
            FlowCard {
                Txt("이번 주 미션  3 / 5 완료", size = 14f, weight = Bold)
                DqProgress(0.6f, height = 8.dp, color = DQColors.Flow, rounded = false)
            }
            Txt("친구가 보낸 미션", size = 17f, weight = Bold)
            FlowCard {
                listOf("지원  ·  오늘 커피 한 잔 마시기" to "+50 코인", "정원  ·  10분 동안 산책하기" to "+100 코인").forEach { (m, c) ->
                    Row { Txt(m, size = 12f, modifier = Modifier.width(200.dp)); Txt(c, size = 12f) }
                }
                Txt("확인 대기 중인 인증  2개  →", size = 12f, weight = Bold, color = DQColors.Green, modifier = Modifier.clickable { nav.navigate(Routes.FLOW_VERIFY_CHECK) })
            }
            Txt("내 방  ·  우리들의 아지트   4명  →", size = 14f, weight = Bold, modifier = Modifier.clickable { nav.navigate(Routes.ROOM) })
            Row(horizontalArrangement = Arrangement.spacedBy(38.dp)) {
                listOf("홈" to Routes.DAILY_QUEST, "미션" to Routes.FLOW_MISSION_VERIFY, "쇼핑" to Routes.SHOP, "마이" to null).forEach { (label, route) ->
                    Txt(label, size = 14f, weight = Bold, color = DQColors.Green, modifier = Modifier.clickable { route?.let { if (it != Routes.DAILY_QUEST) nav.navigate(it) } })
                }
            }
        }
    }
}

/** 18 · 미션 인증 */
@Composable
fun FlowMissionVerifyScreen() {
    val nav = LocalNav.current
    val (picked, pick) = rememberMediaPicker()
    var note by remember { mutableStateOf("") }
    var story by remember { mutableStateOf(true) }

    DqScreen {
        FlowColumn {
            FlowTitle("미션 인증")
            FlowCard(soft = true) {
                Txt("오늘의 하늘 찍기", size = 20f, weight = Bold)
                Txt("앱 미션 · 커스터마이징 아이템 1개", size = 12f)
            }
            Box(
                Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(12.dp)).background(DQColors.Soft).clickable { pick() },
                contentAlignment = Alignment.Center,
            ) {
                if (picked != null) Image(picked, "선택한 사진", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Txt("사진 / 영상 미리보기", size = 13f, color = DQColors.Muted)
            }
            FlowButton("사진 / 영상 선택") { pick() }
            FlowCard {
                Txt("오늘의 순간을 적어주세요", size = 14f, color = DQColors.Muted)
                Box {
                    if (note.isEmpty()) Txt("예: 수업 끝나고 본 하늘이 정말 맑았어!", size = 12f, color = DQColors.Muted)
                    BasicTextField(
                        value = note, onValueChange = { if (it.length <= 100) note = it },
                        textStyle = TextStyle(fontFamily = NotoSansKr, fontSize = 12.sp, color = DQColors.Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FlowCard {
                Txt("공유할 방  ·  우리들의 아지트", size = 14f, weight = Bold)
                Txt("Private · 방 멤버에게만 공개", size = 12f)
                Row(Modifier.fillMaxWidth().clickable { story = !story }) {
                    Txt("스토리에 표시", size = 13f, modifier = Modifier.width(185.dp))
                    Txt(if (story) "켜짐" else "꺼짐", size = 13f)
                }
            }
            Txt("제출 후 팀원이 수행 여부를 확인해요.", size = 12f, color = DQColors.Muted)
            FlowButton("인증 제출하기") { nav.navigate(Routes.FLOW_VERIFY_CHECK) }
        }
    }
}

/** 19 · 인증 확인 */
@Composable
fun FlowVerifyCheckScreen() {
    DqScreen {
        FlowColumn {
            FlowTitle("인증 확인")
            FlowCard {
                Txt("지원  ·  우리들의 아지트", size = 15f, weight = Bold)
                Txt("오늘의 하늘 찍기  /  방금 전", size = 12f, color = DQColors.Muted)
            }
            DqImage(R.drawable.img_photo_sky, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp)), description = "지원의 인증 사진")
            Txt("“수업 끝나고 본 오늘의 하늘!”", size = 15f)
            FlowCard(soft = true) {
                Txt("팀원 확인 중", size = 17f, weight = Bold)
                Txt("현재 확인 1명 · 내 확인을 기다리고 있어요", size = 12f)
                Txt("승인 기준과 보상 지급 시점은 기획 확정 예정", size = 11f, color = DQColors.Muted)
            }
            FlowButton("수행 확인하기")
            FlowButton("다시 인증 요청")
            Txt("앱 미션 → 아이템\n멤버 미션 → 코인", size = 12f, color = DQColors.Muted)
        }
    }
}

