package com.dailyquest.app.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

/** QA 디자인 5단계: 앱 안 알림이 뜨고, 성공/실패가 구분되고, 시간이 지나면 사라진다. */
class DqToastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsSuccessThenErrorAndDisappears() {
        val state = DqToastState()
        compose.mainClock.autoAdvance = false
        compose.setContent { DqToastHost(state) }

        compose.runOnIdle { state.show("'라군 눈' 구매가 완료됐어요.") }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("dqToast").assert(hasText("'라군 눈' 구매가 완료됐어요."))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "성공"))

        compose.runOnIdle { state.show("코인이 부족해요.", isError = true) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("dqToast").assert(hasText("코인이 부족해요."))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "실패"))

        compose.mainClock.advanceTimeBy(DQ_TOAST_MILLIS + 1_000)
        compose.onNodeWithTag("dqToast").assertDoesNotExist()
    }
}
