package com.dailyquest.app.data.room

import com.dailyquest.app.data.auth.AuthErrorMessages
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException

/** 방 관련 서버 오류 코드를 한국어 안내로 바꾼다. 분기는 message가 아니라 code로 한다. */
object RoomErrorMessages {
    fun forApi(e: ApiException): String {
        if (e !is ApiException.Http) return AuthErrorMessages.forApi(e)
        return when (e.code) {
            ApiErrorCode.ROOM_NOT_FOUND -> "존재하지 않거나 삭제된 방이에요."
            ApiErrorCode.ROOM_NOT_PUBLIC -> "비공개 방은 초대코드로만 참여할 수 있어요."
            ApiErrorCode.ALREADY_JOINED -> "이미 참여 중인 방이에요."
            ApiErrorCode.ROOM_FULL -> "방 정원이 가득 찼어요."
            ApiErrorCode.INVALID_INVITE_CODE -> "유효하지 않은 초대코드예요. 코드를 다시 확인해 주세요."
            ApiErrorCode.OWNER_CANNOT_LEAVE -> "방장은 다른 멤버가 남아 있으면 나갈 수 없어요. 방장을 넘기거나 방을 삭제해 주세요."
            ApiErrorCode.MAX_MEMBERS_BELOW_CURRENT -> "최대 인원은 현재 멤버 수보다 적게 설정할 수 없어요."
            ApiErrorCode.ROOM_NOT_PRIVATE -> "공개 방에는 초대코드가 없어요."
            ApiErrorCode.CANNOT_KICK_SELF -> "자기 자신은 내보낼 수 없어요."
            ApiErrorCode.NOT_A_MEMBER -> "방 멤버가 아니에요."
            ApiErrorCode.ALREADY_OWNER -> "이미 방장이에요."
            ApiErrorCode.FORBIDDEN -> "이 작업을 수행할 권한이 없어요."
            ApiErrorCode.VALIDATION_ERROR -> "입력한 내용을 다시 확인해 주세요."
            else -> when {
                e.status == 403 -> "이 작업을 수행할 권한이 없어요."
                e.status == 404 -> "요청한 방 정보를 찾을 수 없어요."
                else -> AuthErrorMessages.forApi(e)
            }
        }
    }
}
