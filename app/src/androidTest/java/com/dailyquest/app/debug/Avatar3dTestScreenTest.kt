package com.dailyquest.app.debug

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * STEP 9 디버그 3D 테스트 화면 스모크 테스트. 화면은 앱 본체의 AvatarView로 그린다(9-4).
 * 실제 S3에서 GLB를 받으므로 **인터넷이 연결된 기기·에뮬레이터**가 필요하다.
 * 3D가 눈에 보이는지는 자동으로 판별할 수 없어서, 상태 줄의 "준비됨·파츠 수·받은 파일 수"로 확인한다.
 *
 * SceneView는 매 프레임 계속 그리므로 Compose 테스트가 "화면이 멈출 때"를 기다리면 시간 초과가 난다
 * (ComposeNotIdleException). 그래서 프레임 시계를 자동으로 돌리지 않고, 기다리는 동안 테스트가 직접 넘긴다.
 */
class Avatar3dTestScreenTest {
    @get:Rule val compose = createAndroidComposeRule<Avatar3dTestActivity>()

    @Before fun pauseFrameClock() {
        compose.mainClock.autoAdvance = false
    }

    /** 실제 시간으로 최대 [LOAD_TIMEOUT_MS] 동안, 프레임을 넘기면서 상태 줄에 [texts]가 모두 나오기를 기다린다. */
    private fun waitStatus(vararg texts: String) {
        val matcher = texts.fold(hasTestTag("avatar3dStatus")) { m, t -> m and hasText(t, substring = true) }
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            val failed = compose.onAllNodes(hasTestTag("avatar3dStatus") and hasText("실패", substring = true))
            if (failed.fetchSemanticsNodes().isNotEmpty()) fail("불러오기 실패: ${statusText()}")
            Thread.sleep(FRAME_STEP_MS)
        }
        fail("상태 줄에 ${texts.toList()}가 ${LOAD_TIMEOUT_MS}ms 안에 나오지 않았습니다. 현재: ${statusText()}")
    }

    private fun statusText(): String = runCatching {
        compose.onNodeWithTag("avatar3dStatus").fetchSemanticsNode().config
            .let { it[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
    }.getOrDefault("?")

    /**
     * 버튼의 클릭 동작을 직접 실행한다. 화면 밖에 걸친 칩도 누를 수 있다.
     * performScrollTo()는 스크롤 애니메이션이 프레임 시계를 기다리는데, 이 테스트는 시계를 멈춰 두므로 영원히 멈춘다(실제로 멈췄다).
     */
    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
    }

    private fun select(label: String, partCount: Int) {
        click(label)
        waitStatus("$label · 준비됨 · 파츠 ${partCount}개")
    }

    @Test fun firstPartLoadsOnLaunch() {
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
        compose.onNodeWithTag("avatarView").assertIsDisplayed()
        waitStatus("남 몸체 · 준비됨 · 파츠 1개", "받은 파일 1개")
    }

    @Test fun everyPartAndOutfitLoads() {
        waitStatus("남 몸체 · 준비됨")
        (TEST_SINGLE_PARTS + TEST_OUTFITS).forEach { select(it.label, it.codes.size) }
    }

    /** 9-4: 한 벌에서 모자만 쓰면 모자 파일 하나만 받는다. */
    @Test fun puttingOnHatFetchesOnlyHat() {
        waitStatus("남 몸체 · 준비됨")
        select("남 기본", 6)
        click("모자 바꾸기")
        waitStatus("남 기본 · 준비됨 · 파츠 7개", "받은 파일 1개")
    }

    /** 9-4: 상의를 바꾸면 상의 파일 하나만 받고 파츠 수는 그대로다. */
    @Test fun changingTopFetchesOnlyTop() {
        waitStatus("남 몸체 · 준비됨")
        select("남 기본", 6)
        click("상의 바꾸기")
        waitStatus("남 기본 · 준비됨 · 파츠 6개", "받은 파일 1개")
        click("상의 바꾸기")
        waitStatus("남 기본 · 준비됨 · 파츠 6개", "받은 파일 1개")
    }

    /** 9-4: 모자를 벗으면 아무것도 받지 않는다. */
    @Test fun takingOffHatFetchesNothing() {
        waitStatus("남 몸체 · 준비됨")
        select("남 카페+모자", 7)
        click("모자 벗기")
        waitStatus("남 카페+모자 · 준비됨 · 파츠 6개", "받은 파일 0개")
    }

    /** 9-4: 회전을 끈 고정 보기로 바꿔도 모델은 다시 받지 않는다. */
    @Test fun fixedViewKeepsModels() {
        waitStatus("남 몸체 · 준비됨")
        select("여 기본", 6)
        click("고정 보기")
        repeat(10) { compose.mainClock.advanceTimeBy(FRAME_STEP_MS) }
        waitStatus("여 기본 · 준비됨 · 파츠 6개")
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 40_000L
        const val FRAME_STEP_MS = 100L
    }
}
