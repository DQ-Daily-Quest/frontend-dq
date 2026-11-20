package com.dailyquest.app.data.room

import com.dailyquest.app.data.model.Room
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
import com.dailyquest.app.data.remote.dto.JoinByCodeRequest
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.remote.dto.TransferOwnerRequest
import com.dailyquest.app.data.remote.dto.UpdateRoomRequest

/** 서버가 정의한 정렬 값. 서버는 createdAt 외의 값을 모두 인기순으로 처리한다. */
enum class RoomSort(val param: String) {
    POPULARITY("popularity,desc"),
    NEWEST("createdAt,desc"),
}

/** 서버 페이지 응답. 서버가 hasNext를 주지 않으므로 포함하지 않는다. */
data class RoomPage(
    val rooms: List<Room>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

/**
 * 방 관리 API 호출 계층. 전역 사용자 상태를 보관하지 않는다.
 * 상태 변경 요청은 응답을 받지 못해도 서버에서는 성공했을 수 있으므로 자동 재시도하지 않는다.
 * 인증과 토큰 갱신은 [DqApi]의 OkHttp 인터셉터가 처리한다.
 */
class RoomRepository(private val api: DqApi) {

    suspend fun listPublicRooms(
        keyword: String? = null,
        sort: RoomSort = RoomSort.POPULARITY,
        page: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<RoomPage> = apiCall {
        val r = api.listPublicRooms(keyword?.trim()?.takeIf { it.isNotEmpty() }, sort.param, page, size)
        RoomPage(r.content.map { it.toRoom() }, r.page, r.size, r.totalElements, r.totalPages)
    }

    suspend fun listMyRooms(): ApiResult<List<Room>> = apiCall { api.listMyRooms().map { it.toRoom() } }

    suspend fun getRoom(roomId: Long): ApiResult<RoomDetailDto> = apiCall { api.getRoom(roomId) }

    suspend fun listMembers(roomId: Long): ApiResult<List<RoomMemberDto>> = apiCall { api.listRoomMembers(roomId) }

    suspend fun createRoom(name: String, description: String?, isPublic: Boolean, maxMembers: Int): ApiResult<CreateRoomResponse> =
        apiCall { api.createRoom(CreateRoomRequest(name.trim(), description?.trim()?.takeIf { it.isNotEmpty() }, isPublic, maxMembers)) }

    suspend fun joinPublicRoom(roomId: Long): ApiResult<RoomDetailDto> = apiCall { api.joinPublicRoom(roomId) }

    /** 서버가 trim·대문자 변환을 하므로 공백만 제거해 보낸다. */
    suspend fun joinByInviteCode(code: String): ApiResult<RoomDetailDto> =
        apiCall { api.joinRoomByCode(JoinByCodeRequest(code.trim())) }

    /** 보낸 필드만 수정된다. 설명을 지우려면 빈 문자열을 넘긴다. */
    suspend fun updateRoom(
        roomId: Long,
        name: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
        maxMembers: Int? = null,
    ): ApiResult<RoomDetailDto> = apiCall { api.updateRoom(roomId, UpdateRoomRequest(name?.trim(), description?.trim(), isPublic, maxMembers)) }

    suspend fun deleteRoom(roomId: Long): ApiResult<Unit> = apiCall { api.deleteRoom(roomId) }

    suspend fun leaveRoom(roomId: Long): ApiResult<Unit> = apiCall { api.leaveRoom(roomId) }

    suspend fun regenerateInviteCode(roomId: Long): ApiResult<String> = apiCall { api.regenerateInviteCode(roomId).inviteCode }

    suspend fun kickMember(roomId: Long, userId: Long): ApiResult<Unit> = apiCall { api.kickMember(roomId, userId) }

    suspend fun transferOwner(roomId: Long, userId: Long): ApiResult<RoomDetailDto> =
        apiCall { api.transferOwner(roomId, TransferOwnerRequest(userId)) }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

internal fun RoomSummaryDto.toRoom() = Room(
    roomId = roomId,
    name = name,
    memberCount = memberCount,
    isPublic = isPublic,
    description = description,
    maxMembers = maxMembers,
    thumbnailUrl = thumbnailUrl,
    isHot = isHot,
    joined = joined,
    todayMissionDone = todayMissionDone,
    pendingProofCount = pendingProofCount,
)
