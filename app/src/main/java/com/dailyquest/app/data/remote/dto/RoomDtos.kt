package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonNames

/*
 * 백엔드 RoomDtos.kt / RoomFlowTest 의 JSON 계약 기준.
 * Boolean 필드는 실제 JSON 키가 isPublic/isHot 이고 OpenAPI 는 public/hot 으로 표기한다.
 * 응답 DTO 는 두 표기를 모두 읽고, 요청은 isPublic 으로만 보낸다.
 */

@Serializable
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

/** GET /rooms, GET /rooms/mine. todayMissionDone·pendingProofCount 는 /rooms/mine 에서만 채워진다. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoomSummaryDto(
    val roomId: Long,
    val name: String,
    val description: String? = null,
    @SerialName("isPublic") @JsonNames("public") val isPublic: Boolean,
    val memberCount: Long = 0,
    val maxMembers: Int = 10,
    val thumbnailUrl: String? = null,
    @SerialName("isHot") @JsonNames("hot") val isHot: Boolean = false,
    val joined: Boolean = false,
    val todayMissionDone: Boolean? = null,
    val pendingProofCount: Long? = null,
)

@Serializable
data class RoomOwnerDto(val userId: Long, val nickname: String)

@Serializable
data class RoomTodayMissionDto(val missionId: Long, val content: String)

/** myRole: OWNER | MEMBER | null(비멤버). inviteCode·todayMission 은 멤버에게만 내려온다. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RoomDetailDto(
    val roomId: Long,
    val name: String,
    val description: String? = null,
    @SerialName("isPublic") @JsonNames("public") val isPublic: Boolean,
    val inviteCode: String? = null,
    val owner: RoomOwnerDto,
    val memberCount: Long = 0,
    val maxMembers: Int = 10,
    val myRole: String? = null,
    val todayMission: RoomTodayMissionDto? = null,
    val createdAt: String? = null,
)

@Serializable
data class RoomMemberDto(
    val userId: Long,
    val nickname: String,
    val role: String,
    val avatar: AvatarDto = AvatarDto(),
    val todayMissionDone: Boolean = false,
    val joinedAt: String? = null,
)

/** 서버 기본값(maxMembers=10)에 의존하지 않도록 항상 명시한다. */
@Serializable
data class CreateRoomRequest(
    val name: String,
    val description: String? = null,
    @SerialName("isPublic") val isPublic: Boolean,
    val maxMembers: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateRoomResponse(
    val roomId: Long,
    val name: String,
    @SerialName("isPublic") @JsonNames("public") val isPublic: Boolean,
    val inviteCode: String? = null,
    val ownerId: Long,
)

/** 보낸 필드만 수정된다. null/생략은 "변경 없음"이며 설명을 지우려면 빈 문자열을 보낸다. */
@Serializable
data class UpdateRoomRequest(
    val name: String? = null,
    val description: String? = null,
    @SerialName("isPublic") val isPublic: Boolean? = null,
    val maxMembers: Int? = null,
)

@Serializable
data class JoinByCodeRequest(val inviteCode: String)

@Serializable
data class TransferOwnerRequest(val userId: Long)

@Serializable
data class InviteCodeDto(val inviteCode: String)
