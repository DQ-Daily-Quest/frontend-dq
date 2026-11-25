package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

/** 멤버 미션 수정·삭제 UI. 서버 규칙: 수정=작성자, 삭제=작성자 또는 방장. FakeApi만 사용하며 실제 서버에는 요청하지 않는다. */
class MissionManageUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.seeded = true; TestMissionCalls.manageSeed = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun openMission(roomName: String, roomText: String, mission: String) {
        click("홈 · 우리만 (마을 지도)"); waitText(roomName)
        compose.onAllNodes(hasText(roomName) and hasText("명", substring = true))[0].performClick()
        waitText(roomText)
        click("미션")
        waitText(mission)
        compose.onNode(hasText(mission) and hasAnyAncestor(hasTestTag("roomMissions"))).performScrollTo().performClick()
        waitTag("missionDetail")
    }

    @Test fun roomOwnerSeesOnlyDeleteOnFriendsMission() {
        openMission("서버 내 방 1", "서버 설명 9101", "서버 친구 미션")
        waitTag("missionMenu")
        compose.onNodeWithTag("missionMenu").performClick()
        compose.onNodeWithTag("missionMenuDelete").assertExists()
        compose.onNodeWithTag("missionMenuEdit").assertDoesNotExist()
    }

    @Test fun appMissionHasNoMenu() {
        openMission("서버 내 방 1", "서버 설명 9101", "서버 앱 미션 9101")
        compose.waitForIdle()
        compose.onNodeWithTag("missionMenu").assertDoesNotExist()
    }

    @Test fun memberSeesNoMenuOnOthersMission() {
        openMission("서버 내 방 2", "서버 설명 9102", "친구 미션 9102")
        compose.waitForIdle()
        Thread.sleep(500)
        compose.onNodeWithTag("missionMenu").assertDoesNotExist()
    }

    @Test fun creatorEditsContentAndSendsOnlyChangedFields() {
        openMission("서버 내 방 2", "서버 설명 9102", "내 미션 9102")
        waitTag("missionMenu")
        compose.onNodeWithTag("missionMenu").performClick()
        compose.onNodeWithTag("missionMenuEdit").performClick()
        waitTag("editMission")
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("바뀐 미션 내용")
        click("저장하기")
        compose.waitUntil(5_000) { TestMissionCalls.updateRequests.isNotEmpty() }
        val (id, body) = TestMissionCalls.updateRequests.single()
        assertEquals(8005L, id); assertEquals("바뀐 미션 내용", body.content); assertTrue(body.dueAt == null)
        waitTag("missionDetail")
        waitText("바뀐 미션 내용")
    }

    @Test fun creatorDeleteAsksConfirmationThenReturns() {
        openMission("서버 내 방 2", "서버 설명 9102", "내 미션 9102")
        waitTag("missionMenu")
        compose.onNodeWithTag("missionMenu").performClick()
        compose.onNodeWithTag("missionMenuDelete").performClick()
        compose.onNodeWithText("이 미션을 삭제할까요?").assertExists()
        assertTrue(TestMissionCalls.deleteRequests.isEmpty())
        compose.onNodeWithTag("missionDeleteConfirm").performClick()
        compose.waitUntil(5_000) { TestMissionCalls.deleteRequests.isNotEmpty() }
        assertEquals(listOf(8005L), TestMissionCalls.deleteRequests)
        waitText("서버 설명 9102")
        compose.onNodeWithTag("missionDetail").assertDoesNotExist()
    }

    @Test fun deleteCancelDoesNotCallServer() {
        openMission("서버 내 방 2", "서버 설명 9102", "내 미션 9102")
        waitTag("missionMenu")
        compose.onNodeWithTag("missionMenu").performClick()
        compose.onNodeWithTag("missionMenuDelete").performClick()
        click("취소")
        compose.waitForIdle()
        assertTrue(TestMissionCalls.deleteRequests.isEmpty())
        compose.onNodeWithTag("missionDetail").assertExists()
    }

    @Test fun deleteMissionHasProofsShowsServerMessage() {
        TestMissionCalls.manageFailure = retrofit2.HttpException(
            retrofit2.Response.error<Any>(409, """{"code":"MISSION_HAS_PROOFS","message":"x"}"""
                .toResponseBody("application/json".toMediaType()))
        )
        openMission("서버 내 방 2", "서버 설명 9102", "내 미션 9102")
        waitTag("missionMenu")
        compose.onNodeWithTag("missionMenu").performClick()
        compose.onNodeWithTag("missionMenuDelete").performClick()
        compose.onNodeWithTag("missionDeleteConfirm").performClick()
        waitTag("missionActionError")
        compose.onNodeWithTag("missionDetail").assertExists()
        assertEquals(1, TestMissionCalls.deleteRequests.size)
    }
}
