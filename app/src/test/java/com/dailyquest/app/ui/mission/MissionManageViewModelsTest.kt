package com.dailyquest.app.ui.mission

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MissionRewardDto
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import com.dailyquest.app.data.remote.dto.UpdateMissionRequest
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
class MissionManageViewModelsTest {

    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var detail: suspend () -> MissionDetailDto = { error("unset") }
        var myRole: String? = "MEMBER"
        var roomFails = false
        var update: suspend (UpdateMissionRequest) -> MissionDto = { error("unset") }
        var delete: suspend () -> Unit = {}
        val updates = mutableListOf<UpdateMissionRequest>()
        var deleteCalls = 0
        var detailCalls = 0
        override suspend fun getMission(missionId: Long): MissionDetailDto { detailCalls++; return detail() }
        override suspend fun getRoom(roomId: Long): RoomDetailDto {
            if (roomFails) throw IOException("x")
            return RoomDetailDto(roomId, "방", isPublic = false, owner = RoomOwnerDto(9, "방장"), myRole = myRole)
        }
        override suspend fun updateMission(missionId: Long, body: UpdateMissionRequest): MissionDto { updates.add(body); return update(body) }
        override suspend fun deleteMission(missionId: Long) { deleteCalls++; delete() }
    }

    private fun detailDto(type: String = "MEMBER", creator: Long? = 1, content: String = "원래 내용") = MissionDetailDto(
        missionId = 5, type = type, content = content, reward = MissionRewardDto("COIN", 50, 0),
        createdBy = creator?.let { RoomOwnerDto(it, "작성자") }, dueAt = "2030-01-01T00:00:00Z",
    )
    private fun missionDto(content: String) = MissionDto(
        missionId = 5, type = "MEMBER", content = content, reward = MissionRewardDto("COIN", 50, 0),
        createdBy = RoomOwnerDto(1, "작성자"), dueAt = "2030-01-01T00:00:00Z",
    )
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var repo: MissionRepository

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused); repo = MissionRepository(api)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun detailVm(userId: Long, onChanged: () -> Unit = {}) =
        MissionDetailViewModel(5, 10, repo, MutableStateFlow(profile(userId)), onChanged = onChanged)

    // ---------- 권한 매트릭스 (서버: 수정=작성자, 삭제=작성자 또는 방장) ----------

    @Test fun creatorSeesEditAndDelete() = runTest {
        api.detail = { detailDto(creator = 1) }
        val vm = detailVm(1); advanceUntilIdle()
        assertTrue(vm.ui.value.canEdit); assertTrue(vm.ui.value.canDelete); assertTrue(vm.ui.value.showMenu)
    }

    @Test fun roomOwnerCanDeleteButNotEditOthersMission() = runTest {
        api.detail = { detailDto(creator = 1) }; api.myRole = "OWNER"
        val vm = detailVm(9); advanceUntilIdle()
        assertFalse(vm.ui.value.canEdit); assertTrue(vm.ui.value.canDelete); assertTrue(vm.ui.value.showMenu)
    }

    @Test fun otherMemberSeesNoMenu() = runTest {
        api.detail = { detailDto(creator = 1) }
        val vm = detailVm(2); advanceUntilIdle()
        assertFalse(vm.ui.value.canEdit); assertFalse(vm.ui.value.canDelete); assertFalse(vm.ui.value.showMenu)
    }

    @Test fun appMissionNeverShowsMenuEvenForOwner() = runTest {
        api.detail = { detailDto(type = "APP", creator = null) }; api.myRole = "OWNER"
        val vm = detailVm(9); advanceUntilIdle()
        assertFalse(vm.ui.value.showMenu)
    }

    @Test fun ownerLookupFailureDoesNotGrantOwnerDelete() = runTest {
        api.detail = { detailDto(creator = 1) }; api.roomFails = true
        val vm = detailVm(9); advanceUntilIdle()
        assertFalse(vm.ui.value.isRoomOwner); assertFalse(vm.ui.value.showMenu)
    }

    @Test fun withdrawnCreatorMissionOnlyOwnerCanDelete() = runTest {
        api.detail = { detailDto(creator = null) }; api.myRole = "OWNER"
        val vm = detailVm(9); advanceUntilIdle()
        assertFalse(vm.ui.value.canEdit); assertTrue(vm.ui.value.canDelete)
    }

    // ---------- 삭제 ----------

    @Test fun deleteSuccessEmitsEventOnceAndNotifiesChange() = runTest {
        var changed = 0
        api.detail = { detailDto(creator = 1) }
        val vm = detailVm(1) { changed++ }; advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.ui.value.deleted); assertEquals(1, changed); assertEquals(1, api.deleteCalls)
        vm.delete(); advanceUntilIdle()
        assertEquals(1, api.deleteCalls)
        vm.consumeDeleted()
        assertFalse(vm.ui.value.deleted)
    }

    @Test fun doubleTapDeleteSendsSingleRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.detail = { detailDto(creator = 1) }; api.delete = { gate.await() }
        val vm = detailVm(1); advanceUntilIdle()
        vm.delete(); vm.delete(); runCurrent()
        assertTrue(vm.ui.value.deleting)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.deleteCalls)
    }

    @Test fun deleteMissionHasProofsShowsMessageAndReloads() = runTest {
        api.detail = { detailDto(creator = 1) }; api.delete = { throw http(409, "MISSION_HAS_PROOFS") }
        val vm = detailVm(1); advanceUntilIdle()
        val before = api.detailCalls
        vm.delete(); advanceUntilIdle()
        assertFalse(vm.ui.value.deleted); assertFalse(vm.ui.value.deleting)
        assertTrue(vm.ui.value.actionError!!.contains("인증"))
        assertEquals(1, api.deleteCalls); assertTrue(api.detailCalls > before)
    }

    @Test fun deleteForbiddenShowsMessageWithoutRetry() = runTest {
        api.detail = { detailDto(creator = 1) }; api.delete = { throw http(403, "FORBIDDEN") }
        val vm = detailVm(1); advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.ui.value.actionError!!.contains("방장")); assertEquals(1, api.deleteCalls)
    }

    @Test fun lostResponseThenMissionGoneCountsAsDeleted() = runTest {
        var gone = false
        api.detail = { if (gone) throw http(404, "MISSION_NOT_FOUND") else detailDto(creator = 1) }
        api.delete = { gone = true; throw IOException("lost") }
        val vm = detailVm(1); advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertTrue(vm.ui.value.deleted); assertEquals(1, api.deleteCalls)
    }

    @Test fun lostResponseWhileMissionStillExistsAsksUserWithoutRetry() = runTest {
        api.detail = { detailDto(creator = 1) }; api.delete = { throw IOException("lost") }
        val vm = detailVm(1); advanceUntilIdle()
        vm.delete(); advanceUntilIdle()
        assertFalse(vm.ui.value.deleted); assertNotNull(vm.ui.value.actionError); assertEquals(1, api.deleteCalls)
    }

    // ---------- 수정 ----------

    private fun editVm(userId: Long, onChanged: () -> Unit = {}) =
        EditMissionViewModel(5, 10, repo, { userId }, now = { 1_000_000_000_000L }, onChanged = onChanged)

    @Test fun editLoadsOriginalContentForCreator() = runTest {
        api.detail = { detailDto(creator = 1) }
        val vm = editVm(1); advanceUntilIdle()
        assertEquals("원래 내용", vm.ui.value.content); assertNull(vm.ui.value.loadError)
    }

    @Test fun editBlockedForNonCreatorIncludingRoomOwner() = runTest {
        api.detail = { detailDto(creator = 1) }; api.myRole = "OWNER"
        val vm = editVm(9); advanceUntilIdle()
        assertNotNull(vm.ui.value.loadError); assertNull(vm.ui.value.original)
        vm.submit(); advanceUntilIdle()
        assertTrue(api.updates.isEmpty())
    }

    @Test fun editSendsOnlyChangedContent() = runTest {
        var changed = 0
        api.detail = { detailDto(creator = 1) }; api.update = { missionDto(it.content!!) }
        val vm = editVm(1) { changed++ }; advanceUntilIdle()
        vm.onContentChange("  바뀐 내용  "); vm.submit(); advanceUntilIdle()
        assertEquals(listOf(UpdateMissionRequest(content = "바뀐 내용")), api.updates)
        assertTrue(vm.ui.value.saved); assertEquals(1, changed)
    }

    @Test fun editSendsDueAtOnlyWhenChosen() = runTest {
        api.detail = { detailDto(creator = 1) }; api.update = { missionDto("원래 내용") }
        val vm = editVm(1); advanceUntilIdle()
        vm.onDueChange(MissionDueOption.THREE_DAYS); vm.submit(); advanceUntilIdle()
        val sent = api.updates.single()
        assertNull(sent.content); assertNotNull(sent.dueAt)
    }

    @Test fun editWithoutChangesDoesNotCallServer() = runTest {
        api.detail = { detailDto(creator = 1) }
        val vm = editVm(1); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertTrue(api.updates.isEmpty()); assertNotNull(vm.ui.value.serverError)
    }

    @Test fun editRejectsBlankAndTooShortContent() = runTest {
        api.detail = { detailDto(creator = 1) }
        val vm = editVm(1); advanceUntilIdle()
        vm.onContentChange("      "); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.contentError)
        vm.onContentChange(" a "); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.contentError); assertTrue(api.updates.isEmpty())
    }

    @Test fun editDoubleSubmitSendsOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.detail = { detailDto(creator = 1) }; api.update = { gate.await(); missionDto(it.content!!) }
        val vm = editVm(1); advanceUntilIdle()
        vm.onContentChange("바뀐 내용"); vm.submit(); vm.submit(); runCurrent()
        gate.complete(Unit); advanceUntilIdle(); vm.submit(); advanceUntilIdle()
        assertEquals(1, api.updates.size)
    }

    @Test fun editMissionHasProofsShowsServerError() = runTest {
        api.detail = { detailDto(creator = 1) }; api.update = { throw http(409, "MISSION_HAS_PROOFS") }
        val vm = editVm(1); advanceUntilIdle()
        vm.onContentChange("바뀐 내용"); vm.submit(); advanceUntilIdle()
        assertFalse(vm.ui.value.saved); assertTrue(vm.ui.value.serverError!!.contains("수정"))
        assertEquals(1, api.updates.size)
    }

    @Test fun editLostResponseIsOutcomeUnknownWithoutRetry() = runTest {
        var changed = 0
        api.detail = { detailDto(creator = 1) }; api.update = { throw IOException("lost") }
        val vm = editVm(1) { changed++ }; advanceUntilIdle()
        vm.onContentChange("바뀐 내용"); vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.outcomeUnknown); assertFalse(vm.ui.value.saved)
        assertEquals(1, api.updates.size); assertEquals(1, changed)
    }
}
