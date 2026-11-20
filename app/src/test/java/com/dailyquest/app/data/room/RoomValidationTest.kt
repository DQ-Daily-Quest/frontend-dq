package com.dailyquest.app.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoomValidationTest {
    @Test fun validName() {
        assertNull(RoomValidation.nameError("우리들의 마을"))
        assertNull(RoomValidation.nameError("가나"))
        assertNull(RoomValidation.nameError("가".repeat(20)))
    }

    @Test fun nameLengthIsCheckedAfterTrim() {
        assertNotNull(RoomValidation.nameError("가"))
        assertNotNull(RoomValidation.nameError("  가  "))
        assertNull(RoomValidation.nameError("  가나  "))
        assertNotNull(RoomValidation.nameError("가".repeat(21)))
        assertNull(RoomValidation.nameError(" " + "가".repeat(20) + " "))
    }

    @Test fun blankNameIsRejected() {
        assertEquals("방 이름을 입력해 주세요.", RoomValidation.nameError("     "))
        assertEquals("방 이름을 입력해 주세요.", RoomValidation.nameError(""))
    }

    @Test fun descriptionIsOptionalAndLimitedTo100() {
        assertNull(RoomValidation.descriptionError(""))
        assertNull(RoomValidation.descriptionError("가".repeat(100)))
        assertNotNull(RoomValidation.descriptionError("가".repeat(101)))
        assertNull(RoomValidation.descriptionError(" " + "가".repeat(100) + " "))
    }

    @Test fun maxMembersRange() {
        assertNotNull(RoomValidation.maxMembersError("1"))
        assertNull(RoomValidation.maxMembersError("2"))
        assertNull(RoomValidation.maxMembersError("30"))
        assertNotNull(RoomValidation.maxMembersError("31"))
        assertNotNull(RoomValidation.maxMembersError(""))
        assertNotNull(RoomValidation.maxMembersError("abc"))
        assertEquals(10, RoomValidation.DEFAULT_MAX_MEMBERS)
    }

    @Test fun inviteCodeIsNormalizedAndValidated() {
        assertEquals("ABC123", RoomValidation.normalizeInviteCode("  abc123 "))
        assertNull(RoomValidation.inviteCodeError("ABC123"))
        assertEquals("초대코드를 입력해 주세요.", RoomValidation.inviteCodeError("   "))
        assertNotNull(RoomValidation.inviteCodeError("ABC12"))
        assertNotNull(RoomValidation.inviteCodeError("ABC1234"))
        assertNotNull(RoomValidation.inviteCodeError("ABC-12"))
    }
}
