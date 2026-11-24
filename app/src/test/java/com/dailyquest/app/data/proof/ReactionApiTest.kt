package com.dailyquest.app.data.proof

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 피드·리액션·삭제 API 계약(MockWebServer, 실서버 호출 없음). */
class ReactionApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: ProofRepository

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        val session = SessionManager(tokens, emptyList())
        val api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        repo = ProofRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun addReactionSendsBodyAndNormalizesToFiveEmojis() = runBlocking {
        server.enqueue(json("""[{"emoji":"🔥","count":2,"reacted":true},{"emoji":"🤖","count":9,"reacted":true}]"""))
        val r = repo.addReaction(5, "🔥") as ApiResult.Success
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/api/v1/proofs/5/reactions", req.path)
        assertEquals("""{"emoji":"🔥"}""", req.body.readUtf8())
        assertEquals(ReactionEmojis.ALLOWED, r.value.map { it.emoji })
        assertEquals(2, r.value.first { it.emoji == "🔥" }.count)
        assertTrue(r.value.first { it.emoji == "🔥" }.reacted)
        assertEquals(0, r.value.first { it.emoji == "❤️" }.count)
    }

    @Test fun removeReactionUsesDeleteWithBody() = runBlocking {
        server.enqueue(json("[]"))
        val r = repo.removeReaction(5, "👏") as ApiResult.Success
        val req = server.takeRequest()
        assertEquals("DELETE", req.method); assertEquals("/api/v1/proofs/5/reactions", req.path)
        assertEquals("""{"emoji":"👏"}""", req.body.readUtf8())
        assertTrue(r.value.all { it.count == 0 && !it.reacted })
    }

    @Test fun invalidEmojiMapsToMessage() = runBlocking {
        server.enqueue(json("""{"code":"INVALID_EMOJI","message":"x"}""", 400))
        val r = repo.addReaction(5, "x") as ApiResult.Failure
        assertEquals("사용할 수 없는 리액션이에요.", ProofErrorMessages.forApi(r.error))
    }

    @Test fun lostReactionResponseIsNotRetried() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json("[]"))
        val r = repo.addReaction(5, "❤️")
        assertTrue(r is ApiResult.Failure && r.error !is ApiException.Http)
        assertEquals(1, server.requestCount)
    }

    @Test fun deleteHandles204AndForbiddenAndFinalized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.delete(5) is ApiResult.Success)
        assertEquals("DELETE", server.takeRequest().method)
        server.enqueue(json("""{"code":"PROOF_ALREADY_FINALIZED","message":"x"}""", 409))
        val f = repo.delete(5) as ApiResult.Failure
        assertEquals("이미 승인 또는 거절이 확정된 인증이에요.", ProofErrorMessages.forApi(f.error))
        server.enqueue(json("""{"code":"FORBIDDEN","message":"x"}""", 403))
        assertTrue(repo.delete(5) is ApiResult.Failure)
    }

    @Test fun feedUsesFeedPathAndParsesItems() = runBlocking {
        server.enqueue(json("""{"content":[{"proofId":9,"user":{"userId":2,"nickname":"a"},"mission":{"missionId":3,"content":"m"},"mediaType":"VIDEO","thumbnailUrl":null,"status":"PENDING","viewed":true,"createdAt":"2025-04-17T00:00:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1}"""))
        val r = repo.feed(10) as ApiResult.Success
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/api/v1/rooms/10/feed?"))
        assertEquals(ProofMediaType.VIDEO, r.value.items.single().mediaType)
    }
}
