package com.dailyquest.app.data.proof

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** 업로드·제출·투표 요청의 자동 재전송 방지와 multipart 형식, 그리고 ProofSync. 실제 서버가 아닌 MockWebServer만 쓴다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProofSyncAndRetryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DqApi
    private lateinit var tmp: File

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("OLD", "R1")))
        val session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        tmp = File.createTempFile("proof", ".jpg").also { it.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)) }
    }

    @After fun tearDown() { server.shutdown(); tmp.delete() }

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val proof = """{"proofId":5,"missionId":3,"user":{"userId":1,"nickname":"나"},"mediaType":"IMAGE","status":"PENDING","approval":{"approved":0,"rejected":0,"required":1}}"""
    private val vote = """{"proofId":5,"status":"PENDING","approval":{"approved":1,"rejected":0,"required":2,"myVote":"APPROVE"}}"""

    private suspend fun warmUp() { server.enqueue(json("[]")); api.listMyMissions(); server.takeRequest() }

    @Test fun uploadSendsMultipartFilePartWithPurposeQuery() = runBlocking {
        server.enqueue(json("""{"url":"https://cdn/a.jpg","mediaType":"IMAGE"}""", 201))
        val r = UploadRepository(api).uploadProofMedia(tmp, "image/jpeg")
        assertTrue(r is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertTrue(req.path!!.contains("/uploads?purpose=PROOF"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"file\"")); assertTrue(body.contains("image/jpeg"))
    }

    @Test fun uploadSubmitAndVoteAreNeverResentAfterConnectionDrop() = runBlocking {
        warmUp()
        val repo = ProofRepository(api)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(UploadRepository(api).uploadProofMedia(tmp, "image/jpeg") is ApiResult.Failure)
        assertEquals(2, server.requestCount) // 워밍업 + 업로드 1회

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(repo.submit(3, UploadedMedia("https://cdn/a.jpg", null, ProofMediaType.IMAGE), "hi") is ApiResult.Failure)
        assertEquals(3, server.requestCount)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(repo.vote(5, VoteChoice.APPROVE) is ApiResult.Failure)
        assertEquals(4, server.requestCount)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(repo.vote(5, VoteChoice.REJECT) is ApiResult.Failure)
        assertEquals(5, server.requestCount)
    }

    @Test fun detailReadsStillRetry() = runBlocking {
        warmUp()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json("""{"proofId":5,"missionId":3,"user":{"userId":1,"nickname":"나"},"mediaType":"TEXT","status":"PENDING","mission":{"missionId":3,"type":"APP","content":"c"},"room":{"roomId":1,"name":"r"}}"""))
        assertTrue(ProofRepository(api).detail(5) is ApiResult.Success)
        assertEquals(3, server.requestCount)
    }

    @Test fun votePathsMatchServerContract() = runBlocking {
        server.enqueue(json(vote)); server.enqueue(json(vote))
        val repo = ProofRepository(api)
        repo.vote(5, VoteChoice.APPROVE); repo.vote(5, VoteChoice.REJECT)
        assertTrue(server.takeRequest().path!!.endsWith("/proofs/5/approve"))
        assertTrue(server.takeRequest().path!!.endsWith("/proofs/5/reject"))
    }

    @Test fun submitBodyUsesUploadedUrlAndTrimsText() = runBlocking {
        server.enqueue(json(proof, 201))
        ProofRepository(api).submit(3, UploadedMedia("https://cdn/a.jpg", "https://cdn/t.jpg", ProofMediaType.IMAGE), "  ")
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/missions/3/proofs"))
        val b = req.body.readUtf8()
        assertTrue(b.contains("https://cdn/a.jpg")); assertTrue(!b.contains("text"))
    }

    @Test fun syncBumpsVersionAndRefreshesCoinsOnce() = runTest {
        var refreshed = 0
        val sync = ProofSync(TestScope(StandardTestDispatcher(testScheduler)), { refreshed++ })
        assertEquals(0, sync.version.value)
        sync.notifyChanged(); advanceUntilIdle()
        assertEquals(1, sync.version.value); assertEquals(1, refreshed)
        sync.notifyChanged(refreshCoins = false); advanceUntilIdle()
        assertEquals(2, sync.version.value); assertEquals(1, refreshed)
    }
}
