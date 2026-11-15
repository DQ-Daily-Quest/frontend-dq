package com.dailyquest.app.data.mock

import org.junit.Assert.*
import org.junit.Test

class MockRoomDataTest {
    @Test
    fun createValidatesBoundariesWithoutAddingInvalidRooms() {
        val before = MockRoomData.myRooms.size
        listOf("", " ", "가", "가".repeat(21)).forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { MockRoomData.createRoom(name) }
        }
        listOf(1, 31).forEach { capacity ->
            assertThrows(IllegalArgumentException::class.java) { MockRoomData.createRoom("테스트", maxMembers = capacity) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MockRoomData.createRoom("테스트", description = "가".repeat(101))
        }
        assertEquals(before, MockRoomData.myRooms.size)
        val min = MockRoomData.createRoom(" 가나 ", "   ", maxMembers = 2)
        assertEquals("가나", min.name)
        assertNull(min.description)
        val max = MockRoomData.createRoom("가".repeat(20), "가".repeat(100), maxMembers = 30)
        assertEquals(30, max.maxMembers)
        assertEquals(100, max.description!!.length)
    }

    @Test
    fun createdRoomsAreJoinedAndAppearInCorrectLists() {
        val publicRoom = MockRoomData.createRoom("공개 테스트", isPublic = true)
        val privateRoom = MockRoomData.createRoom("비공개 테스트")
        assertTrue(MockRoomData.publicRooms.any { it.roomId == publicRoom.roomId })
        assertFalse(MockRoomData.publicRooms.any { it.roomId == privateRoom.roomId })
        listOf(publicRoom, privateRoom).forEach { room ->
            assertEquals(1L, room.memberCount)
            assertTrue(room.joined)
            assertTrue(MockRoomData.myRooms.any { it.roomId == room.roomId })
            assertTrue(MockRoomData.isCreatedRoom(room.roomId))
        }
        assertEquals(privateRoom.roomId, MockRoomData.focusedRoomId)
    }

    @Test
    fun publicJoinUpdatesHomeAndRepeatedJoinDoesNotIncreaseCount() {
        val initial = MockRoomData.getRoomById(5L)!!
        val result = MockRoomData.joinPublicRoom(5L) as MockRoomData.JoinResult.Success
        assertFalse(result.alreadyJoined)
        assertEquals(initial.memberCount + 1, result.room.memberCount)
        assertTrue(MockRoomData.myRooms.any { it.roomId == 5L })
        assertTrue(MockRoomData.joinRoom(5L))
        val repeated = MockRoomData.joinPublicRoom(5L) as MockRoomData.JoinResult.Success
        assertTrue(repeated.alreadyJoined)
        assertEquals(result.room.memberCount, repeated.room.memberCount)
        assertEquals(5L, MockRoomData.focusedRoomId)
    }

    @Test
    fun inviteJoinAllowsPrivateRoomAndIsIdempotentEvenWhenNowFull() {
        assertTrue(MockRoomData.joinPublicRoom(9L) is MockRoomData.JoinResult.Failure)
        val result = MockRoomData.joinByInviteCode(" dq0009 ") as MockRoomData.JoinResult.Success
        assertEquals(2L, result.room.memberCount)
        assertFalse(result.room.isPublic)
        assertFalse(result.alreadyJoined)
        val before = MockRoomData.myRooms
        repeat(3) {
            val repeated = MockRoomData.joinByInviteCode("DQ0009") as MockRoomData.JoinResult.Success
            assertTrue(repeated.alreadyJoined)
            assertEquals(result.room, repeated.room)
            assertEquals(2L, repeated.room.memberCount)
            assertTrue(repeated.room.joined)
            assertEquals(before, MockRoomData.myRooms)
            assertEquals(1, MockRoomData.myRooms.count { it.roomId == 9L })
        }
    }

    @Test
    fun invalidCodesMissingRoomsAndFullRoomsDoNotChangeMembership() {
        val before = MockRoomData.myRooms.size
        listOf("", " ", "INVALID", "DQ999999", "DQ0010").forEach {
            assertTrue(MockRoomData.joinByInviteCode(it) is MockRoomData.JoinResult.Failure)
        }
        assertTrue(MockRoomData.joinPublicRoom(10L) is MockRoomData.JoinResult.Failure)
        assertFalse(MockRoomData.joinRoom(Long.MAX_VALUE))
        assertEquals(before, MockRoomData.myRooms.size)
        assertEquals(2L, MockRoomData.getRoomById(10L)!!.memberCount)
        assertFalse(MockRoomData.getRoomById(10L)!!.joined)
    }

    @Test
    fun joiningCreatedRoomByCodeDoesNotAddAnotherMember() {
        val room = MockRoomData.createRoom("코드 테스트")
        val result = MockRoomData.joinByInviteCode(MockRoomData.inviteCodeFor(room.roomId)!!) as MockRoomData.JoinResult.Success
        assertTrue(result.alreadyJoined)
        assertEquals(1L, result.room.memberCount)
        assertNull(MockRoomData.inviteCodeFor(-1L))
    }
}
