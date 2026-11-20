package com.dailyquest.app.data.mission

import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto

enum class MissionType { APP, MEMBER }

data class MissionUser(val userId: Long, val nickname: String)

data class MissionProof(val proofId: Long, val user: MissionUser, val status: String)

/**
 * 서버에 상태 enum이 없으므로 화면의 상태는 서버 필드(dueAt, myProof, assignee, createdBy)에서만 계산한다.
 * [roomId]·[roomName]은 /missions/mine 의 방 묶음 또는 방별 조회에서 채운 값이다.
 */
data class Mission(
    val missionId: Long,
    val roomId: Long,
    val roomName: String?,
    val type: MissionType,
    val content: String,
    val rewardCoins: Int,
    val streakBonus: Int,
    val createdBy: MissionUser?,
    val assignee: MissionUser?,
    val date: String?,
    val dueAtMs: Long?,
    val dueAtRaw: String,
    val myProofStatus: String?,
    val proofCount: Long,
    val myProofId: Long? = null,
) {
    fun isExpired(nowMs: Long): Boolean = dueAtMs != null && dueAtMs <= nowMs

    /** 내가 만든 MEMBER 미션 여부(만든 사람은 수행할 수 없다). */
    fun isCreatedBy(userId: Long?): Boolean = userId != null && createdBy?.userId == userId

    /** 다른 멤버가 보낸 미션(APP 제외). */
    fun isFromFriend(userId: Long?): Boolean = type == MissionType.MEMBER && createdBy != null && !isCreatedBy(userId)

    /** 서버 규칙: APP은 모든 멤버, MEMBER는 작성자 제외·지정 대상이 있으면 그 대상만. */
    fun isEligible(userId: Long?): Boolean = when {
        userId == null -> false
        type == MissionType.APP -> true
        isCreatedBy(userId) -> false
        else -> assignee == null || assignee.userId == userId
    }

    /**
     * [rejected]는 내 인증이 거절된 적이 있다는 뜻(서버의 myProof에는 PENDING·APPROVED만 오므로 미션 상세의 인증 목록에서 확인한 값).
     * 아직 마감 전이고 내가 수행 대상이면 재인증이 필요한 상태가 된다.
     */
    fun state(userId: Long?, nowMs: Long, rejected: Boolean = false): MissionState = when {
        myProofStatus == PROOF_APPROVED -> MissionState.DONE
        myProofStatus == PROOF_PENDING -> MissionState.PENDING_REVIEW
        myProofStatus != null -> MissionState.PROOF_OTHER
        isExpired(nowMs) -> MissionState.EXPIRED
        !isEligible(userId) -> MissionState.NOT_APPLICABLE
        rejected -> MissionState.RESUBMIT_NEEDED
        else -> MissionState.DOABLE
    }

    /** 거절 이력을 확인할 가치가 있는 미션: 내 인증이 없고, 마감 전이며, 내가 수행 대상이고, 제출된 인증이 하나라도 있다. */
    fun mayHaveRejectedProof(userId: Long?, nowMs: Long): Boolean =
        myProofStatus == null && proofCount > 0 && !isExpired(nowMs) && isEligible(userId)

    companion object {
        const val PROOF_PENDING = "PENDING"
        const val PROOF_APPROVED = "APPROVED"
    }
}

/**
 * DOABLE 수행 가능, PENDING_REVIEW 인증 대기, DONE 완료, EXPIRED 마감, NOT_APPLICABLE 내가 수행 대상이 아님(내가 보낸 미션 등),
 * RESUBMIT_NEEDED 이전 인증이 거절돼 다시 인증해야 하는 상태, PROOF_OTHER 문서에 없는 인증 상태(예: REJECTED) — 서버 값을 그대로 보존하기 위한 분류.
 */
enum class MissionState { DOABLE, RESUBMIT_NEEDED, PENDING_REVIEW, DONE, EXPIRED, NOT_APPLICABLE, PROOF_OTHER }

data class MissionDetail(val mission: Mission, val proofs: List<MissionProof>) {
    /** 내 인증 중 거절된 것이 있는지. 마감·권한 판단은 [Mission.state]가 한다. */
    fun hasMyRejectedProof(userId: Long?): Boolean =
        userId != null && proofs.any { it.user.userId == userId && it.status.equals("REJECTED", ignoreCase = true) }
}

data class RoomMissions(val roomId: Long, val roomName: String, val missions: List<Mission>)

data class MissionPage(val missions: List<Mission>, val page: Int, val totalPages: Int, val totalElements: Long) {
    val hasNext: Boolean get() = page + 1 < totalPages
}

/** 서버 type 값. 알 수 없는 값은 MEMBER 로 취급하지 않고 APP이 아닌 것으로만 본다. */
private fun typeOf(raw: String) = if (raw.equals("APP", ignoreCase = true)) MissionType.APP else MissionType.MEMBER

fun MissionDto.toMission(roomId: Long, roomName: String? = null) = Mission(
    missionId = missionId,
    roomId = roomId,
    roomName = roomName,
    type = typeOf(type),
    content = content,
    rewardCoins = reward.amount,
    streakBonus = reward.streakBonus,
    createdBy = createdBy?.let { MissionUser(it.userId, it.nickname) },
    assignee = assignee?.let { MissionUser(it.userId, it.nickname) },
    date = date,
    dueAtMs = MissionTime.parse(dueAt),
    dueAtRaw = dueAt,
    myProofStatus = myProof?.status,
    proofCount = proofCount,
    myProofId = myProof?.proofId,
)

/** 상세 응답에는 방 정보가 없으므로 호출자가 아는 roomId(없으면 0)를 넣는다. */
fun MissionDetailDto.toDetail(roomId: Long = 0L) = MissionDetail(
    MissionDto(missionId, type, content, reward, createdBy, assignee, date, dueAt, myProof, proofCount, createdAt)
        .toMission(roomId),
    proofs.map { MissionProof(it.proofId, MissionUser(it.user.userId, it.user.nickname), it.status) },
)
