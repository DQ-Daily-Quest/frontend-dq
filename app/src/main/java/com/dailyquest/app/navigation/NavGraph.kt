
package com.dailyquest.app.navigation

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.ui.auth.LocalAuth
import com.dailyquest.app.ui.components.Medium
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import com.dailyquest.app.ui.room.CreateRoomViewModel
import com.dailyquest.app.ui.room.LocalAppContainer
import com.dailyquest.app.ui.room.LocalMyRooms
import com.dailyquest.app.ui.room.RoomJoinViewModel
import com.dailyquest.app.ui.room.RoomManageViewModel
import com.dailyquest.app.ui.room.LocalPublicRooms
import com.dailyquest.app.ui.room.RoomDetailViewModel
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailyquest.app.ui.screens.*

/** 로그인 없이 볼 수 있는 경로. 그 외 모든 경로는 인증된 세션이 있어야 한다. */
private val PUBLIC_ROUTES = setOf(Routes.SPLASH, Routes.LOGIN, Routes.SIGNUP_INFO)

/** 서버와 연동된 화면. 이 외 화면은 아직 Mock 데이터를 사용한다. */
private val SERVER_BACKED_ROUTES = PUBLIC_ROUTES + setOf(Routes.SIGNUP_AVATAR, Routes.MY)

@Composable
fun DQNavGraph() {
    val nav = rememberNavController()
    val auth = LocalAuth.current
    val session by auth.session.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // 개발용 화면(Catalog, B안, 세로 흐름)은 디버그 빌드에서만 등록한다. 인증 가드는 동일하게 적용된다.
    val devRoutes = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // 세션 상태에 따른 화면 전환과 보호 경로 접근 차단
    LaunchedEffect(session, route) {
        if (route == null) return@LaunchedEffect
        when (session) {
            is SessionState.Unauthenticated ->
                if (route !in PUBLIC_ROUTES || route == Routes.SPLASH) nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            is SessionState.Authenticated ->
                if (route == Routes.SPLASH || route == Routes.LOGIN) nav.navigate(auth.consumePostLoginRoute()) { popUpTo(0) { inclusive = true } }
            else -> if (route !in PUBLIC_ROUTES) nav.navigate(Routes.SPLASH) { popUpTo(0) { inclusive = true } }
        }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.START
        ) {
            composable(Routes.SPLASH) {
                SplashScreen()
            }

            composable(Routes.MY) {
                AccountScreen()
            }

            if (devRoutes) composable(Routes.CATALOG) {
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
                val initialIsPublic = backStackEntry.arguments?.getBoolean("isPublic") ?: false
                val myRooms = LocalMyRooms.current
                val publicRooms = LocalPublicRooms.current
                val container = LocalAppContainer.current
                val vm: CreateRoomViewModel = viewModel(
                    factory = CreateRoomViewModel.Factory(
                        initialIsPublic, container,
                        onCreated = { myRooms.refresh(); publicRooms.refresh() },
                        onOutcomeUnknown = { myRooms.refresh() },
                    ),
                )
                CreateRoomScreen(viewModel = vm)
            }

            composable(Routes.JOIN_ROOM) {
                val myRooms = LocalMyRooms.current
                val publicRooms = LocalPublicRooms.current
                val vm: RoomJoinViewModel = viewModel(
                    factory = RoomJoinViewModel.Factory(
                        LocalAppContainer.current,
                        onChanged = { myRooms.refresh(); publicRooms.refresh() },
                        onOutcomeUnknown = { myRooms.refresh() },
                    ),
                )
                JoinRoomScreen(vm)
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

                val vm: RoomDetailViewModel = viewModel(
                    key = "room-$roomId",
                    factory = RoomDetailViewModel.Factory(roomId, LocalAppContainer.current),
                )
                val myRooms = LocalMyRooms.current
                val publicRooms = LocalPublicRooms.current
                val manage: RoomManageViewModel = viewModel(
                    key = "room-manage-$roomId",
                    factory = RoomManageViewModel.Factory(
                        roomId, LocalAppContainer.current,
                        onListsChanged = { myRooms.refresh(); publicRooms.refresh() },
                    ),
                )
                RoomScreen(roomId = roomId, viewModel = vm, manage = manage)
            }

            composable(
                route = Routes.MOCK_ROOM,
                arguments = listOf(navArgument("roomId") { type = NavType.LongType })
            ) { backStackEntry ->
                MockRoomScreen(roomId = requireNotNull(backStackEntry.arguments).getLong("roomId"))
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
            if (devRoutes) composable(Routes.B1_HOME) {
                B1HomeScreen()
            }

            if (devRoutes) composable(Routes.ROOM_DETAIL) {
                RoomDetailScreen()
            }

            if (devRoutes) composable(Routes.B3_MISSION_VERIFY) {
                B3MissionVerifyScreen()
            }

            if (devRoutes) composable(Routes.B4_SEND_MISSION) {
                B4SendMissionScreen()
            }

            if (devRoutes) composable(Routes.B5_SHOP) {
                B5ShopScreen()
            }

            if (devRoutes) composable(Routes.DAILY_QUEST) {
                DailyQuestScreen()
            }

            if (devRoutes) composable(Routes.FLOW_MISSION_VERIFY) {
                FlowMissionVerifyScreen()
            }

            if (devRoutes) composable(Routes.FLOW_VERIFY_CHECK) {
                FlowVerifyCheckScreen()
            }
        }
        if (route != null && route !in SERVER_BACKED_ROUTES) {
            Txt(
                if (route == Routes.HOME_PUBLIC || route == Routes.HOME || route == Routes.ROOM) "일부 샘플 데이터 포함" else "샘플 데이터 · 서버 미연동", size = 10.5f, weight = Medium, color = DQColors.White,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp)
                    .background(DQColors.Ink.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        }
    }
}
