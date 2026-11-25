package com.dailyquest.app.ui.avatar

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** 아바타 속도 2-1단계: 로그인 뒤 화면이 쉴 때 내 착용 정보와 3D 엔진을 미리 준비한다. 가짜 함수로 확인한다. */
class AvatarPrewarmTest {
    @get:Rule val compose = createComposeRule()

    private fun profile(id: Long) = UserProfile(id, null, "u$id", 0, 0, 0, 0, 0)
    private val prefetched = CopyOnWriteArrayList<Long>()
    private val engines = AtomicInteger()

    private fun setUp(session: MutableStateFlow<SessionState>) = compose.setContent {
        AvatarPrewarm(session, prefetchMyAvatar = { prefetched += it }, createEngine = { engines.incrementAndGet() }, delayMs = 200)
    }

    private fun waitReal(ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { compose.waitForIdle(); Thread.sleep(50) }
    }

    @Test fun nothingIsPreparedWhenLoggedOut() {
        setUp(MutableStateFlow(SessionState.Unauthenticated()))
        waitReal(1_500)
        assertTrue(prefetched.isEmpty())
        assertEquals(0, engines.get())
    }

    @Test fun afterLoginItPrefetchesMyAvatarThenCreatesTheEngine() {
        setUp(MutableStateFlow(SessionState.Authenticated(profile(7))))
        compose.waitUntil(5_000) { engines.get() == 1 }
        assertEquals(listOf(7L), prefetched.toList())
        waitReal(1_000)
        assertEquals("엔진은 한 번만", 1, engines.get())
    }

    @Test fun switchingAccountsPrefetchesTheNewAccount() {
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(7)))
        setUp(session)
        compose.waitUntil(5_000) { prefetched.contains(7L) }
        session.value = SessionState.Authenticated(profile(8))
        compose.waitUntil(5_000) { prefetched.contains(8L) }
        assertEquals(listOf(7L, 8L), prefetched.toList())
    }
}
