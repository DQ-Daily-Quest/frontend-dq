package com.dailyquest.app.debug

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** STEP 10-4 디버그 "DQ 방 테스트" 화면이 실제 S3 그림으로 예시 배치를 다 그리고, 빈 방·원래 크기 버튼이 동작한다. 인터넷 필요. */
class RoomDecorTestScreenTest {
    @get:Rule val compose = createAndroidComposeRule<RoomDecorTestActivity>()

    private fun waitStatus(vararg texts: String) {
        val matcher = texts.fold(hasTestTag("roomTestStatus")) { m, t -> m and hasText(t, substring = true) }
        compose.waitUntil(90_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun exampleLoadsThenEmptyRoomAndResetWork() {
        waitStatus("가구 54개", "준비됨")
        compose.onNodeWithText("빈 방").performClick()
        waitStatus("가구 0개", "준비됨")
        compose.onNodeWithText("예시 54개").performClick()
        waitStatus("가구 54개", "준비됨")
        compose.onNodeWithText("원래 크기").performClick()
        waitStatus("확대 1.0배")
    }
}
