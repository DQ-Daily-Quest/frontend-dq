package com.dailyquest.app.data.avatar

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** 프로필 얼굴 7단계: 사람(userId) → 착용 아바타 (`GET /users/{id}`). */
class UserAvatarsTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: UserAvatarRepository
    private val requested = ConcurrentHashMap<Long, Int>()
    @Volatile private var hat: Long? = null
    @Volatile private var failing: Long? = null

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = Regex("/api/v1/users/(\\d+)$").find(request.path!!)?.groupValues?.get(1)?.toLong()
                    ?: return MockResponse().setResponseCode(404)
                requested.merge(id, 1, Int::plus)
                if (id == 404L) return MockResponse().setResponseCode(404).setBody("""{"code":"USER_NOT_FOUND","message":"x"}""")
                if (id == failing) return MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"x"}""")
                return MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """{"userId":$id,"nickname":"u$id","gender":"MALE","avatar":{"skin":$id,"hat":${hat ?: "null"}},"stats":{"roomCount":1,"completedMissionCount":0}}""",
                )
            }
        }
        server.start()
        val api = NetworkModule.createApi(SessionTokens(InMemoryTokenStore(TokenPair("A", "R"))), {}, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        repo = UserAvatarRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `여러 사람을 한 번씩 받아 기억한다`() = runBlocking {
        val got = repo.refresh(listOf(7, 8, 7))
        assertEquals(setOf(7L, 8L), got.keys)
        assertEquals(mapOf(7L to 1, 8L to 1), requested.toMap())
        assertEquals(7L, repo.cached(7)!!.skin)
        assertNull(repo.cached(9))
    }

    @Test fun `다시 받으면 새 착용, 실패한 사람은 예전 착용, 없는 사람은 빠진다`() = runBlocking {
        repo.refresh(listOf(7, 8))
        hat = 50
        failing = 8
        val got = repo.refresh(listOf(7, 8, 404))
        assertEquals(setOf(7L), got.keys)
        assertEquals(50L, repo.cached(7)!!.hat)
        assertNull("실패한 사람은 예전 착용 그대로", repo.cached(8)!!.hat)
        assertNull(repo.cached(404))
    }

    @Test fun `계정이 바뀌면 비운다`() = runBlocking {
        repo.refresh(listOf(7))
        repo.clear()
        assertNull(repo.cached(7))
    }
}
