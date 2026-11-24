package com.dailyquest.app.ui.auth

import com.dailyquest.app.data.auth.PasswordResetMessages
import com.dailyquest.app.data.auth.PasswordResetRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.PasswordResetConfirmRequest
import com.dailyquest.app.data.remote.dto.PasswordResetRequest
import com.dailyquest.app.data.remote.dto.PasswordResetVerifyRequest
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

private fun httpErr(status: Int, code: String) =
    HttpException(Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType())))

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var gate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null
        val requests = mutableListOf<String>()
        val verifies = mutableListOf<Pair<String, String>>()
        val confirms = mutableListOf<Triple<String, String, String>>()

        override suspend fun requestPasswordReset(body: PasswordResetRequest) { requests += body.email; gate?.await(); failure?.let { throw it } }
        override suspend fun verifyPasswordReset(body: PasswordResetVerifyRequest) { verifies += body.email to body.code; gate?.await(); failure?.let { throw it } }
        override suspend fun confirmPasswordReset(body: PasswordResetConfirmRequest) {
            confirms += Triple(body.email, body.code, body.newPassword); gate?.await(); failure?.let { throw it }
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeApi
    private var now = 1_000L
    private lateinit var vm: PasswordResetViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        api = FakeApi(Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as DqApi)
        vm = PasswordResetViewModel(PasswordResetRepository(api)) { now }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.toCode() {
        vm.onEmail("a@b.com"); vm.submitEmail(); advanceUntilIdle()
        assertEquals(ResetStep.CODE, vm.ui.value.step)
    }

    private fun kotlinx.coroutines.test.TestScope.toPassword(code: String = "012345") {
        toCode(); vm.onCode(code); vm.verify(); advanceUntilIdle()
        assertEquals(ResetStep.PASSWORD, vm.ui.value.step)
    }

    @Test fun invalidEmailSendsNothing() = runTest(dispatcher) {
        vm.onEmail("nope"); vm.submitEmail(); advanceUntilIdle()
        assertTrue(api.requests.isEmpty()); assertNotNull(vm.ui.value.error); assertEquals(ResetStep.EMAIL, vm.ui.value.step)
        vm.onEmail("nope2"); assertNull(vm.ui.value.error)
    }

    @Test fun requestSuccessMovesToCodeAndStartsCooldown() = runTest(dispatcher) {
        toCode()
        assertEquals(PasswordResetMessages.SENT, vm.ui.value.notice)
        assertEquals(60, vm.ui.value.resendRemainingSeconds(now))
        assertEquals(1, vm.ui.value.resendRemainingSeconds(now + 59_001))
        assertEquals(0, vm.ui.value.resendRemainingSeconds(now + 60_000))
    }

    @Test fun duplicateRequestClicksSendOnce() = runTest(dispatcher) {
        api.gate = CompletableDeferred()
        vm.onEmail("a@b.com"); vm.submitEmail(); vm.submitEmail(); vm.submitEmail(); runCurrent()
        assertTrue(vm.ui.value.requesting)
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.requests.size)
    }

    @Test fun resendBlockedDuringCooldownAndResetsCodeAfter() = runTest(dispatcher) {
        toCode()
        vm.onCode("123456")
        vm.resend(); advanceUntilIdle()
        assertEquals(1, api.requests.size)
        now += 60_000
        vm.resend(); advanceUntilIdle()
        assertEquals(2, api.requests.size)
        assertEquals("", vm.ui.value.code); assertFalse(vm.ui.value.verified)
        assertEquals(PasswordResetMessages.RESENT, vm.ui.value.notice)
        assertEquals(60, vm.ui.value.resendRemainingSeconds(now))
    }

    @Test fun failedResendDoesNotClaimNewCode() = runTest(dispatcher) {
        toCode(); vm.onCode("123456"); now += 60_000
        api.failure = httpErr(503, "MAIL_UNAVAILABLE")
        vm.resend(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertNull(vm.ui.value.notice)
        assertEquals("123456", vm.ui.value.code)
    }

    @Test fun lostRequestResponseIsUncertainAndStillCooldowns() = runTest(dispatcher) {
        api.failure = IOException("lost")
        vm.onEmail("a@b.com"); vm.submitEmail(); advanceUntilIdle()
        val s = vm.ui.value
        assertEquals(ResetStep.EMAIL, s.step); assertTrue(s.codeMayBeSent); assertEquals(PasswordResetMessages.SEND_UNCONFIRMED, s.error)
        assertEquals(1, api.requests.size)
        vm.goToCodeEntry(); assertEquals(ResetStep.CODE, vm.ui.value.step)
    }

    @Test fun wrongCodeShowsGuidanceAndStaysOnCodeStep() = runTest(dispatcher) {
        toCode(); api.failure = httpErr(400, "INVALID_RESET_CODE")
        vm.onCode("111111"); vm.verify(); advanceUntilIdle()
        assertEquals(ResetStep.CODE, vm.ui.value.step); assertEquals(PasswordResetMessages.CODE_INVALID, vm.ui.value.error)
        assertFalse(vm.ui.value.verified)
    }

    @Test fun shortCodeSendsNothing() = runTest(dispatcher) {
        toCode(); vm.onCode("12345"); vm.verify(); advanceUntilIdle()
        assertTrue(api.verifies.isEmpty()); assertNotNull(vm.ui.value.error)
    }

    @Test fun verifyKeepsLeadingZeroAndConfirmReusesSameCode() = runTest(dispatcher) {
        toPassword("012345")
        assertEquals(listOf("a@b.com" to "012345"), api.verifies)
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("N3wP@ssword"); vm.submitPassword(); advanceUntilIdle()
        assertEquals(listOf(Triple("a@b.com", "012345", "N3wP@ssword")), api.confirms)
    }

    @Test fun confirmSuccessClearsSensitiveStateAndDoesNotLogIn() = runTest(dispatcher) {
        toPassword()
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("N3wP@ssword"); vm.submitPassword(); advanceUntilIdle()
        val s = vm.ui.value
        assertTrue(s.done); assertEquals("", s.code); assertEquals("", s.newPassword); assertEquals("", s.confirm); assertEquals("", s.email)
    }

    @Test fun passwordValidationBlocksSubmit() = runTest(dispatcher) {
        toPassword()
        vm.onNewPassword("short"); vm.onConfirm("short"); vm.submitPassword(); advanceUntilIdle()
        assertTrue(api.confirms.isEmpty()); assertNotNull(vm.ui.value.error)
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("other-pass1"); vm.submitPassword(); advanceUntilIdle()
        assertTrue(api.confirms.isEmpty())
    }

    @Test fun duplicateConfirmSubmitsOnce() = runTest(dispatcher) {
        toPassword()
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("N3wP@ssword")
        api.gate = CompletableDeferred()
        vm.submitPassword(); vm.submitPassword(); runCurrent()
        assertTrue(vm.ui.value.saving)
        api.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.confirms.size)
    }

    @Test fun codeExpiredBeforeConfirmReturnsToCodeStep() = runTest(dispatcher) {
        toPassword()
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("N3wP@ssword")
        api.failure = httpErr(400, "INVALID_RESET_CODE"); vm.submitPassword(); advanceUntilIdle()
        val s = vm.ui.value
        assertEquals(ResetStep.CODE, s.step); assertEquals("", s.code); assertEquals("", s.newPassword); assertFalse(s.verified); assertFalse(s.done)
    }

    @Test fun lostConfirmResponseIsNeverShownAsSuccess() = runTest(dispatcher) {
        toPassword()
        vm.onNewPassword("N3wP@ssword"); vm.onConfirm("N3wP@ssword")
        api.failure = IOException("lost"); vm.submitPassword(); advanceUntilIdle()
        val s = vm.ui.value
        assertFalse(s.done); assertTrue(s.confirmUnknown); assertEquals(PasswordResetMessages.CONFIRM_UNCONFIRMED, s.error)
        assertEquals(1, api.confirms.size)
        // 재시도에서 코드가 이미 소모됐다고 답해도 성공이라 단정하지 않는다.
        api.failure = httpErr(400, "INVALID_RESET_CODE"); vm.submitPassword(); advanceUntilIdle()
        assertFalse(vm.ui.value.done); assertEquals(PasswordResetMessages.CONFIRM_MAYBE_DONE, vm.ui.value.error)
    }

    @Test fun changingEmailResetsVerificationAndDropsLateResponse() = runTest(dispatcher) {
        api.gate = CompletableDeferred()
        vm.onEmail("a@b.com"); vm.submitEmail(); runCurrent()
        // 요청 중에는 이메일을 바꿀 수 없다.
        vm.onEmail("c@d.com"); assertEquals("a@b.com", vm.ui.value.email)
        api.gate!!.complete(Unit); advanceUntilIdle()
        api.gate = null
        vm.back(); vm.onEmail("c@d.com")
        val s = vm.ui.value
        assertEquals("c@d.com", s.email); assertNull(s.codeEmail); assertEquals(0, s.resendRemainingSeconds(now)); assertFalse(s.verified)
    }

    @Test fun sameEmailWithinCooldownSkipsNewRequest() = runTest(dispatcher) {
        toCode(); vm.back()
        vm.submitEmail(); advanceUntilIdle()
        assertEquals(1, api.requests.size); assertEquals(ResetStep.CODE, vm.ui.value.step)
    }

    @Test fun backNavigationWalksStepsAndSignalsExitAtStart() = runTest(dispatcher) {
        toPassword()
        assertTrue(vm.back()); assertEquals(ResetStep.CODE, vm.ui.value.step)
        assertTrue(vm.back()); assertEquals(ResetStep.EMAIL, vm.ui.value.step)
        assertFalse(vm.back())
    }

    @Test fun codeInputKeepsOnlyDigitsUpToSix() = runTest(dispatcher) {
        toCode(); vm.onCode("0a1 2b3-4567"); assertEquals("012345", vm.ui.value.code)
    }
}
