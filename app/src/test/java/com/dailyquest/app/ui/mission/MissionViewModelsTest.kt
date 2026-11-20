package com.dailyquest.app.ui.mission

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionScope
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.CreateMissionRequest
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MissionRewardDto
import com.dailyquest.app.data.remote.dto.MissionRoomBriefDto
import com.dailyquest.app.data.remote.dto.MyRoomMissionsDto
import com.dailyquest.app.data.remote.dto.PageDto
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
class MissionViewModelsTest {

    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var mine: suspend () -> List<MyRoomMissionsDto> = { emptyList() }
        var roomPage: suspend (Long, String?, Int) -> PageDto<MissionDto> = { _, _, _ -> page(emptyList()) }
        var detail: suspend (Long) -> MissionDetailDto = { error("unset") }
        var create: suspend (Long, CreateMissionRequest) -> MissionDto = { _, _ -> dto(1) }
        var mineCalls = 0
        val roomCalls = mutableListOf<Triple<Long, String?, Int>>()
        val createCalls = mutableListOf<Pair<Long, CreateMissionRequest>>()
        override suspend fun listMyMissions(): List<MyRoomMissionsDto> { mineCalls++; return mine() }
        override suspend fun listRoomMissions(roomId: Long, status: String?, type: String?, page: Int, size: Int): PageDto<MissionDto> {
            roomCalls.add(Triple(roomId, status, page)); return roomPage(roomId, status, page)
        }
        override suspend fun getMission(missionId: Long): MissionDetailDto = detail(missionId)
        override suspend fun createMission(roomId: Long, body: CreateMissionRequest): MissionDto {
            createCalls.add(roomId to body); return create(roomId, body)
        }
    }

    private companion object {
        fun dto(id: Long, type: String = "MEMBER", content: String = "미션$id") = MissionDto(
            missionId = id, type = type, content = content, reward = MissionRewardDto("COIN", 50, 0),
            dueAt = "2030-01-01T00:00:00Z", createdAt = "2025-01-01T00:00:00Z",
        )
        fun page(items: List<MissionDto>, p: Int = 0, total: Int = 1) =
            PageDto(items, p, 20, items.size.toLong(), total)
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ ->
        error("not used")
    } as DqApi

    private lateinit var api: FakeApi
    private lateinit var repo: MissionRepository
    private lateinit var session: MutableStateFlow<SessionState>

    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused); repo = MissionRepository(api); session = MutableStateFlow(profile(1))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    // ---------- 내 미션 ----------

    @Test fun myMissionsEmptyWhenNoRooms() = runTest {
        val vm = MyMissionsViewModel(repo, session); advanceUntilIdle()
        assertTrue(vm.ui.value.loaded); assertTrue(vm.ui.value.isEmpty)
        assertEquals(1, api.mineCalls)
    }

    @Test fun myMissionsSplitsAppAndFriendMissionsPerRoom() = runTest {
        api.mine = {
            listOf(
                MyRoomMissionsDto(MissionRoomBriefDto(10, "방A"), listOf(dto(1, "APP"))),
                MyRoomMissionsDto(MissionRoomBriefDto(11, "방B"), listOf(dto(2, "APP"), dto(3).copy(createdBy = com.dailyquest.app.data.remote.dto.RoomOwnerDto(2, "친구")))),
            )
        }
        val vm = MyMissionsViewModel(repo, session); advanceUntilIdle()
        val s = vm.ui.value
        assertEquals(listOf(10L, 11L), s.appMissions.map { it.roomId })
        assertEquals(listOf(3L), s.friendMissions.map { it.mission.missionId })
        assertEquals("방B", s.friendMissions[0].roomName)
    }

    @Test fun myMissionsErrorThenRetry() = runTest {
        api.mine = { throw IOException("x") }
        val vm = MyMissionsViewModel(repo, session); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertFalse(vm.ui.value.loaded)
        api.mine = { listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(dto(1)))) }
        vm.retry(); advanceUntilIdle()
        assertNull(vm.ui.value.error); assertEquals(1, vm.ui.value.groups.size)
    }

    @Test fun myMissionsAccountSwitchClearsAndIgnoresLateResponse() = runTest {
        val gate = CompletableDeferred<List<MyRoomMissionsDto>>()
        api.mine = { gate.await() }
        val vm = MyMissionsViewModel(repo, session); runCurrent()
        api.mine = { emptyList() }
        session.value = profile(2); advanceUntilIdle()
        gate.complete(listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "이전계정방"), listOf(dto(1)))))
        advanceUntilIdle()
        assertEquals(2L, vm.ui.value.userId)
        assertTrue(vm.ui.value.groups.isEmpty())
    }

    @Test fun myMissionsClearedOnLogout() = runTest {
        api.mine = { listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(dto(1)))) }
        val vm = MyMissionsViewModel(repo, session); advanceUntilIdle()
        session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        assertTrue(vm.ui.value.groups.isEmpty()); assertNull(vm.ui.value.userId)
    }

    // ---------- 방별 미션 ----------

    @Test fun roomMissionsLoadsOnlyOwnRoomAndPages() = runTest {
        api.roomPage = { _, _, p -> if (p == 0) page(listOf(dto(1)), 0, 2) else page(listOf(dto(2)), 1, 2) }
        val vm = RoomMissionsViewModel(10, repo, session); advanceUntilIdle()
        assertEquals(listOf(1L), vm.ui.value.missions.map { it.missionId }); assertTrue(vm.ui.value.hasNext)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.ui.value.missions.map { it.missionId }); assertFalse(vm.ui.value.hasNext)
        assertTrue(api.roomCalls.all { it.first == 10L })
        assertTrue(vm.ui.value.missions.all { it.roomId == 10L })
    }

    @Test fun roomMissionsScopeSwitchRequestsExpiredAndDropsStaleResponse() = runTest {
        val gate = CompletableDeferred<PageDto<MissionDto>>()
        api.roomPage = { _, status, _ -> if (status == "ACTIVE") gate.await() else page(listOf(dto(9))) }
        val vm = RoomMissionsViewModel(10, repo, session); runCurrent()
        vm.selectScope(MissionScope.EXPIRED); advanceUntilIdle()
        gate.complete(page(listOf(dto(1)))); advanceUntilIdle()
        assertEquals(MissionScope.EXPIRED, vm.ui.value.scope)
        assertEquals(listOf(9L), vm.ui.value.missions.map { it.missionId })
    }

    @Test fun roomMissionsErrorAndAccountSwitch() = runTest {
        api.roomPage = { _, _, _ -> throw http(403, "FORBIDDEN") }
        val vm = RoomMissionsViewModel(10, repo, session); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertTrue(vm.ui.value.missions.isEmpty())
        api.roomPage = { _, _, _ -> page(listOf(dto(1))) }
        vm.retry(); advanceUntilIdle()
        assertEquals(1, vm.ui.value.missions.size)
        session.value = profile(2); advanceUntilIdle()
        assertEquals(2L, vm.ui.value.userId)
        assertEquals(1, vm.ui.value.missions.size) // 새 계정 기준으로 다시 조회한 결과
        assertTrue(api.roomCalls.size >= 3)
    }

    // ---------- 상세 ----------

    @Test fun detailGoneOn404ClearsDetail() = runTest {
        api.detail = { throw http(404, "MISSION_NOT_FOUND") }
        val vm = MissionDetailViewModel(5, 10, repo, session); advanceUntilIdle()
        assertTrue(vm.ui.value.gone); assertNull(vm.ui.value.detail)
    }

    @Test fun detailNetworkErrorIsRetryableNotGone() = runTest {
        api.detail = { throw IOException() }
        val vm = MissionDetailViewModel(5, 10, repo, session); advanceUntilIdle()
        assertFalse(vm.ui.value.gone); assertNotNull(vm.ui.value.error)
    }

    // ---------- 생성 ----------

    private var created = 0
    private var unknown = 0

    private fun TestScope.createVm(me: Long = 1): CreateMissionViewModel {
        created = 0; unknown = 0
        val vm = CreateMissionViewModel(10, repo, session, { me }, { 1_000_000L }, { created++ }, { unknown++ })
        runCurrent(); return vm
    }

    @Test fun createValidatesWithoutRequest() = runTest {
        val vm = createVm()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.contentError); assertTrue(api.createCalls.isEmpty())
        vm.onContentChange("가"); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.contentError); assertTrue(api.createCalls.isEmpty())
        vm.onContentChange("산책하기"); vm.onAssigneeChange(1); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.assigneeError); assertTrue(api.createCalls.isEmpty())
    }

    @Test fun createToWholeRoomOmitsAssigneeAndUsesServerResult() = runTest {
        api.create = { _, _ -> dto(777, content = "서버결과") }
        val vm = createVm()
        vm.onContentChange("  산책하기 "); vm.submit(); advanceUntilIdle()
        val (roomId, body) = api.createCalls.single()
        assertEquals(10L, roomId); assertEquals("산책하기", body.content); assertNull(body.assigneeUserId)
        assertEquals(50, body.rewardCoins); assertNotNull(body.dueAt)
        assertEquals(777L, vm.ui.value.created?.missionId); assertEquals(1, created)
    }

    @Test fun createToMemberSendsAssigneeAndReward() = runTest {
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.onAssigneeChange(7); vm.stepReward(10); vm.submit(); advanceUntilIdle()
        val body = api.createCalls.single().second
        assertEquals(7L, body.assigneeUserId); assertEquals(60, body.rewardCoins)
    }

    @Test fun doubleSubmitSendsOneRequest() = runTest {
        val gate = CompletableDeferred<MissionDto>()
        api.create = { _, _ -> gate.await() }
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.submit(); vm.submit(); runCurrent(); vm.submit()
        assertTrue(vm.ui.value.submitting)
        gate.complete(dto(1)); advanceUntilIdle()
        assertEquals(1, api.createCalls.size); assertEquals(1, created)
        vm.submit(); advanceUntilIdle()
        assertEquals(1, api.createCalls.size) // 성공 후 소비 전 재제출 불가
    }

    @Test fun successEventIsConsumedOnce() = runTest {
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.created)
        vm.consumeCreated()
        assertNull(vm.ui.value.created)
    }

    @Test fun serverValidationKeepsInputAndRestoresButton() = runTest {
        api.create = { _, _ -> throw http(400, "VALIDATION_ERROR") }
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.submit(); advanceUntilIdle()
        assertFalse(vm.ui.value.submitting); assertNotNull(vm.ui.value.serverError)
        assertEquals("산책하기", vm.ui.value.content); assertNull(vm.ui.value.created); assertEquals(0, created)
    }

    @Test fun forbiddenAndNotAMemberShowServerError() = runTest {
        for (code in listOf("FORBIDDEN" to 403, "NOT_A_MEMBER" to 404)) {
            api.create = { _, _ -> throw http(code.second, code.first) }
            val vm = createVm()
            vm.onContentChange("산책하기"); vm.submit(); advanceUntilIdle()
            assertNotNull(vm.ui.value.serverError); assertFalse(vm.ui.value.outcomeUnknown)
        }
    }

    @Test fun networkErrorIsUnknownOutcomeWithoutRetryAndRefreshesListsOnly() = runTest {
        api.create = { _, _ -> throw IOException("lost") }
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.outcomeUnknown); assertNull(vm.ui.value.created)
        assertEquals(1, api.createCalls.size); assertEquals(1, unknown); assertEquals(0, created)
        assertEquals("산책하기", vm.ui.value.content)
    }

    @Test fun refreshFailureCallbackDoesNotFailCreation() = runTest {
        val vm = CreateMissionViewModel(10, repo, session, { 1 }, { 0 }, { error("refresh failed") }, {})
        runCurrent()
        vm.onContentChange("산책하기"); vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.created); assertNull(vm.ui.value.serverError)
        assertEquals(1, api.createCalls.size)
    }

    @Test fun accountSwitchDropsLateCreateResult() = runTest {
        val gate = CompletableDeferred<MissionDto>()
        api.create = { _, _ -> gate.await() }
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.submit(); runCurrent()
        session.value = profile(2); runCurrent()
        gate.complete(dto(1)); advanceUntilIdle()
        assertNull(vm.ui.value.created); assertEquals("", vm.ui.value.content); assertEquals(0, created)
    }

    @Test fun rewardTextKeepsOnlyDigitsAndSyncsWithButtons() = runTest {
        val vm = createVm()
        vm.onRewardTextChange("1a2"); assertEquals("12", vm.ui.value.rewardText)
        vm.stepReward(10); assertEquals("20", vm.ui.value.rewardText) // 10 단위로 정렬
        vm.onRewardTextChange("500"); vm.stepReward(10); assertEquals("500", vm.ui.value.rewardText)
        vm.onRewardTextChange("13"); vm.stepReward(-10); assertEquals("10", vm.ui.value.rewardText)
        vm.onRewardTextChange(""); vm.stepReward(10); assertEquals("60", vm.ui.value.rewardText)
        vm.onRewardTextChange("70"); vm.stepReward(-10); assertEquals("60", vm.ui.value.rewardText)
    }

    @Test fun invalidRewardTextBlocksSubmitWithError() = runTest {
        val vm = createVm()
        vm.onContentChange("산책하기")
        for (bad in listOf("", "5", "15", "0", "510", "1000")) {
            vm.onRewardTextChange(bad)
            assertNotNull("error for '$bad'", vm.ui.value.rewardError)
            vm.submit(); advanceUntilIdle()
            assertTrue(api.createCalls.isEmpty())
        }
        for (ok in listOf("10", "250", "500")) {
            vm.onRewardTextChange(ok); assertNull(vm.ui.value.rewardError)
        }
    }

    @Test fun typedRewardIsSentToServer() = runTest {
        val vm = createVm()
        vm.onContentChange("산책하기"); vm.onRewardTextChange("120"); vm.submit(); advanceUntilIdle()
        assertEquals(120, api.createCalls.single().second.rewardCoins)
    }

    @Test fun appMissionTypeIsRecognised() = runTest {
        api.mine = { listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(dto(1, "APP")))) }
        val vm = MyMissionsViewModel(repo, session); advanceUntilIdle()
        assertEquals(MissionType.APP, vm.ui.value.groups[0].missions[0].type)
    }
}
