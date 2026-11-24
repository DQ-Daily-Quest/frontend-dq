package com.dailyquest.app.ui.notification

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.notification.NotificationRepository
import com.dailyquest.app.data.notification.UnreadCountTracker
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.NotificationDto
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.UnreadCountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class NotificationViewModelTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var list: suspend (Boolean, Int) -> PageDto<NotificationDto> = { _, _ -> PageDto(emptyList(), 0, 20, 0, 1) }
        var read: suspend (Long) -> Unit = {}
        var readAll: suspend () -> Unit = {}
        var unread: suspend () -> Long = { 0 }
        val readCalls = mutableListOf<Long>()
        var readAllCalls = 0
        val listCalls = mutableListOf<Pair<Boolean, Int>>()
        override suspend fun listNotifications(unreadOnly: Boolean, page: Int, size: Int): PageDto<NotificationDto> { listCalls += unreadOnly to page; return list(unreadOnly, page) }
        override suspend fun markNotificationRead(notificationId: Long) { readCalls += notificationId; read(notificationId) }
        override suspend fun markAllNotificationsRead() { readAllCalls++; readAll() }
        override suspend fun getUnreadCount(): UnreadCountDto = UnreadCountDto(unread())
    }

    private fun dto(id: Long, read: Boolean = false) = NotificationDto(id, "PROOF_APPROVED", "t$id", "b", 1, 2, id, read, "2025-04-17T00:00:00Z")
    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private var unreadChanged = 0
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType())))

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()); api = FakeApi(unused); session = MutableStateFlow(profile(1)); unreadChanged = 0 }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = NotificationViewModel(NotificationRepository(api), session) { unreadChanged++ }

    @Test fun loadsNewestFirstAsServerOrdered() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(3), dto(2, true)), 0, 20, 2, 1) }
        val v = vm(); advanceUntilIdle()
        assertEquals(listOf(3L, 2L), v.ui.value.items.map { it.id })
        assertTrue(v.ui.value.loaded)
    }

    @Test fun unreadFilterRequestsUnreadOnly() = runTest {
        val v = vm(); advanceUntilIdle()
        v.setUnreadOnly(true); advanceUntilIdle()
        assertEquals(true to 0, api.listCalls.last())
    }

    @Test fun paginationAppendsWithoutDuplicates() = runTest {
        api.list = { _, p -> if (p == 0) PageDto(listOf(dto(3), dto(2)), 0, 2, 3, 2) else PageDto(listOf(dto(2), dto(1)), 1, 2, 3, 2) }
        val v = vm(); advanceUntilIdle()
        v.loadMore(); v.loadMore(); advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), v.ui.value.items.map { it.id })
        assertFalse(v.ui.value.hasNext)
        assertEquals(2, api.listCalls.size)
    }

    @Test fun markReadAppliesOnlyAfterServerSuccessAndIsSingleFlight() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(1)), 0, 20, 1, 1) }
        val gate = CompletableDeferred<Unit>()
        api.read = { gate.await() }
        val v = vm(); advanceUntilIdle()
        v.markRead(1); v.markRead(1); advanceUntilIdle()
        assertFalse(v.ui.value.items.single().isRead)
        assertEquals(1, api.readCalls.size)
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(v.ui.value.items.single().isRead)
        v.markRead(1); advanceUntilIdle()
        assertEquals(1, api.readCalls.size)
    }

    @Test fun markReadFailureLeavesUnreadAndShowsError() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(1)), 0, 20, 1, 1) }
        api.read = { throw IOException("offline") }
        val v = vm(); advanceUntilIdle()
        v.markRead(1); advanceUntilIdle()
        assertFalse(v.ui.value.items.single().isRead)
        assertNotNull(v.ui.value.actionError)
        assertTrue(v.ui.value.markingIds.isEmpty())
    }

    @Test fun markReadNotFoundRemovesItem() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(1), dto(2)), 0, 20, 2, 1) }
        api.read = { throw http(404, "NOTIFICATION_NOT_FOUND") }
        val v = vm(); advanceUntilIdle()
        v.markRead(1); advanceUntilIdle()
        assertEquals(listOf(2L), v.ui.value.items.map { it.id })
    }

    @Test fun markAllReadReloadsFromServer() = runTest {
        var all = false
        api.list = { _, _ -> PageDto(listOf(dto(1, all), dto(2, all)), 0, 20, 2, 1) }
        api.readAll = { all = true }
        val v = vm(); advanceUntilIdle()
        v.markAllRead(); v.markAllRead(); advanceUntilIdle()
        assertEquals(1, api.readAllCalls)
        assertTrue(v.ui.value.items.all { it.isRead })
        assertEquals(2, api.listCalls.size)
        assertTrue(unreadChanged > 0)
    }

    @Test fun markAllFailureKeepsItemsUnread() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(1)), 0, 20, 1, 1) }
        api.readAll = { throw IOException() }
        val v = vm(); advanceUntilIdle()
        v.markAllRead(); advanceUntilIdle()
        assertFalse(v.ui.value.items.single().isRead)
        assertNotNull(v.ui.value.actionError)
    }

    @Test fun accountSwitchClearsStateAndIgnoresStaleResponse() = runTest {
        val gate = CompletableDeferred<PageDto<NotificationDto>>()
        var first = true
        api.list = { _, _ -> if (first) { first = false; gate.await() } else PageDto(listOf(dto(50)), 0, 20, 1, 1) }
        val v = vm(); advanceUntilIdle()
        session.value = profile(2); advanceUntilIdle()
        gate.complete(PageDto(listOf(dto(99)), 0, 20, 1, 1)); advanceUntilIdle()
        assertEquals(listOf(50L), v.ui.value.items.map { it.id })
        assertEquals(2L, v.ui.value.userId)
    }

    @Test fun logoutClearsItems() = runTest {
        api.list = { _, _ -> PageDto(listOf(dto(1)), 0, 20, 1, 1) }
        val v = vm(); advanceUntilIdle()
        session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        assertTrue(v.ui.value.items.isEmpty()); assertNull(v.ui.value.userId)
    }

    @Test fun trackerUsesServerCountResetsOnAccountChangeAndIgnoresStale() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        var count = 3L
        val gate = CompletableDeferred<Unit>()
        var gateOnce = true
        api.unread = { if (gateOnce) { gateOnce = false; gate.await(); 77 } else count }
        val t = UnreadCountTracker(NotificationRepository(api), session, scope)
        scope.testScheduler.advanceUntilIdle()
        session.value = profile(2); scope.testScheduler.advanceUntilIdle()
        assertEquals(3, t.count.value)
        gate.complete(Unit); scope.testScheduler.advanceUntilIdle()
        assertEquals(3, t.count.value)
        count = 0; t.refresh(); scope.testScheduler.advanceUntilIdle()
        assertEquals(0, t.count.value)
        session.value = SessionState.Unauthenticated(); scope.testScheduler.advanceUntilIdle()
        count = 9; t.refresh(); scope.testScheduler.advanceUntilIdle()
        assertEquals(0, t.count.value)
    }
}
