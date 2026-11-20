package com.dailyquest.app.ui.room

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.room.RoomRepository
import com.dailyquest.app.data.room.RoomSort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PublicRoomsViewModelTest {

    private data class Call(val keyword: String?, val sort: String?, val page: Int, val size: Int)

    private class FakeApi(delegate: DqApi, var handler: suspend (Call) -> PageDto<RoomSummaryDto>) : DqApi by delegate {
        val calls = mutableListOf<Call>()
        override suspend fun listPublicRooms(keyword: String?, sort: String?, page: Int, size: Int): PageDto<RoomSummaryDto> {
            val c = Call(keyword, sort, page, size)
            calls += c
            return handler(c)
        }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
        error("not used")
    } as DqApi

    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>

    private fun dto(id: Long, name: String = "방$id") = RoomSummaryDto(roomId = id, name = name, isPublic = true, memberCount = 1)
    private fun page(vararg ids: Long, page: Int = 0, totalPages: Int = 1) =
        PageDto(ids.map { dto(it) }, page, 20, ids.size.toLong(), totalPages)

    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused) { page(1, 2) }
        session = MutableStateFlow(SessionState.Loading)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): PublicRoomsViewModel {
        val vm = PublicRoomsViewModel(RoomRepository(api), session)
        runCurrent()
        return vm
    }

    private fun TestScope.loggedIn(userId: Long = 1): PublicRoomsViewModel {
        val vm = vm()
        session.value = profile(userId)
        advanceUntilIdle()
        return vm
    }

    @Test fun `no request before authentication`() = runTest {
        val vm = vm()
        advanceUntilIdle()
        assertTrue(api.calls.isEmpty())
        assertTrue(vm.ui.value.initialLoading)
    }

    @Test fun `initial load succeeds with first page defaults`() = runTest {
        val vm = loggedIn()
        assertEquals(listOf(Call(null, "popularity,desc", 0, 20)), api.calls)
        assertEquals(listOf(1L, 2L), vm.ui.value.rooms.map { it.roomId })
        assertFalse(vm.ui.value.initialLoading)
        assertFalse(vm.ui.value.isEmpty)
    }

    @Test fun `empty list is distinguished from empty search`() = runTest {
        api.handler = { PageDto() }
        val vm = loggedIn()
        assertTrue(vm.ui.value.isEmptyOverall)
        vm.setKeyword("없는방")
        advanceUntilIdle()
        assertTrue(vm.ui.value.isEmptyForSearch)
        assertFalse(vm.ui.value.isEmptyOverall)
    }

    @Test fun `failure shows error without mock rooms and retry recovers`() = runTest {
        api.handler = { throw IOException("offline") }
        val vm = loggedIn()
        assertTrue(vm.ui.value.error != null)
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertFalse(vm.ui.value.isEmpty)
        api.handler = { page(7) }
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.ui.value.error)
        assertEquals(listOf(7L), vm.ui.value.rooms.map { it.roomId })
    }

    @Test fun `search is debounced and trimmed`() = runTest {
        val vm = loggedIn()
        api.calls.clear()
        vm.setKeyword("a"); advanceTimeBy(100)
        vm.setKeyword("ab"); advanceTimeBy(100)
        vm.setKeyword("  abc  ")
        advanceTimeBy(299); runCurrent()
        assertTrue(api.calls.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(Call("abc", "popularity,desc", 0, 20)), api.calls)
    }

    @Test fun `blank keyword requests all rooms and unchanged keyword does not refetch`() = runTest {
        val vm = loggedIn()
        vm.setKeyword("x"); advanceUntilIdle()
        api.calls.clear()
        vm.setKeyword("x "); advanceUntilIdle()
        assertTrue(api.calls.isEmpty())
        vm.setKeyword("   "); advanceUntilIdle()
        assertEquals(listOf(Call(null, "popularity,desc", 0, 20)), api.calls)
    }

    @Test fun `search resets to page 0 and clears old list`() = runTest {
        api.handler = { c -> if (c.keyword == null) page(1, 2, page = c.page, totalPages = 3) else page(9) }
        val vm = loggedIn()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(1, vm.ui.value.page)
        vm.setKeyword("k"); advanceUntilIdle()
        assertEquals(0, vm.ui.value.page)
        assertEquals(listOf(9L), vm.ui.value.rooms.map { it.roomId })
        assertEquals(Call("k", "popularity,desc", 0, 20), api.calls.last())
    }

    @Test fun `stale search response is ignored`() = runTest {
        val slow = CompletableDeferred<PageDto<RoomSummaryDto>>()
        api.handler = { c ->
            when (c.keyword) {
                "old" -> slow.await()
                "new" -> page(2)
                else -> page(1)
            }
        }
        val vm = loggedIn()
        vm.setKeyword("old"); advanceTimeBy(301); runCurrent()
        vm.setKeyword("new"); advanceTimeBy(301); runCurrent()
        slow.complete(page(99))
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.ui.value.rooms.map { it.roomId })
    }

    @Test fun `sort sends server parameter and resets list`() = runTest {
        val vm = loggedIn()
        vm.setSort(RoomSort.NEWEST); advanceUntilIdle()
        assertEquals("createdAt,desc", api.calls.last().sort)
        assertEquals(0, api.calls.last().page)
        vm.setSort(RoomSort.POPULARITY); advanceUntilIdle()
        assertEquals("popularity,desc", api.calls.last().sort)
        val n = api.calls.size
        vm.setSort(RoomSort.POPULARITY); advanceUntilIdle()
        assertEquals(n, api.calls.size)
    }

    @Test fun `server order is preserved without client sorting`() = runTest {
        api.handler = { PageDto(listOf(dto(5).copy(memberCount = 1), dto(3).copy(memberCount = 9)), 0, 20, 2, 1) }
        val vm = loggedIn()
        assertEquals(listOf(5L, 3L), vm.ui.value.rooms.map { it.roomId })
    }

    @Test fun `next page is appended and duplicate roomIds are dropped`() = runTest {
        api.handler = { c -> if (c.page == 0) page(1, 2, page = 0, totalPages = 2) else page(2, 3, page = 1, totalPages = 2) }
        val vm = loggedIn()
        assertTrue(vm.ui.value.hasNext)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.ui.value.rooms.map { it.roomId })
        assertEquals(1, vm.ui.value.page)
    }

    @Test fun `concurrent loadMore requests only one page`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.handler = { c -> if (c.page == 0) page(1, page = 0, totalPages = 3) else { gate.await(); page(2, page = 1, totalPages = 3) } }
        val vm = loggedIn()
        vm.loadMore(); vm.loadMore(); vm.loadMore(); runCurrent()
        assertEquals(1, api.calls.count { it.page == 1 })
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.ui.value.rooms.map { it.roomId })
    }

    @Test fun `no request after the last page`() = runTest {
        api.handler = { c -> page(c.page.toLong() + 1, page = c.page, totalPages = 2) }
        val vm = loggedIn()
        vm.loadMore(); advanceUntilIdle()
        assertFalse(vm.ui.value.hasNext)
        val n = api.calls.size
        vm.loadMore(); advanceUntilIdle()
        assertEquals(n, api.calls.size)
    }

    @Test fun `append failure keeps list and retry loads the same page`() = runTest {
        api.handler = { c -> if (c.page == 0) page(1, page = 0, totalPages = 2) else throw IOException("offline") }
        val vm = loggedIn()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L), vm.ui.value.rooms.map { it.roomId })
        assertTrue(vm.ui.value.loadMoreError != null)
        assertNull(vm.ui.value.error)
        api.handler = { page(2, page = 1, totalPages = 2) }
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.ui.value.rooms.map { it.roomId })
        assertNull(vm.ui.value.loadMoreError)
        assertEquals(1, api.calls.last().page)
    }

    @Test fun `account switch clears previous user data and ignores late response`() = runTest {
        val slow = CompletableDeferred<PageDto<RoomSummaryDto>>()
        var first = true
        api.handler = { if (first) { first = false; page(1).let { p -> PageDto(listOf(dto(1).copy(joined = true)), 0, 20, 1, 1) } } else slow.await() }
        val vm = loggedIn(1)
        assertTrue(vm.ui.value.rooms.single().joined)
        session.value = profile(2); runCurrent()
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertTrue(vm.ui.value.initialLoading)
        session.value = SessionState.Unauthenticated(); runCurrent()
        slow.complete(page(55)); advanceUntilIdle()
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertEquals("", vm.ui.value.keyword)
    }

    @Test fun `logout resets keyword and sort`() = runTest {
        val vm = loggedIn()
        vm.setKeyword("zzz"); vm.setSort(RoomSort.NEWEST); advanceUntilIdle()
        session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        assertEquals(PublicRoomsUiState(), vm.ui.value)
    }

    @Test fun `repeated session emissions for same user do not refetch`() = runTest {
        val vm = loggedIn(1)
        val n = api.calls.size
        session.value = SessionState.BootstrapFailed(true); advanceUntilIdle()
        session.value = profile(1); advanceUntilIdle()
        assertEquals(n, api.calls.size)
        assertEquals(2, vm.ui.value.rooms.size)
    }
}
