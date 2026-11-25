package com.dailyquest.app.data.mission

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 미션 관련 서버 오류 코드를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object MissionErrorMessages {
    /** 수정·삭제용. 권한(403)과 인증 상태(409)를 구분해 안내한다. */
    fun forManage(e: ApiException, deleting: Boolean): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        val verb = if (deleting) "삭제" else "수정"
        return when (e.code) {
            ApiErrorCode.MISSION_HAS_PROOFS -> "이미 인증이 제출됐거나 승인된 미션은 $verb 할 수 없어요."
            ApiErrorCode.APP_MISSION_IMMUTABLE -> "오늘의 미션은 $verb 할 수 없어요."
            ApiErrorCode.MISSION_NOT_FOUND -> "존재하지 않거나 이미 삭제된 미션이에요."
            ApiErrorCode.FORBIDDEN -> if (deleting) "미션을 만든 사람이나 방장만 삭제할 수 있어요." else "미션을 만든 사람만 수정할 수 있어요."
            ApiErrorCode.VALIDATION_ERROR -> "입력한 내용을 다시 확인해 주세요. (내용 2~100자, 마감은 미래 30일 이내)"
            else -> if (e.status == 429 || e.isRateLimited) AuthErrorMessages.forApi(e) else forApi(e)
        }
    }

    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.MISSION_NOT_FOUND -> "존재하지 않거나 삭제된 미션이에요."
            ApiErrorCode.ROOM_NOT_FOUND -> "존재하지 않거나 삭제된 방이에요."
            ApiErrorCode.NOT_A_MEMBER -> "선택한 대상이 이 방의 멤버가 아니에요."
            ApiErrorCode.FORBIDDEN -> "이 방의 멤버만 미션을 보거나 보낼 수 있어요."
            ApiErrorCode.MISSION_EXPIRED -> "이미 마감된 미션이에요."
            ApiErrorCode.NO_ACTIVE_TEMPLATE -> "오늘의 미션이 아직 준비되지 않았어요. 잠시 후 다시 확인해 주세요."
            ApiErrorCode.VALIDATION_ERROR -> "입력한 내용을 다시 확인해 주세요. (내용 2~100자, 보상 10~500코인, 마감은 미래, 본인 지정 불가)"
            else -> when (e.status) {
                403 -> "이 방의 멤버만 미션을 보거나 보낼 수 있어요."
                404 -> "요청한 미션 정보를 찾을 수 없어요."
                else -> AuthErrorMessages.forApi(e)
            }
        }
    }
}
