package com.dailyquest.app

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

/** FakeApi 기반 비밀번호 찾기 흐름. 실제 서버에는 인증 코드 요청도, 비밀번호 변경도 보내지 않는다. */
class PasswordResetFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { TestRoomCalls.reset(); TestShopCalls.seedDefaults(); TestResetCalls.reset() }
    }).around(compose)

    /** 로그아웃으로 세션이 끝나므로 다음 테스트를 위해 가짜 로그인 상태로 복구한다. */
    @After fun restore() {
        TestResetCalls.reset(); TestRoomCalls.reset()
        val c = (androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DQApplication).container
        c.tokens.set(com.dailyquest.app.data.auth.TokenPair("test-access", "test-refresh"))
        kotlinx.coroutines.runBlocking { c.authRepository.refreshProfile() }
    }

    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun openLogin() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
        compose.onNode(hasText("마이") and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).performClick()
        waitTag("myScreen")
        compose.onNodeWithTag("myLogout").performScrollTo().performClick()
        waitTag("forgotPassword")
    }
    private fun openReset() { openLogin(); compose.onNodeWithTag("forgotPassword").performClick(); waitTag("resetScreen") }
    private fun toCodeStep() {
        openReset()
        field("resetEmailInput").performTextInput("a@b.com")
        compose.onNodeWithTag("resetSendCode").performClick()
        waitTag("resetCodeInput")
    }

    @Test fun enterFromLoginAndRequestCode() {
        toCodeStep()
        compose.onNodeWithTag("resetNotice").assertTextContains("가입된 이메일이라면 인증 코드를 보냈어요.", substring = true)
        assertEquals(listOf("a@b.com"), TestResetCalls.requests.toList())
        compose.onNodeWithTag("resetResend").assertIsNotEnabled()
        compose.onNodeWithTag("resetResend").assertTextContains("초", substring = true)
        compose.onNodeWithTag("resetCodeHint").assertTextContains("10분", substring = true)
    }

    @Test fun invalidEmailIsRejectedLocally() {
        openReset()
        field("resetEmailInput").performTextInput("nope")
        compose.onNodeWithTag("resetSendCode").performClick()
        waitTag("resetError")
        assertTrue(TestResetCalls.requests.isEmpty())
    }

    @Test fun mailUnavailableShowsError() {
        TestResetCalls.requestFailure = TestShopCalls.http(503, "MAIL_UNAVAILABLE")
        openReset()
        field("resetEmailInput").performTextInput("a@b.com")
        compose.onNodeWithTag("resetSendCode").performClick()
        waitTag("resetError")
        compose.onNodeWithTag("resetCodeInput").assertDoesNotExist()
    }

    @Test fun wrongCodeShowsGuidance() {
        toCodeStep()
        field("resetCodeInput").performTextInput("999999")
        compose.onNodeWithTag("resetVerify").performClick()
        waitTag("resetError")
        compose.onNodeWithTag("resetError").assertTextContains("올바르지 않거나 만료", substring = true)
        compose.onNodeWithTag("resetNewPassword").assertDoesNotExist()
    }

    @Test fun fullFlowReturnsToLoginWithoutAutoLogin() {
        toCodeStep()
        field("resetCodeInput").performTextInput("012345")
        compose.onNodeWithTag("resetVerify").performClick()
        waitTag("resetNewPassword")
        field("resetNewPassword").performTextInput("N3wP@ssword")
        field("resetConfirmPassword").performTextInput("N3wP@ssword")
        compose.onNodeWithTag("resetSubmit").performClick()
        waitTag("forgotPassword")
        waitText("비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.")
        assertEquals(listOf(Triple("a@b.com", "012345", "N3wP@ssword")), TestResetCalls.confirms.toList())
        compose.onNodeWithTag("myScreen").assertDoesNotExist()
    }

    @Test fun passwordMismatchBlocksSubmit() {
        toCodeStep()
        field("resetCodeInput").performTextInput("012345")
        compose.onNodeWithTag("resetVerify").performClick()
        waitTag("resetNewPassword")
        field("resetNewPassword").performTextInput("N3wP@ssword")
        field("resetConfirmPassword").performTextInput("different123")
        compose.onNodeWithTag("resetSubmit").performClick()
        waitTag("resetError")
        assertTrue(TestResetCalls.confirms.isEmpty())
    }

    @Test fun lostConfirmResponseIsNotShownAsSuccess() {
        TestResetCalls.confirmFailure = java.io.IOException("lost")
        toCodeStep()
        field("resetCodeInput").performTextInput("012345")
        compose.onNodeWithTag("resetVerify").performClick()
        waitTag("resetNewPassword")
        field("resetNewPassword").performTextInput("N3wP@ssword")
        field("resetConfirmPassword").performTextInput("N3wP@ssword")
        compose.onNodeWithTag("resetSubmit").performClick()
        waitTag("resetError")
        compose.onNodeWithTag("resetError").assertTextContains("확인하지 못했어요", substring = true)
        compose.onNodeWithTag("resetGoLogin").assertExists()
    }

    @Test fun backWalksStepsThenReturnsToLogin() {
        toCodeStep()
        field("resetCodeInput").performTextInput("012345")
        compose.onNodeWithTag("resetVerify").performClick()
        waitTag("resetNewPassword")
        Espresso.pressBack(); waitTag("resetCodeInput")
        Espresso.pressBack(); waitTag("resetEmailInput")
        Espresso.pressBack(); waitTag("forgotPassword")
        compose.onNodeWithTag("resetScreen").assertDoesNotExist()
    }

    @Test fun loginScreenStillHasEmailAndSocialLogin() {
        openLogin()
        waitText("로그인")
        compose.onNodeWithText("카카오로 시작하기").assertExists()
        compose.onNodeWithText("Google로 시작하기").assertExists()
        compose.onNodeWithTag("forgotPassword").assertExists()
    }
}
