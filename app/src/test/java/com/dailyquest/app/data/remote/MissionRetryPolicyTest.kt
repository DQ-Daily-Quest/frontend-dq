package com.dailyquest.app.data.remote

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 미션 생성(POST)은 연결 실패 시 자동 재전송하지 않고, 조회 API는 기본 재시도를 유지한다. */
class MissionRetryPolicyTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DqApi

    private val mission = """{"missionId":5,"type":"MEMBER","content":"산책","reward":{"type":"COIN","amount":50,"streakBonus":0},"dueAt":"2030-01-01T00:00:00Z","proofCount":0}"""

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("OLD", "R1")))
        val session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun warmUp() {
        server.enqueue(json("[]"))
        api.listMyMissions()
        server.takeRequest()
    }

    @Test fun createMissionIsNotResentAfterConnectionDrop() = runBlocking {
        warmUp()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json(mission, 201))
        val r = runCatching { api.createMission(10, CreateMissionRequest("산책", 50)) }
        assertTrue(r.exceptionOrNull() is java.io.IOException)
        assertEquals(2, server.requestCount) // 워밍업 1 + 생성 1(재전송 없음)
    }

    @Test fun createMissionPostsToRoomMissionsPath() = runBlocking {
        server.enqueue(json(mission, 201))
        assertEquals(5L, api.createMission(10, CreateMissionRequest("산책", 50)).missionId)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/rooms/10/missions"))
    }

    @Test fun readRequestsStillUseDefaultConnectionRetry() = runBlocking {
        warmUp()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json("[]"))
        assertTrue(api.listMyMissions().isEmpty())
        assertEquals(3, server.requestCount)
    }
}
