package com.dailyquest.app.ui.room

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.*
import com.dailyquest.app.data.room.RoomRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

private val unusedApi: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
    error("not used")
} as DqApi

private fun httpError(status: Int, code: String) = HttpException(
    Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
)

private fun profileOf(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

private fun detail(id: Long, role: String? = "MEMBER") =
    RoomDetailDto(roomId = id, name = "방", isPublic = true, owner = RoomOwnerDto(7, "o"), myRole = role)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomManageViewModelTest {
    private class Api(d: DqApi) : DqApi by d {
        val calls = mutableListOf<String>()
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        var lastUpdate: UpdateRoomRequest? = null
        private suspend fun enter(name: String) { calls.add(name); gate?.await(); failure?.let { throw it } }
        override suspend fun updateRoom(roomId: Long, body: UpdateRoomRequest): RoomDetailDto { enter("update"); lastUpdate = body; return detail(roomId, "OWNER") }
        override suspend fun deleteRoom(roomId: Long) { enter("delete") }
        override suspend fun leaveRoom(roomId: Long) { enter("leave") }
        override suspend fun kickMember(roomId: Long, userId: Long) { enter("kick:$userId") }
        override suspend fun transferOwner(roomId: Long, body: TransferOwnerRequest): RoomDetailDto { enter("transfer:${body.userId}"); return detail(roomId) }
        override suspend fun regenerateInviteCode(roomId: Long): InviteCodeDto { enter("regenerate"); return InviteCodeDto("NEWCDE") }
    }

    private lateinit var api: Api
    private lateinit var session: MutableStateFlow<SessionState>
    private var lists = 0

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = Api(unusedApi); session = MutableStateFlow(profileOf(1)); lists = 0
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(onLists: () -> Unit = { lists++ }) =
        RoomManageViewModel(10, RoomRepository(api), session, onLists).also { runCurrent() }

    @Test fun updateSendsOnlyGivenFieldsAndEmitsChanged() = runTest {
        val vm = vm()
        vm.update("새 이름", "", null, null); advanceUntilIdle()
        assertEquals(UpdateRoomRequest(name = "새 이름", description = ""), api.lastUpdate)
        assertEquals(RoomManageEvent.Changed(RoomAction.UPDATE), vm.ui.value.event)
        assertEquals(1, lists)
        vm.consumeEvent()
        assertNull(vm.ui.value.event)
    }

    @Test fun deleteAndLeaveEmitGone() = runTest {
        val vm = vm()
        vm.delete(); advanceUntilIdle()
        assertEquals(RoomManageEvent.Gone(RoomAction.DELETE), vm.ui.value.event)
        vm.consumeEvent()
        vm.leave(); advanceUntilIdle()
        assertEquals(RoomManageEvent.Gone(RoomAction.LEAVE), vm.ui.value.event)
        assertEquals(listOf("delete", "leave"), api.calls)
    }

    @Test fun kickAndTransferRefuseSelfWithoutRequest() = runTest {
        val vm = vm()
        vm.kick(1); vm.transferOwner(1); advanceUntilIdle()
        assertTrue(api.calls.isEmpty())
        vm.kick(8); advanceUntilIdle(); vm.transferOwner(8); advanceUntilIdle()
        assertEquals(listOf("kick:8", "transfer:8"), api.calls)
    }

    @Test fun regenerateShowsNoticeThatOldCodeIsInvalid() = runTest {
        val vm = vm()
        vm.regenerateInviteCode(); advanceUntilIdle()
        assertTrue(vm.ui.value.notice!!.contains("이전 코드"))
        assertFalse(vm.ui.value.notice!!.contains("NEWCDE"))
    }

    @Test fun onlyOneRequestAtATime() = runTest {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.delete(); runCurrent(); vm.delete(); vm.leave(); vm.kick(8); runCurrent()
        assertEquals(RoomAction.DELETE, vm.ui.value.busy)
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("delete"), api.calls)
        assertNull(vm.ui.value.busy)
    }

    @Test fun serverRejectionKeepsRoomAndShowsMessage() = runTest {
        api.failure = httpError(409, "OWNER_CANNOT_LEAVE")
        val vm = vm()
        vm.leave(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error)
        assertNull(vm.ui.value.event)
        assertEquals(0, lists)
    }

    @Test fun forbiddenOrMissingTriggersResync() = runTest {
        api.failure = httpError(403, "FORBIDDEN")
        val vm = vm()
        vm.kick(8); advanceUntilIdle()
        assertEquals(RoomManageEvent.Unknown(RoomAction.KICK), vm.ui.value.event)
    }

    @Test fun networkErrorIsUnknownNotSuccessAndNotRetried() = runTest {
        api.failure = IOException("lost")
        val vm = vm()
        vm.delete(); advanceUntilIdle()
        assertEquals(MANAGE_UNKNOWN_MESSAGE, vm.ui.value.error)
        assertEquals(RoomManageEvent.Unknown(RoomAction.DELETE), vm.ui.value.event)
        assertEquals(listOf("delete"), api.calls)
        assertEquals(1, lists)
    }

    @Test fun listRefreshFailureDoesNotFailTheAction() = runTest {
        val vm = vm(onLists = { error("boom") })
        vm.delete(); advanceUntilIdle()
        assertEquals(RoomManageEvent.Gone(RoomAction.DELETE), vm.ui.value.event)
        assertNull(vm.ui.value.error)
    }

    @Test fun accountSwitchDropsPreviousUsersResult() = runTest {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.delete(); runCurrent()
        session.value = profileOf(2); runCurrent()
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertNull(vm.ui.value.event)
        assertNull(vm.ui.value.busy)
        assertEquals(2L, vm.ui.value.myUserId)
        assertEquals(0, lists)
    }
}
