
package com.dailyquest.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** 화면 경로 (피그마 화면 번호와 1:1) */
object Routes {

    // 인증 없이 접근 가능한 경로
    const val SPLASH = "splash"

    // 개발용 Catalog (디버그 빌드에서만 등록)
    const val CATALOG = "catalog"
    const val MY = "my"
    const val HOME = "home"
    const val HOME_PUBLIC = "home_public"

    // 03 방 내부 — roomId 기반 화면 이동
    const val ROOM = "room/{roomId}"

    fun room(roomId: Long): String {
        return "room/$roomId"
    }

    // 미션·피드·꾸미기 미연동 샘플 방 화면(고정 샘플, roomId 무관). 실제 서버 roomId는 ROOM 경로로만 이동한다.
    const val MOCK_ROOM = "mock_room/{roomId}"

    fun mockRoom(roomId: Long): String = "mock_room/$roomId"

    // 방 생성 및 초대코드 참여
    const val CREATE_ROOM = "create_room"
    const val CREATE_ROOM_ROUTE = "$CREATE_ROOM?isPublic={isPublic}"

    fun createRoom(isPublic: Boolean): String = "$CREATE_ROOM?isPublic=$isPublic"

    const val JOIN_ROOM = "join_room"

    const val STORY = "story"
    const val MISSION_VERIFY = "mission_verify"
    const val VERIFY_CHECK = "verify_check"
    const val SEND_MISSION = "send_mission"

    // 실제 서버와 연결된 미션 화면. 인증(STEP 5)이 붙을 때 missionId·roomId를 그대로 넘겨받는다.
    const val MISSIONS = "missions"
    const val MISSION_DETAIL = "mission/{missionId}?roomId={roomId}"

    fun missionDetail(missionId: Long, roomId: Long): String = "mission/$missionId?roomId=$roomId"

    // 미션 인증(STEP 5): 실제 missionId·proofId·roomId만 받는다.
    const val PROOF_SUBMIT = "mission/{missionId}/proof?roomId={roomId}"
    const val PROOF_DETAIL = "proof/{proofId}?roomId={roomId}"
    const val PENDING_PROOFS = "room/{roomId}/proofs/pending"

    fun proofSubmit(missionId: Long, roomId: Long): String = "mission/$missionId/proof?roomId=$roomId"
    fun proofDetail(proofId: Long, roomId: Long = 0L): String = "proof/$proofId?roomId=$roomId"
    fun pendingProofs(roomId: Long): String = "room/$roomId/proofs/pending"

    const val SEND_ROOM_MISSION = "room/{roomId}/send-mission"

    fun sendRoomMission(roomId: Long): String = "room/$roomId/send-mission"

    // 방 꾸미기 편집 (STEP 10-6)
    const val ROOM_DECOR = "room/{roomId}/decor"

    fun roomDecor(roomId: Long): String = "room/$roomId/decor"
    const val SHOP = "shop"
    const val COIN_HISTORY = "coin_history"
    const val INVENTORY = "inventory"
    const val PROFILE_EDIT = "profile_edit"
    const val PASSWORD_CHANGE = "password_change"
    const val WITHDRAW = "withdraw"

    // 로그인 및 회원가입
    const val LOGIN = "login"
    const val PASSWORD_RESET = "password_reset"
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

    /** 앱 시작 화면. 세션 상태에 따라 LOGIN/HOME으로 분기한다. */
    const val START = SPLASH
}

val LocalNav = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

private val HOME_ROUTES = setOf(Routes.HOME, Routes.HOME_PUBLIC, Routes.B1_HOME)

/**
 * 탭 이동. 홈 계열 탭(공개/우리만)은 현재 화면을 교체하고, 그 외 탭은 홈 위에 쌓아 뒤로가기 시 홈으로 돌아가게 한다.
 * 홈 계열로 돌아올 때는 스택을 비워 홈이 중복되지 않게 한다.
 */
fun NavHostController.switchTo(route: String) {
    val current = currentDestination?.route
    val currentIsHome = current in HOME_ROUTES
    navigate(route) {
        when {
            route in HOME_ROUTES && !currentIsHome -> popUpTo(0) { inclusive = true }
            currentIsHome && route !in HOME_ROUTES -> Unit
            current != null -> popUpTo(current) { inclusive = true }
        }
        launchSingleTop = true
    }
}

/** 이전 화면이 없으면 홈으로 이동한다. 개발용 Catalog로는 이동하지 않는다. */
fun NavHostController.backOrHome() {
    if (previousBackStackEntry != null) popBackStack()
    else navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
}
