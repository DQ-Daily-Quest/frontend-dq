package com.dailyquest.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** 참여한 방이 없을 때 Mock 미션 대신 빈 상태를 보여주는지 검증한다. */
class MissionEmptyStateTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestMissionCalls.noRooms = true }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun homeShowsEmptyStateWithoutMockMissions() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitTag("homeMissionsNoRooms")
        compose.onNodeWithText("서버 앱 미션 9101").assertDoesNotExist()
        compose.onNodeWithText("4월 17일", substring = true).assertDoesNotExist()
    }

    @Test fun missionTabShowsEmptyState() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        compose.onNode(hasText("미션") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        waitTag("missionListNoRooms")
    }
}
