package com.dailyquest.app.data.mission

import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MyRoomMissionsDto
import com.dailyquest.app.data.remote.dto.PageDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionDtoTest {
    private val json = NetworkModule.json

    private val app = """{"missionId":1,"type":"APP","content":"하늘 찍기","reward":{"type":"COIN","amount":50,"streakBonus":0},
        "createdBy":null,"assignee":null,"date":"2025-04-17","dueAt":"2025-04-17T14:59:59Z","myProof":null,"proofCount":0,"createdAt":"2025-04-16T15:00:00Z","extra":1}"""
    private val member = """{"missionId":2,"type":"MEMBER","content":"산책","reward":{"type":"COIN","amount":100,"streakBonus":10},
        "createdBy":{"userId":2,"nickname":"지수"},"assignee":{"userId":1,"nickname":"나"},"date":null,"dueAt":"2025-04-20T00:00:00Z",
        "myProof":{"proofId":9,"status":"PENDING"},"proofCount":3,"createdAt":"2025-04-17T00:00:00Z"}"""

    @Test fun decodesAppAndMemberMissions() {
        val a = json.decodeFromString<MissionDto>(app).toMission(10, "방")
        assertEquals(MissionType.APP, a.type)
        assertNull(a.createdBy); assertNull(a.assignee); assertNull(a.myProofStatus)
        assertEquals(50, a.rewardCoins); assertEquals("2025-04-17", a.date)
        val m = json.decodeFromString<MissionDto>(member).toMission(10)
        assertEquals(MissionType.MEMBER, m.type)
        assertEquals("지수", m.createdBy?.nickname); assertEquals(1L, m.assignee?.userId)
        assertEquals("PENDING", m.myProofStatus); assertEquals(10, m.streakBonus); assertEquals(3L, m.proofCount)
        assertEquals(MissionTime.parse("2025-04-20T00:00:00Z"), m.dueAtMs)
    }

    @Test fun decodesPageGroupsAndDetail() {
        val page = json.decodeFromString<PageDto<MissionDto>>("""{"content":[$app,$member],"page":0,"size":20,"totalElements":2,"totalPages":1}""")
        assertEquals(2, page.content.size)
        val groups = json.decodeFromString<List<MyRoomMissionsDto>>("""[{"room":{"roomId":10,"name":"방"},"missions":[$app]},{"room":{"roomId":11,"name":"빈방"},"missions":[]}]""")
        assertEquals(listOf(10L, 11L), groups.map { it.room.roomId })
        val d = json.decodeFromString<MissionDetailDto>(
            member.trimEnd().removeSuffix("}") + ""","proofs":[{"proofId":9,"user":{"userId":1,"nickname":"나"},"status":"PENDING","thumbnailUrl":null,"createdAt":"2025-04-17T01:00:00Z"}]}""",
        ).toDetail(10)
        assertEquals(1, d.proofs.size); assertEquals("나", d.proofs[0].user.nickname)
    }

    @Test fun requestOmitsNullFields() {
        val whole = json.encodeToString(CreateMissionRequest("산책", 50))
        assertFalse(whole.contains("assigneeUserId")); assertFalse(whole.contains("dueAt"))
        val one = json.encodeToString(CreateMissionRequest("산책", 50, "2025-04-20T00:00:00Z", 7))
        assertTrue(one.contains("\"assigneeUserId\":7")); assertTrue(one.contains("\"rewardCoins\":50"))
    }
}
