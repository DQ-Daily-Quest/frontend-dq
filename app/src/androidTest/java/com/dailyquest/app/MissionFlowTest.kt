package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test

/** FakeApi 기반 STEP 4 미션 흐름. 실제 서버에는 요청을 보내지 않는다. */
class MissionFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    // 앱 시작 시 전역 미션 목록이 로드되므로 시드는 Activity 시작 전에 설정한다.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.seeded = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private val contentField = hasSetTextAction() and !hasTestTag("rewardValue")
    private fun openHome() { click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1") }

    private fun openRoomMissions() {
        openHome()
        compose.onNode(hasText("서버 내 방 1") and hasText("1명")).performClick()
        waitText("서버 설명 9101")
        click("미션")
        waitText("서버 앱 미션 9101")
    }

    @Test fun homeShowsServerMissionsWithRoomNamesAndNoMockOrFixedDate() {
        openHome()
        waitTag("homeMissions")
        waitText("서버 앱 미션 9101")
        compose.onNode(hasText("서버 앱 미션 9101") ).assertExists()
        assert(compose.onAllNodesWithText("서버 내 방 2", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("서버 앱 미션 9102").assertExists()
        compose.onNodeWithText("4월 17일", substring = true).assertDoesNotExist()
    }

    @Test fun missionTabListsServerMissionsBySectionAndOpensDetail() {
        openHome()
        tab("미션").performClick()
        waitTag("missionList")
        waitText("서버 앱 미션 9101")
        compose.onNodeWithText("수행 가능한 미션").assertExists()
        compose.onNodeWithText("인증 대기 중인 미션").assertExists()
        compose.onNodeWithText("서버 앱 미션 9101").performClick()
        waitTag("missionDetail")
        compose.onNodeWithTag("missionProofAction").assertExists()
        compose.onNodeWithText("인증하기").assertExists()
        Espresso.pressBack()
        waitTag("missionList")
        Espresso.pressBack()
        waitText("서버 내 방 1")
        compose.onNodeWithText("홈 · 우리만 (마을 지도)").assertDoesNotExist()
    }

    @Test fun roomMissionTabShowsOnlyThatRoomsMissionsAndExpiredScope() {
        openRoomMissions()
        compose.onNodeWithText("서버 친구 미션").assertExists()
        compose.onNodeWithText("서버 앱 미션 9102").assertDoesNotExist()
        assertTrue(TestMissionCalls.roomCalls.all { it.first == 9101L })
        click("마감")
        waitText("지난 미션 9101")
        compose.onNodeWithText("서버 친구 미션").assertDoesNotExist()
    }

    @Test fun sendMissionToMemberCreatesAndRefreshesRoomList() {
        openRoomMissions()
        click("+ 미션 보내기")
        waitTag("sendRoomMission")
        compose.onNodeWithTag("recipient-1").assertDoesNotExist() // 본인은 대상이 아님
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("recipient-8").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("recipient-8").performClick()
        compose.onNode(contentField).performTextInput("테스트 미션")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("rewardPlus").performClick()
        click("미션 보내기")
        waitText("서버 설명 9101")
        click("미션")
        waitText("테스트 미션")
        val (roomId, body) = TestMissionCalls.createRequests.single()
        assertEquals(9101L, roomId); assertEquals(8L, body.assigneeUserId); assertEquals(60, body.rewardCoins)
        compose.onNodeWithTag("sendRoomMission").assertDoesNotExist()
    }

    @Test fun sendMissionToWholeRoomOmitsAssignee() {
        openRoomMissions()
        click("+ 미션 보내기")
        waitTag("sendRoomMission")
        compose.onNode(contentField).performTextInput("전체 미션")
        Espresso.closeSoftKeyboard()
        click("미션 보내기")
        waitText("서버 설명 9101")
        click("미션")
        waitText("전체 미션")
        assertNull(TestMissionCalls.createRequests.single().second.assigneeUserId)
    }

    @Test fun sendMissionValidationBlocksRequest() {
        openRoomMissions()
        click("+ 미션 보내기")
        waitTag("sendRoomMission")
        click("미션 보내기")
        compose.onNodeWithText("미션 내용을 입력해 주세요.").assertExists()
        assertTrue(TestMissionCalls.createRequests.isEmpty())
    }

    @Test fun sendMissionNetworkErrorShowsUnknownOutcomeAndSendsOnce() {
        TestMissionCalls.createFailure = java.io.IOException("lost")
        openRoomMissions()
        click("+ 미션 보내기")
        waitTag("sendRoomMission")
        compose.onNode(contentField).performTextInput("유실 미션")
        Espresso.closeSoftKeyboard()
        click("미션 보내기")
        waitTag("sendMissionOutcomeUnknown")
        assertEquals(1, TestMissionCalls.createRequests.size)
        compose.onNodeWithTag("sendRoomMission").assertExists()
    }

    @Test fun rewardCanBeTypedValidatedAndSyncedWithButtons() {
        openRoomMissions()
        click("+ 미션 보내기")
        waitTag("sendRoomMission")
        compose.onNode(contentField).performTextInput("직접 입력 미션")
        val reward = compose.onNodeWithTag("rewardValue")
        reward.performTextReplacement("55")
        compose.onNodeWithTag("rewardHint").assertTextContains("10코인 단위", substring = true)
        click("미션 보내기")
        assertTrue(TestMissionCalls.createRequests.isEmpty())
        compose.onNodeWithTag("rewardPlus").performClick()
        reward.assertTextContains("60")
        reward.performTextReplacement("120")
        compose.onNodeWithTag("rewardMinus").performClick()
        reward.assertTextContains("110")
        Espresso.closeSoftKeyboard()
        click("미션 보내기")
        waitText("서버 설명 9101")
        assertEquals(110, TestMissionCalls.createRequests.single().second.rewardCoins)
    }

    @Test fun connectedMissionScreensHideSampleBanner() {
        openHome()
        tab("미션").performClick()
        waitTag("missionList")
        compose.onNodeWithText("샘플 데이터 · 서버 미연동").assertDoesNotExist()
    }
}
