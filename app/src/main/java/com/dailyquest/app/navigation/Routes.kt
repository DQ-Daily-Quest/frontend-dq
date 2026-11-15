
package com.dailyquest.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** 화면 경로 (피그마 화면 번호와 1:1) */
object Routes {

    const val CATALOG = "catalog"
    const val HOME = "home"
    const val HOME_PUBLIC = "home_public"

    // 03 방 내부 — roomId 기반 화면 이동
    const val ROOM = "room/{roomId}"

    fun room(roomId: Long): String {
        return "room/$roomId"
    }

    // 방 생성 및 초대코드 참여
    const val CREATE_ROOM = "create_room"
    const val CREATE_ROOM_ROUTE = "$CREATE_ROOM?isPublic={isPublic}"

    fun createRoom(isPublic: Boolean): String = "$CREATE_ROOM?isPublic=$isPublic"

    const val JOIN_ROOM = "join_room"

    const val STORY = "story"
    const val MISSION_VERIFY = "mission_verify"
    const val VERIFY_CHECK = "verify_check"
    const val SEND_MISSION = "send_mission"
    const val SHOP = "shop"

    // 로그인 및 회원가입
    const val LOGIN = "login"
    const val SIGNUP_INFO = "signup_info"
    const val SIGNUP_AVATAR = "signup_avatar"

    // 기존 B안 화면 — 개발 및 참고용으로 유지
    const val B1_HOME = "b1_home"
    const val ROOM_DETAIL = "room_detail"
    const val B3_MISSION_VERIFY = "b3_mission_verify"
    const val B4_SEND_MISSION = "b4_send_mission"
    const val B5_SHOP = "b5_shop"

    // 기존 세로 흐름형 화면
    const val DAILY_QUEST = "daily_quest"
    const val FLOW_MISSION_VERIFY = "flow_mission_verify"
    const val FLOW_VERIFY_CHECK = "flow_verify_check"

    /** 앱 시작 화면. 실제 서비스에서는 로그인 상태에 따라 변경 */
    const val START = CATALOG
}

val LocalNav = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

/** 탭 이동: 스택을 쌓지 않고 교체 */
fun NavHostController.switchTo(route: String) {
    navigate(route) {
        currentDestination?.route?.let {
            popUpTo(it) {
                inclusive = true
            }
        }
        launchSingleTop = true
    }
}

fun NavHostController.backOrCatalog() {
    if (!popBackStack()) navigate(Routes.CATALOG)
}
