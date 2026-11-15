
package com.dailyquest.app.data.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import com.dailyquest.app.data.model.Room

object MockRoomData {

    // Mock 전용 상태이며 백엔드 DTO에 포함하지 않는다.
    var focusedRoomId: Long? by mutableStateOf(null)
        private set
    private val createdRoomIds = mutableStateListOf<Long>()
    fun isCreatedRoom(roomId: Long) = roomId in createdRoomIds

    // 전체 방 데이터
    private val rooms = mutableStateListOf(
        Room(1L, "우리들의 아지트", 4L, false, joined = true),
        Room(2L, "맛집 탐험대", 3L, false, joined = true),
        Room(3L, "하늘 보는 사람들", 4L, false, joined = true),
        Room(4L, "운동하는 우리", 3L, false, joined = true),

        Room(5L, "새벽 러닝클럽", 6L, true, isHot = true),
        Room(6L, "고양이 집사들", 4L, true),
        Room(7L, "필름 사진관", 5L, true),
        Room(8L, "오늘도 베이킹", 3L, true),
        Room(9L, "초대코드 테스트방", 1L, false, maxMembers = 2),
        Room(10L, "정원 초과 테스트방", 2L, true, maxMembers = 2)
    )

    // 내가 참여한 방 목록
    val myRooms: List<Room>
        get() = rooms.filter { it.joined }

    // 전체 공개방 목록
    val publicRooms: List<Room>
        get() = rooms.filter { it.isPublic }

    // 방 ID로 상세 정보 조회
    fun getRoomById(roomId: Long): Room? {
        return rooms.find { it.roomId == roomId }
    }

    // 새로운 방 생성
    fun createRoom(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
        maxMembers: Int = 10
    ): Room {

        val trimmedName = name.trim()
        val trimmedDescription = description?.trim()

        require(trimmedName.length in 2..20) {
            "방 이름은 2~20자여야 합니다."
        }

        require((trimmedDescription?.length ?: 0) <= 100) {
            "방 설명은 최대 100자까지 입력할 수 있습니다."
        }

        require(maxMembers in 2..30) {
            "최대 인원은 2~30명이어야 합니다."
        }

        val newRoomId =
            (rooms.maxOfOrNull { it.roomId } ?: 0L) + 1L

        val newRoom = Room(
            roomId = newRoomId,
            name = trimmedName,
            memberCount = 1L,
            isPublic = isPublic,
            description = trimmedDescription
                ?.takeIf { it.isNotBlank() },
            maxMembers = maxMembers,
            joined = true
        )

        rooms.add(newRoom)
        createdRoomIds.add(newRoomId)
        focusedRoomId = newRoomId

        return newRoom
    }

    sealed interface JoinResult {
        data class Success(val room: Room, val alreadyJoined: Boolean) : JoinResult
        data class Failure(val message: String) : JoinResult
    }

    // 실제 API 명세와 무관한 개발 테스트용 코드.
    fun inviteCodeFor(roomId: Long): String? =
        getRoomById(roomId)?.let { "DQ" + roomId.toString().padStart(4, '0') }

    fun joinByInviteCode(code: String): JoinResult {
        val normalized = code.trim().uppercase(java.util.Locale.ROOT)
        val room = rooms.find { inviteCodeFor(it.roomId) == normalized }
            ?: return JoinResult.Failure("유효하지 않은 초대코드입니다.")
        return join(room.roomId, allowPrivate = true)
    }

    // 기존 Boolean 인터페이스도 보존한다.
    fun joinRoom(roomId: Long): Boolean = joinPublicRoom(roomId) is JoinResult.Success

    fun joinPublicRoom(roomId: Long): JoinResult = join(roomId, allowPrivate = false)

    private fun join(roomId: Long, allowPrivate: Boolean): JoinResult {
        val index = rooms.indexOfFirst { it.roomId == roomId }
        if (index == -1) return JoinResult.Failure("방을 찾을 수 없습니다.")
        val room = rooms[index]
        if (room.joined) {
            focusedRoomId = roomId
            return JoinResult.Success(room, alreadyJoined = true)
        }
        if (!room.isPublic && !allowPrivate) return JoinResult.Failure("초대코드가 필요한 방입니다.")
        if (room.memberCount >= room.maxMembers.toLong()) return JoinResult.Failure("현재 정원이 가득 찼습니다.")
        val joinedRoom = room.copy(joined = true, memberCount = room.memberCount + 1L)
        rooms[index] = joinedRoom
        focusedRoomId = roomId
        return JoinResult.Success(joinedRoom, alreadyJoined = false)
    }
}
