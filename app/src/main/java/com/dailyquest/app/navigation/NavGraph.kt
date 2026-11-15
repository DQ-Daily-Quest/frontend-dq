
package com.dailyquest.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailyquest.app.ui.screens.*

@Composable
fun DQNavGraph() {
    val nav = rememberNavController()

    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(
            navController = nav,
            startDestination = Routes.START
        ) {
            composable(Routes.CATALOG) {
                CatalogScreen()
            }

            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.HOME_PUBLIC) {
                HomePublicScreen()
            }

            composable(
                route = Routes.CREATE_ROOM_ROUTE,
                arguments = listOf(navArgument("isPublic") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                CreateRoomScreen(initialIsPublic = backStackEntry.arguments?.getBoolean("isPublic") ?: false)
            }

            composable(Routes.JOIN_ROOM) {
                JoinRoomScreen()
            }

            // 방 ID를 전달받아 해당 방 화면으로 이동
            composable(
                route = Routes.ROOM,
                arguments = listOf(
                    navArgument("roomId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->

                val roomId = requireNotNull(
                    backStackEntry.arguments
                ).getLong("roomId")

                RoomScreen(roomId = roomId)
            }

            composable(Routes.STORY) {
                StoryScreen()
            }

            composable(Routes.MISSION_VERIFY) {
                MissionVerifyScreen()
            }

            composable(Routes.VERIFY_CHECK) {
                VerifyCheckScreen()
            }

            composable(Routes.SEND_MISSION) {
                SendMissionScreen()
            }

            composable(Routes.SHOP) {
                ShopScreen()
            }

            composable(Routes.LOGIN) {
                LoginScreen()
            }

            composable(Routes.SIGNUP_INFO) {
                SignupInfoScreen()
            }

            composable(Routes.SIGNUP_AVATAR) {
                SignupAvatarScreen()
            }

            // 기존 B안 화면 — 개발 및 참고용으로 유지
            composable(Routes.B1_HOME) {
                B1HomeScreen()
            }

            composable(Routes.ROOM_DETAIL) {
                RoomDetailScreen()
            }

            composable(Routes.B3_MISSION_VERIFY) {
                B3MissionVerifyScreen()
            }

            composable(Routes.B4_SEND_MISSION) {
                B4SendMissionScreen()
            }

            composable(Routes.B5_SHOP) {
                B5ShopScreen()
            }

            composable(Routes.DAILY_QUEST) {
                DailyQuestScreen()
            }

            composable(Routes.FLOW_MISSION_VERIFY) {
                FlowMissionVerifyScreen()
            }

            composable(Routes.FLOW_VERIFY_CHECK) {
                FlowVerifyCheckScreen()
            }
        }
    }
}
