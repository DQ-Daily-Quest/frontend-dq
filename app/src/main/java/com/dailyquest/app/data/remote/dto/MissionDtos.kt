package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

/*
 * 백엔드 MissionDtos.kt 계약 기준. 시각(dueAt, createdAt)은 ISO-8601 Instant 문자열, date는 KST 기준 yyyy-MM-dd 다.
 * 서버에는 제목·설명 분리, 코인 차감, 인증 방식이 없으며 미션 내용은 content 하나다.
 */

/** reward.type은 현재 COIN 하나다. */
@Serializable
data class MissionRewardDto(val type: String = "COIN", val amount: Int = 0, val streakBonus: Int = 0)

/** status: PENDING | APPROVED (REJECTED가 올 수 있는지는 문서에 없다). */
@Serializable
data class MyProofDto(val proofId: Long, val status: String)

/** type: APP | MEMBER. createdBy는 APP 미션이면 null, assignee가 null이면 방 전체 대상, date는 APP 미션에만 있다. */
@Serializable
data class MissionDto(
    val missionId: Long,
    val type: String,
    val content: String,
    val reward: MissionRewardDto = MissionRewardDto(),
    val createdBy: RoomOwnerDto? = null,
    val assignee: RoomOwnerDto? = null,
    val date: String? = null,
    val dueAt: String,
    val myProof: MyProofDto? = null,
    val proofCount: Long = 0,
    val createdAt: String? = null,
)

@Serializable
data class MissionRoomBriefDto(val roomId: Long, val name: String)

/** GET /missions/mine 의 방별 묶음. */
@Serializable
data class MyRoomMissionsDto(val room: MissionRoomBriefDto, val missions: List<MissionDto> = emptyList())

@Serializable
data class MissionProofItemDto(
    val proofId: Long,
    val user: RoomOwnerDto,
    val status: String,
    val thumbnailUrl: String? = null,
    val createdAt: String? = null,
)

/** GET /missions/{id}. 미션 필드에 제출된 인증 목록이 더해진다. */
@Serializable
data class MissionDetailDto(
    val missionId: Long,
    val type: String,
    val content: String,
    val reward: MissionRewardDto = MissionRewardDto(),
    val createdBy: RoomOwnerDto? = null,
    val assignee: RoomOwnerDto? = null,
    val date: String? = null,
    val dueAt: String,
    val myProof: MyProofDto? = null,
    val proofCount: Long = 0,
    val createdAt: String? = null,
    val proofs: List<MissionProofItemDto> = emptyList(),
)

/** null 필드(dueAt, assigneeUserId)는 JSON에서 생략한다(encodeDefaults=false). null이면 서버 기본값(7일 뒤·방 전체)을 쓴다. */
@Serializable
data class CreateMissionRequest(
    val content: String,
    val rewardCoins: Int,
    val dueAt: String? = null,
    val assigneeUserId: Long? = null,
)
