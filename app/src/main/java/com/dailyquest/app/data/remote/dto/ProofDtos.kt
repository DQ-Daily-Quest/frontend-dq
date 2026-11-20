package com.dailyquest.app.data.remote.dto

import kotlinx.serialization.Serializable

/*
 * 백엔드 ProofDtos.kt / UploadResponse 계약 기준.
 * status: PENDING | APPROVED | REJECTED | EXPIRED, mediaType: IMAGE | VIDEO | TEXT, vote: APPROVE | REJECT.
 * 리액션·피드는 STEP 11 범위이므로 응답의 reactions 필드는 읽지 않는다(ignoreUnknownKeys).
 */

/** POST /uploads 응답. 썸네일은 이미지에만 있다. */
@Serializable
data class UploadDto(
    val url: String,
    val thumbnailUrl: String? = null,
    val mediaType: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** null 필드는 JSON에서 생략한다(encodeDefaults=false). mediaUrl·thumbnailUrl ≤ 512자, text ≤ 300자. */
@Serializable
data class SubmitProofRequest(
    val mediaType: String,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val text: String? = null,
)

@Serializable
data class VoterDto(val userId: Long, val nickname: String, val vote: String)

/** voters는 상세 조회에서만 채워진다. myVote는 내가 투표하지 않았으면 null. */
@Serializable
data class ApprovalDto(
    val approved: Int = 0,
    val rejected: Int = 0,
    val required: Int = 1,
    val myVote: String? = null,
    val voters: List<VoterDto>? = null,
)

@Serializable
data class ProofMissionBriefDto(val missionId: Long, val type: String, val content: String)

@Serializable
data class ProofRoomRefDto(val roomId: Long, val name: String)

/** POST /missions/{id}/proofs 응답. 상세 응답은 [ProofDetailDto]. */
@Serializable
data class ProofDto(
    val proofId: Long,
    val missionId: Long,
    val user: RoomOwnerDto,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaType: String,
    val text: String? = null,
    val status: String,
    val approval: ApprovalDto = ApprovalDto(),
    val createdAt: String? = null,
)

/** GET /proofs/{id}. 이 요청은 서버에서 내 "읽음" 표시를 남긴다. */
@Serializable
data class ProofDetailDto(
    val proofId: Long,
    val missionId: Long,
    val user: RoomOwnerDto,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaType: String,
    val text: String? = null,
    val status: String,
    val approval: ApprovalDto = ApprovalDto(),
    val createdAt: String? = null,
    val mission: ProofMissionBriefDto,
    val room: ProofRoomRefDto,
)

/** 승인·거절 응답. rewardGranted는 이 투표로 최종 승인이 확정됐을 때만 들어 있다. */
@Serializable
data class VoteDto(
    val proofId: Long,
    val status: String,
    val approval: ApprovalDto = ApprovalDto(),
    val rewardGranted: MissionRewardDto? = null,
)

@Serializable
data class PendingMissionBriefDto(val missionId: Long, val content: String)

/** GET /rooms/{id}/proofs/pending 항목. 내가 아직 투표하지 않았고 내가 승인할 수 있는 인증만 온다. */
@Serializable
data class PendingProofDto(
    val proofId: Long,
    val user: RoomOwnerDto,
    val mission: PendingMissionBriefDto,
    val mediaType: String,
    val thumbnailUrl: String? = null,
    val status: String,
    val viewed: Boolean = false,
    val createdAt: String? = null,
)
