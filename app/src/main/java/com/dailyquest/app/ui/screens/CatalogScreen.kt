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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailyquest.app.navigation.LocalNav
import com.dailyquest.app.navigation.Routes
import com.dailyquest.app.ui.components.Black
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.DqScreen
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors

private val GROUPS = listOf(
    "A · 기존 화면 (01–08)" to listOf(
        Triple("01", "홈 · 우리만 (마을 지도)", Routes.HOME),
        Triple("02", "홈 · 공개 (다른 마을 구경)", Routes.HOME_PUBLIC),
        Triple("03", "방 내부 (우리들의 아지트)", Routes.ROOM),
        Triple("04", "챌린지 스토리 뷰어", Routes.STORY),
        Triple("05", "미션 인증하기", Routes.MISSION_VERIFY),
        Triple("06", "인증 확인 (팀원 투표)", Routes.VERIFY_CHECK),
        Triple("07", "미션 보내기", Routes.SEND_MISSION),
        Triple("08", "쇼핑 (아바타 · 우리 집)", Routes.SHOP),
    ),
    "B · 로그인 · 회원가입 (09–11)" to listOf(
        Triple("09", "A1 로그인", Routes.LOGIN),
        Triple("10", "A2 회원가입 · 정보 입력", Routes.SIGNUP_INFO),
        Triple("11", "A3 회원가입 · 아바타 만들기", Routes.SIGNUP_AVATAR),
    ),
    "C · B 플로우 (12–16)" to listOf(
        Triple("12", "B1 홈", Routes.B1_HOME),
        Triple("13", "B2 방 내부", Routes.ROOM_DETAIL),
        Triple("14", "B3 미션 인증", Routes.B3_MISSION_VERIFY),
        Triple("15", "B4 미션 보내기", Routes.B4_SEND_MISSION),
        Triple("16", "B5 쇼핑", Routes.B5_SHOP),
    ),
    "D · 세로 흐름형 (17–19)" to listOf(
        Triple("17", "Daily Quest", Routes.DAILY_QUEST),
        Triple("18", "미션 인증", Routes.FLOW_MISSION_VERIFY),
        Triple("19", "인증 확인", Routes.FLOW_VERIFY_CHECK),
    ),
)

/** 개발용 화면 목록 — 19개 화면으로 바로 이동 */
@Composable
fun CatalogScreen() {
    val nav = LocalNav.current
    DqScreen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
        ) {
            Txt("Daily Quest · 작은 마을 테마", size = 24f, weight = Black)
            Txt("개발용 화면 목록 · 19개", size = 13f, color = DQColors.Muted, modifier = Modifier.padding(top = 6.dp))
            GROUPS.forEach { (title, items) ->
                Txt(title, size = 14f, weight = Bold, color = Color(0xFF20563E), modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { (num, label, route) ->
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(DQColors.White).border(1.dp, DQColors.Line, shape)
                                .clickable { nav.navigate(route) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(28.dp).background(DQColors.Soft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Txt(num, size = 12f, weight = Bold, color = DQColors.PrimaryStrong)
                            }
                            Txt(label, size = 14f, weight = Medium)
                        }
                    }
                }
            }
        }
    }
}
