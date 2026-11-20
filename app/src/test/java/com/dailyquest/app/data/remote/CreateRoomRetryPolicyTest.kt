package com.dailyquest.app.data.remote

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 방 생성은 연결 실패 시 OkHttp가 자동 재전송하지 않고, 다른 API의 재시도 동작은 그대로여야 한다. */
class CreateRoomRetryPolicyTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DqApi

    private val created = """{"roomId":77,"name":"방","public":true,"inviteCode":null,"ownerId":1}"""
    private val me = """{"userId":1,"email":"a@b.c","nickname":"n","coins":0,"avatar":{},"streak":{"current":0,"longest":0},"stats":{"roomCount":0,"completedMissionCount":0},"createdAt":"2025-01-01T00:00:00Z"}"""
    private val tokenBody =
        """{"accessToken":"NEW","refreshToken":"R2","expiresIn":1800,"user":{"userId":1,"nickname":"n","avatarUrl":null,"coins":0},"isNewUser":false}"""

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("OLD", "R1")))
        val session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 201) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val body = CreateRoomRequest(name = "방", description = null, isPublic = true, maxMembers = 10)

    // 풀에 남은 연결이 끊긴 경우(응답 유실과 같은 상황)를 만들기 위해 먼저 요청 하나로 keep-alive 연결을 만든다.
    private suspend fun warmUp() {
        server.enqueue(json(me, 200))
        api.me()
        server.takeRequest()
    }

    @Test fun createIsNotResentAfterConnectionDropAndMarkerHeaderIsNotSent() = runBlocking {
        warmUp()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json(created))
        val r = runCatching { api.createRoom(body) }
        assertTrue(r.exceptionOrNull() is java.io.IOException)
        assertEquals(2, server.requestCount) // 워밍업 1 + 생성 1(재전송 없음)
        assertNull(server.takeRequest().getHeader(NetworkModule.NO_RETRY_HEADER))
    }

    @Test fun otherRequestsStillUseDefaultConnectionRetry() = runBlocking {
        warmUp()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json(me, 200))
        assertEquals(1L, api.me().userId)
        assertEquals(3, server.requestCount) // 워밍업 1 + 끊긴 요청 1 + 자동 재시도 1
    }

    @Test fun createIsResentOnceAfterTokenExpiredBecauseServerRejectedBeforeProcessing() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("/auth/refresh") -> json(tokenBody, 200)
                request.getHeader("Authorization") == "Bearer OLD" ->
                    json("""{"code":"TOKEN_EXPIRED","message":"m"}""", 401)
                else -> json(created)
            }
        }
        assertEquals(77L, api.createRoom(body).roomId)
        val paths = (1..server.requestCount).map { server.takeRequest().path }
        assertEquals(2, paths.count { it!!.endsWith("/rooms") })
    }
}
