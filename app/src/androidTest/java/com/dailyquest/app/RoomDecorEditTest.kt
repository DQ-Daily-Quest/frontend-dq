package com.dailyquest.app

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
import com.dailyquest.app.data.remote.dto.DecorationsDto
import com.dailyquest.app.data.remote.dto.PlacementDto
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * STEP 10-6 꾸미기 편집 화면 (앱 전체 + 가짜 서버). 방 9101("서버 내 방 1")에서 "방 꾸미기"로 들어간다.
 * 가구 목록: 책상(멤버 2명 보유), 의자(1명 보유). 손가락으로 끌어 옮기기는 ui.decor.RoomEditGesturesTest가 확인한다.
 */
class RoomDecorEditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val desk = DecorItemDto(142, "오크 서랍 책상", "DESK", "https://example.invalid/desk_oak.png", null, "desk_oak")
    private val chair = DecorItemDto(143, "세이지 다이닝 체어", "CHAIR", "https://example.invalid/chair_sage.png", null, "chair_sage")

    @Before fun seed() {
        TestDecorCalls.reset()
        TestDecorCalls.availableItems = listOf(
            AvailableItemDto(TestDecorCalls.defaultRoom, ownerCount = 2),
            AvailableItemDto(desk, ownerCount = 2),
            AvailableItemDto(chair, ownerCount = 1),
        )
    }

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestDecorCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun status(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("decorEditStatus") and hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }
    // 가구 카드는 통째로 누르는 버튼이라 안의 글자가 하나로 합쳐진다. 합치기 전 구조에서 "남은 N개" 글자를 찾는다.
    private fun remaining(id: Long, text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("decorTrayRemaining_$id") and hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openEditor() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9101").fetchSemanticsNodes().isNotEmpty() }
        waitTag("roomDecorEdit")
        compose.onNodeWithTag("roomDecorEdit").performScrollTo().performClick()
        waitTag("decorEditRoom")
    }

    @Test fun emptyRoomShowsTrayWithRemainingCounts() {
        openEditor()
        status("가구 0개")
        compose.onNodeWithTag("decorTray_142").assertExists()
        compose.onNodeWithTag("decorTray_143").assertExists()
        compose.onNodeWithTag("decorTray_9001").assertDoesNotExist() // 기본 방(바닥)은 가구 목록에 없다
        remaining(142, "남은 2개")
        remaining(143, "남은 1개")
    }

    @Test fun addRotateRemoveUpdatesRoomAndCounts() {
        openEditor()
        compose.onNodeWithTag("decorTray_142").performClick()
        status("가구 1개")
        compose.onNode(hasTestTag("decorEditSelected") and hasText("오크 서랍 책상")).assertExists()
        compose.onNode(hasTestTag("decorEditRoom") and hasContentDescription("방, 가구 1개")).assertExists()
        remaining(142, "남은 1개")

        compose.onNodeWithTag("decorRotate").performClick()
        status("가구 1개") // 돌려도 겹치거나 방 밖으로 나가지 않는다

        compose.onNodeWithTag("decorTray_142").performClick()
        status("가구 2개")
        remaining(142, "남은 0개")

        compose.onNodeWithTag("decorRemove").performClick()
        status("가구 1개")
        remaining(142, "남은 1개")
        compose.onNodeWithTag("decorEditSelected").assertDoesNotExist()
    }

    @Test fun cannotPlaceMoreThanMembersOwn() {
        openEditor()
        compose.onNodeWithTag("decorTray_143").performClick()
        status("가구 1개")
        remaining(143, "남은 0개")
        compose.onNodeWithTag("decorTray_143").performClick()
        status("가구 1개") // 그대로 (이유는 토스트로 알려 준다)
    }

    @Test fun savedFurnitureIsLoadedIntoEditor() {
        TestDecorCalls.seed(
            9101,
            DecorationsDto(
                roomId = 9101,
                background = TestDecorCalls.defaultRoom,
                placements = listOf(PlacementDto(1, desk, ownerId = 1, x = 0.25, y = 0.25, z = 1, rotation = 0)),
            ),
        )
        openEditor()
        status("가구 1개")
        remaining(142, "남은 1개")
    }

    @Test fun loadFailureShowsRetry() {
        openEditorWithFailure()
        TestDecorCalls.failure = null
        compose.onNodeWithTag("decorEditRetry").performClick()
        status("가구 0개")
    }

    private fun openEditorWithFailure() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9101").fetchSemanticsNodes().isNotEmpty() }
        waitTag("roomDecorEdit")
        TestDecorCalls.failure = IOException("offline")
        compose.onNodeWithTag("roomDecorEdit").performScrollTo().performClick()
        waitTag("decorEditError")
        waitTag("decorEditRetry")
    }

    @Test fun backReturnsToRoom() {
        openEditor()
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9101").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("decorEdit").assertDoesNotExist()
    }
}
