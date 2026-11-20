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
class RoomJoinViewModelTest {
    private class Api(d: DqApi) : DqApi by d {
        var publicCalls = 0
        var codeCalls = 0
        var codes = mutableListOf<String>()
        var publicHandler: suspend (Long) -> RoomDetailDto = { detail(it) }
        var codeHandler: suspend (String) -> RoomDetailDto = { detail(55) }
        override suspend fun joinPublicRoom(roomId: Long): RoomDetailDto { publicCalls++; return publicHandler(roomId) }
        override suspend fun joinRoomByCode(body: JoinByCodeRequest): RoomDetailDto { codeCalls++; codes.add(body.inviteCode); return codeHandler(body.inviteCode) }
    }

    private lateinit var api: Api
    private lateinit var session: MutableStateFlow<SessionState>
    private var changed = 0
    private var unknown = 0

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = Api(unusedApi); session = MutableStateFlow(profileOf(1)); changed = 0; unknown = 0
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(onChanged: () -> Unit = { changed++ }) =
        RoomJoinViewModel(RoomRepository(api), session, onChanged, { unknown++ }).also { runCurrent() }

    @Test fun publicJoinSucceedsOnceAndRefreshesLists() = runTest {
        val vm = vm()
        vm.joinPublic(9001); vm.joinPublic(9001); advanceUntilIdle()
        assertEquals(1, api.publicCalls)
        assertEquals(9001L, vm.ui.value.joinedRoomId)
        assertEquals(1, changed)
        vm.consumeJoined()
        assertNull(vm.ui.value.joinedRoomId)
    }

    @Test fun duplicateClickWhileSubmittingSendsOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.publicHandler = { gate.await(); detail(it) }
        val vm = vm()
        vm.joinPublic(1); runCurrent(); vm.joinPublic(1); vm.joinPublic(2); runCurrent()
        assertTrue(vm.ui.value.submitting)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.publicCalls)
    }

    @Test fun listRefreshFailureDoesNotTurnJoinIntoFailure() = runTest {
        val vm = vm(onChanged = { error("refresh failed") })
        vm.joinPublic(3); advanceUntilIdle()
        assertEquals(3L, vm.ui.value.joinedRoomId)
        assertNull(vm.ui.value.error)
    }

    @Test fun publicJoinServerErrorsShowKoreanMessagesWithoutNavigation() = runTest {
        for (code in listOf("ROOM_FULL", "ROOM_NOT_PUBLIC", "ROOM_NOT_FOUND")) {
            api.publicHandler = { throw httpError(if (code == "ROOM_NOT_PUBLIC") 403 else if (code == "ROOM_FULL") 409 else 404, code) }
            val vm = vm()
            vm.joinPublic(4); advanceUntilIdle()
            assertNotNull(code, vm.ui.value.error)
            assertNull(vm.ui.value.joinedRoomId)
            assertFalse(vm.ui.value.submitting)
        }
    }

    @Test fun alreadyJoinedPublicRoomStillNavigatesToKnownRoom() = runTest {
        api.publicHandler = { throw httpError(409, "ALREADY_JOINED") }
        val vm = vm()
        vm.joinPublic(9002); advanceUntilIdle()
        assertEquals(9002L, vm.ui.value.joinedRoomId)
    }

    @Test fun codeIsNormalizedAndValidatedBeforeRequest() = runTest {
        val vm = vm()
        vm.joinByCode(); advanceUntilIdle()
        assertNotNull(vm.ui.value.codeError)
        vm.onCodeChange("ab12"); vm.joinByCode(); advanceUntilIdle()
        assertNotNull(vm.ui.value.codeError)
        assertEquals(0, api.codeCalls)
        vm.onCodeChange("  abc123 "); vm.joinByCode(); advanceUntilIdle()
        assertEquals(listOf("ABC123"), api.codes)
        assertEquals(55L, vm.ui.value.joinedRoomId)
    }

    @Test fun codeAlreadyJoinedNeverGuessesRoomId() = runTest {
        api.codeHandler = { throw httpError(409, "ALREADY_JOINED") }
        val vm = vm()
        vm.onCodeChange("ABC123"); vm.joinByCode(); advanceUntilIdle()
        assertNull(vm.ui.value.joinedRoomId)
        assertEquals(ALREADY_JOINED_CODE_MESSAGE, vm.ui.value.error)
    }

    @Test fun invalidCodeAndFullRoomShowServerMessages() = runTest {
        val vm = vm()
        api.codeHandler = { throw httpError(404, "INVALID_INVITE_CODE") }
        vm.onCodeChange("ZZZ999"); vm.joinByCode(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertNull(vm.ui.value.joinedRoomId)
        api.codeHandler = { throw httpError(409, "ROOM_FULL") }
        vm.joinByCode(); advanceUntilIdle()
        assertTrue(vm.ui.value.error!!.contains("정원"))
    }

    @Test fun networkErrorIsUnknownOutcomeWithoutAutoRetryAndRefreshesMyRooms() = runTest {
        api.publicHandler = { throw IOException("lost") }
        val vm = vm()
        vm.joinPublic(8); advanceUntilIdle()
        assertTrue(vm.ui.value.outcomeUnknown)
        assertNull(vm.ui.value.joinedRoomId)
        assertEquals(1, api.publicCalls)
        assertEquals(1, unknown)
        assertEquals(0, changed)
    }

    @Test fun accountSwitchDropsLateResultOfPreviousUser() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.publicHandler = { gate.await(); detail(it) }
        val vm = vm()
        vm.joinPublic(9); runCurrent()
        session.value = profileOf(2); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertNull(vm.ui.value.joinedRoomId)
        assertFalse(vm.ui.value.submitting)
        assertEquals(0, changed)
    }
}
