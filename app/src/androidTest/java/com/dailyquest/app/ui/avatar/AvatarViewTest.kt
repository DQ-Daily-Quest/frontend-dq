package com.dailyquest.app.ui.avatar

import android.os.SystemClock
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.AvatarPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * STEP 9-4 AvatarView의 실패·다시 시도 화면. 가짜 다운로더를 써서 인터넷 없이 돈다.
 * (성공해서 실제 3D가 보이는 흐름은 debug.Avatar3dTestScreenTest가 실제 S3 파일로 확인한다.)
 */
class AvatarViewTest {
    @get:Rule val compose = createComposeRule()

    @Before fun pauseFrameClock() {
        // SceneView가 매 프레임 그려서 Compose가 멈추지 않으므로 프레임 시계를 직접 넘긴다.
        compose.mainClock.autoAdvance = false
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (condition()) return
            Thread.sleep(50)
        }
        fail("$what 10초 안에 되지 않았습니다")
    }

    private fun SemanticsNodeInteractionsProvider.exists(tag: String) =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun failedPartShowsRetryAndRetryFetchesAgain() {
        val calls = AtomicInteger(0)
        val statuses = mutableListOf<AvatarLoadStatus>()
        val offline = AvatarModelFetcher { calls.incrementAndGet(); Result.failure(IOException("offline")) }

        compose.setContent {
            AvatarView(
                parts = mapOf(AvatarPart.SKIN to "https://example.invalid/body.glb"),
                modifier = Modifier.size(300.dp),
                fetcher = offline,
                onStatusChange = { statuses += it },
            )
        }

        waitUntil("다시 시도 버튼 표시") { compose.exists("avatarViewRetry") }
        assertEquals(1, calls.get())
        assertTrue(statuses.last() is AvatarLoadStatus.Failed)
        assertEquals(setOf(AvatarPart.SKIN), (statuses.last() as AvatarLoadStatus.Failed).failed)

        compose.onNodeWithTag("avatarViewRetry").performClick()
        waitUntil("다시 시도 후 재요청") { calls.get() == 2 }
        waitUntil("다시 실패 표시") { compose.exists("avatarViewRetry") }
    }

    @Test fun loadingShowsOnFirstFrameBeforeSceneStarts() {
        // 3D 엔진을 만드는 동안 화면이 멈추므로, 엔진을 만들기 전 첫 화면에 이미 진행 표시가 있어야 한다.
        compose.setContent {
            AvatarView(
                parts = mapOf(AvatarPart.SKIN to "https://example.invalid/body.glb"),
                modifier = Modifier.size(300.dp),
                fetcher = { awaitCancellation() },
            )
        }
        assertTrue(compose.exists("avatarViewLoading"))
    }

    @Test fun loadingStaysWhileModelsAreDownloading() {
        val calls = AtomicInteger(0)
        compose.setContent {
            AvatarView(
                parts = mapOf(AvatarPart.SKIN to "https://example.invalid/body.glb"),
                modifier = Modifier.size(300.dp),
                fetcher = { calls.incrementAndGet(); awaitCancellation() },
            )
        }
        waitUntil("3D 화면 시작 후 다운로드 요청") { calls.get() == 1 }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(compose.exists("avatarViewLoading"))
        assertTrue(!compose.exists("avatarViewRetry"))
    }

    @Test fun emptyPartsIsReadyWithoutFetching() {
        val calls = AtomicInteger(0)
        var last: AvatarLoadStatus? = null
        compose.setContent {
            AvatarView(
                parts = emptyMap(),
                modifier = Modifier.size(300.dp),
                fetcher = { calls.incrementAndGet(); Result.failure(IOException()) },
                onStatusChange = { last = it },
            )
        }

        waitUntil("준비됨 상태") { last is AvatarLoadStatus.Ready }
        assertEquals(0, calls.get())
        assertTrue(!compose.exists("avatarViewRetry"))
        assertTrue(!compose.exists("avatarViewLoading"))
    }
}
