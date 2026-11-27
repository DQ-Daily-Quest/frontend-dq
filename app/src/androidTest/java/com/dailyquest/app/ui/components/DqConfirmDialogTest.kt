package com.dailyquest.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** QA 디자인: 공용 확인 창(인증 승인·거절에 씀). 제목·설명이 보이고, 확인·취소가 각각 한 번씩 불린다. */
class DqConfirmDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmAndCancelCallTheirHandlers() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            DqConfirmDialog(
                title = "이 인증을 거절할까요?", body = "윤진영님의 인증입니다.", confirmLabel = "거절", danger = true,
                confirmTag = "proofVoteConfirm", onConfirm = { confirmed++ }, onDismiss = { dismissed++ },
            )
        }
        compose.onNodeWithText("이 인증을 거절할까요?").assertExists()
        compose.onNodeWithText("윤진영님의 인증입니다.").assertExists()
        compose.onNodeWithTag("proofVoteConfirm").performClick()
        compose.onNodeWithTag("dqConfirmCancel").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }
}
