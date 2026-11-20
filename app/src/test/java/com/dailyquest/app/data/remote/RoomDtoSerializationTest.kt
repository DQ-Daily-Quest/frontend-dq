package com.dailyquest.app.data.remote

import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.remote.dto.TransferOwnerRequest
import com.dailyquest.app.data.remote.dto.UpdateRoomRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class RoomDtoSerializationTest {
    private val json = NetworkModule.json

    private val summary = """{"roomId":9007199254740993,"name":"방","description":null,"isPublic":true,"memberCount":3,"maxMembers":10,"thumbnailUrl":null,"isHot":true,"joined":false}"""

    @Test fun summaryParsesBooleanKeysLongIdAndNulls() {
        val r = json.decodeFromString(RoomSummaryDto.serializer(), summary)
        assertEquals(9007199254740993L, r.roomId)
        assertTrue(r.isPublic); assertTrue(r.isHot); assertFalse(r.joined)
        assertNull(r.description); assertNull(r.thumbnailUrl)
        assertNull(r.todayMissionDone); assertNull(r.pendingProofCount)
    }

    @Test fun summaryAcceptsOpenApiStyleKeys() {
        val r = json.decodeFromString(RoomSummaryDto.serializer(), """{"roomId":1,"name":"n","public":false,"hot":true}""")
        assertFalse(r.isPublic); assertTrue(r.isHot)
    }

    @Test fun mineSummaryHasMissionFields() {
        val r = json.decodeFromString(
            RoomSummaryDto.serializer(),
            """{"roomId":1,"name":"n","isPublic":false,"memberCount":1,"maxMembers":2,"isHot":false,"joined":true,"todayMissionDone":true,"pendingProofCount":4}""",
        )
        assertEquals(true, r.todayMissionDone); assertEquals(4L, r.pendingProofCount)
    }

    @Test fun pageHasOnlyServerFields() {
        val p = json.decodeFromString(PageDto.serializer(RoomSummaryDto.serializer()), """{"content":[$summary],"page":1,"size":20,"totalElements":41,"totalPages":3}""")
        assertEquals(1, p.content.size); assertEquals(1, p.page); assertEquals(3, p.totalPages); assertEquals(41L, p.totalElements)
        val keys = json.encodeToString(PageDto.serializer(RoomSummaryDto.serializer()), p)
        assertFalse(keys.contains("hasNext"))
    }

    @Test fun detailForMemberAndNonMember() {
        val member = json.decodeFromString(
            RoomDetailDto.serializer(),
            """{"roomId":2,"name":"n","description":"d","isPublic":false,"inviteCode":"ABC234","owner":{"userId":5,"nickname":"o"},"memberCount":2,"maxMembers":5,"myRole":"MEMBER","todayMission":{"missionId":7,"content":"c"},"createdAt":"2025-01-01T00:00:00Z"}""",
        )
        assertEquals("ABC234", member.inviteCode); assertEquals(5L, member.owner.userId); assertEquals(7L, member.todayMission?.missionId)
        val outsider = json.decodeFromString(
            RoomDetailDto.serializer(),
            """{"roomId":3,"name":"n","description":null,"isPublic":true,"inviteCode":null,"owner":{"userId":5,"nickname":"o"},"memberCount":2,"maxMembers":5,"myRole":null,"todayMission":null,"createdAt":"2025-01-01T00:00:00Z"}""",
        )
        assertNull(outsider.myRole); assertNull(outsider.inviteCode); assertNull(outsider.todayMission)
    }

    @Test fun memberParsesAvatarWithNullSlots() {
        val m = json.decodeFromString(
            RoomMemberDto.serializer(),
            """{"userId":1,"nickname":"n","role":"OWNER","avatar":{"hair":3,"eyes":null,"skin":1,"top":null,"bottom":null,"shoes":null,"hat":null},"todayMissionDone":false,"joinedAt":"2025-01-01T00:00:00Z"}""",
        )
        assertEquals(3L, m.avatar.hair); assertNull(m.avatar.eyes); assertEquals("OWNER", m.role)
    }

    @Test fun createRequestUsesIsPublicAndAlwaysSendsMaxMembers() {
        val o = json.parseToJsonElement(json.encodeToString(CreateRoomRequest.serializer(), CreateRoomRequest("방", null, false, 10))).jsonObject
        assertEquals(setOf("name", "isPublic", "maxMembers"), o.keys.let { it - "description" })
        assertFalse(o["isPublic"]!!.jsonPrimitive.boolean)
        assertEquals("10", o["maxMembers"]!!.jsonPrimitive.content)
        assertFalse(o.containsKey("public"))
    }

    @Test fun createResponseParses() {
        val r = json.decodeFromString(CreateRoomResponse.serializer(), """{"roomId":4,"name":"n","isPublic":false,"inviteCode":"ABC234","ownerId":9}""")
        assertEquals("ABC234", r.inviteCode); assertEquals(9L, r.ownerId); assertFalse(r.isPublic)
    }

    @Test fun updateRequestSendsOnlyChangedFields() {
        fun enc(r: UpdateRoomRequest): JsonObject = json.parseToJsonElement(json.encodeToString(UpdateRoomRequest.serializer(), r)).jsonObject
        assertEquals(setOf("name"), enc(UpdateRoomRequest(name = "새 이름")).keys)
        assertEquals(setOf("isPublic"), enc(UpdateRoomRequest(isPublic = true)).keys)
        assertEquals(setOf("description"), enc(UpdateRoomRequest(description = "")).keys)
        assertTrue(enc(UpdateRoomRequest()).isEmpty())
    }

    @Test fun transferRequestUsesLongUserId() {
        assertEquals("""{"userId":42}""", json.encodeToString(TransferOwnerRequest.serializer(), TransferOwnerRequest(42)))
    }
}
