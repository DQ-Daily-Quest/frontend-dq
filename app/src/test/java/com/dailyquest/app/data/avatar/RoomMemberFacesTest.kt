package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.NetworkModule
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.room.RoomRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** 프로필 얼굴 6단계: 홈 지도 핀에 누구를 보여줄지, 방마다 멤버 목록 받기. */
class RoomMemberFacesTest {
    private fun m(id: Long, name: String = "u$id", skin: Long? = id) = RoomMemberDto(id, name, "MEMBER", AvatarDto(skin = skin))

    @Test fun `다른 멤버 먼저, 나는 맨 뒤, 최대 3명과 나머지 +N`() {
        val members = listOf(m(1, "나"), m(2), m(3), m(4), m(5))
        val f = pickMapFaces(members, memberCount = 5, myUserId = 1, myAvatar = null)
        assertEquals(listOf(2L, 3L, 4L), f.shown.map { it.userId })
        assertEquals(2, f.more)
    }

    @Test fun `내가 보이면 세션의 지금 착용을 쓴다`() {
        val mine = AvatarDto(skin = 1, hat = 99)
        val f = pickMapFaces(listOf(m(1, "나"), m(2)), memberCount = 2, myUserId = 1, myAvatar = mine)
        assertEquals(listOf(2L, 1L), f.shown.map { it.userId })
        assertEquals(mine, f.shown[1].avatar)
        assertEquals(0, f.more)
    }

    @Test fun `인원은 방 목록 숫자와 받은 멤버 수 중 큰 값`() {
        assertEquals("방 목록이 더 크면(목록을 받은 뒤 누가 들어옴) 그만큼 +N", 3, pickMapFaces(listOf(m(2)), memberCount = 4, myUserId = 1, myAvatar = null).more)
        assertEquals("받은 멤버가 더 많으면 그 수로", 1, pickMapFaces(listOf(m(2), m(3), m(4), m(5)), memberCount = 2, myUserId = 1, myAvatar = null).more)
        assertEquals(0, pickMapFaces(emptyList(), memberCount = 0, myUserId = 1, myAvatar = null).more)
    }

    // ---- 방마다 멤버 목록 받기 (MockWebServer) ----

    private lateinit var server: MockWebServer
    private lateinit var repo: RoomMemberFaceRepository
    private val requested = ConcurrentHashMap<Long, Int>()
    @Volatile private var failRoom: Long? = null
    @Volatile private var hatOf7: Long? = null

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = Regex("/api/v1/rooms/(\\d+)/members").find(request.path!!)?.groupValues?.get(1)?.toLong()
                    ?: return MockResponse().setResponseCode(404)
                requested.merge(id, 1, Int::plus)
                if (id == failRoom) return MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"x"}""")
                val hat = hatOf7?.toString() ?: "null"
                return MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """[{"userId":7,"nickname":"방장$id","role":"OWNER","avatar":{"skin":1,"hat":$hat}},{"userId":8,"nickname":"멤버$id","role":"MEMBER","avatar":{}}]""",
                )
            }
        }
        server.start()
        val api = NetworkModule.createApi(SessionTokens(InMemoryTokenStore(TokenPair("A", "R"))), {}, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        repo = RoomMemberFaceRepository(RoomRepository(api))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `보이는 방들을 한 번씩 받아 기억한다`() = runBlocking {
        val got = repo.refresh(listOf(11, 12, 12, 13))
        assertEquals(setOf(11L, 12L, 13L), got.keys)
        assertEquals(mapOf(11L to 1, 12L to 1, 13L to 1), requested.toMap())
        assertEquals("방장12", repo.cached(12)!!.first().nickname)
        assertNull(repo.cached(99))
    }

    @Test fun `한 방이 실패해도 다른 방은 받고, 실패한 방은 예전 목록을 둔다`() = runBlocking {
        repo.refresh(listOf(11, 12))
        failRoom = 12
        hatOf7 = 50
        val got = repo.refresh(listOf(11, 12))
        assertEquals(setOf(11L), got.keys)
        assertEquals("새로 받은 방은 새 착용", 50L, repo.cached(11)!!.first().avatar.hat)
        assertNull("실패한 방은 예전 착용 그대로", repo.cached(12)!!.first().avatar.hat)
    }

    @Test fun `계정이 바뀌면 비운다`() = runBlocking {
        repo.refresh(listOf(11))
        repo.clear()
        assertNull(repo.cached(11))
        assertTrue(requested.isNotEmpty())
    }
}
