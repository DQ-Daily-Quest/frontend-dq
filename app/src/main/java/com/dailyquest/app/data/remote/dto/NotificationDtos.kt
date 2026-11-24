package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/*
 * 백엔드 NotificationDtos.kt 계약. GET /notifications 는 PageDto<NotificationDto>.
 * type 은 NEW_APP_MISSION | NEW_MEMBER_MISSION | PROOF_SUBMITTED | PROOF_APPROVED | PROOF_REJECTED 이고, roomId/missionId/proofId 는 없을 수 있다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationDto(
    val notificationId: Long,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val roomId: Long? = null,
    val missionId: Long? = null,
    val proofId: Long? = null,
    // Kotlin 의 isRead 는 JSON 에서 isRead 로 나오고 OpenAPI 는 read 로 표기할 수 있어 둘 다 읽는다.
    @SerialName("isRead") @JsonNames("read") val isRead: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class UnreadCountDto(val unreadCount: Long = 0)

@Serializable
data class FcmTokenRequest(val token: String)
