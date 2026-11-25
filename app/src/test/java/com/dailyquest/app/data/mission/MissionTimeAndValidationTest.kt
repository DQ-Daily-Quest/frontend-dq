package com.dailyquest.app.data.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionTimeAndValidationTest {

    @Test fun parsesInstantWithAndWithoutFraction() {
        assertEquals(0L, MissionTime.parse("1970-01-01T00:00:00Z"))
        assertEquals(1_700_000_000_123L, MissionTime.parse("2023-11-14T22:13:20.123456Z"))
        assertEquals(MissionTime.parse("2025-04-17T00:00:00Z"), MissionTime.parse("2025-04-17T09:00:00+09:00"))
        assertNull(MissionTime.parse("not a date"))
        assertNull(MissionTime.parse(null))
    }

    @Test fun isoRoundTrips() {
        val ms = 1_700_000_000_000L
        assertEquals("2023-11-14T22:13:20Z", MissionTime.toIso(ms))
        assertEquals(ms, MissionTime.parse(MissionTime.toIso(ms)))
    }

    @Test fun labelsUseKst() {
        // 2025-04-16T16:30Z = KST 2025-04-17 01:30 (목요일)
        val ms = MissionTime.parse("2025-04-16T16:30:00Z")!!
        assertEquals("4월 17일 (목)", MissionTime.kstDateLabel(ms))
        assertEquals("4월 17일 01:30", MissionTime.kstDateTimeLabel(ms))
    }

    @Test fun remainingLabel() {
        assertEquals("마감", MissionTime.remainingLabel(1000, 1000))
        assertEquals("30분 남음", MissionTime.remainingLabel(30 * 60_000L, 0))
        assertEquals("5시간 남음", MissionTime.remainingLabel(5 * 3_600_000L + 1, 0))
        assertEquals("2일 남음", MissionTime.remainingLabel(2 * 86_400_000L + 1, 0))
    }

    @Test fun contentLimitsAreTrimmed() {
        assertNotNull(MissionValidation.contentError(""))
        assertNotNull(MissionValidation.contentError("   "))
        assertNotNull(MissionValidation.contentError(" a "))
        assertNull(MissionValidation.contentError("ab"))
        assertNull(MissionValidation.contentError("가".repeat(100)))
        assertNotNull(MissionValidation.contentError("가".repeat(101)))
    }

    @Test fun rewardRangeAndUnit() {
        assertNotNull(MissionValidation.rewardError(0))
        assertNotNull(MissionValidation.rewardError(5))
        assertNull(MissionValidation.rewardError(10))
        assertNotNull(MissionValidation.rewardError(15))
        assertNull(MissionValidation.rewardError(500))
        assertNotNull(MissionValidation.rewardError(510))
        assertEquals(10, MissionValidation.stepReward(10, -10))
        assertEquals(500, MissionValidation.stepReward(500, 10))
        assertEquals(60, MissionValidation.stepReward(50, 10))
    }

    @Test fun dueIsLimitedToThirtyDays() {
        val day = 86_400_000L
        assertNull(MissionValidation.dueError(30 * day, 0))
        assertNotNull(MissionValidation.dueError(30 * day + 1, 0))
        assertNull(MissionValidation.dueError(7 * day, 0))
    }

    @Test fun whitespaceOnlyContentIsRefused() {
        assertNotNull(MissionValidation.contentError("   \t\n "))
        assertNotNull(MissionValidation.contentError(" a "))
        assertNull(MissionValidation.contentError("  ab  "))
        assertNotNull(com.dailyquest.app.data.room.RoomValidation.nameError("   "))
    }

    @Test fun dueMustBeFutureAndSelfAssigneeIsRefused() {
        assertNotNull(MissionValidation.dueError(100, 100))
        assertNull(MissionValidation.dueError(101, 100))
        assertNotNull(MissionValidation.assigneeError(7, 7))
        assertNull(MissionValidation.assigneeError(8, 7))
        assertNull(MissionValidation.assigneeError(null, 7))
    }

    private fun mission(
        type: MissionType = MissionType.MEMBER, createdBy: Long? = 2, assignee: Long? = null,
        proof: String? = null, due: Long? = 1000,
    ) = Mission(
        1, 10, "방", type, "내용", 50, 0,
        createdBy?.let { MissionUser(it, "c") }, assignee?.let { MissionUser(it, "a") },
        null, due, "", proof, 0,
    )

    @Test fun stateIsDerivedOnlyFromServerFields() {
        val me = 1L
        assertEquals(MissionState.DOABLE, mission().state(me, 0))
        assertEquals(MissionState.EXPIRED, mission().state(me, 1000))
        assertEquals(MissionState.PENDING_REVIEW, mission(proof = "PENDING").state(me, 0))
        assertEquals(MissionState.DONE, mission(proof = "APPROVED").state(me, 5000))
        assertEquals(MissionState.PROOF_OTHER, mission(proof = "REJECTED").state(me, 0))
        assertEquals(MissionState.NOT_APPLICABLE, mission(createdBy = me).state(me, 0))
        assertEquals(MissionState.NOT_APPLICABLE, mission(assignee = 9).state(me, 0))
        assertEquals(MissionState.DOABLE, mission(assignee = me).state(me, 0))
        assertEquals(MissionState.DOABLE, mission(type = MissionType.APP, createdBy = null).state(me, 0))
    }

    @Test fun friendMissionsExcludeMineAndApp() {
        assertTrue(mission(createdBy = 2).isFromFriend(1))
        assertTrue(!mission(createdBy = 1).isFromFriend(1))
        assertTrue(!mission(type = MissionType.APP, createdBy = null).isFromFriend(1))
    }
}
