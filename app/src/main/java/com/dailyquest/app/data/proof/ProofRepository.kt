package com.dailyquest.app.data.proof

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.SubmitProofRequest

/**
 * 인증 API 호출 계층. 전역 사용자 상태를 보관하지 않는다.
 * 제출·투표는 멱등성 키가 없어 응답을 잃어도 서버에서 처리됐을 수 있으므로 자동 재시도하지 않는다(DqApi의 No-Retry 헤더).
 */
class ProofRepository(private val api: DqApi) {

    suspend fun submit(missionId: Long, media: UploadedMedia, text: String?): ApiResult<Proof> = apiCall {
        api.submitProof(
            missionId,
            SubmitProofRequest(
                mediaType = media.mediaType.name,
                mediaUrl = media.url,
                thumbnailUrl = media.thumbnailUrl,
                text = text?.let(ProofValidation::normalizeText),
            ),
        ).toProof()
    }

    suspend fun detail(proofId: Long): ApiResult<ProofDetail> = apiCall { api.getProof(proofId).toDetail() }

    suspend fun pending(roomId: Long, page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): ApiResult<PendingPage> = apiCall {
        val r = api.listPendingProofs(roomId, page, size)
        PendingPage(r.content.map { it.toPending() }, r.page, r.totalPages, r.totalElements)
    }

    /** 방의 최근 24시간 인증 피드(서버 기본 기간). 멤버가 아니면 403. */
    suspend fun feed(roomId: Long, page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): ApiResult<PendingPage> = apiCall {
        val r = api.listFeed(roomId, page, size)
        PendingPage(r.content.map { it.toPending() }, r.page, r.totalPages, r.totalElements)
    }

    suspend fun vote(proofId: Long, choice: VoteChoice): ApiResult<VoteResult> = apiCall {
        when (choice) {
            VoteChoice.APPROVE -> api.approveProof(proofId)
            VoteChoice.REJECT -> api.rejectProof(proofId)
        }.toResult()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
