package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** FakeApi 기반 STEP 5 인증 흐름. 실제 서버에는 요청을 보내지 않는다(업로드·제출·승인 모두 Fake). */
class ProofFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset(); TestMissionCalls.seeded = true
            TestProofCalls.seedPending(7001, 9101, 8001)
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun openRoomMissionsTab() {
        click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1")
        compose.onNode(hasText("서버 내 방 1") and hasText("1명")).performClick()
        waitText("서버 설명 9101")
        click("미션")
        waitText("서버 앱 미션 9101")
    }

    @Test fun doableMissionOpensProofSubmitAndBackReturnsToDetail() {
        openRoomMissionsTab()
        compose.onNode(hasText("서버 앱 미션 9101") and hasAnyAncestor(hasTestTag("roomMissions"))).performScrollTo().performClick()
        waitTag("missionDetail")
        compose.onNodeWithTag("missionProofAction").assertExists().performClick()
        waitTag("proofSubmit")
        compose.onNodeWithText("사진 촬영").assertExists()
        compose.onNodeWithText("앨범 영상").assertExists()
        compose.onNodeWithText("샘플 데이터", substring = true).assertDoesNotExist()
        // 미디어 없이는 제출할 수 없고 서버 요청도 없다.
        compose.onNodeWithTag("proofSubmitButton").assertIsNotEnabled()
        assertTrue(TestProofCalls.calls.none { it.startsWith("upload") || it.startsWith("submit") })
        Espresso.pressBack()
        waitTag("missionDetail")
    }

    @Test fun pendingProofListLeadsToDetailAndApproveShowsServerResult() {
        openRoomMissionsTab()
        compose.onNodeWithTag("roomPendingProofs").performScrollTo().performClick()
        waitTag("pendingProof-7001")
        compose.onNodeWithTag("pendingProof-7001").performClick()
        waitTag("proofDetail")
        waitTag("proofApprove")
        compose.onNodeWithTag("proofApprove").performClick()
        compose.onNodeWithTag("proofVoteConfirm").performClick()
        waitTag("proofVoteNotice")
        assertEquals(listOf("approve:7001"), TestProofCalls.calls.filter { it.startsWith("approve") })
        compose.onNodeWithTag("proofStatus").assertTextContains("승인 완료")
        // 투표 후에는 버튼이 사라진다.
        compose.onNodeWithTag("proofApprove").assertDoesNotExist()
        Espresso.pressBack()
        waitTag("pendingProofs")
    }

    @Test fun rejectedProofShowsRejectedStateAndVoteErrorIsShown() {
        TestProofCalls.voteFailure = retrofit2.HttpException(
            retrofit2.Response.error<Any>(409, """{"code":"PROOF_ALREADY_FINALIZED","message":"x"}""".toResponseBodyJson())
        )
        openRoomMissionsTab()
        compose.onNodeWithTag("roomPendingProofs").performScrollTo().performClick()
        waitTag("pendingProof-7001")
        compose.onNodeWithTag("pendingProof-7001").performClick()
        waitTag("proofReject")
        compose.onNodeWithTag("proofReject").performClick()
        compose.onNodeWithTag("proofVoteConfirm").performClick()
        waitTag("proofVoteError")
    }

    @Test fun myPendingProofMissionShowsViewMyProofNotSubmit() {
        TestProofCalls.seedPending(1L, 9102, 8003, user = okRoomOwner())
        click("홈 · 우리만 (마을 지도)"); waitText("서버 내 방 1")
        tab("미션").performClick()
        waitTag("missionList")
        compose.onNodeWithText("서버 앱 미션 9102").performClick()
        waitTag("missionDetail")
        compose.onNodeWithText("인증하기").assertDoesNotExist()
        compose.onNodeWithText("내 인증 보기").performClick()
        waitTag("proofDetail")
        waitTag("proofStatus")
        compose.onNodeWithTag("proofApprove").assertDoesNotExist()
        assertTrue(TestProofCalls.calls.contains("detail:1"))
        Espresso.pressBack()
        waitTag("missionDetail")
    }

    private fun okRoomOwner() = com.dailyquest.app.data.remote.dto.RoomOwnerDto(1, "uitest")

    private fun String.toResponseBodyJson() =
        toResponseBody("application/json".toMediaType())
}
