package com.dailyquest.app.ui.room

import com.dailyquest.app.data.room.RoomValidation

/** 서버가 발급한 값만 담는다. */
data class CreatedRoom(val roomId: Long, val name: String, val isPublic: Boolean, val inviteCode: String?)

data class CreateRoomUiState(
    val name: String = "",
    val description: String = "",
    val isPublic: Boolean = false,
    val maxMembers: String = RoomValidation.DEFAULT_MAX_MEMBERS.toString(),
    val nameError: String? = null,
    val descriptionError: String? = null,
    val maxMembersError: String? = null,
    val submitting: Boolean = false,
    /** 서버가 명확히 거절한 오류(검증·권한 등). */
    val serverError: String? = null,
    /** 응답을 받지 못해 생성 여부를 알 수 없는 상태. 자동 재시도하지 않는다. */
    val outcomeUnknown: Boolean = false,
    /** 성공 이벤트. 화면이 이동한 뒤 [CreateRoomViewModel.consumeCreated]로 비워 한 번만 처리한다. */
    val created: CreatedRoom? = null,
)
