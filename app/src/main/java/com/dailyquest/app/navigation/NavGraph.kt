package com.dailyquest.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailyquest.app.ui.screens.*

@Composable
fun DQNavGraph() {
    val nav = rememberNavController()
    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(navController = nav, startDestination = Routes.START) {
            composable(Routes.CATALOG) { CatalogScreen() }
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.HOME_PUBLIC) { HomePublicScreen() }
            composable(Routes.ROOM) { RoomScreen() }
            composable(Routes.STORY) { StoryScreen() }
            composable(Routes.MISSION_VERIFY) { MissionVerifyScreen() }
            composable(Routes.VERIFY_CHECK) { VerifyCheckScreen() }
            composable(Routes.SEND_MISSION) { SendMissionScreen() }
            composable(Routes.SHOP) { ShopScreen() }
            composable(Routes.LOGIN) { LoginScreen() }
            composable(Routes.SIGNUP_INFO) { SignupInfoScreen() }
            composable(Routes.SIGNUP_AVATAR) { SignupAvatarScreen() }
            composable(Routes.B1_HOME) { B1HomeScreen() }
            composable(Routes.ROOM_DETAIL) { RoomDetailScreen() }
            composable(Routes.B3_MISSION_VERIFY) { B3MissionVerifyScreen() }
            composable(Routes.B4_SEND_MISSION) { B4SendMissionScreen() }
            composable(Routes.B5_SHOP) { B5ShopScreen() }
            composable(Routes.DAILY_QUEST) { DailyQuestScreen() }
            composable(Routes.FLOW_MISSION_VERIFY) { FlowMissionVerifyScreen() }
            composable(Routes.FLOW_VERIFY_CHECK) { FlowVerifyCheckScreen() }
        }
    }
}
