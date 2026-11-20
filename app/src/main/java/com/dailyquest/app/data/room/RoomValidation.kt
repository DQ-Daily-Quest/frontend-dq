package com.dailyquest.app.data.room

/** 방 생성 입력 검증. 기준은 서버 CreateRoomRequest(name 2~20, description ≤100, maxMembers 2~30, 기본 10)다. */
object RoomValidation {
    const val NAME_MIN = 2
    const val NAME_MAX = 20
    const val DESCRIPTION_MAX = 100
    const val MEMBERS_MIN = 2
    const val MEMBERS_MAX = 30
    const val DEFAULT_MAX_MEMBERS = 10

    fun nameError(raw: String): String? {
        val n = raw.trim()
        return when {
            n.isEmpty() -> "방 이름을 입력해 주세요."
            n.length !in NAME_MIN..NAME_MAX -> "방 이름은 ${NAME_MIN}~${NAME_MAX}자로 입력해 주세요."
            else -> null
        }
    }

    fun descriptionError(raw: String): String? =
        if (raw.trim().length > DESCRIPTION_MAX) "방 설명은 최대 ${DESCRIPTION_MAX}자까지 입력할 수 있어요." else null

    /** 숫자가 아니거나 비어 있어도 같은 안내를 쓴다. */
    fun maxMembersError(raw: String): String? {
        val n = raw.trim().toIntOrNull()
        return if (n == null || n !in MEMBERS_MIN..MEMBERS_MAX) "최대 인원은 ${MEMBERS_MIN}~${MEMBERS_MAX}명으로 입력해 주세요." else null
    }

    const val INVITE_CODE_LENGTH = 6

    /** 서버는 trim·대문자 변환 후 6자리 코드로 조회한다. 클라이언트도 같은 정규화를 적용한다. */
    fun normalizeInviteCode(raw: String): String = raw.trim().uppercase()

    fun inviteCodeError(raw: String): String? {
        val c = normalizeInviteCode(raw)
        return when {
            c.isEmpty() -> "초대코드를 입력해 주세요."
            c.length != INVITE_CODE_LENGTH || !c.all { it in 'A'..'Z' || it in '0'..'9' } ->
                "초대코드는 영문·숫자 ${INVITE_CODE_LENGTH}자리예요."
            else -> null
        }
    }
}
