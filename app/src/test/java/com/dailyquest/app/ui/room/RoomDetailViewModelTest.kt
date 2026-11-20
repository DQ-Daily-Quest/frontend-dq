package com.dailyquest.app.ui.room

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomMemberDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import com.dailyquest.app.data.remote.dto.RoomTodayMissionDto
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDetailViewModelTest {

    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var detail: suspend (Long) -> RoomDetailDto = { error("unset") }
        var members: suspend (Long) -> List<RoomMemberDto> = { emptyList() }
        var detailCalls = 0
        var memberCalls = 0
        override suspend fun getRoom(id: Long): RoomDetailDto { detailCalls++; return detail(id) }
        override suspend fun listRoomMembers(id: Long): List<RoomMemberDto> { memberCalls++; return members(id) }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
        error("not used")
    } as DqApi

    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>

    private fun detail(
        id: Long = 1, isPublic: Boolean = false, role: String? = "MEMBER", code: String? = "DQ0001",
        mission: String? = null, name: String = "방$id",
    ) = RoomDetailDto(
        roomId = id, name = name, description = "설명$id", isPublic = isPublic, inviteCode = code,
        owner = RoomOwnerDto(7, "방장님"), memberCount = 2, maxMembers = 10, myRole = role,
        todayMission = mission?.let { RoomTodayMissionDto(5, it) }, createdAt = "2026-01-01T00:00:00",
    )

    private fun member(id: Long, role: String = "MEMBER") = RoomMemberDto(userId = id, nickname = "멤버$id", role = role)
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused)
        api.detail = { detail(it) }
        session = MutableStateFlow(SessionState.Loading)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(roomId: Long = 1, userId: Long = 1, withMembers: Boolean = true): RoomDetailViewModel {
        val vm = RoomDetailViewModel(roomId, RoomRepository(api), session, withMembers)
        runCurrent()
        session.value = profile(userId)
        advanceUntilIdle()
        return vm
    }

    @Test fun `no request before authentication`() = runTest {
        RoomDetailViewModel(1, RoomRepository(api), session)
        advanceUntilIdle()
        assertEquals(0, api.detailCalls)
    }

    @Test fun `detail success shows real name description and owner`() = runTest {
        val ui = vm().ui.value
        assertFalse(ui.loading)
        assertEquals("방1", ui.detail?.name)
        assertEquals("설명1", ui.detail?.description)
        assertEquals("방장님", ui.detail?.owner?.nickname)
        assertNull(ui.failure)
    }

    @Test fun `owner loads members and sees invite code of private room`() = runTest {
        api.detail = { detail(it, role = "OWNER") }
        api.members = { listOf(member(7, "OWNER"), member(8)) }
        val ui = vm().ui.value
        assertTrue(ui.isOwner)
        assertEquals(listOf("멤버7", "멤버8"), ui.members.map { it.nickname })
        assertEquals("OWNER", ui.members.first().role)
        assertEquals("DQ0001", ui.visibleInviteCode)
        assertEquals(1, api.memberCalls)
    }

    @Test fun `member loads members and sees server provided invite code`() = runTest {
        api.members = { listOf(member(8)) }
        val ui = vm().ui.value
        assertTrue(ui.isMember)
        assertFalse(ui.isOwner)
        assertEquals("DQ0001", ui.visibleInviteCode)
        assertEquals(1, ui.members.size)
    }

    @Test fun `non member of public room never calls members api and hides member data`() = runTest {
        api.detail = { detail(it, isPublic = true, role = null, code = null) }
        val ui = vm().ui.value
        assertFalse(ui.isMember)
        assertEquals(0, api.memberCalls)
        assertNull(ui.visibleInviteCode)
        assertTrue(ui.members.isEmpty())
        assertEquals("방1", ui.detail?.name)
    }

    @Test fun `invite code hidden for public room and for non member even if present`() = runTest {
        api.detail = { detail(it, isPublic = true, role = "MEMBER", code = "LEAK") }
        assertNull(vm().ui.value.visibleInviteCode)
        api.detail = { detail(it, isPublic = false, role = null, code = "LEAK") }
        assertNull(vm(userId = 2).ui.value.visibleInviteCode)
    }

    @Test fun `joined flag from list is not a permission basis`() = runTest {
        // 서버가 myRole=null을 주면 목록의 joined와 무관하게 비멤버로 처리한다.
        api.detail = { detail(it, isPublic = true, role = null) }
        vm()
        assertEquals(0, api.memberCalls)
    }

    @Test fun `todayMission is exposed read only for members`() = runTest {
        api.detail = { detail(it, mission = "물 마시기") }
        assertEquals("물 마시기", vm().ui.value.detail?.todayMission?.content)
    }

    @Test fun `404 maps to not found and 403 to forbidden without detail`() = runTest {
        api.detail = { throw http(404, "ROOM_NOT_FOUND") }
        var ui = vm().ui.value
        assertEquals(RoomDetailFailure.NOT_FOUND, ui.failure)
        assertNull(ui.detail)
        assertEquals("존재하지 않거나 삭제된 방이에요.", ui.error)
        api.detail = { throw http(403, "FORBIDDEN") }
        ui = vm(userId = 2).ui.value
        assertEquals(RoomDetailFailure.FORBIDDEN, ui.failure)
        assertEquals(0, api.memberCalls)
    }

    @Test fun `network error is not reported as forbidden and retry recovers`() = runTest {
        api.detail = { throw IOException("offline") }
        val vm = vm()
        assertEquals(RoomDetailFailure.OTHER, vm.ui.value.failure)
        assertNull(vm.ui.value.detail)
        api.detail = { detail(it) }
        vm.retry()
        advanceUntilIdle()
        assertNull(vm.ui.value.failure)
        assertEquals("방1", vm.ui.value.detail?.name)
    }

    @Test fun `members failure keeps detail and retryMembers recovers`() = runTest {
        api.members = { throw IOException("offline") }
        val vm = vm()
        assertEquals("방1", vm.ui.value.detail?.name)
        assertNull(vm.ui.value.failure)
        assertTrue(vm.ui.value.membersError != null)
        assertFalse(vm.ui.value.membersLoaded)
        api.members = { listOf(member(8)) }
        vm.retryMembers()
        advanceUntilIdle()
        assertNull(vm.ui.value.membersError)
        assertEquals(1, vm.ui.value.members.size)
        assertEquals(1, api.detailCalls)
    }

    @Test fun `refresh failure keeps existing detail`() = runTest {
        val vm = vm()
        api.detail = { throw IOException("offline") }
        vm.refresh()
        advanceUntilIdle()
        assertEquals("방1", vm.ui.value.detail?.name)
        assertTrue(vm.ui.value.error != null)
        assertFalse(vm.ui.value.refreshing)
    }

    @Test fun `403 or 404 on refresh drops previous detail invite code and members`() = runTest {
        api.members = { listOf(member(8)) }
        val vm = vm()
        assertEquals("DQ0001", vm.ui.value.visibleInviteCode)
        api.detail = { throw http(403, "FORBIDDEN") }
        vm.refresh()
        advanceUntilIdle()
        val ui = vm.ui.value
        assertEquals(RoomDetailFailure.FORBIDDEN, ui.failure)
        assertNull(ui.detail)
        assertNull(ui.visibleInviteCode)
        assertTrue(ui.members.isEmpty())
    }

    @Test fun `preview mode never loads members`() = runTest {
        vm(withMembers = false)
        assertEquals(0, api.memberCalls)
    }

    @Test fun `each room has its own state with no leakage`() = runTest {
        val a = vm(roomId = 1)
        val b = vm(roomId = 2)
        assertEquals("방1", a.ui.value.detail?.name)
        assertEquals("방2", b.ui.value.detail?.name)
        assertEquals(2L, b.ui.value.roomId)
    }

    @Test fun `late response from before retry is ignored`() = runTest {
        val first = CompletableDeferred<RoomDetailDto>()
        api.detail = { first.await() }
        val vm = RoomDetailViewModel(1, RoomRepository(api), session)
        runCurrent()
        session.value = profile(1)
        runCurrent()
        api.detail = { detail(it, name = "새 응답") }
        vm.retry()
        advanceUntilIdle()
        first.complete(detail(1, name = "늦은 응답"))
        advanceUntilIdle()
        assertEquals("새 응답", vm.ui.value.detail?.name)
    }

    @Test fun `account switch clears previous user's room and ignores late response`() = runTest {
        api.members = { listOf(member(8)) }
        val vm = vm(userId = 1)
        assertEquals("방1", vm.ui.value.detail?.name)
        val late = CompletableDeferred<RoomDetailDto>()
        api.detail = { late.await() }
        session.value = profile(2)
        runCurrent()
        assertNull(vm.ui.value.detail)
        assertTrue(vm.ui.value.members.isEmpty())
        assertTrue(vm.ui.value.loading)
        late.complete(detail(1, name = "이전 계정 응답"))
        advanceUntilIdle()
        assertEquals("이전 계정 응답", vm.ui.value.detail?.name) // 현재 사용자(2)의 정상 응답
    }

    @Test fun `logout clears data and stops requests`() = runTest {
        api.members = { listOf(member(8)) }
        val vm = vm()
        val calls = api.detailCalls
        session.value = SessionState.Unauthenticated()
        advanceUntilIdle()
        assertNull(vm.ui.value.detail)
        assertTrue(vm.ui.value.members.isEmpty())
        assertEquals(calls, api.detailCalls)
    }

    @Test fun `late response after logout is dropped`() = runTest {
        val late = CompletableDeferred<RoomDetailDto>()
        api.detail = { late.await() }
        val vm = RoomDetailViewModel(1, RoomRepository(api), session)
        runCurrent()
        session.value = profile(1)
        runCurrent()
        session.value = SessionState.Unauthenticated()
        runCurrent()
        late.complete(detail(1))
        advanceUntilIdle()
        assertNull(vm.ui.value.detail)
    }
}
