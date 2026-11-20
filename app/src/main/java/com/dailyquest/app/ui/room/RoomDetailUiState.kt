package com.dailyquest.app.ui.room

import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto

enum class RoomDetailFailure { NOT_FOUND, FORBIDDEN, OTHER }

const val ROLE_OWNER = "OWNER"
const val ROLE_MEMBER = "MEMBER"

/**
 * 방 상세 화면 상태. 권한은 서버가 준 [RoomDetailDto.myRole]만 기준으로 한다(목록의 joined 값은 쓰지 않는다).
 * 상세와 멤버는 따로 실패할 수 있으므로 멤버 오류는 [membersError]로 분리한다.
 */
data class RoomDetailUiState(
    val roomId: Long,
    val detail: RoomDetailDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val failure: RoomDetailFailure? = null,
    val error: String? = null,
    val members: List<RoomMemberDto> = emptyList(),
    val membersLoading: Boolean = false,
    val membersLoaded: Boolean = false,
    val membersError: String? = null,
) {
    val myRole: String? get() = detail?.myRole
    val isOwner: Boolean get() = myRole == ROLE_OWNER
    val isMember: Boolean get() = myRole == ROLE_OWNER || myRole == ROLE_MEMBER

    /** 비공개방이고 멤버이며 서버가 코드를 내려준 경우에만 표시한다. */
    val visibleInviteCode: String?
        get() = detail?.takeIf { isMember && !it.isPublic }?.inviteCode?.takeIf { it.isNotBlank() }
}
