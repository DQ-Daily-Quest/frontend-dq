package com.dailyquest.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.After
import org.junit.Test
import org.junit.Rule

/** 일반 사용자 흐름의 뒤로가기가 개발용 Catalog가 아닌 홈으로 돌아오는지 검증한다(FakeApi 기반). */
class NavigationBackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun isRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun tab(label: String) = compose.onNode(hasText(label) and isRole(Role.Tab))

    // 하네스는 Catalog에서 시작하므로 홈 진입 후 Catalog 항목이 보이지 않아야 일반 홈 화면이다.
    private fun openHome() {
        compose.onNode(hasText("홈 · 우리만 (마을 지도)") and hasClickAction()).performClick()
        waitText("서버 내 방 1")
    }

    private fun assertOnHome() {
        waitText("서버 내 방 1")
        compose.onNodeWithText("홈 · 우리만 (마을 지도)").assertDoesNotExist()
    }

    @Test
    fun missionTabSystemBackReturnsToHome() {
        openHome()
        tab("미션").performClick()
        waitTag("missionList")
        Espresso.pressBack()
        assertOnHome()
    }

    @Test
    fun missionTabScreenBackButtonReturnsToHome() {
        openHome()
        tab("미션").performClick()
        waitTag("missionList")
        compose.onAllNodes(isRole(Role.Button) and hasClickAction())[0].performClick()
        assertOnHome()
    }

    @Test
    fun shopAndMyTabsSystemBackReturnToHome() {
        for (label in listOf("쇼핑", "마이")) {
            openHome()
            tab(label).performClick()
            compose.waitForIdle()
            Thread.sleep(500)
            Espresso.pressBack()
            assertOnHome()
            // 다음 반복을 위해 Catalog 하네스로 복귀
            Espresso.pressBack()
            waitText("홈 · 우리만 (마을 지도)")
        }
    }

    @Test
    fun switchingBetweenTabsDoesNotStackDuplicatesAndBackGoesHome() {
        openHome()
        tab("쇼핑").performClick()
        waitText("쇼핑")
        tab("마이").performClick()
        waitText("마이")
        Espresso.pressBack()
        assertOnHome()
    }

    @Test
    fun connectedJoinAndCreateScreensHideSampleBanner() {
        openHome()
        compose.onNodeWithText("샘플 데이터 · 서버 미연동").assertDoesNotExist()
        compose.onNode(hasText("초대코드 참여") and hasClickAction()).performClick()
        compose.onNodeWithTag("realJoinRoom").assertExists()
        compose.onNodeWithText("샘플 데이터 · 서버 미연동").assertDoesNotExist()
    }
}
