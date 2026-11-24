package com.dailyquest.app.data.push

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.notification.NotificationRepository
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.FcmTokenRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistrarTest {
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        val puts = mutableListOf<String>()
        var failure: Throwable? = null
        override suspend fun updateFcmToken(body: FcmTokenRequest) { puts += body.token; failure?.let { throw it } }
    }

    private class Source(var token: String?) : PushTokenSource { override suspend fun currentToken() = token }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private fun profile(id: Long) = SessionState.Authenticated(UserProfile(id, null, "n", 0, 0, 0, 0, 0))

    private class Env(val api: FakeApi, val session: MutableStateFlow<SessionState>, val reg: PushTokenRegistrar)

    private fun TestScope.env(initial: SessionState, token: String? = "tok-A"): Env {
        val api = FakeApi(unused)
        val session = MutableStateFlow(initial)
        val reg = PushTokenRegistrar(NotificationRepository(api), session, Source(token), TestScope(StandardTestDispatcher(testScheduler)))
        return Env(api, session, reg)
    }

    @Test fun registersAfterLoginAndNotWhenSignedOut() = runTest {
        val e = env(SessionState.Unauthenticated())
        advanceUntilIdle()
        assertTrue(e.api.puts.isEmpty())
        e.session.value = profile(1); advanceUntilIdle()
        assertEquals(listOf("tok-A"), e.api.puts)
    }

    @Test fun doesNotRepeatForSameAccountAndToken() = runTest {
        val e = env(profile(1)); advanceUntilIdle()
        e.reg.sync(); e.reg.sync(); advanceUntilIdle()
        assertEquals(1, e.api.puts.size)
    }

    @Test fun newTokenIsReRegistered() = runTest {
        val e = env(profile(1)); advanceUntilIdle()
        e.reg.onNewToken("tok-B"); advanceUntilIdle()
        assertEquals(listOf("tok-A", "tok-B"), e.api.puts)
    }

    @Test fun failedRegistrationRetriesOnNextSync() = runTest {
        val e = env(profile(1)); e.api.failure = IOException("offline"); advanceUntilIdle()
        e.api.failure = null
        e.reg.sync(); advanceUntilIdle()
        e.reg.sync(); advanceUntilIdle()
        assertEquals(2, e.api.puts.size)
    }

    @Test fun accountSwitchAndRelogRegisterAgainPerAccount() = runTest {
        val e = env(profile(1)); advanceUntilIdle()
        e.session.value = profile(2); advanceUntilIdle()
        assertEquals(2, e.api.puts.size)
        e.session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        e.reg.sync(); advanceUntilIdle()
        assertEquals(2, e.api.puts.size)
        e.session.value = profile(2); advanceUntilIdle()
        assertEquals(3, e.api.puts.size)
    }

    @Test fun noTokenAvailableMeansNoRequestAndBlankIsIgnored() = runTest {
        val e = env(profile(1), token = null); advanceUntilIdle()
        e.reg.onNewToken("  "); advanceUntilIdle()
        assertTrue(e.api.puts.isEmpty())
    }

    @Test fun pendingTargetDeliversOnceToTheRightAccount() {
        val p = PendingPushTarget()
        p.post("proof/1?roomId=2", ownerUserId = null)
        assertEquals("proof/1?roomId=2", p.take(5)); assertNull(p.take(5))
        p.post("room/3", ownerUserId = 1)
        assertNull(p.take(2)); assertNull(p.take(1))
        p.post("room/4", ownerUserId = 1)
        assertEquals("room/4", p.take(1))
        p.post("room/5", ownerUserId = 1); p.post("room/6", ownerUserId = 1)
        assertEquals("room/6", p.take(1))
        p.post("room/7", null); p.clear(); assertNull(p.take(1))
    }
}
