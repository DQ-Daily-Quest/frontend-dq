package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** STEP 11: 방 인증 피드와 상세 진입. FakeApi만 사용하며 실서버를 호출하지 않는다. */
class SocialFeedUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.seeded = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestMissionCalls.reset(); TestProofCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun openFeed() {
        click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1")
        compose.onNode(hasText("서버 내 방 1") and hasText("1명")).performClick()
        waitText("서버 설명 9101")
        click("미션")
        waitText("서버 앱 미션 9101")
        compose.onNodeWithTag("roomFeed").performScrollTo().performClick()
        waitTag("feed")
    }

    @Test fun emptyFeedShowsGuidance() {
        openFeed()
        waitTag("feedEmpty")
        compose.onNodeWithTag("feedEmpty").assertTextContains("24시간", substring = true)
    }

    @Test fun feedListsItemsAndOpensProofDetailThenBackReturns() {
        TestProofCalls.seedPending(7201, 9101, 8001)
        TestProofCalls.seedPending(7202, 9101, 8001, mediaType = "VIDEO")
        openFeed()
        waitTag("feedItem-7201")
        compose.onNodeWithTag("feedItem-7202").assertExists()
        compose.onNodeWithTag("feedStrip").assertExists()
        compose.onNodeWithTag("feedItem-7201").performClick()
        waitTag("proofDetail")
        assertTrue(TestProofCalls.calls.contains("detail:7201"))
        Espresso.pressBack()
        waitTag("feedItem-7201")
    }

    @Test fun feedErrorShowsRetryAndRecovers() {
        TestProofCalls.seedPending(7203, 9101, 8001)
        TestProofCalls.feedFailure = java.io.IOException("offline")
        openFeed()
        waitTag("feedError")
        TestProofCalls.feedFailure = null
        compose.onNodeWithTag("feedRetry").performClick()
        waitTag("feedItem-7203")
    }

    private fun openDetail(id: Long) {
        openFeed(); waitTag("feedItem-$id"); compose.onNodeWithTag("feedItem-$id").performClick(); waitTag("reactionBar")
    }

    @Test fun reactionTogglesWithServerCountAndSupportsSeveralEmojis() {
        TestProofCalls.seedPending(7301, 9101, 8001)
        openDetail(7301)
        compose.onNodeWithTag("reaction-🔥").performScrollTo().performClick()
        compose.waitUntil(5_000) { TestProofCalls.calls.contains("react:7301:🔥") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("reaction-🔥") and hasText("🔥 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reaction-👏").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("reaction-👏") and hasText("👏 1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reaction-🔥").assertTextContains("1", substring = true)
        compose.onNodeWithTag("reaction-🔥").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("reaction-🔥") and hasText("🔥 0")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(TestProofCalls.calls.contains("unreact:7301:🔥"))
    }

    @Test fun reactionFailureKeepsStateAndShowsError() {
        TestProofCalls.seedPending(7302, 9101, 8001)
        openDetail(7302)
        TestProofCalls.reactionFailure = TestShopCalls.http(400, "INVALID_EMOJI")
        compose.onNodeWithTag("reaction-😮").performScrollTo().performClick()
        waitTag("reactionError")
        compose.onNodeWithTag("reaction-😮").assertTextContains("0", substring = true)
    }

    @Test fun deleteIsHiddenForOthersProofs() {
        TestProofCalls.seedPending(7303, 9101, 8001)
        openDetail(7303)
        compose.onNodeWithTag("proofDelete").assertDoesNotExist()
    }

    @Test fun ownPendingProofCanBeDeletedAfterConfirmation() {
        TestProofCalls.seedPending(7304, 9101, 8001, user = com.dailyquest.app.data.remote.dto.RoomOwnerDto(1, "uitest"))
        openDetail(7304)
        compose.onNodeWithTag("proofDelete").performScrollTo().performClick()
        compose.onNodeWithText("이 인증을 삭제할까요?").assertExists()
        assertTrue(TestProofCalls.calls.none { it.startsWith("delete:") })
        compose.onNodeWithTag("proofDeleteConfirm").performClick()
        compose.waitUntil(5_000) { TestProofCalls.calls.contains("delete:7304") }
        waitTag("feed")
    }
}
