package com.dailyquest.app.ui.proof

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.proof.PendingProof
import com.dailyquest.app.data.proof.PrepareResult
import com.dailyquest.app.data.proof.PreparedMedia
import com.dailyquest.app.data.proof.ProofMediaPreparer
import com.dailyquest.app.data.proof.ProofMediaType
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.proof.UploadRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.MissionDetailDto
import com.dailyquest.app.data.remote.dto.MissionRewardDto
import com.dailyquest.app.data.remote.dto.MyProofDto
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.PendingMissionBriefDto
import com.dailyquest.app.data.remote.dto.PendingProofDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofDto
import com.dailyquest.app.data.remote.dto.ProofMissionBriefDto
import com.dailyquest.app.data.remote.dto.ProofRoomRefDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
import com.dailyquest.app.data.remote.dto.SubmitProofRequest
import com.dailyquest.app.data.remote.dto.UploadDto
import com.dailyquest.app.data.remote.dto.VoteDto
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
import okhttp3.MultipartBody
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
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class ProofViewModelsTest {

    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var upload: suspend () -> UploadDto = { UploadDto("https://cdn/a.jpg", null, "IMAGE") }
        var submit: suspend (SubmitProofRequest) -> ProofDto = { proofDto(5) }
        var mission: suspend (Long) -> MissionDetailDto = { missionDto(myProof = null) }
        var detail: suspend (Long) -> ProofDetailDto = { detailDto() }
        var vote: suspend (Boolean) -> VoteDto = { voteDto() }
        var pending: suspend (Long, Int) -> PageDto<PendingProofDto> = { _, _ -> PageDto(emptyList(), 0, 20, 0, 1) }
        var uploads = 0
        var submits = 0
        var missionCalls = 0
        var detailCalls = 0
        var votes = 0
        override suspend fun upload(file: MultipartBody.Part, purpose: String?): UploadDto { uploads++; return upload() }
        override suspend fun submitProof(missionId: Long, body: SubmitProofRequest): ProofDto { submits++; return submit(body) }
        override suspend fun getMission(missionId: Long): MissionDetailDto { missionCalls++; return mission(missionId) }
        override suspend fun getProof(proofId: Long): ProofDetailDto { detailCalls++; return detail(proofId) }
        override suspend fun approveProof(proofId: Long): VoteDto { votes++; return vote(true) }
        override suspend fun rejectProof(proofId: Long): VoteDto { votes++; return vote(false) }
        override suspend fun listPendingProofs(roomId: Long, page: Int, size: Int) = pending(roomId, page)
    }

    private class FakePreparer : ProofMediaPreparer {
        val discarded = mutableListOf<PreparedMedia>()
        var result: PrepareResult? = null
        override suspend fun prepare(source: String): PrepareResult =
            result ?: PrepareResult.Ready(PreparedMedia(File("/tmp/$source"), "image/jpeg", ProofMediaType.IMAGE, 100, null))
        override fun discard(media: PreparedMedia) { discarded += media }
    }

    private companion object {
        val user = RoomOwnerDto(7, "서버방장")
        fun proofDto(id: Long) = ProofDto(id, 3, RoomOwnerDto(1, "나"), "https://cdn/a.jpg", null, "IMAGE", null, "PENDING")
        fun missionDto(myProof: MyProofDto?, createdBy: RoomOwnerDto? = null, type: String = "APP") = MissionDetailDto(
            missionId = 3, type = type, content = "미션", reward = MissionRewardDto("COIN", 50, 0), createdBy = createdBy,
            dueAt = "2030-01-01T00:00:00Z", myProof = myProof,
        )
        fun detailDto(type: String = "APP", status: String = "PENDING", userId: Long = 7, myVote: String? = null) = ProofDetailDto(
            proofId = 5, missionId = 3, user = RoomOwnerDto(userId, "n"), mediaType = "IMAGE", status = status,
            approval = ApprovalDto(0, 0, 1, myVote), mission = ProofMissionBriefDto(3, type, "c"), room = ProofRoomRefDto(10, "방"),
        )
        fun voteDto(status: String = "PENDING", reward: Int? = null) = VoteDto(
            proofId = 5, status = status, approval = ApprovalDto(1, 0, 1, "APPROVE"),
            rewardGranted = reward?.let { MissionRewardDto("COIN", it, 0) },
        )
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var preparer: FakePreparer
    private lateinit var session: MutableStateFlow<SessionState>

    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused); preparer = FakePreparer(); session = MutableStateFlow(profile(1))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun submitVm(onChanged: () -> Unit = {}) =
        ProofSubmitViewModel(3, ProofRepository(api), UploadRepository(api), MissionRepository(api), preparer, session, { 1L }, onChanged)

    private fun ProofSubmitViewModel.withMedia() { onMediaSelected("a") }

    // ---------- 제출 ----------

    @Test fun submitRequiresMedia() = runTest {
        val vm = submitVm(); advanceUntilIdle()
        assertFalse(vm.ui.value.canSubmit)
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.mediaError); assertEquals(0, api.uploads)
    }

    @Test fun successUploadsOnceSubmitsAndNotifiesOnce() = runTest {
        var changed = 0
        val vm = submitVm { changed++ }; vm.withMedia(); advanceUntilIdle()
        assertTrue(vm.ui.value.canSubmit)
        vm.submit(); advanceUntilIdle()
        assertEquals(5L, vm.ui.value.done?.proofId); assertFalse(vm.ui.value.done!!.recovered)
        assertEquals(1, api.uploads); assertEquals(1, api.submits); assertEquals(1, changed)
        assertNull(vm.ui.value.media); assertEquals(1, preparer.discarded.size)
    }

    @Test fun doubleSubmitSendsOnlyOneRequest() = runTest {
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); vm.submit(); vm.submit(); advanceUntilIdle()
        assertEquals(1, api.uploads); assertEquals(1, api.submits)
        vm.submit(); advanceUntilIdle() // 성공 후에도 무시
        assertEquals(1, api.submits)
    }

    @Test fun submitFailureRetryDoesNotReupload() = runTest {
        api.submit = { throw http(409, "MISSION_EXPIRED") }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertTrue(vm.ui.value.uploaded); assertNotNull(vm.ui.value.media)
        api.submit = { proofDto(6) }
        vm.submit(); advanceUntilIdle()
        assertEquals(1, api.uploads); assertEquals(2, api.submits); assertEquals(6L, vm.ui.value.done?.proofId)
    }

    @Test fun uploadFailureKeepsMediaAndAllowsRetry() = runTest {
        api.upload = { throw http(400, "FILE_TOO_LARGE") }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertEquals(0, api.submits); assertFalse(vm.ui.value.uploaded)
        api.upload = { UploadDto("https://cdn/b.jpg", null, "IMAGE") }
        vm.submit(); advanceUntilIdle()
        assertNotNull(vm.ui.value.done)
    }

    @Test fun lostSubmitResponseRecoversWhenServerHasMyProof() = runTest {
        api.submit = { throw IOException("reset") }
        api.mission = { missionDto(MyProofDto(5, "PENDING")) }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.done!!.recovered); assertEquals(1, api.submits); assertEquals(5L, vm.ui.value.done?.proofId)
    }

    @Test fun lostSubmitResponseWithoutProofAllowsManualRetryOnly() = runTest {
        api.submit = { throw IOException("reset") }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertNull(vm.ui.value.done); assertFalse(vm.ui.value.outcomeUnknown); assertEquals(1, api.submits)
    }

    @Test fun unverifiableOutcomeReverifiesBeforeResubmitting() = runTest {
        api.submit = { throw IOException("reset") }
        api.mission = { throw IOException("down") }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.outcomeUnknown)
        api.mission = { missionDto(MyProofDto(5, "APPROVED")) }
        vm.submit(); advanceUntilIdle()
        assertEquals(1, api.submits); assertTrue(vm.ui.value.done!!.recovered)
    }

    @Test fun alreadySubmittedSyncsListsAndShowsMessage() = runTest {
        api.submit = { throw http(409, "PROOF_ALREADY_SUBMITTED") }
        var changed = 0
        val vm = submitVm { changed++ }; vm.withMedia(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()
        assertEquals(1, changed); assertTrue(vm.ui.value.error!!.contains("이미"))
    }

    @Test fun rejectedMediaShowsErrorAndKeepsNoSelection() = runTest {
        preparer.result = PrepareResult.Rejected("영상은 5초 이하만 가능해요.")
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        assertNull(vm.ui.value.media); assertEquals("영상은 5초 이하만 가능해요.", vm.ui.value.mediaError)
    }

    @Test fun clearMediaDiscardsTempFile() = runTest {
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.clearMedia()
        assertNull(vm.ui.value.media); assertEquals(1, preparer.discarded.size)
    }

    @Test fun tooLongTextBlocksSubmit() = runTest {
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.onTextChange("a".repeat(301))
        assertFalse(vm.ui.value.canSubmit); assertNotNull(vm.ui.value.textError)
        vm.submit(); advanceUntilIdle()
        assertEquals(0, api.uploads)
    }

    @Test fun accountSwitchDiscardsMediaAndState() = runTest {
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.onTextChange("hi")
        session.value = profile(2); advanceUntilIdle()
        assertNull(vm.ui.value.media); assertEquals("", vm.ui.value.text); assertEquals(2L, vm.ui.value.myUserId)
        assertEquals(1, preparer.discarded.size)
    }

    @Test fun cancelUploadStopsBeforeSubmit() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<UploadDto>()
        api.upload = { gate.await() }
        val vm = submitVm(); vm.withMedia(); advanceUntilIdle()
        vm.submit(); runCurrent()
        assertEquals(SubmitPhase.UPLOADING, vm.ui.value.phase)
        vm.cancelUpload(); gate.complete(UploadDto("https://cdn/a.jpg", null, "IMAGE")); advanceUntilIdle()
        assertEquals(SubmitPhase.IDLE, vm.ui.value.phase); assertEquals(0, api.submits); assertNotNull(vm.ui.value.media)
    }

    // ---------- 상세·투표 ----------

    private fun detailVm(onChanged: () -> Unit = {}) =
        ProofDetailViewModel(5, ProofRepository(api), MissionRepository(api), session, onChanged = onChanged)

    @Test fun approveShowsNoRewardUnlessServerGrantsIt() = runTest {
        val vm = detailVm(); advanceUntilIdle()
        assertTrue(vm.ui.value.canVote)
        vm.approve(); advanceUntilIdle()
        assertFalse(vm.ui.value.voteNotice!!.contains("보상"))
        api.vote = { voteDto("APPROVED", reward = 50) }
        vm.approve(); advanceUntilIdle()
        assertTrue(vm.ui.value.voteNotice!!.contains("+50"))
    }

    @Test fun voteReloadsDetailAndNotifies() = runTest {
        var changed = 0
        val vm = detailVm { changed++ }; advanceUntilIdle()
        val before = api.detailCalls
        api.detail = { detailDto(status = "APPROVED") }
        vm.approve(); advanceUntilIdle()
        assertEquals(before + 1, api.detailCalls); assertEquals(1, changed); assertFalse(vm.ui.value.canVote)
    }

    @Test fun doubleVoteSendsOneRequest() = runTest {
        val vm = detailVm(); advanceUntilIdle()
        vm.approve(); vm.reject(); vm.approve(); advanceUntilIdle()
        assertEquals(1, api.votes)
    }

    @Test fun ownProofCannotBeVoted() = runTest {
        api.detail = { detailDto(userId = 1) }
        val vm = detailVm(); advanceUntilIdle()
        assertFalse(vm.ui.value.canVote)
        vm.approve(); advanceUntilIdle()
        assertEquals(0, api.votes)
    }

    @Test fun alreadyFinalizedShowsServerMessageAndReloads() = runTest {
        api.vote = { throw http(409, "PROOF_ALREADY_FINALIZED") }
        val vm = detailVm(); advanceUntilIdle()
        vm.reject(); advanceUntilIdle()
        assertTrue(vm.ui.value.voteError!!.contains("확정"))
        assertNull(vm.ui.value.voteNotice)
    }

    @Test fun lostVoteResponseDoesNotAssumeSuccessAndReloads() = runTest {
        api.vote = { throw IOException("reset") }
        val vm = detailVm(); advanceUntilIdle()
        val before = api.detailCalls
        vm.approve(); advanceUntilIdle()
        assertEquals(VOTE_OUTCOME_UNKNOWN_MESSAGE, vm.ui.value.voteError); assertNull(vm.ui.value.voteNotice)
        assertEquals(before + 1, api.detailCalls)
    }

    @Test fun forbiddenOrMissingClearsPreviousDetail() = runTest {
        val vm = detailVm(); advanceUntilIdle()
        assertNotNull(vm.ui.value.detail)
        api.detail = { throw http(404, "PROOF_NOT_FOUND") }
        vm.retry(); advanceUntilIdle()
        assertNull(vm.ui.value.detail); assertTrue(vm.ui.value.gone)
    }

    @Test fun memberProofOnlyCreatorCanVote() = runTest {
        api.detail = { detailDto(type = "MEMBER") }
        api.mission = { missionDto(null, createdBy = RoomOwnerDto(2, "생성자"), type = "MEMBER") }
        val vm = detailVm(); advanceUntilIdle()
        assertEquals(2L, vm.ui.value.missionCreatorId); assertFalse(vm.ui.value.canVote)
        session.value = profile(2); advanceUntilIdle()
        assertTrue(vm.ui.value.canVote)
    }

    @Test fun accountSwitchClearsDetailAndIgnoresLateResponse() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<ProofDetailDto>()
        val vm = detailVm(); advanceUntilIdle()
        api.detail = { gate.await() }
        session.value = profile(2); runCurrent()
        assertNull(vm.ui.value.detail); assertEquals(2L, vm.ui.value.userId)
        session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        gate.complete(detailDto()); advanceUntilIdle()
        assertNull(vm.ui.value.detail); assertNull(vm.ui.value.userId)
    }

    // ---------- 승인 대기 목록 ----------

    private fun pend(id: Long) = PendingProofDto(id, user, PendingMissionBriefDto(3, "c"), "IMAGE", null, "PENDING")

    @Test fun pendingPagesAndAppends() = runTest {
        api.pending = { _, p -> PageDto(listOf(pend(p + 1L)), p, 20, 2, 2) }
        val vm = PendingProofsViewModel(10, ProofRepository(api), session); advanceUntilIdle()
        assertEquals(listOf(1L), vm.ui.value.items.map { it.proofId }); assertTrue(vm.ui.value.hasNext)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.ui.value.items.map { it.proofId }); assertFalse(vm.ui.value.hasNext)
    }

    @Test fun pendingAccountSwitchClearsOldItems() = runTest {
        api.pending = { _, _ -> PageDto(listOf(pend(1)), 0, 20, 1, 1) }
        val vm = PendingProofsViewModel(10, ProofRepository(api), session); advanceUntilIdle()
        api.pending = { _, _ -> throw http(403, "FORBIDDEN") }
        session.value = profile(2); advanceUntilIdle()
        assertTrue(vm.ui.value.items.isEmpty()); assertTrue(vm.ui.value.gone)
    }

    @Test fun pendingEmptyIsLoadedNotError() = runTest {
        val vm = PendingProofsViewModel(10, ProofRepository(api), session); advanceUntilIdle()
        assertTrue(vm.ui.value.loaded); assertTrue(vm.ui.value.items.isEmpty()); assertNull(vm.ui.value.error)
    }
}
