package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dailyquest.app.data.remote.dto.NotificationDto
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** STEP 11: 알림함과 홈 배지. FakeApi만 사용한다. */
class SocialNotificationUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.seeded = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestMissionCalls.reset(); TestProofCalls.reset(); TestNotificationCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun n(id: Long, type: String = "PROOF_APPROVED", read: Boolean = false, proofId: Long? = null, missionId: Long? = null, roomId: Long? = 9101) =
        NotificationDto(id, type, "알림 $id", "내용 $id", roomId, missionId, proofId, read, "2025-04-17T00:00:00Z")

    private fun openInbox() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
        compose.onNodeWithTag("homeBell").performClick()
        waitTag("notifications")
    }

    @Test fun emptyInboxShowsGuidanceAndNoBadge() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
        compose.onNodeWithTag("notificationBadge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("homeBell").performClick()
        waitTag("notificationsEmpty")
    }

    @Test fun badgeShowsServerUnreadCount() {
        TestNotificationCalls.items += listOf(n(1), n(2), n(3, read = true))
        // 앱 복귀(onResume)가 서버 값을 다시 읽는다.
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitTag("notificationBadge")
        compose.onNode(hasTestTag("notificationBadge") and hasAnyChild(hasText("2")), useUnmergedTree = true).assertExists()
    }

    @Test fun tappingProofNotificationMarksReadAndOpensProofDetail() {
        TestProofCalls.seedPending(7401, 9101, 8001)
        TestNotificationCalls.items += n(11, proofId = 7401, missionId = 8001)
        openInbox()
        waitTag("notification-11")
        compose.onNodeWithTag("notification-11").performClick()
        compose.waitUntil(5_000) { TestNotificationCalls.calls.contains("read:11") }
        waitTag("proofDetail")
    }

    @Test fun notificationWithoutTargetOnlyMarksRead() {
        TestNotificationCalls.items += n(12, type = "UNKNOWN_TYPE", roomId = null)
        openInbox()
        waitTag("notification-12")
        compose.onNodeWithTag("notification-12").performClick()
        compose.waitUntil(5_000) { TestNotificationCalls.calls.contains("read:12") }
        compose.onNodeWithTag("notifications").assertExists()
    }

    @Test fun readAllClearsUnreadMarkers() {
        TestNotificationCalls.items += listOf(n(21), n(22))
        openInbox()
        waitTag("notificationDot-21")
        compose.onNodeWithTag("notificationsReadAll").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("notificationDot-21", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        assertTrue(TestNotificationCalls.calls.contains("readAll"))
    }

    @Test fun unreadFilterHidesReadItems() {
        TestNotificationCalls.items += listOf(n(31), n(32, read = true))
        openInbox()
        waitTag("notification-32")
        compose.onNodeWithTag("notificationsUnread").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("notification-32").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("notification-31").assertExists()
    }

    @Test fun listFailureShowsRetryAndRecovers() {
        TestNotificationCalls.items += n(41)
        TestNotificationCalls.listFailure = java.io.IOException("offline")
        openInbox()
        waitTag("notificationsError")
        TestNotificationCalls.listFailure = null
        compose.onNodeWithTag("notificationsRetry").performClick()
        waitTag("notification-41")
    }
}
