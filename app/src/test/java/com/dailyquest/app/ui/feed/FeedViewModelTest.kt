package com.dailyquest.app.ui.feed

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.PendingMissionBriefDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var feed: suspend (Long, Int) -> PageDto<PendingProofDto> = { _, _ -> PageDto(emptyList(), 0, 20, 0, 1) }
        val pages = mutableListOf<Pair<Long, Int>>()
        override suspend fun listFeed(roomId: Long, page: Int, size: Int): PageDto<PendingProofDto> { pages += roomId to page; return feed(roomId, page) }
    }

    private fun item(id: Long, type: String = "IMAGE") =
        PendingProofDto(id, RoomOwnerDto(7, "친구"), PendingMissionBriefDto(3, "미션"), type, "https://cdn/t$id.jpg", "PENDING", false, "2025-04-17T00:00:00Z")

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()); api = FakeApi(unused); session = MutableStateFlow(profile(1)) }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(roomId: Long = 10) = FeedViewModel(roomId, ProofRepository(api), session)

    @Test fun loadsItemsAndKeepsMediaType() = runTest {
        api.feed = { _, _ -> PageDto(listOf(item(2, "VIDEO"), item(1)), 0, 20, 2, 1) }
        val v = vm(); advanceUntilIdle()
        assertTrue(v.ui.value.loaded)
        assertEquals(listOf(2L, 1L), v.ui.value.items.map { it.proofId })
        assertEquals(ProofMediaType.VIDEO, v.ui.value.items.first().mediaType)
        assertFalse(v.ui.value.hasNext)
    }

    @Test fun emptyFeedIsLoadedAndEmpty() = runTest {
        val v = vm(); advanceUntilIdle()
        assertTrue(v.ui.value.loaded); assertTrue(v.ui.value.items.isEmpty()); assertNull(v.ui.value.error)
    }

    @Test fun paginationAppendsWithoutDuplicates() = runTest {
        api.feed = { _, p -> if (p == 0) PageDto(listOf(item(3), item(2)), 0, 2, 3, 2) else PageDto(listOf(item(2), item(1)), 1, 2, 3, 2) }
        val v = vm(); advanceUntilIdle()
        assertTrue(v.ui.value.hasNext)
        v.loadMore(); v.loadMore(); advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), v.ui.value.items.map { it.proofId })
        assertEquals(listOf(0, 1), api.pages.map { it.second })
        assertFalse(v.ui.value.hasNext)
    }

    @Test fun networkErrorShowsRetryAndRecovers() = runTest {
        api.feed = { _, _ -> throw IOException("down") }
        val v = vm(); advanceUntilIdle()
        assertNotNull(v.ui.value.error); assertFalse(v.ui.value.loaded); assertFalse(v.ui.value.gone)
        api.feed = { _, _ -> PageDto(listOf(item(1)), 0, 20, 1, 1) }
        v.retry(); advanceUntilIdle()
        assertNull(v.ui.value.error); assertEquals(1, v.ui.value.items.size)
    }

    @Test fun forbiddenClearsItemsAndMarksGone() = runTest {
        api.feed = { _, _ -> PageDto(listOf(item(1)), 0, 20, 1, 1) }
        val v = vm(); advanceUntilIdle()
        api.feed = { _, _ -> throw http(403, "FORBIDDEN") }
        v.refresh(); advanceUntilIdle()
        assertTrue(v.ui.value.gone); assertTrue(v.ui.value.items.isEmpty())
    }

    @Test fun accountSwitchDropsItemsAndIgnoresLateResponse() = runTest {
        val gate = CompletableDeferred<PageDto<PendingProofDto>>()
        api.feed = { _, _ -> gate.await() }
        val v = vm(); runCurrent()
        api.feed = { _, _ -> PageDto(emptyList(), 0, 20, 0, 1) }
        session.value = profile(2); runCurrent()
        gate.complete(PageDto(listOf(item(99)), 0, 20, 1, 1)); advanceUntilIdle()
        assertTrue(v.ui.value.items.none { it.proofId == 99L })
        assertEquals(2L, v.ui.value.userId)
    }

    @Test fun signedOutDoesNotRequestFeed() = runTest {
        session.value = SessionState.Unauthenticated()
        vm(); advanceUntilIdle()
        assertTrue(api.pages.isEmpty())
    }

    @Test fun markViewedTurnsOffNewUntilServerCatchesUp() = runTest {
        api.feed = { _, _ -> PageDto(listOf(item(2), item(1)), 0, 20, 2, 1) }
        val v = vm(); advanceUntilIdle()
        v.markViewed(2)
        assertTrue(v.ui.value.items.first { it.proofId == 2L }.viewed)
        assertFalse(v.ui.value.items.first { it.proofId == 1L }.viewed)
        // 서버가 아직 기록하기 전이라 viewed=false로 다시 와도, 이 앱에서 연 인증은 봤음으로 둔다.
        v.refresh(); advanceUntilIdle()
        assertTrue(v.ui.value.items.first { it.proofId == 2L }.viewed)
        assertFalse(v.ui.value.items.first { it.proofId == 1L }.viewed)
    }

    @Test fun accountSwitchForgetsOpenedProofs() = runTest {
        api.feed = { _, _ -> PageDto(listOf(item(2)), 0, 20, 1, 1) }
        val v = vm(); advanceUntilIdle()
        v.markViewed(2)
        session.value = profile(2); advanceUntilIdle()
        assertFalse("다른 계정에서는 그 계정의 서버 값대로", v.ui.value.items.first { it.proofId == 2L }.viewed)
    }
}
