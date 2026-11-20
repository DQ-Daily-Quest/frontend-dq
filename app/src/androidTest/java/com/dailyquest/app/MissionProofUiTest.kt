package com.dailyquest.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** STEP 5 후속 UX: 미션 필터, 재인증 상태, 한국어 인증 상태, 자동 승인 표시, 영상 인증. FakeApi만 사용한다. */
class MissionProofUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset(); TestMissionCalls.seeded = true
            TestMissionCalls.rejectedMissionIds.add(8001)
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestMissionCalls.reset(); TestProofCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun openMissionTab() {
        click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1")
        tab("미션").performClick(); waitTag("missionList")
    }
    private fun me() = com.dailyquest.app.data.remote.dto.RoomOwnerDto(1, "uitest")

    @Test fun filterChipsShowCountsAndFilterByState() {
        openMissionTab()
        waitTag("missionFilter-ALL")
        waitText("재인증이 필요한 미션")
        compose.onNodeWithTag("missionFilter-REJECTED").assertTextContains("거절 1")
        compose.onNodeWithTag("missionFilter-PENDING").assertTextContains("승인 대기 1")
        compose.onNodeWithTag("missionFilter-REJECTED").performClick()
        compose.onNodeWithText("서버 앱 미션 9101").assertExists()
        compose.onNodeWithText("서버 앱 미션 9102").assertDoesNotExist()
        compose.onNodeWithTag("missionFilter-PENDING").performClick()
        compose.onNodeWithText("서버 앱 미션 9102").assertExists()
        compose.onNodeWithTag("missionFilter-DONE").performClick()
        compose.onNodeWithTag("missionFilterEmpty").assertExists()
        compose.onNodeWithTag("missionFilter-SENT").performClick()
        compose.onNodeWithTag("missionFilterEmpty").assertExists()
    }

    @Test fun rejectedMissionShowsReproofStateAndButton() {
        openMissionTab()
        compose.onNodeWithTag("missionFilter-REJECTED").performClick()
        compose.onNodeWithText("서버 앱 미션 9101").performClick()
        waitTag("missionDetail")
        compose.onNodeWithTag("missionReproofHint").assertTextContains("이전 인증이 거절됐어요. 다시 인증할 수 있어요.")
        compose.onNodeWithText("다시 인증하기").assertExists()
        compose.onNodeWithTag("missionProof-${7700 + 8001}").performScrollTo().assertTextContains("uitest · 인증 거절")
        compose.onNodeWithText("REJECTED", substring = true).assertDoesNotExist()
    }

    @Test fun autoApprovedProofShowsAutoApprovalCopyWithoutRequiredCount() {
        TestProofCalls.seedPending(1L, 9102, 8003, user = me(), status = "APPROVED")
        openMissionTab()
        compose.onNodeWithText("서버 앱 미션 9102").performClick()
        waitTag("missionDetail")
        compose.onNodeWithText("내 인증 보기").performClick()
        waitTag("proofAutoApproved")
        compose.onNodeWithTag("proofStatus").assertTextContains("자동 승인 완료")
        compose.onNodeWithTag("proofCounts").assertDoesNotExist()
        compose.onNodeWithText("필요 승인", substring = true).assertDoesNotExist()
        compose.onNodeWithText("APPROVED", substring = true).assertDoesNotExist()
    }

    @Test fun videoProofShowsPlayerSurfaceWithFallbackAndBackReturns() {
        TestProofCalls.seedPending(7101, 9101, 8001, mediaType = "VIDEO")
        click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1")
        compose.onNode(hasText("서버 내 방 1") and hasText("1명")).performClick()
        waitText("서버 설명 9101")
        click("미션")
        waitText("서버 앱 미션 9101")
        compose.onNodeWithTag("roomPendingProofs").performScrollTo().performClick()
        waitTag("pendingProof-7101")
        compose.onNodeWithTag("pendingProof-7101").performClick()
        waitTag("proofVideo")
        // 네트워크 없이도 썸네일 대체 UI가 나오고 앱이 종료되지 않는다.
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("proofVideoThumbFallback").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("proofVideoUnavailable").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("proofVideoThumb").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("영상 보기").assertDoesNotExist()
        compose.onNodeWithTag("proofVideo").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("proofVideoPlayer").fetchSemanticsNodes().isNotEmpty()
        }
        Espresso.pressBack()
        waitTag("pendingProof-7101")
        assertTrue(TestProofCalls.calls.contains("detail:7101"))
    }
}
