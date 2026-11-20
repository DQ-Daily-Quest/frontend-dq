package com.dailyquest.app.ui.mission

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionState
import com.dailyquest.app.data.proof.toDetail
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionDto
import com.dailyquest.app.data.remote.dto.MissionProofItemDto
import com.dailyquest.app.data.remote.dto.MissionRewardDto
import com.dailyquest.app.data.remote.dto.MissionRoomBriefDto
import com.dailyquest.app.data.remote.dto.MyProofDto
import com.dailyquest.app.data.remote.dto.MyRoomMissionsDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofMissionBriefDto
import com.dailyquest.app.data.remote.dto.ProofRoomRefDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MissionFilterTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var mine: List<MyRoomMissionsDto> = emptyList()
        var details: (Long) -> MissionDetailDto = { error("unset") }
        val detailCalls = mutableListOf<Long>()
        override suspend fun listMyMissions() = mine
        override suspend fun getMission(missionId: Long): MissionDetailDto { detailCalls.add(missionId); return details(missionId) }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("x") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private val future = "2999-01-01T00:00:00Z"
    private val past = "2000-01-01T00:00:00Z"

    private fun m(id: Long, proof: String? = null, count: Long = 0, due: String = future, by: Long? = null, type: String = "MEMBER", assignee: Long? = null) =
        MissionDto(
            id, type, "미션$id", MissionRewardDto("COIN", 50, 0), by?.let { RoomOwnerDto(it, "u$it") },
            assignee?.let { RoomOwnerDto(it, "u$it") }, null, due, proof?.let { MyProofDto(id * 10, it) }, count,
        )

    private fun detail(id: Long, vararg proofs: Pair<Long, String>) = MissionDetailDto(
        id, "MEMBER", "c", dueAt = future, createdBy = RoomOwnerDto(2, "u2"),
        proofs = proofs.map { (uid, st) -> MissionProofItemDto(id * 10 + uid, RoomOwnerDto(uid, "u$uid"), st) },
    )

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused)
        session = MutableStateFlow(SessionState.Authenticated(UserProfile(1, null, "n", 0, 0, 0, 0, 0)))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = MyMissionsViewModel(MissionRepository(api), session)

    @Test fun filtersClassifyServerStateAndCount() = runTest {
        api.mine = listOf(
            MyRoomMissionsDto(
                MissionRoomBriefDto(10, "방"),
                listOf(
                    m(1, by = 2, type = "MEMBER"),                  // 미인증
                    m(2, proof = "PENDING", count = 1, by = 2),     // 승인 대기
                    m(3, proof = "APPROVED", count = 1, by = 2),    // 완료
                    m(4, count = 1, by = 2),                        // 거절 후보
                    m(5, by = 1, assignee = 7),                     // 보낸 미션
                ),
            )
        )
        api.details = { id -> if (id == 4L) detail(4, 1L to "REJECTED") else error("no") }
        val vm = vm(); advanceUntilIdle()
        val s = vm.ui.value; val now = System.currentTimeMillis()
        assertEquals(5, s.count(MissionFilter.ALL, now))
        assertEquals(setOf(1L, 4L), s.filtered(MissionFilter.TODO, now).map { it.mission.missionId }.toSet())
        assertEquals(listOf(2L), s.filtered(MissionFilter.PENDING, now).map { it.mission.missionId })
        assertEquals(listOf(3L), s.filtered(MissionFilter.DONE, now).map { it.mission.missionId })
        assertEquals(listOf(4L), s.filtered(MissionFilter.REJECTED, now).map { it.mission.missionId })
        assertEquals(listOf(5L), s.filtered(MissionFilter.SENT, now).map { it.mission.missionId })
        assertEquals(MissionState.RESUBMIT_NEEDED, s.stateOf(s.filtered(MissionFilter.ALL, now).first { it.mission.missionId == 4L }.mission, now))
        // 내 인증이 있거나 제출이 없는 미션은 상세를 조회하지 않는다.
        assertEquals(listOf(4L), api.detailCalls)
    }

    @Test fun otherUsersRejectionDoesNotMarkMissionAsRejected() = runTest {
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, count = 1, by = 2))))
        api.details = { detail(4, 7L to "REJECTED") }
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.ui.value.rejectedIds.isEmpty())
        assertEquals(0, vm.ui.value.count(MissionFilter.REJECTED, System.currentTimeMillis()))
    }

    @Test fun expiredMissionIsNotResubmittable() = runTest {
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, count = 1, by = 2, due = past))))
        val vm = vm(); advanceUntilIdle()
        assertTrue(api.detailCalls.isEmpty())
        assertEquals(0, vm.ui.value.count(MissionFilter.TODO, System.currentTimeMillis()))
    }

    @Test fun detailFailureKeepsListAndDoesNotMarkRejected() = runTest {
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, count = 1, by = 2))))
        api.details = { throw IOException("x") }
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.ui.value.loaded); assertTrue(vm.ui.value.error == null)
        assertTrue(vm.ui.value.rejectedIds.isEmpty())
        assertEquals(1, vm.ui.value.count(MissionFilter.TODO, System.currentTimeMillis()))
    }

    @Test fun rejectedFlagClearsAfterResubmissionOnRefresh() = runTest {
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, count = 1, by = 2))))
        api.details = { detail(4, 1L to "REJECTED") }
        val vm = vm(); advanceUntilIdle()
        assertEquals(setOf(4L), vm.ui.value.rejectedIds)
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, proof = "PENDING", count = 2, by = 2))))
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.ui.value.rejectedIds.isEmpty())
        assertEquals(1, vm.ui.value.count(MissionFilter.PENDING, System.currentTimeMillis()))
    }

    @Test fun accountSwitchDropsRejectedIdsAndLateDetails() = runTest {
        api.mine = listOf(MyRoomMissionsDto(MissionRoomBriefDto(10, "방"), listOf(m(4, count = 1, by = 2))))
        val gate = CompletableDeferred<Unit>()
        api.details = { detail(4, 1L to "REJECTED") }
        val vm = vm(); advanceUntilIdle()
        assertEquals(setOf(4L), vm.ui.value.rejectedIds)
        api.mine = emptyList()
        session.value = SessionState.Authenticated(UserProfile(9, null, "n", 0, 0, 0, 0, 0))
        advanceUntilIdle()
        assertFalse(4L in vm.ui.value.rejectedIds)
        gate.complete(Unit)
    }

    @Test fun autoApprovalDetectedOnlyFromServerVotes() {
        fun d(type: String, status: String, approved: Int) = ProofDetailDto(
            proofId = 5, missionId = 3, user = RoomOwnerDto(1, "n"), mediaType = "IMAGE", status = status,
            approval = ApprovalDto(approved, 0, 1, null), mission = ProofMissionBriefDto(3, type, "c"), room = ProofRoomRefDto(10, "방"),
        ).toDetail()
        assertTrue(d("APP", "APPROVED", 0).isAutoApproved)
        assertFalse(d("APP", "APPROVED", 1).isAutoApproved)
        assertFalse(d("MEMBER", "APPROVED", 0).isAutoApproved)
        assertFalse(d("APP", "PENDING", 0).isAutoApproved)
        assertFalse(d("APP", "REJECTED", 0).isAutoApproved)
    }
}
