package com.dailyquest.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** 화면 경로 (피그마 화면 번호와 1:1) */
object Routes {
    const val CATALOG = "catalog"            // 개발용 화면 목록
    const val HOME = "home"                  // 01 홈 · 우리만 (마을 지도)
    const val HOME_PUBLIC = "home_public"    // 02 홈 · 공개
    const val ROOM = "room"                  // 03 방 내부
    const val STORY = "story"                // 04 챌린지 스토리 뷰어
    const val MISSION_VERIFY = "mission_verify" // 05 미션 인증하기
    const val VERIFY_CHECK = "verify_check"  // 06 인증 확인 (팀원 투표)
    const val SEND_MISSION = "send_mission"  // 07 미션 보내기
    const val SHOP = "shop"                  // 08 쇼핑 (아바타 · 우리 집)
    const val LOGIN = "login"                // 09 A1 로그인
    const val SIGNUP_INFO = "signup_info"    // 10 A2 회원가입 · 정보 입력
    const val SIGNUP_AVATAR = "signup_avatar" // 11 A3 회원가입 · 아바타
    const val B1_HOME = "b1_home"            // 12 B1 홈
    const val ROOM_DETAIL = "room_detail"    // 13 B2 방 내부
    const val B3_MISSION_VERIFY = "b3_mission_verify" // 14 B3 미션 인증
    const val B4_SEND_MISSION = "b4_send_mission"     // 15 B4 미션 보내기
    const val B5_SHOP = "b5_shop"            // 16 B5 쇼핑
    const val DAILY_QUEST = "daily_quest"    // 17 Daily Quest
    const val FLOW_MISSION_VERIFY = "flow_mission_verify" // 18 미션 인증
    const val FLOW_VERIFY_CHECK = "flow_verify_check"     // 19 인증 확인

    /** 앱 시작 화면. 실제 서비스에서는 LOGIN 으로 바꾸면 됩니다. */
    const val START = CATALOG
}

val LocalNav = staticCompositionLocalOf<NavHostController> { error("NavController not provided") }

/** 탭 이동: 스택을 쌓지 않고 교체 */
fun NavHostController.switchTo(route: String) {
    navigate(route) {
        currentDestination?.route?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    }
}

fun NavHostController.backOrCatalog() {
    if (!popBackStack()) navigate(Routes.CATALOG)
}
