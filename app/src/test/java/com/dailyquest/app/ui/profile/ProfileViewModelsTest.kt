package com.dailyquest.app.ui.profile

import com.dailyquest.app.data.auth.AuthRepository
import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.profile.ProfileErrorMessages
import com.dailyquest.app.data.profile.ProfileRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.AvatarDto
import com.dailyquest.app.data.remote.dto.ChangePasswordRequest
import com.dailyquest.app.data.remote.dto.UpdateMeRequest
import com.dailyquest.app.data.remote.dto.UserMeResponse
import com.dailyquest.app.data.remote.dto.WithdrawRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private fun httpErr(status: Int, code: String) = HttpException(Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType())))

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelsTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var gate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null
        var meFailure: Throwable? = null
        val updates = mutableListOf<String>()
        val passwordCalls = mutableListOf<String>()
        val withdrawCalls = mutableListOf<String?>()
        var meCalls = 0

        override suspend fun updateMe(body: UpdateMeRequest): UserMeResponse {
            updates += body.nickname; gate?.await(); failure?.let { throw it }
            return UserMeResponse(1, "t@example.com", body.nickname, 50)
        }
        override suspend fun changePassword(body: ChangePasswordRequest) { passwordCalls += body.newPassword; gate?.await(); failure?.let { throw it } }
        override suspend fun withdraw(body: WithdrawRequest) { withdrawCalls += body.password; gate?.await(); failure?.let { throw it } }
        override suspend fun me(): UserMeResponse { meCalls++; meFailure?.let { throw it }; return UserMeResponse(1, "t@example.com", "tester", 77, AvatarDto()) }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeApi
    private lateinit var session: SessionManager
    private lateinit var repo: ProfileRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        api = FakeApi(Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as DqApi)
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        session = SessionManager(tokens, emptyList())
        repo = ProfileRepository(api, session, AuthRepository(api, tokens, session))
        session.markAuthenticated(UserProfile(1, "t@example.com", "tester", 10, 0, 0, 0, 0))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun nicknameSaveIsSingleFlightAndMarksSaved() = runTest(dispatcher) {
        val vm = NicknameEditViewModel(repo, session.state)
        api.gate = CompletableDeferred()
        vm.onInput("새닉")
        vm.save(); vm.save(); vm.save()
        runCurrent()
        assertTrue(vm.ui.value.saving)
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("새닉"), api.updates)
        assertTrue(vm.ui.value.saved)
        assertEquals("새닉", (session.state.value as SessionState.Authenticated).profile.nickname)
    }

    @Test fun invalidOrUnchangedNicknameSendsNothing() = runTest(dispatcher) {
        val vm = NicknameEditViewModel(repo, session.state)
        vm.save(); advanceUntilIdle()
        assertTrue(api.updates.isEmpty()); assertNotNull(vm.ui.value.error)
        vm.onInput("a"); vm.save(); advanceUntilIdle()
        assertTrue(api.updates.isEmpty())
    }

    @Test fun duplicateNicknameShowsErrorAndAllowsRetry() = runTest(dispatcher) {
        val vm = NicknameEditViewModel(repo, session.state)
        api.failure = httpErr(409, "DUPLICATE_NICKNAME")
        vm.onInput("다른닉"); vm.save(); advanceUntilIdle()
        assertFalse(vm.ui.value.saving); assertFalse(vm.ui.value.saved); assertNotNull(vm.ui.value.error)
    }

    @Test fun passwordMismatchSendsNothing() = runTest(dispatcher) {
        val vm = PasswordChangeViewModel(repo)
        vm.onCurrent("oldpass12"); vm.onNew("newpass12"); vm.onConfirm("other1234")
        vm.submit(); advanceUntilIdle()
        assertTrue(api.passwordCalls.isEmpty()); assertNotNull(vm.ui.value.error)
    }

    @Test fun passwordSubmitIsSingleFlightAndLogsOut() = runTest(dispatcher) {
        val vm = PasswordChangeViewModel(repo)
        api.gate = CompletableDeferred()
        vm.onCurrent("oldpass12"); vm.onNew("newpass12"); vm.onConfirm("newpass12")
        vm.submit(); vm.submit(); runCurrent()
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.passwordCalls.size)
        assertEquals(LogoutReason.PASSWORD_CHANGED, (session.state.value as SessionState.Unauthenticated).reason)
        assertEquals("", vm.ui.value.new)
    }

    @Test fun socialAccountMarkedUnsupportedOnlyAfterServerSaysSo() = runTest(dispatcher) {
        val vm = PasswordChangeViewModel(repo)
        assertFalse(vm.ui.value.unsupported)
        api.failure = httpErr(409, "NO_PASSWORD_SET")
        vm.onCurrent("oldpass12"); vm.onNew("newpass12"); vm.onConfirm("newpass12")
        vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.unsupported)
    }

    @Test fun lostPasswordResponseThenWrongCurrentExplainsMaybeChanged() = runTest(dispatcher) {
        val vm = PasswordChangeViewModel(repo)
        api.failure = IOException("lost")
        vm.onCurrent("oldpass12"); vm.onNew("newpass12"); vm.onConfirm("newpass12")
        vm.submit(); advanceUntilIdle()
        assertTrue(vm.ui.value.outcomeUnknown)
        assertTrue(session.state.value is SessionState.Authenticated)
        api.failure = httpErr(401, "INVALID_CREDENTIALS")
        vm.submit(); advanceUntilIdle()
        assertEquals(ProfileErrorMessages.PASSWORD_MAYBE_CHANGED, vm.ui.value.error)
    }

    @Test fun withdrawNeverSendsWithoutAgreementAndConfirmation() = runTest(dispatcher) {
        val vm = WithdrawViewModel(repo)
        vm.confirm(); vm.requestConfirm(); advanceUntilIdle()
        assertTrue(api.withdrawCalls.isEmpty()); assertFalse(vm.ui.value.showConfirm)
        vm.onAgreed(true); vm.confirm(); advanceUntilIdle()
        assertTrue("확인 다이얼로그 없이 탈퇴가 실행됨", api.withdrawCalls.isEmpty())
    }

    @Test fun withdrawConfirmIsSingleFlightAndEndsSession() = runTest(dispatcher) {
        val vm = WithdrawViewModel(repo)
        api.gate = CompletableDeferred()
        vm.onPassword("pw123456"); vm.onAgreed(true); vm.requestConfirm()
        vm.confirm(); vm.confirm(); runCurrent()
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf<String?>("pw123456"), api.withdrawCalls)
        assertEquals(LogoutReason.WITHDRAWN, (session.state.value as SessionState.Unauthenticated).reason)
    }

    @Test fun ownerBlockedSetsFlagAndKeepsSession() = runTest(dispatcher) {
        val vm = WithdrawViewModel(repo)
        api.failure = httpErr(409, "OWNER_MUST_TRANSFER")
        vm.onAgreed(true); vm.requestConfirm(); vm.confirm(); advanceUntilIdle()
        assertTrue(vm.ui.value.ownerBlocked); assertFalse(vm.ui.value.saving)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun lostWithdrawResponseWithServer200IsNotTreatedAsSuccess() = runTest(dispatcher) {
        val vm = WithdrawViewModel(repo)
        api.failure = IOException("lost")
        vm.onAgreed(true); vm.requestConfirm(); vm.confirm(); advanceUntilIdle()
        assertEquals(ProfileErrorMessages.WITHDRAW_NOT_APPLIED, vm.ui.value.error)
        assertTrue(session.state.value is SessionState.Authenticated)
    }

    @Test fun profileRefreshUpdatesSessionAndKeepsDataOnFailure() = runTest(dispatcher) {
        val auth = AuthRepository(api, SessionTokens(InMemoryTokenStore(TokenPair("A", "R"))), session)
        val vm = ProfileViewModel(auth, session.state)
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.ui.value.loaded); assertEquals(77, (session.state.value as SessionState.Authenticated).profile.coins)
        api.meFailure = IOException("down")
        vm.refresh(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error)
        assertEquals(77, (session.state.value as SessionState.Authenticated).profile.coins)
        assertNull(null)
    }
}
