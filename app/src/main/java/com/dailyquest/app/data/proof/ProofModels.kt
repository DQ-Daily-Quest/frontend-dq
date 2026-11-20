package com.dailyquest.app.data.proof

import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.mission.MissionUser
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofDto
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto

/** 서버 ProofStatus. 알 수 없는 값은 UNKNOWN으로 두고 원문을 [Proof.rawStatus]에 보존한다. */
enum class ProofStatus {
    PENDING, APPROVED, REJECTED, EXPIRED, UNKNOWN;

    companion object {
        fun parse(raw: String?): ProofStatus = entries.firstOrNull { it != UNKNOWN && it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ProofMediaType {
    IMAGE, VIDEO, TEXT;

    companion object {
        fun parse(raw: String?): ProofMediaType = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEXT
    }
}

enum class VoteChoice {
    APPROVE, REJECT;

    companion object {
        fun parse(raw: String?): VoteChoice? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

data class Voter(val userId: Long, val nickname: String, val vote: VoteChoice?)

data class Approval(
    val approved: Int,
    val rejected: Int,
    val required: Int,
    val myVote: VoteChoice?,
    val voters: List<Voter>,
)

data class Proof(
    val proofId: Long,
    val missionId: Long,
    val user: MissionUser,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val mediaType: ProofMediaType,
    val text: String?,
    val status: ProofStatus,
    val rawStatus: String,
    val approval: Approval,
    val createdAtMs: Long?,
)

/** 상세 응답에는 미션 생성자가 없다. 승인 가능 여부의 최종 판단은 서버(NOT_MISSION_APPROVER)가 한다. */
data class ProofDetail(
    val proof: Proof,
    val missionType: MissionType,
    val missionContent: String,
    val roomId: Long,
    val roomName: String,
) {
    /**
     * 화면에 승인·거절 버튼을 보일지 여부. 서버가 확정 전 투표 변경을 허용해도 앱은 투표 후에는 버튼을 숨긴다.
     * MEMBER 미션은 생성자만 투표할 수 있다. 생성자를 모르면([missionCreatorId]가 null) 버튼은 보이되 최종 판단은 서버가 한다.
     */
    fun canVote(myUserId: Long?, missionCreatorId: Long?): Boolean {
        if (myUserId == null || proof.status != ProofStatus.PENDING) return false
        if (proof.user.userId == myUserId || proof.approval.myVote != null) return false
        return missionType == MissionType.APP || missionCreatorId == null || missionCreatorId == myUserId
    }

    fun isMine(myUserId: Long?): Boolean = myUserId != null && proof.user.userId == myUserId
}

data class PendingProof(
    val proofId: Long,
    val user: MissionUser,
    val missionId: Long,
    val missionContent: String,
    val mediaType: ProofMediaType,
    val thumbnailUrl: String?,
    val viewed: Boolean,
    val createdAtMs: Long?,
)

data class PendingPage(val items: List<PendingProof>, val page: Int, val totalPages: Int, val totalElements: Long) {
    val hasNext: Boolean get() = page + 1 < totalPages
}

/** [rewardCoins]·[streakBonus]는 서버가 이 투표로 최종 승인을 확정하며 알려준 값이다(null이면 보상 미지급). */
data class VoteResult(
    val proofId: Long,
    val status: ProofStatus,
    val approval: Approval,
    val rewardCoins: Int?,
    val streakBonus: Int,
)

data class UploadedMedia(val url: String, val thumbnailUrl: String?, val mediaType: ProofMediaType)

private fun ApprovalDto.toApproval() = Approval(
    approved, rejected, required, VoteChoice.parse(myVote),
    voters.orEmpty().map { Voter(it.userId, it.nickname, VoteChoice.parse(it.vote)) },
)

private fun user(id: Long, nickname: String) = MissionUser(id, nickname)

fun ProofDto.toProof() = Proof(
    proofId, missionId, user(user.userId, user.nickname), mediaUrl, thumbnailUrl,
    ProofMediaType.parse(mediaType), text, ProofStatus.parse(status), status, approval.toApproval(), MissionTime.parse(createdAt),
)

fun ProofDetailDto.toDetail() = ProofDetail(
    proof = ProofDto(proofId, missionId, user, mediaUrl, thumbnailUrl, mediaType, text, status, approval, createdAt).toProof(),
    missionType = if (mission.type.equals("APP", ignoreCase = true)) MissionType.APP else MissionType.MEMBER,
    missionContent = mission.content,
    roomId = room.roomId,
    roomName = room.name,
)

fun PendingProofDto.toPending() = PendingProof(
    proofId, user(user.userId, user.nickname), mission.missionId, mission.content,
    ProofMediaType.parse(mediaType), thumbnailUrl, viewed, MissionTime.parse(createdAt),
)

fun VoteDto.toResult() = VoteResult(
    proofId, ProofStatus.parse(status), approval.toApproval(), rewardGranted?.amount, rewardGranted?.streakBonus ?: 0,
)

fun UploadDto.toUploaded() = UploadedMedia(url, thumbnailUrl, ProofMediaType.parse(mediaType))
