package com.dailyquest.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

/** 방 관리 흐름(FakeApi 기반). 실제 서버에는 어떤 요청도 보내지 않는다. 관리 버튼은 방 헤더의 ⋮ 메뉴에 있다(QA 디자인 1단계). */
class RoomManageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun confirm() = compose.onNodeWithTag("confirmAction").performClick()
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun openMenu() = compose.onNodeWithTag("roomMenu").performClick()
    /** 초대하기 버튼의 상태 설명(우리만 방: "초대코드 ○○○", 공개방: "공개방"). QA 디자인 2단계에서 초대코드 글자 줄 대신. */
    private fun assertInvite(state: String) = compose.onNodeWithTag("roomInvite")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
    private fun hasInvite(state: String) = hasTestTag("roomInvite") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)
    private fun menu(item: String) { openMenu(); compose.onNode(hasText(item) and hasClickAction()).performClick() }

    private fun openMyRoom(n: Int) {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
        compose.onNodeWithText("서버 내 방 $n").performClick()
        waitText("서버 설명 910$n")
        waitText("서버멤버")
    }

    @Test
    fun memberSeesNoOwnerControlsAndCanLeave() {
        openMyRoom(2)
        openMenu()
        compose.onNodeWithText("방 나가기").assertExists()
        compose.onNodeWithText("방 설정").assertDoesNotExist()
        compose.onNodeWithText("방 삭제").assertDoesNotExist()
        compose.onNodeWithText("멤버 관리").assertDoesNotExist()
        compose.onNodeWithText("코드 재발급").assertDoesNotExist()
        click("방 나가기")
        confirm()
        waitText("1 / 1 페이지 · 4개 방")
        compose.onNodeWithText("서버 내 방 2").assertDoesNotExist()
        assertEquals(listOf("leave:9102"), TestRoomCalls.calls.toList())
    }

    @Test
    fun ownerWithOtherMembersCannotLeave() {
        openMyRoom(1)
        menu("방 나가기")
        compose.onNodeWithText("방장은 다른 멤버가 있으면 나갈 수 없어요.", substring = true).assertExists()
        assertTrue(TestRoomCalls.calls.isEmpty())
    }

    @Test
    fun ownerUpdatesOnlyChangedFields() {
        openMyRoom(1)
        menu("방 설정")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("바뀐 이름")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("저장").performClick()
        waitText("바뀐 이름")
        val sent = TestRoomCalls.lastUpdate!!
        assertEquals("바뀐 이름", sent.name)
        assertNull(sent.description)
        assertNull(sent.isPublic)
        assertNull(sent.maxMembers)
        assertEquals(listOf("update:9101"), TestRoomCalls.calls.toList())
    }

    @Test
    fun capacityOutOfRangeIsRejectedLocally() {
        openMyRoom(1)
        menu("방 설정")
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement("1")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("저장").performClick()
        compose.onNodeWithText("최대 인원은 2~30명으로 입력해 주세요.").assertExists()
        assertTrue(TestRoomCalls.calls.isEmpty())
    }

    @Test
    fun ownerRegeneratesInviteCodeAfterConfirm() {
        openMyRoom(1)
        assertInvite("초대코드 SRV9101")
        menu("코드 재발급")
        compose.onNodeWithText("기존 코드는 즉시 사용할 수 없게 돼요.", substring = true).assertExists()
        confirm()
        compose.waitUntil(5_000) { TestRoomCalls.calls.any { it.startsWith("regenerate") } }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasInvite("초대코드 SRV9101")).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("roomInvite").assertExists()
        assertEquals(1, TestRoomCalls.calls.count { it.startsWith("regenerate") })
    }

    @Test
    fun ownerKicksMemberAfterConfirm() {
        openMyRoom(1)
        menu("멤버 관리")
        compose.onNodeWithTag("membersDialog").assertExists()
        compose.onAllNodesWithText("내보내기")[1].performClick()
        compose.onNodeWithText("‘서버멤버’ 님을 내보낼까요?").assertExists()
        confirm()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("서버멤버").fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf("kick:9101:8"), TestRoomCalls.calls.toList())
    }

    @Test
    fun ownerTransfersOwnershipAndLosesOwnerControls() {
        openMyRoom(1)
        menu("멤버 관리")
        compose.onAllNodesWithText("방장 위임")[1].performClick()
        confirm()
        compose.waitUntil(5_000) { TestRoomCalls.calls.any { it.startsWith("transfer") } }
        compose.waitForIdle()
        openMenu()
        compose.onNodeWithText("방 나가기").assertExists()
        compose.onNodeWithText("방 설정").assertDoesNotExist()
        compose.onNodeWithText("방 삭제").assertDoesNotExist()
        compose.onNodeWithText("멤버 관리").assertDoesNotExist()
        assertEquals(listOf("transfer:9101:8"), TestRoomCalls.calls.toList())
    }

    @Test
    fun ownerDeletesRoomAfterConfirmAndReturnsHome() {
        openMyRoom(1)
        menu("방 삭제")
        compose.onNodeWithText("되돌릴 수 없어요.", substring = true).assertExists()
        confirm()
        waitText("1 / 1 페이지 · 4개 방")
        compose.onNodeWithText("서버 내 방 1").assertDoesNotExist()
        assertEquals(listOf("delete:9101"), TestRoomCalls.calls.toList())
    }

    @Test
    fun failedManageRequestKeepsRoomAndShowsServerMessage() {
        openMyRoom(1)
        TestRoomCalls.mutationFailure = java.io.IOException("lost")
        menu("방 삭제")
        confirm()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("manageError").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("manageError").assertTextContains("요청 결과를 확인하지 못했어요.", substring = true)
        assertFalse(TestRoomCalls.gone.contains(9101L))
        assertNotEquals(0, TestRoomCalls.calls.size)
    }

    @Test
    fun ownerMenuHasAllManageItemsAndBodyHasNoManageButtons() {
        openMyRoom(1)
        // 본문에는 관리 버튼이 없다(⋮ 메뉴로 옮김).
        compose.onNodeWithText("방 설정").assertDoesNotExist()
        compose.onNodeWithText("방장 위임").assertDoesNotExist()
        compose.onNodeWithTag("roomVisibility").assert(hasAnyDescendant(hasText("우리만")))
        openMenu()
        listOf("menuEdit", "menuRegenerate", "menuMembers", "menuLeave", "menuDelete").forEach { compose.onNodeWithTag(it).assertExists() }
        assertTrue(TestRoomCalls.calls.isEmpty())
    }

    @Test
    fun publicRoomShowsPublicBadgeAndNoRegenerate() {
        openMyRoom(2)
        compose.onNodeWithTag("roomVisibility").assert(hasAnyDescendant(hasText("공개")))
        openMenu()
        compose.onNodeWithTag("menuRegenerate").assertDoesNotExist()
    }

    @Test
    fun closingMembersDialogDoesNothing() {
        openMyRoom(1)
        menu("멤버 관리")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("membersDialog").assertDoesNotExist()
        assertTrue(TestRoomCalls.calls.isEmpty())
    }
}
