package com.dailyquest.app.ui.feed

import com.dailyquest.app.data.proof.PendingProof

/** 멤버 얼굴 테두리(스토리 링, QA 디자인 2-1단계). 최근 24시간 인증이 없으면 테두리 없음. */
enum class StoryRing {
    /** 내가 아직 안 본 인증이 있다(분홍). */
    NEW,
    /** 인증이 있지만 모두 봤다(연한 회색). */
    SEEN,
}

/**
 * 방 피드(최근 24시간 인증)로 멤버별 테두리를 정한다. 서버의 `viewed`(내가 상세를 연 적이 있는지)를 쓴다.
 * 피드는 한 번에 일정 개수씩 받으므로, 아직 받지 않은 뒤쪽 인증은 계산에 들어가지 않는다.
 */
fun storyRings(items: List<PendingProof>): Map<Long, StoryRing> =
    items.groupBy { it.user.userId }.mapValues { (_, proofs) -> if (proofs.any { !it.viewed }) StoryRing.NEW else StoryRing.SEEN }

/** [userId]가 올린 인증(피드 순서 = 최신순). */
fun proofsOf(items: List<PendingProof>, userId: Long): List<PendingProof> = items.filter { it.user.userId == userId }

/** 멤버 얼굴을 누르면 바로 열 인증: 안 본 것 중 가장 최근 것, 다 봤으면 가장 최근 것. [proofs]는 최신순. 없으면 null. */
fun storyProofToOpen(proofs: List<PendingProof>): PendingProof? = proofs.firstOrNull { !it.viewed } ?: proofs.firstOrNull()
