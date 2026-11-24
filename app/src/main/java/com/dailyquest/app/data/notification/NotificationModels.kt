package com.dailyquest.app.data.notification

import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.remote.dto.NotificationDto
import com.dailyquest.app.navigation.Routes

/** 서버 알림 유형. 알 수 없는 값은 UNKNOWN으로 두고 제목·본문만 보여 준다. */
enum class NotificationType {
    NEW_APP_MISSION, NEW_MEMBER_MISSION, PROOF_SUBMITTED, PROOF_APPROVED, PROOF_REJECTED, UNKNOWN;

    companion object {
        fun parse(raw: String?): NotificationType = entries.firstOrNull { it != UNKNOWN && it.name == raw } ?: UNKNOWN
    }
}

data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val roomId: Long?,
    val missionId: Long?,
    val proofId: Long?,
    val isRead: Boolean,
    val createdAtMs: Long?,
)

data class NotificationPage(val items: List<AppNotification>, val page: Int, val totalPages: Int, val totalElements: Long) {
    val hasNext: Boolean get() = page + 1 < totalPages
}

/** 서버 ID는 양수만 유효하다. 0·음수·누락은 null. */
private fun Long?.validId(): Long? = this?.takeIf { it > 0 }

fun NotificationDto.toNotification() = AppNotification(
    notificationId, NotificationType.parse(type), title, body, roomId.validId(), missionId.validId(), proofId.validId(),
    isRead, MissionTime.parse(createdAt),
)

/**
 * 알림함 항목과 FCM 알림 탭이 함께 쓰는 이동 규칙. 대상 화면(미션·인증·방)이 삭제됐거나 권한이 없으면
 * 도착한 화면이 서버의 404/403 응답을 안내하므로 여기서는 ID가 있는 가장 구체적인 경로만 고른다.
 * 외부 입력이므로 ID는 [parseId]로만 만든다.
 */
object NotificationTargets {
    /** FCM data payload 값(문자열)을 안전하게 파싱한다. 숫자가 아니거나 0 이하, 지나치게 긴 값은 null. */
    fun parseId(raw: String?): Long? = raw?.trim()?.takeIf { it.length in 1..18 && it.all { c -> c in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }

    fun routeFor(type: NotificationType, roomId: Long?, missionId: Long?, proofId: Long?): String? {
        val room = roomId?.takeIf { it > 0 }
        val mission = missionId?.takeIf { it > 0 }
        val proof = proofId?.takeIf { it > 0 }
        val byMission = mission?.let { Routes.missionDetail(it, room ?: 0L) }
        val byRoom = room?.let { Routes.room(it) }
        return when (type) {
            NotificationType.NEW_APP_MISSION, NotificationType.NEW_MEMBER_MISSION -> byMission ?: byRoom
            NotificationType.PROOF_SUBMITTED, NotificationType.PROOF_APPROVED, NotificationType.PROOF_REJECTED ->
                proof?.let { Routes.proofDetail(it, room ?: 0L) } ?: byMission ?: byRoom
            NotificationType.UNKNOWN -> null
        }
    }

    fun routeFor(n: AppNotification): String? = routeFor(n.type, n.roomId, n.missionId, n.proofId)

    /** FCM data payload(모든 값이 문자열이며 일부는 생략)에서 이동 경로를 만든다. 알 수 없는 유형이나 ID 없음이면 null. */
    fun routeFromPayload(data: Map<String, String?>): String? =
        routeFor(NotificationType.parse(data["type"]), parseId(data["roomId"]), parseId(data["missionId"]), parseId(data["proofId"]))
}
