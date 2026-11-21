package com.dailyquest.app.debug

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * STEP 9-2 디버그 3D 테스트 화면 스모크 테스트.
 * 실제 S3에서 GLB를 받으므로 **인터넷이 연결된 기기·에뮬레이터**가 필요하다.
 * 3D가 눈에 보이는지는 자동으로 판별할 수 없어서, 다운로드·모델 생성이 오류 없이 끝나는지만 확인한다.
 *
 * SceneView는 매 프레임 계속 그리므로 Compose 테스트가 "화면이 멈출 때"를 기다리면 시간 초과가 난다
 * (ComposeNotIdleException). 그래서 프레임 시계를 자동으로 돌리지 않고, 기다리는 동안 테스트가 직접 넘긴다.
 */
class Avatar3dTestScreenTest {
    @get:Rule val compose = createAndroidComposeRule<Avatar3dTestActivity>()

    @Before fun pauseFrameClock() {
        compose.mainClock.autoAdvance = false
    }

    /** 실제 시간으로 최대 [LOAD_TIMEOUT_MS] 동안, 프레임을 넘기면서 [fileName] 로드 완료 문구를 기다린다. */
    private fun waitLoaded(fileName: String) {
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
            val loaded = compose.onAllNodes(
                hasTestTag("avatar3dStatus") and hasText(fileName, substring = true) and hasText("모델 생성", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
            if (loaded) return
            assertNoFailure()
            Thread.sleep(FRAME_STEP_MS)
        }
        fail("$fileName 로드가 ${LOAD_TIMEOUT_MS}ms 안에 끝나지 않았습니다")
    }

    private fun assertNoFailure() {
        val failures = compose.onAllNodesWithText("실패", substring = true).fetchSemanticsNodes()
        if (failures.isNotEmpty()) fail("로드 실패 메시지가 보입니다")
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
    }

    @Test fun firstPartLoadsOnLaunch() {
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
        compose.onNodeWithTag("avatar3dScene").assertIsDisplayed()
        waitLoaded("DQ_Boy_01_Body.glb")
    }

    @Test fun everySamplePartLoads() {
        waitLoaded("DQ_Boy_01_Body.glb")
        SAMPLE_PARTS.drop(1).forEach { (label, url) ->
            click(label)
            waitLoaded(url.substringAfterLast('/'))
        }
    }

    @Test fun reloadDownloadsAgain() {
        waitLoaded("DQ_Boy_01_Body.glb")
        click("다시 불러오기")
        // 다시 불러오는 동안에는 "불러오는 중" 문구로 바뀐다
        compose.onNodeWithText("불러오는 중", substring = true).assertIsDisplayed()
        waitLoaded("DQ_Boy_01_Body.glb")
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val FRAME_STEP_MS = 100L
    }
}
