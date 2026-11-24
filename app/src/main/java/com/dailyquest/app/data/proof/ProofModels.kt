package com.dailyquest.app.data.proof

import com.dailyquest.app.data.mission.MissionTime
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.mission.MissionUser
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofDto
import com.dailyquest.app.data.remote.dto.ReactionSummaryDto
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto

/** 서버 ProofStatus. 알 수 없는 값은 UNKNOWN으로 두고 원문을 [Proof.rawStatus]에 보존한다. */
enum class ProofStatus {
    PENDING, APPROVED, REJECTED, EXPIRED, UNKNOWN;

    companion object {
        fun parse(raw: String?): ProofStatus = entries.firstOrNull { it != UNKNOWN && it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** 사용자에게 보이는 인증 상태 문구. 모든 화면이 이 함수를 쓰며, 알 수 없는 서버 값은 원문을 노출하지 않고 대체 문구를 쓴다. */
fun proofStatusLabel(raw: String?): String = proofStatusLabel(ProofStatus.parse(raw))

fun proofStatusLabel(status: ProofStatus): String = when (status) {
    ProofStatus.PENDING -> "승인 대기"
    ProofStatus.APPROVED -> "승인 완료"
    ProofStatus.REJECTED -> "인증 거절"
    ProofStatus.EXPIRED -> "인증 만료"
    ProofStatus.UNKNOWN -> "상태 확인 중"
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
    val reactions: List<Reaction> = emptyList(),
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

    /**
     * 혼자 있는 방의 APP 미션 자동 승인 여부. 백엔드는 이 경우에만 투표 없이 APPROVED로 확정하고(ProofService),
     * 정상 승인은 승인 투표가 필요 수 이상 쌓여야 확정되며 확정 뒤에는 투표가 바뀌지 않는다.
     * 따라서 "승인 완료인데 승인 투표가 0건인 APP 인증"만 자동 승인으로 본다. 현재 방 인원으로는 판단하지 않는다.
     */
    val isAutoApproved: Boolean
        get() = missionType == MissionType.APP && proof.status == ProofStatus.APPROVED && proof.approval.approved == 0
}

/** 서버가 허용하는 리액션 이모지(백엔드 ALLOWED_EMOJIS). 서버 응답에 없는 이모지는 개수 0으로 보여 준다. */
object ReactionEmojis {
    val ALLOWED = listOf("❤️", "🔥", "👏", "😂", "😮")
}

data class Reaction(val emoji: String, val count: Int, val reacted: Boolean)

/** 서버 응답을 허용된 5개 이모지 순서로 정리한다. 허용되지 않은 값은 버리고 음수 개수는 0으로 본다. */
fun List<ReactionSummaryDto>.toReactions(): List<Reaction> = ReactionEmojis.ALLOWED.map { e ->
    val r = firstOrNull { it.emoji == e }
    Reaction(e, (r?.count ?: 0).coerceAtLeast(0), r?.reacted == true)
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
    reactions = reactions.toReactions(),
)

fun PendingProofDto.toPending() = PendingProof(
    proofId, user(user.userId, user.nickname), mission.missionId, mission.content,
    ProofMediaType.parse(mediaType), thumbnailUrl, viewed, MissionTime.parse(createdAt),
)

fun VoteDto.toResult() = VoteResult(
    proofId, ProofStatus.parse(status), approval.toApproval(), rewardGranted?.amount, rewardGranted?.streakBonus ?: 0,
)

fun UploadDto.toUploaded() = UploadedMedia(url, thumbnailUrl, ProofMediaType.parse(mediaType))
