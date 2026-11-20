package com.dailyquest.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.IOException

/** FakeApi 기반 STEP 8 마이페이지·계정 관리 흐름. 실제 서버에는 닉네임·비밀번호·탈퇴 요청을 보내지 않는다. */
class ProfileFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestShopCalls.seedDefaults(); TestShopCalls.coins = 320
        }
    }).around(compose)

    /** 로그아웃·탈퇴 테스트가 세션을 끝내므로, 같은 프로세스의 다음 테스트를 위해 가짜 로그인 상태로 복구한다. */
    @After fun resetFakeServer() {
        TestRoomCalls.reset()
        val c = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication).container
        c.tokens.set(com.dailyquest.app.data.auth.TokenPair("test-access", "test-refresh"))
        kotlinx.coroutines.runBlocking { c.authRepository.refreshProfile() }
    }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun openHome() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
    }
    private fun openMy() { openHome(); tab("마이").performClick(); waitTag("myScreen") }
    private fun menu(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun myPageShowsServerProfileStatsAndNoInventedValues() {
        openMy()
        compose.onNodeWithTag("myNickname").assertTextEquals("uitest")
        compose.onNodeWithTag("myEmail").assertTextEquals("ui-test@example.com")
        compose.onNodeWithTag("myJoinedAt").assertTextContains("2025.03.04", substring = true)
        compose.onNodeWithTag("myStreak").assertTextContains("3일", substring = true)
        compose.onNodeWithTag("myLongestStreak").assertTextContains("8일", substring = true)
        compose.onNodeWithTag("myRoomCount").assertTextContains("2개", substring = true)
        compose.onNodeWithTag("myCompleted").assertTextContains("11건", substring = true)
        compose.onNodeWithTag("myAvatarPlaceholder").assertExists()
    }

    @Test fun coinBalanceIsSameOnHomeMissionShopAndMy() {
        openHome()
        compose.onNodeWithTag("homeCoin").assertTextContains("320", substring = true)
        tab("미션").performClick(); waitTag("missionCoin")
        compose.onNodeWithTag("missionCoin").assertTextContains("320", substring = true)
        tab("쇼핑").performClick(); waitTag("shopCoin")
        compose.onNodeWithTag("shopCoin").assertTextContains("320", substring = true)
        tab("마이").performClick(); waitTag("myCoin")
        compose.onNodeWithTag("myCoin").assertTextContains("320", substring = true)
    }

    @Test fun coinPillOpensHistoryAndBackReturnsToMy() {
        openMy()
        compose.onNodeWithTag("myCoin").performClick()
        compose.waitForIdle(); Thread.sleep(500)
        compose.onNodeWithTag("myScreen").assertDoesNotExist()
        Espresso.pressBack()
        waitTag("myScreen")
    }

    @Test fun myInventoryMenuOpensOwnedItems() {
        openMy()
        menu("myInventory")
        waitTag("inventoryGrid")
        compose.onNodeWithText("기본 머리").assertExists()
        Espresso.pressBack()
        waitTag("myScreen")
    }

    @Test fun nicknameEditSavesAndReflectsOnMyPage() {
        openMy()
        menu("myEditProfile")
        waitTag("profileEditScreen")
        compose.onNodeWithTag("emailReadOnly").assertTextEquals("ui-test@example.com")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("새닉네임")
        compose.onNodeWithTag("nicknameSave").performClick()
        waitTag("myScreen")
        assertEquals(listOf("새닉네임"), TestProfileCalls.updates)
        compose.onNodeWithTag("myNickname").assertTextEquals("새닉네임")
    }

    @Test fun duplicateNicknameShowsErrorAndStaysOnScreen() {
        openMy()
        menu("myEditProfile")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(TestProfileCalls.TAKEN)
        compose.onNodeWithTag("nicknameSave").performClick()
        waitTag("nicknameError")
        compose.onNodeWithTag("profileEditScreen").assertExists()
    }

    @Test fun invalidNicknameDoesNotCallServer() {
        openMy()
        menu("myEditProfile")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("a")
        compose.onNodeWithTag("nicknameSave").performClick()
        waitTag("nicknameError")
        assertTrue(TestProfileCalls.updates.isEmpty())
    }

    private fun fillPasswords(cur: String, new: String, confirm: String) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(cur)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(new)
        compose.onAllNodes(hasSetTextAction())[2].performTextInput(confirm)
    }

    @Test fun passwordMismatchBlocksRequest() {
        openMy(); menu("myChangePassword"); waitTag("passwordScreen")
        fillPasswords("oldpass12", "newpass12", "different12")
        compose.onNodeWithTag("passwordSubmit").performClick()
        waitTag("passwordError")
        assertTrue(TestProfileCalls.passwordCalls.isEmpty())
    }

    @Test fun passwordChangeSuccessReturnsToLoginWithNotice() {
        openMy(); menu("myChangePassword"); waitTag("passwordScreen")
        fillPasswords("oldpass12", "newpass12", "newpass12")
        compose.onNodeWithTag("passwordSubmit").performClick()
        waitText("비밀번호가 변경되었어요")
        assertEquals(listOf("newpass12"), TestProfileCalls.passwordCalls)
    }

    @Test fun socialAccountSeesUnsupportedMessage() {
        TestProfileCalls.passwordFailure = TestShopCalls.http(409, "NO_PASSWORD_SET")
        openMy(); menu("myChangePassword"); waitTag("passwordScreen")
        fillPasswords("oldpass12", "newpass12", "newpass12")
        compose.onNodeWithTag("passwordSubmit").performClick()
        waitTag("passwordUnsupported")
    }

    @Test fun withdrawRequiresAgreementAndFinalConfirmation() {
        openMy(); menu("myWithdraw"); waitTag("withdrawScreen")
        compose.onNodeWithTag("withdrawSubmit").performScrollTo().performClick()
        waitTag("withdrawError")
        assertTrue(TestProfileCalls.withdrawCalls.isEmpty())
        compose.onNodeWithTag("withdrawAgree").performScrollTo().performClick()
        compose.onNodeWithTag("withdrawSubmit").performScrollTo().performClick()
        waitTag("withdrawCancel")
        compose.onNodeWithTag("withdrawCancel").performClick()
        compose.waitForIdle()
        assertTrue(TestProfileCalls.withdrawCalls.isEmpty())
    }

    @Test fun withdrawConfirmedGoesToLogin() {
        openMy(); menu("myWithdraw"); waitTag("withdrawScreen")
        compose.onNodeWithTag("withdrawAgree").performScrollTo().performClick()
        compose.onNodeWithTag("withdrawSubmit").performScrollTo().performClick()
        waitTag("withdrawConfirm")
        compose.onNodeWithTag("withdrawConfirm").performClick()
        waitText("회원 탈퇴가 완료됐어요")
        assertEquals(1, TestProfileCalls.withdrawCalls.size)
    }

    @Test fun ownerBlockedWithdrawShowsGuidanceAndKeepsAccount() {
        TestProfileCalls.withdrawFailure = TestShopCalls.http(409, "OWNER_MUST_TRANSFER")
        openMy(); menu("myWithdraw"); waitTag("withdrawScreen")
        compose.onNodeWithTag("withdrawAgree").performScrollTo().performClick()
        compose.onNodeWithTag("withdrawSubmit").performScrollTo().performClick()
        waitTag("withdrawConfirm"); compose.onNodeWithTag("withdrawConfirm").performClick()
        waitTag("withdrawOwnerNotice")
        compose.onNodeWithTag("withdrawGoRooms").performScrollTo().performClick()
        waitText("서버 내 방 1")
    }

    @Test fun logoutFromMyPageReturnsToLogin() {
        openMy(); menu("myLogout")
        waitText("로그인")
    }
}
