package com.dailyquest.app.ui.room

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.RoomSummaryDto
import com.dailyquest.app.data.room.RoomRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class MyRoomsViewModelTest {

    private class FakeApi(delegate: DqApi, var handler: suspend () -> List<RoomSummaryDto>) : DqApi by delegate {
        var calls = 0
        override suspend fun listMyRooms(): List<RoomSummaryDto> { calls++; return handler() }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
        error("not used")
    } as DqApi

    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>

    private fun dto(id: Long, isPublic: Boolean = false, members: Long = 2) =
        RoomSummaryDto(roomId = id, name = "방$id", isPublic = isPublic, memberCount = members, joined = true)

    private fun rooms(n: Int) = (1..n).map { dto(it.toLong(), isPublic = it % 2 == 0, members = it.toLong()) }
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused) { rooms(2) }
        session = MutableStateFlow(SessionState.Loading)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loggedIn(userId: Long = 1): MyRoomsViewModel {
        val vm = MyRoomsViewModel(RoomRepository(api), session)
        runCurrent()
        session.value = profile(userId)
        advanceUntilIdle()
        return vm
    }

    @Test fun `no request before authentication`() = runTest {
        val vm = MyRoomsViewModel(RoomRepository(api), session)
        advanceUntilIdle()
        assertEquals(0, api.calls)
        assertTrue(vm.ui.value.initialLoading)
    }

    @Test fun `initial load keeps server order and shows public and private rooms`() = runTest {
        api.handler = { listOf(dto(30, isPublic = true, members = 7), dto(10, isPublic = false, members = 3), dto(20, true)) }
        val vm = loggedIn()
        val ui = vm.ui.value
        assertEquals(listOf(30L, 10L, 20L), ui.rooms.map { it.roomId })
        assertEquals(listOf(true, false, true), ui.rooms.map { it.isPublic })
        assertEquals(listOf(7L, 3L, 2L), ui.rooms.map { it.memberCount })
        assertEquals("방30", ui.rooms.first().name)
        assertFalse(ui.initialLoading)
        assertEquals(1, api.calls)
    }

    @Test fun `zero rooms is empty state without mock fallback`() = runTest {
        api.handler = { emptyList() }
        val ui = loggedIn().ui.value
        assertTrue(ui.isEmpty)
        assertEquals(0, ui.totalRooms)
        assertTrue(ui.pageRooms.isEmpty())
        assertEquals(1, ui.pageCount)
        assertFalse(ui.hasNext || ui.hasPrev)
    }

    @Test fun `one two three and four rooms fit on a single page`() = runTest {
        for (n in 1..4) {
            api.handler = { rooms(n) }
            val vm = loggedIn(n.toLong())
            assertEquals(n, vm.ui.value.pageRooms.size)
            assertEquals(1, vm.ui.value.pageCount)
            assertFalse(vm.ui.value.hasNext)
        }
    }

    @Test fun `five or more rooms paginate by four with boundary guards`() = runTest {
        api.handler = { rooms(9) }
        val vm = loggedIn()
        assertEquals(3, vm.ui.value.pageCount)
        assertEquals(listOf(1L, 2L, 3L, 4L), vm.ui.value.pageRooms.map { it.roomId })
        vm.prevPage()
        assertEquals(0, vm.ui.value.currentPage)
        vm.nextPage()
        assertEquals(listOf(5L, 6L, 7L, 8L), vm.ui.value.pageRooms.map { it.roomId })
        vm.nextPage(); vm.nextPage(); vm.nextPage()
        assertEquals(2, vm.ui.value.currentPage)
        assertEquals(listOf(9L), vm.ui.value.pageRooms.map { it.roomId })
        assertFalse(vm.ui.value.hasNext)
        assertTrue(vm.ui.value.hasPrev)
        assertEquals(1, api.calls)
    }

    @Test fun `page is corrected when the list shrinks on refresh`() = runTest {
        api.handler = { rooms(9) }
        val vm = loggedIn()
        vm.nextPage(); vm.nextPage()
        api.handler = { rooms(5) }
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, vm.ui.value.currentPage)
        api.handler = { rooms(2) }
        vm.refresh(); advanceUntilIdle()
        assertEquals(0, vm.ui.value.page)
        assertEquals(2, vm.ui.value.pageRooms.size)
    }

    @Test fun `network failure shows error with no rooms and retry recovers`() = runTest {
        api.handler = { throw IOException("offline") }
        val vm = loggedIn()
        assertTrue(vm.ui.value.error != null)
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertFalse(vm.ui.value.isEmpty)
        assertFalse(vm.ui.value.initialLoading)
        api.handler = { rooms(3) }
        vm.retry(); advanceUntilIdle()
        assertNull(vm.ui.value.error)
        assertEquals(3, vm.ui.value.rooms.size)
    }

    @Test fun `refresh failure keeps existing rooms and shows error`() = runTest {
        val vm = loggedIn()
        api.handler = { throw IOException("offline") }
        vm.refresh(); advanceUntilIdle()
        assertEquals(2, vm.ui.value.rooms.size)
        assertTrue(vm.ui.value.error != null)
        assertFalse(vm.ui.value.refreshing)
    }

    @Test fun `refresh reloads the list`() = runTest {
        val vm = loggedIn()
        api.handler = { rooms(3) }
        vm.refresh(); advanceUntilIdle()
        assertEquals(3, vm.ui.value.rooms.size)
        assertEquals(2, api.calls)
    }

    @Test fun `account switch clears previous rooms and ignores late response`() = runTest {
        val vm = loggedIn(1)
        assertEquals(2, vm.ui.value.rooms.size)
        val slow = CompletableDeferred<List<RoomSummaryDto>>()
        api.handler = { slow.await() }
        session.value = profile(2); runCurrent()
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertTrue(vm.ui.value.initialLoading)
        session.value = SessionState.Unauthenticated(); runCurrent()
        slow.complete(listOf(dto(99)))
        advanceUntilIdle()
        assertTrue(vm.ui.value.rooms.isEmpty())
        assertEquals(MyRoomsUiState(), vm.ui.value)
    }

    @Test fun `same user session emissions do not refetch`() = runTest {
        val vm = loggedIn(1)
        session.value = SessionState.BootstrapFailed(true); advanceUntilIdle()
        session.value = profile(1); advanceUntilIdle()
        assertEquals(1, api.calls)
        assertEquals(2, vm.ui.value.rooms.size)
    }

    @Test fun `avatar count never exceeds members or slot images`() {
        assertEquals(0, mapAvatarCount(0, 3))
        assertEquals(1, mapAvatarCount(1, 3))
        assertEquals(2, mapAvatarCount(2, 3))
        assertEquals(3, mapAvatarCount(30, 3))
        assertEquals(1, mapAvatarCount(5, 1))
        assertEquals(0, mapAvatarCount(-4, 3))
    }

    @Test fun `unused map slots are never filled`() = runTest {
        for (n in 0..3) {
            api.handler = { rooms(n) }
            val ui = loggedIn(100L + n).ui.value
            assertEquals(n, ui.pageRooms.size)
        }
        api.handler = { rooms(5) }
        val vm = loggedIn(200)
        vm.nextPage()
        assertEquals(1, vm.ui.value.pageRooms.size)
    }
}
