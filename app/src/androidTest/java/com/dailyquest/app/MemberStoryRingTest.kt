package com.dailyquest.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * QA 디자인 2-1단계: 방 멤버 얼굴의 스토리 링(FakeApi). 가짜 피드는 인증을 모두 "안 봄"으로 준다.
 * 서버방장(7)이 인증 하나를 올린 방 9101: 서버방장 얼굴은 분홍(새 인증), 서버멤버(8)는 테두리 없음.
 */
class MemberStoryRingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset(); TestMissionCalls.seeded = true
            TestProofCalls.seedPending(7301, 9101, 8001, status = "APPROVED")
        }
    }).around(compose)

    @After fun reset() { TestRoomCalls.reset(); TestProofCalls.reset() }

    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun ring(userId: Long, state: String) = compose.onNodeWithTag("memberFace-$userId")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private fun openRoom() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
        compose.onNodeWithText("서버 내 방 1").performClick()
        waitText("서버멤버")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("memberFace-7").fetchSemanticsNodes().any {
                it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null } == "새 인증"
            }
        }
    }

    @Test fun memberWithNewProofHasPinkRingOthersHaveNone() {
        openRoom()
        ring(7, "새 인증")
        ring(8, "인증 없음")
    }

    @Test fun tappingRingShowsThatPersonsProofsAndOpeningOneMarksItSeen() {
        openRoom()
        compose.onNodeWithTag("memberFace-7").performClick()
        compose.onNodeWithTag("memberProofs").assertExists()
        compose.onNodeWithText("서버방장님의 인증").assertExists()
        compose.onNodeWithTag("memberProofNew-7301").assertExists()
        compose.onNodeWithTag("memberProof-7301").performClick()
        compose.waitUntil(5_000) { TestProofCalls.calls.contains("detail:7301") }
        Espresso.pressBack()
        waitText("서버멤버")
        // 서버는 아직 viewed=false로 주지만 이 앱에서 연 인증은 봤음으로 둔다.
        ring(7, "인증 봄")
    }

    @Test fun memberWithoutProofsIsNotClickable() {
        openRoom()
        compose.onNodeWithTag("memberFace-8").performClick()
        compose.onNodeWithTag("memberProofs").assertDoesNotExist()
        assertTrue(TestProofCalls.calls.none { it.startsWith("detail:") })
    }
}
