package com.dailyquest.app.ui.proof

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.ApprovalDto
import com.dailyquest.app.data.remote.dto.ProofDetailDto
import com.dailyquest.app.data.remote.dto.ProofMissionBriefDto
import com.dailyquest.app.data.remote.dto.ProofRoomRefDto
import com.dailyquest.app.data.remote.dto.ReactionRequest
import com.dailyquest.app.data.remote.dto.ReactionSummaryDto
import com.dailyquest.app.data.remote.dto.RoomOwnerDto
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
class ReactionDeleteViewModelTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var server = mutableListOf<ReactionSummaryDto>()
        var status = "PENDING"
        var ownerId = 1L
        var add: suspend (String) -> List<ReactionSummaryDto> = { e -> server.add(ReactionSummaryDto(e, 1, true)); server.toList() }
        var remove: suspend (String) -> List<ReactionSummaryDto> = { e -> server.removeAll { it.emoji == e }; server.toList() }
        var delete: suspend () -> Unit = {}
        val calls = mutableListOf<String>()
        var detailCalls = 0
        override suspend fun getProof(proofId: Long): ProofDetailDto {
            detailCalls++
            return ProofDetailDto(
                5, 3, RoomOwnerDto(ownerId, "n"), mediaType = "IMAGE", status = status, approval = ApprovalDto(0, 0, 1),
                mission = ProofMissionBriefDto(3, "APP", "c"), room = ProofRoomRefDto(10, "방"), reactions = server.toList(),
            )
        }
        override suspend fun addReaction(proofId: Long, body: ReactionRequest): List<ReactionSummaryDto> { calls += "add:${body.emoji}"; return add(body.emoji) }
        override suspend fun removeReaction(proofId: Long, body: ReactionRequest): List<ReactionSummaryDto> { calls += "remove:${body.emoji}"; return remove(body.emoji) }
        override suspend fun deleteProof(proofId: Long) { calls += "delete"; delete() }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))
    private fun http(status: Int, code: String) = HttpException(
        Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType()))
    )

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()); api = FakeApi(unused); session = MutableStateFlow(profile(1)) }
    @After fun tearDown() = Dispatchers.resetMain()

    private var changed = 0
    private fun vm() = ProofDetailViewModel(5, ProofRepository(api), MissionRepository(unused), session, onChanged = { changed++ })
    private fun ProofDetailViewModel.count(e: String) = ui.value.detail!!.reactions.first { it.emoji == e }

    @Test fun addThenRemoveFollowsServerList() = runTest {
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🔥"); advanceUntilIdle()
        assertEquals(1, v.count("🔥").count); assertTrue(v.count("🔥").reacted)
        v.toggleReaction("🔥"); advanceUntilIdle()
        assertEquals(0, v.count("🔥").count); assertFalse(v.count("🔥").reacted)
        assertEquals(listOf("add:🔥", "remove:🔥"), api.calls)
    }

    @Test fun multipleDifferentEmojisCanBeSelected() = runTest {
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🔥"); advanceUntilIdle(); v.toggleReaction("👏"); advanceUntilIdle()
        assertTrue(v.count("🔥").reacted && v.count("👏").reacted)
    }

    @Test fun duplicateTapWhileInFlightIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.add = { e -> gate.await(); listOf(ReactionSummaryDto(e, 1, true)) }
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🔥"); v.toggleReaction("🔥"); v.toggleReaction("❤️"); runCurrent()
        assertTrue(v.ui.value.reacting)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("add:🔥"), api.calls)
    }

    @Test fun serverErrorKeepsPreviousStateAndShowsMessage() = runTest {
        api.add = { throw http(400, "INVALID_EMOJI") }
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🔥"); advanceUntilIdle()
        assertEquals(0, v.count("🔥").count); assertEquals("사용할 수 없는 리액션이에요.", v.ui.value.reactionError)
        assertFalse(v.ui.value.reacting)
    }

    @Test fun lostResponseReloadsServerStateWithoutResending() = runTest {
        // 서버에는 반영됐지만 응답이 유실된 경우: 상세를 다시 읽어 실제 상태를 보여 주고, 같은 요청을 다시 보내지 않는다.
        api.add = { e -> api.server.add(ReactionSummaryDto(e, 1, true)); throw IOException("lost") }
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("😂"); advanceUntilIdle()
        assertEquals(listOf("add:😂"), api.calls)
        assertTrue(v.count("😂").reacted)
        assertEquals(REACTION_OUTCOME_UNKNOWN_MESSAGE, v.ui.value.reactionError)
    }

    @Test fun unknownEmojiIsRejectedLocally() = runTest {
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🤖"); advanceUntilIdle()
        assertTrue(api.calls.isEmpty())
    }

    @Test fun lateReactionResponseAfterAccountSwitchIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.add = { e -> gate.await(); listOf(ReactionSummaryDto(e, 9, true)) }
        val v = vm(); advanceUntilIdle()
        v.toggleReaction("🔥"); runCurrent()
        api.add = { listOf() }
        session.value = profile(2); advanceUntilIdle()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(0, v.count("🔥").count)
        assertFalse(v.ui.value.reacting)
    }

    @Test fun deleteVisibleOnlyForOwnPendingProof() = runTest {
        var v = vm(); advanceUntilIdle()
        assertTrue(v.ui.value.canDelete)
        api.ownerId = 2; v = vm(); advanceUntilIdle()
        assertFalse(v.ui.value.canDelete)
        api.ownerId = 1; api.status = "APPROVED"; v = vm(); advanceUntilIdle()
        assertFalse(v.ui.value.canDelete)
    }

    @Test fun deleteSuccessMarksDeletedAndNotifiesOnce() = runTest {
        val v = vm(); advanceUntilIdle()
        v.requestDelete(); v.requestDelete(); advanceUntilIdle()
        assertTrue(v.ui.value.deleted); assertEquals(1, api.calls.count { it == "delete" }); assertEquals(1, changed)
    }

    @Test fun deleteForbiddenShowsMessageAndNotDeleted() = runTest {
        api.delete = { throw http(409, "PROOF_ALREADY_FINALIZED") }
        val v = vm(); advanceUntilIdle()
        v.requestDelete(); advanceUntilIdle()
        assertFalse(v.ui.value.deleted); assertNotNull(v.ui.value.deleteError); assertEquals(0, changed)
    }

    @Test fun lostDeleteResponseReloadsAndNeverClaimsSuccess() = runTest {
        api.delete = { throw IOException("lost") }
        val v = vm(); advanceUntilIdle()
        v.requestDelete(); advanceUntilIdle()
        assertFalse(v.ui.value.deleted); assertEquals(DELETE_OUTCOME_UNKNOWN_MESSAGE, v.ui.value.deleteError)
        assertEquals(1, api.calls.count { it == "delete" }); assertTrue(api.detailCalls >= 2)
        assertNull(v.ui.value.error)
    }
}
