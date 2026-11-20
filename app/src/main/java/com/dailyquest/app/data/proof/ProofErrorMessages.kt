package com.dailyquest.app.data.proof

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 인증·업로드·승인 관련 서버 오류 코드를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object ProofErrorMessages {
    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.MISSION_NOT_FOUND -> "존재하지 않거나 삭제된 미션이에요."
            ApiErrorCode.MISSION_EXPIRED -> "이미 마감된 미션이라 인증할 수 없어요."
            ApiErrorCode.PROOF_NOT_FOUND -> "존재하지 않거나 삭제된 인증이에요."
            ApiErrorCode.PROOF_ALREADY_SUBMITTED -> "이 미션에는 이미 인증을 제출했어요."
            ApiErrorCode.PROOF_ALREADY_FINALIZED -> "이미 승인 또는 거절이 확정된 인증이에요."
            ApiErrorCode.CREATOR_CANNOT_SUBMIT -> "내가 만든 미션에는 인증을 올릴 수 없어요."
            ApiErrorCode.NOT_MISSION_ASSIGNEE -> "이 미션의 대상 멤버만 인증할 수 있어요."
            ApiErrorCode.NOT_MISSION_APPROVER -> "멤버 미션은 미션을 만든 사람만 승인·거절할 수 있어요."
            ApiErrorCode.CANNOT_VOTE_OWN_PROOF -> "내 인증에는 투표할 수 없어요."
            ApiErrorCode.FILE_TOO_LARGE -> "파일 용량이 너무 커요. 사진은 10MB, 영상은 10MB 이하로 올려 주세요."
            ApiErrorCode.FILE_EMPTY -> "비어 있는 파일이에요. 다른 파일을 선택해 주세요."
            ApiErrorCode.UNSUPPORTED_MEDIA_TYPE -> "jpg, png, webp 사진 또는 mp4 영상만 올릴 수 있어요."
            ApiErrorCode.NOT_A_MEMBER, ApiErrorCode.FORBIDDEN -> "이 방의 멤버만 인증을 확인하거나 올릴 수 있어요."
            ApiErrorCode.VALIDATION_ERROR -> "입력한 내용을 다시 확인해 주세요. (인증 문구는 최대 300자)"
            else -> when (e.status) {
                403 -> "이 방의 멤버만 인증을 확인하거나 올릴 수 있어요."
                404 -> "요청한 정보를 찾을 수 없어요."
                413 -> "파일 용량이 너무 커요."
                else -> AuthErrorMessages.forApi(e)
            }
        }
    }
}
