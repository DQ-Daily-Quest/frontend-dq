
package com.dailyquest.app.data.mock

import androidx.compose.runtime.mutableStateListOf
import com.dailyquest.app.data.model.Room

object MockRoomData {

    // 전체 방 데이터
    private val rooms = mutableStateListOf(
        Room(1L, "우리들의 아지트", 4L, false, joined = true),
        Room(2L, "맛집 탐험대", 3L, false, joined = true),
        Room(3L, "하늘 보는 사람들", 4L, false, joined = true),
        Room(4L, "운동하는 우리", 3L, false, joined = true),

        Room(5L, "새벽 러닝클럽", 6L, true, isHot = true),
        Room(6L, "고양이 집사들", 4L, true),
        Room(7L, "필름 사진관", 5L, true),
        Room(8L, "오늘도 베이킹", 3L, true)
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

        return newRoom
    }

    // 공개방 참여
    fun joinRoom(roomId: Long): Boolean {

        val index = rooms.indexOfFirst {
            it.roomId == roomId
        }

        // 존재하지 않는 방
        if (index == -1) return false

        val room = rooms[index]

        // 이미 참여한 방
        if (room.joined) return true

        // 비공개방은 직접 참여 불가능
        if (!room.isPublic) return false

        // 최대 인원 초과 방지
        if (room.memberCount >= room.maxMembers.toLong()) {
            return false
        }

        // 참여 상태 및 인원수 변경
        rooms[index] = room.copy(
            joined = true,
            memberCount = room.memberCount + 1L
        )

        return true
    }
}
