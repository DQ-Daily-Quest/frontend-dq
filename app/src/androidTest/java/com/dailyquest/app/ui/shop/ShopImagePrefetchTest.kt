package com.dailyquest.app.ui.shop

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** 상점 이미지 속도 3단계: 로그인 뒤 화면이 쉴 때 상점 상품 이미지를 미리 받는다. 가짜 함수로 시점만 확인한다. */
class ShopImagePrefetchTest {
    @get:Rule val compose = createComposeRule()

    private fun profile(id: Long) = UserProfile(id, null, "u$id", 0, 0, 0, 0, 0)
    private val runs = AtomicInteger()

    private fun setUp(session: MutableStateFlow<SessionState>) = compose.setContent {
        ShopImagePrefetch(session, prefetch = { runs.incrementAndGet() }, delayMs = 200)
    }

    private fun waitReal(ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { compose.waitForIdle(); Thread.sleep(50) }
    }

    @Test fun nothingIsFetchedWhenLoggedOut() {
        setUp(MutableStateFlow(SessionState.Unauthenticated()))
        waitReal(1_500)
        assertEquals(0, runs.get())
    }

    @Test fun afterLoginItPrefetchesOnceAfterTheDelay() {
        setUp(MutableStateFlow(SessionState.Authenticated(profile(7))))
        compose.waitUntil(5_000) { runs.get() == 1 }
        waitReal(1_000)
        assertEquals(1, runs.get())
    }

    @Test fun loggingOutAndInAgainTriesAgain() {
        // 같은 앱 실행에서 다시 할지는 ShopImagePrefetcher가 정한다(이미 끝났으면 바로 돌아옴). 여기서는 다시 부르는지만 본다.
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(7)))
        setUp(session)
        compose.waitUntil(5_000) { runs.get() == 1 }
        session.value = SessionState.Unauthenticated()
        waitReal(500)
        session.value = SessionState.Authenticated(profile(8))
        compose.waitUntil(5_000) { runs.get() == 2 }
    }
}
