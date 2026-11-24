package com.dailyquest.app.ui.decor

import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailyquest.app.data.decor.RoomCatalogs
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * STEP 10-4 RoomView: 두 손가락 확대, 두 번 탭 되돌리기, 바깥 스크롤과의 관계, 그림 받기 상태.
 * 인터넷 없이 가짜 그림을 쓴다. (실제 S3 그림으로 위치를 확인하는 테스트는 debug.RoomExampleRenderTest)
 */
class RoomViewTest {
    @get:Rule val compose = createComposeRule()

    private val room = RoomCatalogs.load(InstrumentationRegistry.getInstrumentation().targetContext).room
    private val image: ImageBitmap = Bitmap.createBitmap(1080, 810, Bitmap.Config.ARGB_8888).asImageBitmap()
    private val instant = DecorImageSource { Result.success(image) }

    private fun SemanticsNodeInteractionsProvider.exists(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun zoom(state: RoomViewState) = compose.runOnIdle { state.viewport!!.zoom }

    /** 가운데에서 [from]px 떨어진 두 손가락을 [to]px까지 벌린다(가로). */
    private fun pinchOut(from: Float, to: Float) = compose.onNodeWithTag("roomView").performTouchInput {
        pinch(center - Offset(from / 2, 0f), center - Offset(to / 2, 0f), center + Offset(from / 2, 0f), center + Offset(to / 2, 0f), durationMillis = 400)
    }

    @Test fun pinchZoomsAndDoubleTapReturnsToOriginalSize() {
        val state = RoomViewState()
        compose.setContent { RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), state = state, images = instant) }
        assertEquals(1f, zoom(state), 0.001f)

        pinchOut(100f, 200f)
        assertEquals("손가락 사이가 2배로 벌어지면 2배", 2f, zoom(state), 0.15f)

        compose.onNodeWithTag("roomView").performTouchInput { doubleClick() }
        assertEquals(1f, zoom(state), 0.001f)
    }

    @Test fun zoomStopsAtThreeTimes() {
        val state = RoomViewState()
        compose.setContent { RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), state = state, images = instant) }
        pinchOut(40f, 600f)
        assertEquals(3f, zoom(state), 0.001f)
    }

    @Test fun oneFingerAtNormalSizeScrollsTheScreenNotTheRoom() {
        val state = RoomViewState()
        val scroll = ScrollState(0)
        compose.setContent {
            Column(Modifier.size(300.dp, 400.dp).verticalScroll(scroll)) {
                RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), state = state, images = instant)
                Spacer(Modifier.height(800.dp))
            }
        }
        val before = compose.runOnIdle { state.viewport }
        compose.onNodeWithTag("roomView").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertTrue("방 위에서 위로 끌면 화면이 스크롤된다", scroll.value > 0)
            assertEquals("1배에서는 방이 움직이지 않는다", before, state.viewport)
        }
    }

    @Test fun oneFingerWhenZoomedMovesTheRoomNotTheScreen() {
        val state = RoomViewState()
        val scroll = ScrollState(0)
        compose.setContent {
            Column(Modifier.size(300.dp, 400.dp).verticalScroll(scroll)) {
                RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), state = state, images = instant)
                Spacer(Modifier.height(800.dp))
            }
        }
        pinchOut(100f, 200f)
        val zoomed = compose.runOnIdle { state.viewport!! }
        assertTrue(zoomed.isZoomed)

        compose.onNodeWithTag("roomView").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertEquals("확대 상태에서는 바깥 화면이 스크롤되지 않는다", 0, scroll.value)
            assertTrue("방이 위로 움직인다", state.viewport!!.offsetY < zoomed.offsetY)
            assertEquals(zoomed.zoom, state.viewport!!.zoom, 0.001f)
        }
    }

    @Test fun notZoomableIgnoresPinch() {
        val state = RoomViewState()
        compose.setContent {
            RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), state = state, zoomable = false, images = instant)
        }
        pinchOut(100f, 300f)
        assertEquals(1f, zoom(state), 0.001f)
    }

    @Test fun showsLoadingUntilImagesArrive() {
        val gate = CompletableDeferred<Unit>()
        val source = DecorImageSource { gate.await(); Result.success(image) }
        var settled = -1
        compose.setContent {
            RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), images = source, onImagesSettled = { settled = it })
        }
        compose.waitForIdle()
        assertTrue(compose.exists("roomViewLoading"))
        gate.complete(Unit)
        compose.waitUntil(5_000) { settled == 0 }
        assertFalse(compose.exists("roomViewLoading"))
        assertFalse(compose.exists("roomViewRetry"))
    }

    @Test fun failedImageShowsRetryAndRetryLoadsAgain() {
        val calls = AtomicInteger(0)
        val online = AtomicBoolean(false)
        val source = DecorImageSource {
            calls.incrementAndGet()
            if (online.get()) Result.success(image) else Result.failure(IOException("offline"))
        }
        compose.setContent { RoomView(room, "room", emptyList(), Modifier.size(300.dp, 225.dp), images = source) }
        compose.waitUntil(5_000) { compose.exists("roomViewRetry") }
        assertEquals(1, calls.get())

        online.set(true)
        compose.onNodeWithTag("roomViewRetry").performClick()
        compose.waitUntil(5_000) { !compose.exists("roomViewRetry") && !compose.exists("roomViewLoading") }
        assertEquals(2, calls.get())
    }
}
