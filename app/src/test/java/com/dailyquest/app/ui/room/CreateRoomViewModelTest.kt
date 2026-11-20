package com.dailyquest.app.ui.room

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.CreateRoomRequest
import com.dailyquest.app.data.remote.dto.CreateRoomResponse
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
class CreateRoomViewModelTest {

    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var handler: suspend (CreateRoomRequest) -> CreateRoomResponse = { error("unset") }
        val requests = mutableListOf<CreateRoomRequest>()
        override suspend fun createRoom(body: CreateRoomRequest): CreateRoomResponse { requests.add(body); return handler(body) }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
        error("not used")
    } as DqApi

    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private var created = 0
    private var unknown = 0

    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )
    private fun ok(id: Long = 501, isPublic: Boolean = false, invite: String? = "SRV501") =
        CreateRoomResponse(roomId = id, name = "서버가정한이름", isPublic = isPublic, inviteCode = invite, ownerId = 1)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused)
        api.handler = { ok() }
        session = MutableStateFlow(profile(1))
        created = 0; unknown = 0
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(isPublic: Boolean = false, onCreated: () -> Unit = { created++ }): CreateRoomViewModel {
        val vm = CreateRoomViewModel(isPublic, RoomRepository(api), session, onCreated, { unknown++ })
        runCurrent()
        return vm
    }

    private fun CreateRoomViewModel.fill(name: String = "우리 방", desc: String = "", members: String = "10") {
        onNameChange(name); onDescriptionChange(desc); onMaxMembersChange(members)
    }

    @Test fun defaultsFollowEntryTabAndAreEditable() = runTest {
        val priv = vm(false)
        assertFalse(priv.ui.value.isPublic)
        assertEquals("10", priv.ui.value.maxMembers)
        priv.onPublicChange(true)
        assertTrue(priv.ui.value.isPublic)
        assertTrue(vm(true).ui.value.isPublic)
    }

    @Test fun privateCreateUsesServerRoomIdAndInviteCode() = runTest {
        val vm = vm(false)
        vm.fill("  우리 방  ", "설명")
        vm.submit(); advanceUntilIdle()
        val c = vm.ui.value.created!!
        assertEquals(501L, c.roomId)
        assertEquals("SRV501", c.inviteCode)
        assertFalse(c.isPublic)
        assertFalse(vm.ui.value.submitting)
        assertEquals(CreateRoomRequest("우리 방", "설명", false, 10), api.requests.single())
        assertEquals(1, created)
    }

    @Test fun publicCreateSendsPublicFlagAndBlankDescriptionAsNull() = runTest {
        api.handler = { ok(502, true, null) }
        val vm = vm(true)
        vm.fill("공개방", "   ", "5")
        vm.submit(); advanceUntilIdle()
        assertNull(vm.ui.value.created!!.inviteCode)
        assertEquals(CreateRoomRequest("공개방", null, true, 5), api.requests.single())
    }

    @Test fun clientValidationFailureSendsNoRequest() = runTest {
        val vm = vm()
        vm.fill("가", "가".repeat(101), "31")
        vm.submit(); advanceUntilIdle()
        val ui = vm.ui.value
        assertNotNull(ui.nameError); assertNotNull(ui.descriptionError); assertNotNull(ui.maxMembersError)
        assertTrue(api.requests.isEmpty())
        assertFalse(ui.submitting)
        vm.fill("   ")
        vm.submit(); advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
        vm.fill("방", "", "1")
        vm.submit(); advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
    }

    @Test fun submitWhileInProgressIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.handler = { gate.await(); ok() }
        val vm = vm()
        vm.fill()
        vm.submit(); runCurrent()
        assertTrue(vm.ui.value.submitting)
        vm.submit(); vm.submit(); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.requests.size)
        // 성공 후 다시 눌러도 새 요청을 보내지 않는다.
        vm.submit(); advanceUntilIdle()
        assertEquals(1, api.requests.size)
    }

    @Test fun serverValidationErrorKeepsInputsAndAllowsFix() = runTest {
        api.handler = { throw http(400, "VALIDATION_ERROR") }
        val vm = vm()
        vm.fill("우리 방", "설명", "7")
        vm.submit(); advanceUntilIdle()
        val ui = vm.ui.value
        assertEquals("입력한 내용을 다시 확인해 주세요.", ui.serverError)
        assertFalse(ui.outcomeUnknown); assertFalse(ui.submitting); assertNull(ui.created)
        assertEquals("우리 방", ui.name); assertEquals("설명", ui.description); assertEquals("7", ui.maxMembers)
        assertEquals(0, created); assertEquals(0, unknown)
    }

    @Test fun networkFailureIsOutcomeUnknownWithoutAutoRetryOrSuccess() = runTest {
        api.handler = { throw IOException("lost") }
        val vm = vm()
        vm.fill("우리 방", "설명", "7")
        vm.submit(); advanceUntilIdle()
        val ui = vm.ui.value
        assertTrue(ui.outcomeUnknown); assertNull(ui.created); assertFalse(ui.submitting)
        assertEquals("우리 방", ui.name)
        assertEquals(1, api.requests.size)
        assertEquals(0, created)
        assertEquals(1, unknown) // 내 방 목록만 조회
        assertTrue(OUTCOME_UNKNOWN_MESSAGE.contains("내 방 목록"))
    }

    @Test fun userCanRetryManuallyAfterUnknownOutcome() = runTest {
        var fail = true
        api.handler = { if (fail) throw IOException("x") else ok() }
        val vm = vm()
        vm.fill()
        vm.submit(); advanceUntilIdle()
        fail = false
        vm.submit(); advanceUntilIdle()
        assertEquals(2, api.requests.size)
        assertNotNull(vm.ui.value.created)
        assertFalse(vm.ui.value.outcomeUnknown)
    }

    @Test fun successEventIsConsumedOnce() = runTest {
        val vm = vm()
        vm.fill()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.created)
        vm.consumeCreated()
        advanceUntilIdle()
        assertNull(vm.ui.value.created)
        assertEquals(1, api.requests.size)
        assertEquals(1, created)
    }

    @Test fun refreshFailureDoesNotTurnSuccessIntoFailure() = runTest {
        val vm = vm(onCreated = { throw IllegalStateException("refresh failed") })
        vm.fill()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.created)
        assertNull(vm.ui.value.serverError); assertFalse(vm.ui.value.outcomeUnknown)
        assertEquals(1, api.requests.size)
    }

    @Test fun accountSwitchDiscardsStateAndIgnoresLateResponse() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.handler = { gate.await(); ok() }
        val vm = vm()
        vm.fill("이전 사용자 방")
        vm.submit(); runCurrent()
        session.value = profile(2); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        val ui = vm.ui.value
        assertNull(ui.created); assertEquals("", ui.name); assertFalse(ui.submitting)
        assertEquals(0, created)
    }

    @Test fun logoutDiscardsCreatedResult() = runTest {
        val vm = vm()
        vm.fill()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.created)
        session.value = SessionState.Unauthenticated(); runCurrent()
        assertNull(vm.ui.value.created); assertEquals("", vm.ui.value.name)
    }
}
