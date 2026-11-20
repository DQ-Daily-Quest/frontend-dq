package com.dailyquest.app.data.mission

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 미션 관련 서버 오류 코드를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object MissionErrorMessages {
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
