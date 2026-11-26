package com.dailyquest.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.dailyquest.app.data.remote.dto.AvailableItemDto
import com.dailyquest.app.data.remote.dto.DecorItemDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** STEP 10-7 저장 흐름 (앱 전체 + 가짜 서버, 방 9101). */
class RoomDecorSaveFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val desk = DecorItemDto(142, "오크 서랍 책상", "DESK", "https://example.invalid/desk_oak.png", null, "desk_oak")

    @Before fun seed() {
        TestDecorCalls.reset()
        TestDecorCalls.availableItems = listOf(
            AvailableItemDto(TestDecorCalls.defaultRoom, ownerCount = 2),
            AvailableItemDto(desk, ownerCount = 2),
        )
    }

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestDecorCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun openEditor() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9101").fetchSemanticsNodes().isNotEmpty() }
        waitFor(hasTestTag("roomDecorEdit"))
        compose.onNodeWithTag("roomDecorEdit").performScrollTo().performClick()
        waitFor(hasTestTag("decorTray_142"))
    }

    private fun addDesk() {
        compose.onNodeWithTag("decorTray_142").performClick()
        waitFor(hasTestTag("decorEditStatus") and hasText("가구 1개"))
    }

    @Test fun saveIsOffUntilSomethingChanges() {
        openEditor()
        compose.onNodeWithTag("decorSave").assertIsNotEnabled()
        addDesk()
        compose.onNodeWithTag("decorSave").assertIsEnabled()
    }

    @Test fun savingSendsLayoutAndRoomShowsItAfterReturning() {
        openEditor()
        addDesk()
        compose.onNodeWithTag("decorSave").performClick()

        // 방 화면으로 돌아와 저장된 가구를 보여 준다
        waitGone("decorEdit")
        compose.onNodeWithTag("roomDecorCard").performScrollTo()
        waitFor(hasTestTag("roomView") and hasAnyAncestor(hasTestTag("roomDecorCard")) and hasContentDescription("방, 가구 1개"))
        // 방 그림 안에는 안내 글자가 없다(QA 디자인 3단계).
        compose.onNodeWithTag("roomDecorCaption").assertDoesNotExist()

        compose.runOnIdle {
            val (roomId, body) = TestDecorCalls.saveRequests.single()
            assertEquals(9101L, roomId)
            assertEquals("한 번도 저장 안 한 방은 기본 방을 배경으로 보낸다", 9001L, body.backgroundItemId)
            val p = body.placements.single()
            assertEquals(142L, p.itemId)
            assertTrue("0.0625 단위 칸 위치", (p.x * 16) % 1.0 == 0.0 && (p.y * 16) % 1.0 == 0.0)
            assertEquals(0, p.rotation)
        }

        // 다시 편집 화면을 열면 저장된 가구가 들어 있다
        compose.onNodeWithTag("roomDecorEdit").performScrollTo().performClick()
        waitFor(hasTestTag("decorEditStatus") and hasText("가구 1개"))
        compose.onNodeWithTag("decorSave").assertIsNotEnabled()
    }

    @Test fun saveFailureKeepsEditing() {
        openEditor()
        addDesk()
        TestDecorCalls.failure = IOException("offline")
        compose.onNodeWithTag("decorSave").performClick()
        waitFor(hasTestTag("decorSave") and hasText("저장"))
        compose.onNodeWithTag("decorEdit").assertExists()
        compose.onNode(hasTestTag("decorEditStatus") and hasText("가구 1개")).assertExists()
        compose.onNodeWithTag("decorSave").assertIsEnabled()

        TestDecorCalls.failure = null
        compose.onNodeWithTag("decorSave").performClick()
        waitGone("decorEdit")
        compose.runOnIdle { assertEquals(1, TestDecorCalls.saveRequests.size) }
    }

    @Test fun leavingWithChangesAsksFirst() {
        openEditor()
        addDesk()
        Espresso.pressBack()
        waitFor(hasTestTag("decorLeaveCancel"))
        compose.onNodeWithTag("decorLeaveCancel").performClick()
        compose.onNodeWithTag("decorEdit").assertExists()
        compose.onNode(hasTestTag("decorEditStatus") and hasText("가구 1개")).assertExists()

        Espresso.pressBack()
        waitFor(hasTestTag("decorLeaveConfirm"))
        compose.onNodeWithTag("decorLeaveConfirm").performClick()
        waitGone("decorEdit")
        compose.onNodeWithText("서버 설명 9101").assertExists()
        compose.runOnIdle { assertTrue("저장하지 않았다", TestDecorCalls.saveRequests.isEmpty()) }
    }

    @Test fun leavingWithoutChangesDoesNotAsk() {
        openEditor()
        Espresso.pressBack()
        waitGone("decorEdit")
        compose.onNodeWithTag("decorLeaveConfirm").assertDoesNotExist()
    }
}
