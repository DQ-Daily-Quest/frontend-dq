package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** STEP 11: 푸시 알림 탭 → 화면 이동. 실제 FCM 없이 알림 탭 Intent(extras)만 흉내 낸다. */
class PushNavigationUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.seeded = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestMissionCalls.reset(); TestProofCalls.reset(); TestNotificationCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    // 알림 탭 Intent는 MainActivity가 PushIntents로 해석해 이 보관소에 넣는다. 여기서는 그 결과를 직접 넣어 화면 전달을 검증한다.
    private fun tap(vararg extras: Pair<String, String>) {
        val route = com.dailyquest.app.data.notification.NotificationTargets.routeFromPayload(extras.toMap()) ?: return
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication
        app.container.pendingPushTarget.post(route, ownerUserId = null)
    }

    private fun toHome() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
    }

    @Test fun proofNotificationTapOpensProofDetail() {
        TestProofCalls.seedPending(7501, 9101, 8001)
        toHome()
        tap("type" to "PROOF_SUBMITTED", "roomId" to "9101", "missionId" to "8001", "proofId" to "7501")
        waitTag("proofDetail")
    }

    @Test fun missionNotificationTapOpensMissionAndDoesNotStackOnRepeat() {
        toHome()
        tap("type" to "NEW_APP_MISSION", "roomId" to "9101", "missionId" to "8001")
        waitText("서버 앱 미션 9101")
        tap("type" to "NEW_APP_MISSION", "roomId" to "9101", "missionId" to "8001")
        compose.waitForIdle()
        androidx.test.espresso.Espresso.pressBack()
        waitText("서버 내 방 1")
    }

    @Test fun malformedPayloadIsIgnored() {
        toHome()
        tap("type" to "PROOF_APPROVED", "proofId" to "../../x", "roomId" to "abc")
        tap("type" to "HACK", "roomId" to "9101")
        compose.waitForIdle()
        compose.onAllNodesWithTag("proofDetail").assertCountEquals(0)
        waitText("서버 내 방 1")
    }
}

/** 알림 탭 Intent extras 해석. 허용하지 않은 키나 잘못된 값은 이동 대상이 되지 않는다. */
class PushIntentsTest {
    private fun route(vararg extras: Pair<String, String>) = com.dailyquest.app.data.push.PushIntents.routeFrom(
        android.content.Intent().apply { extras.forEach { (k, v) -> putExtra(k, v) } }
    )

    @Test fun mapsExtrasToRoute() {
        org.junit.Assert.assertEquals("proof/9?roomId=2", route("type" to "PROOF_APPROVED", "roomId" to "2", "proofId" to "9"))
        org.junit.Assert.assertEquals("mission/4?roomId=2", route("type" to "NEW_MEMBER_MISSION", "roomId" to "2", "missionId" to "4"))
    }

    @Test fun rejectsMalformedAndMissing() {
        org.junit.Assert.assertNull(route("type" to "PROOF_APPROVED", "proofId" to "x"))
        org.junit.Assert.assertNull(route("roomId" to "2"))
        org.junit.Assert.assertNull(com.dailyquest.app.data.push.PushIntents.routeFrom(null))
        val i = android.content.Intent().putExtra("type", "NEW_APP_MISSION").putExtra("missionId", "3").putExtra("roomId", "1")
        com.dailyquest.app.data.push.PushIntents.clear(i)
        org.junit.Assert.assertNull(com.dailyquest.app.data.push.PushIntents.routeFrom(i))
    }
}
