package com.dailyquest.app.data.room

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** MockWebServer만 사용한다. 실제 서버에는 어떤 요청도 보내지 않는다. */
class RoomRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: RoomRepository
    private lateinit var tokens: SessionTokens

    private val summary = """{"roomId":1,"name":"방","description":null,"isPublic":true,"memberCount":2,"maxMembers":10,"thumbnailUrl":null,"isHot":true,"joined":false}"""
    private val detail = """{"roomId":1,"name":"방","description":null,"isPublic":false,"inviteCode":"ABC234","owner":{"userId":1,"nickname":"o"},"memberCount":2,"maxMembers":10,"myRole":"MEMBER","todayMission":null,"createdAt":"2025-01-01T00:00:00Z"}"""

    private fun ok(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun err(code: String, status: Int) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody("""{"code":"$code","message":"m"}""")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = SessionTokens(InMemoryTokenStore(TokenPair("ACCESS", "REFRESH")))
        val session = SessionManager(tokens, emptyList())
        val api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
        repo = RoomRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value
    private fun <T> ApiResult<T>.http(): ApiException.Http = ((this as ApiResult.Failure).error as ApiException.Http)

    @Test fun listPublicSendsSearchSortAndPagingAndMapsRooms() = runBlocking {
        server.enqueue(ok("""{"content":[$summary],"page":2,"size":5,"totalElements":11,"totalPages":3}"""))
        val page = repo.listPublicRooms(" 러닝 ", RoomSort.NEWEST, page = 2, size = 5).value()
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/v1/rooms", req.requestUrl!!.encodedPath)
        assertEquals("러닝", req.requestUrl!!.queryParameter("keyword"))
        assertEquals("createdAt,desc", req.requestUrl!!.queryParameter("sort"))
        assertEquals("2", req.requestUrl!!.queryParameter("page"))
        assertEquals("5", req.requestUrl!!.queryParameter("size"))
        assertEquals("Bearer ACCESS", req.getHeader("Authorization"))
        assertEquals(3, page.totalPages); assertEquals(11L, page.totalElements)
        val room = page.rooms.single()
        assertTrue(room.isPublic); assertTrue(room.isHot); assertFalse(room.joined); assertNull(room.description)
    }

    @Test fun blankKeywordIsOmittedAndDefaultSortIsPopularity() = runBlocking {
        server.enqueue(ok("""{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}"""))
        assertTrue(repo.listPublicRooms("  ").value().rooms.isEmpty())
        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("keyword"))
        assertEquals("popularity,desc", url.queryParameter("sort"))
        assertEquals("0", url.queryParameter("page"))
    }

    @Test fun myRoomsIsPlainArrayWithMissionFields() = runBlocking {
        server.enqueue(ok("""[{"roomId":1,"name":"a","isPublic":false,"memberCount":1,"maxMembers":2,"isHot":false,"joined":true,"todayMissionDone":false,"pendingProofCount":2}]"""))
        val r = repo.listMyRooms().value().single()
        assertEquals("/api/v1/rooms/mine", server.takeRequest().path)
        assertEquals(false, r.todayMissionDone); assertEquals(2L, r.pendingProofCount); assertTrue(r.joined)
    }

    @Test fun detailAndMembersPaths() = runBlocking {
        server.enqueue(ok(detail))
        assertEquals("ABC234", repo.getRoom(1).value().inviteCode)
        assertEquals("/api/v1/rooms/1", server.takeRequest().path)
        server.enqueue(ok("""[{"userId":1,"nickname":"o","role":"OWNER","avatar":{},"todayMissionDone":true,"joinedAt":"2025-01-01T00:00:00Z"}]"""))
        assertEquals("OWNER", repo.listMembers(1).value().single().role)
        assertEquals("/api/v1/rooms/1/members", server.takeRequest().path)
    }

    @Test fun createSends201BodyWithIsPublic() = runBlocking {
        server.enqueue(ok("""{"roomId":7,"name":"방","isPublic":false,"inviteCode":"ABC234","ownerId":1}""").setResponseCode(201))
        val r = repo.createRoom(" 방 ", "  ", false, 10).value()
        assertEquals(7L, r.roomId); assertEquals("ABC234", r.inviteCode)
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/api/v1/rooms", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"isPublic\":false")); assertTrue(body.contains("\"name\":\"방\"")); assertTrue(body.contains("\"maxMembers\":10"))
        assertFalse(body.contains("description"))
    }

    @Test fun joinPublicAndByCode() = runBlocking {
        server.enqueue(ok(detail))
        repo.joinPublicRoom(1).value()
        val a = server.takeRequest()
        assertEquals("POST", a.method); assertEquals("/api/v1/rooms/1/join", a.path); assertEquals(0L, a.bodySize)
        server.enqueue(ok(detail))
        repo.joinByInviteCode(" abc234 ").value()
        val b = server.takeRequest()
        assertEquals("/api/v1/rooms/join", b.path); assertEquals("""{"inviteCode":"abc234"}""", b.body.readUtf8())
    }

    @Test fun updateSendsPatchWithOnlyChangedFields() = runBlocking {
        server.enqueue(ok(detail))
        repo.updateRoom(1, name = "새", maxMembers = 5).value()
        val req = server.takeRequest()
        assertEquals("PATCH", req.method); assertEquals("/api/v1/rooms/1", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"name\":\"새\"")); assertTrue(body.contains("\"maxMembers\":5"))
        assertFalse(body.contains("isPublic")); assertFalse(body.contains("description"))
    }

    @Test fun noContentResponsesAreSuccess() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        assertTrue(repo.deleteRoom(1) is ApiResult.Success)
        assertTrue(repo.leaveRoom(1) is ApiResult.Success)
        assertTrue(repo.kickMember(1, 9) is ApiResult.Success)
        val reqs = List(3) { server.takeRequest() }
        assertEquals(listOf("DELETE /api/v1/rooms/1", "DELETE /api/v1/rooms/1/leave", "DELETE /api/v1/rooms/1/members/9"), reqs.map { "${it.method} ${it.path}" })
    }

    @Test fun inviteCodeAndTransferOwner() = runBlocking {
        server.enqueue(ok("""{"inviteCode":"ZZZ999"}"""))
        assertEquals("ZZZ999", repo.regenerateInviteCode(1).value())
        assertEquals("/api/v1/rooms/1/invite-code", server.takeRequest().path)
        server.enqueue(ok(detail))
        repo.transferOwner(1, 8).value()
        val req = server.takeRequest()
        assertEquals("/api/v1/rooms/1/transfer-owner", req.path); assertEquals("""{"userId":8}""", req.body.readUtf8())
    }

    @Test fun serverErrorCodesArePreserved() = runBlocking {
        val cases = listOf(
            Triple("VALIDATION_ERROR", 400, "입력한 내용"), Triple("FORBIDDEN", 403, "권한"), Triple("ROOM_NOT_PUBLIC", 403, "초대코드"),
            Triple("ROOM_NOT_FOUND", 404, "삭제된 방"), Triple("INVALID_INVITE_CODE", 404, "초대코드"),
            Triple("ALREADY_JOINED", 409, "이미 참여"), Triple("ROOM_FULL", 409, "정원"), Triple("OWNER_CANNOT_LEAVE", 409, "방장"),
        )
        for ((code, status, text) in cases) {
            server.enqueue(err(code, status))
            val f = repo.joinPublicRoom(1)
            assertEquals(code, f.http().code); assertEquals(status, f.http().status)
            assertTrue("$code -> ${RoomErrorMessages.forApi(f.http())}", RoomErrorMessages.forApi(f.http()).contains(text))
        }
    }

    @Test fun everyRoomCodeHasDistinctMessageAndUnknownFallsBack() {
        val codes = listOf(
            "ROOM_NOT_FOUND", "ROOM_NOT_PUBLIC", "ALREADY_JOINED", "ROOM_FULL", "INVALID_INVITE_CODE", "OWNER_CANNOT_LEAVE",
            "MAX_MEMBERS_BELOW_CURRENT", "ROOM_NOT_PRIVATE", "CANNOT_KICK_SELF", "NOT_A_MEMBER", "ALREADY_OWNER", "FORBIDDEN", "VALIDATION_ERROR",
        )
        fun http(c: String?, s: Int = 409) = ApiException.Http(s, c, null, "")
        assertEquals(codes.size, codes.map { RoomErrorMessages.forApi(http(it)) }.toSet().size)
        assertEquals("요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.", RoomErrorMessages.forApi(http("SOMETHING_NEW")))
        assertTrue(RoomErrorMessages.forApi(http(null, 500)).contains("서버"))
        assertTrue(RoomErrorMessages.forApi(ApiException.Network(java.io.IOException())).contains("네트워크"))
    }

    @Test fun connectionFailureIsNetworkErrorAndMutationIsNotRetried() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val r = repo.createRoom("방", null, true, 10)
        assertTrue((r as ApiResult.Failure).error is ApiException.Network)
        assertEquals(1, server.requestCount)
    }

    @Test fun unreachableServerIsNetworkError() = runBlocking {
        server.shutdown()
        assertTrue((repo.listMyRooms() as ApiResult.Failure).error is ApiException.Network)
    }
}
