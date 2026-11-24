package com.dailyquest.app

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.dailyquest.app.data.remote.dto.DecorItemDto
import com.dailyquest.app.data.remote.dto.DecorUserDto
import com.dailyquest.app.data.remote.dto.DecorationsDto
import com.dailyquest.app.data.remote.dto.PlacementDto
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * STEP 10-5 방 화면의 "꾸민 방" 카드. 가짜 서버(TestDecorCalls)와 빈 그림으로 인터넷 없이 돈다.
 * 방 9101("서버 내 방 1")은 내가 방장인 방이다.
 */
class RoomDecorCardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetDecor() = TestDecorCalls.reset()
    @After fun resetFakeServer() { TestRoomCalls.reset(); TestDecorCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()

    private fun openMyRoom1() {
        click("홈 · 우리만 (마을 지도)")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 내 방 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("서버 내 방 1").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버 설명 9101").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("roomDecorCard").performScrollTo()
    }

    private val cardRoom = hasTestTag("roomView") and hasAnyAncestor(hasTestTag("roomDecorCard"))

    private fun waitFor(vararg matchers: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(5_000) { matchers.all { compose.onAllNodes(it).fetchSemanticsNodes().isNotEmpty() } }

    private fun item(id: Long, code: String) = DecorItemDto(id, code, "DECO", "https://example.invalid/$code.png", null, code)

    @Test fun neverSavedRoomShowsDefaultRoomEmptyAndDecorateButton() {
        openMyRoom1()
        waitFor(hasTestTag("roomDecorCaption") and hasText("아직 꾸미지 않은 방이에요", substring = true))
        compose.onNode(cardRoom and hasContentDescription("방, 가구 0개")).assertExists()
        compose.onNodeWithTag("roomDecorEdit").assertExists()
        compose.runOnIdle { assertTrue(9101L in TestDecorCalls.getRequests) }
    }

    @Test fun savedPlacementsAreDrawnWithLastEditor() {
        TestDecorCalls.seed(
            9101,
            DecorationsDto(
                roomId = 9101,
                background = TestDecorCalls.defaultRoom,
                placements = listOf(
                    PlacementDto(1, item(142, "desk_oak"), ownerId = 1, x = 0.25, y = 0.375, z = 1, rotation = 90),
                    PlacementDto(2, item(150, "rug_sage"), ownerId = 1, x = 0.4375, y = 0.1875, z = 0, rotation = 0),
                ),
                updatedAt = "2026-11-24T00:00:00Z",
                updatedBy = DecorUserDto(16, "지원"),
            ),
        )
        openMyRoom1()
        waitFor(cardRoom and hasContentDescription("방, 가구 2개"))
        compose.onNode(hasTestTag("roomDecorCaption") and hasText("마지막으로 꾸민 사람: 지원")).assertExists()
        compose.onNodeWithTag("roomDecorKept").assertDoesNotExist()
    }

    @Test fun furnitureTheAppDoesNotKnowIsCountedNotDrawn() {
        TestDecorCalls.seed(
            9101,
            DecorationsDto(
                roomId = 9101,
                placements = listOf(
                    PlacementDto(1, item(142, "desk_oak"), ownerId = 1, x = 0.0, y = 0.0),
                    PlacementDto(2, item(900, "new_sofa_2027"), ownerId = 1, x = 0.5, y = 0.5),
                ),
            ),
        )
        openMyRoom1()
        waitFor(cardRoom and hasContentDescription("방, 가구 1개"))
        compose.onNode(hasTestTag("roomDecorKept") and hasText("앱을 업데이트하면 보이는 가구가 1개 있어요.")).assertExists()
    }

    @Test fun loadFailureShowsRetryAndRetryLoads() {
        TestDecorCalls.failure = IOException("offline")
        openMyRoom1()
        waitFor(hasTestTag("roomDecorRetry"))
        TestDecorCalls.failure = null
        compose.onNodeWithTag("roomDecorRetry").performClick()
        waitFor(hasTestTag("roomDecorCaption") and hasText("아직 꾸미지 않은 방이에요", substring = true))
        compose.onNodeWithTag("roomDecorRetry").assertDoesNotExist()
    }

    @Test fun tappingRoomOpensFullViewAndCloseReturns() {
        openMyRoom1()
        waitFor(cardRoom)
        compose.onNode(cardRoom).performTouchInput { click() }
        waitFor(hasTestTag("roomDecorFull"))
        compose.onNodeWithTag("roomDecorFullView").assertExists()
        compose.onNodeWithTag("roomDecorFullClose").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("roomDecorFull")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("서버 설명 9101").assertExists()
    }

    @Test fun decorateButtonTellsEditorIsComing() {
        openMyRoom1()
        waitFor(hasTestTag("roomDecorEdit"))
        compose.onNodeWithTag("roomDecorEdit").performClick()
        compose.onNodeWithText("방 꾸미기 편집 화면은 곧 열려요.").assertExists()
    }
}
