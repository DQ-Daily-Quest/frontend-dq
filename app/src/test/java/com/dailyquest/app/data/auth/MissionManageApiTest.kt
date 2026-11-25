package com.dailyquest.app.data.auth

import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** PATCH/DELETE /missions/{id} 계약. 모두 MockWebServer이며 실제 서버에는 요청하지 않는다. */
class MissionManageApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: MissionRepository

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore())
        val session = SessionManager(tokens, emptyList())
        val api: DqApi = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").toString())
        repo = MissionRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private val missionJson = """{"missionId":5,"type":"MEMBER","content":"새 내용","reward":{"type":"COIN","amount":50,"streakBonus":0},
        "createdBy":{"userId":1,"nickname":"a"},"dueAt":"2030-01-01T00:00:00Z","proofCount":0}"""

    @Test fun patchSendsOnlyChangedContent() = runBlocking {
        server.enqueue(MockResponse().setBody(missionJson).addHeader("Content-Type", "application/json"))
        val r = repo.updateMission(5, 10, "  새 내용 ", null)
        assertTrue(r is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/api/v1/missions/5", req.path)
        assertEquals("""{"content":"새 내용"}""", req.body.readUtf8())
    }

    @Test fun patchSendsDueAtOnlyWhenChanged() = runBlocking {
        server.enqueue(MockResponse().setBody(missionJson).addHeader("Content-Type", "application/json"))
        repo.updateMission(5, 10, null, 1_900_000_000_000L)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.startsWith("""{"dueAt":"20""") && !body.contains("content"))
    }

    @Test fun deleteIs204AndSentOnce() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.deleteMission(5) is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/v1/missions/5", req.path)
        assertEquals(1, server.requestCount)
    }

    @Test fun deleteIsNotRetriedWhenConnectionDrops() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.deleteMission(5) is ApiResult.Failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun patchIsNotRetriedWhenConnectionDrops() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody(missionJson))
        assertTrue(repo.updateMission(5, 10, "새 내용", null) is ApiResult.Failure)
        assertEquals(1, server.requestCount)
    }

    private fun failure(status: Int, code: String, deleting: Boolean): String = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json")
                .setBody("""{"code":"$code","message":"m"}""")
        )
        val r = if (deleting) repo.deleteMission(5) else repo.updateMission(5, 10, "내용", null)
        MissionErrorMessages.forManage((r as ApiResult.Failure).error as ApiException, deleting)
    }

    @Test fun mapsMissionHasProofs() {
        assertTrue(failure(409, "MISSION_HAS_PROOFS", true).contains("인증이 제출됐거나 승인된"))
        assertTrue(failure(409, "MISSION_HAS_PROOFS", false).contains("수정"))
    }

    @Test fun mapsAppMissionImmutableAndForbidden() {
        assertTrue(failure(409, "APP_MISSION_IMMUTABLE", false).contains("오늘의 미션"))
        assertTrue(failure(403, "FORBIDDEN", false).contains("만든 사람만"))
        assertTrue(failure(403, "FORBIDDEN", true).contains("방장"))
    }

    @Test fun mapsNotFoundAndValidation() {
        assertTrue(failure(404, "MISSION_NOT_FOUND", true).contains("삭제된"))
        assertTrue(failure(400, "VALIDATION_ERROR", false).contains("30일"))
    }

    @Test fun rateLimitFallsBackToCommonMessage() {
        val msg = failure(429, "TOO_MANY_REQUESTS", true)
        assertTrue(msg, msg.contains("요청이 너무 많아요"))
    }
}
