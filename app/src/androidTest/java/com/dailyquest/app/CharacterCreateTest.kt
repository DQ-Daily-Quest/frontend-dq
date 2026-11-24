package com.dailyquest.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * STEP 9-9 캐릭터 만들기 (FakeApi 기반).
 * 성별이 없는 계정으로 앱을 켜면 로그인 직후 캐릭터 만들기 화면으로 가고, 고른 성별을 서버에 확정한 뒤 다음 화면으로 넘어간다.
 * 테스트 앱은 3D를 끄므로 미리보기는 빈 상자다(3D 모습은 debug.Avatar3dTestScreenTest가 확인한다).
 */
class CharacterCreateTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestAvatarCalls.reset()
            TestAvatarCalls.gender = null   // 캐릭터가 없는 계정으로 시작
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestAvatarCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun accountWithoutCharacterStartsAtCharacterCreation() {
        waitTag("characterStart")
        compose.onNodeWithText("캐릭터 만들기").assertExists()
        compose.onNodeWithTag("characterPreview").assertExists()
        // 건너뛰기는 없다(필수)
        assertEquals(0, compose.onAllNodesWithText("나중에", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun choosingGirlAndStartingSetsGenderAndMovesOn() {
        waitTag("characterStart")
        compose.onNodeWithTag("characterGender_FEMALE").performClick()
        compose.onNodeWithTag("characterSkin_BROWN").performClick()
        compose.onNodeWithTag("characterStart").assertIsEnabled().performClick()

        // 테스트 앱의 로그인 후 첫 화면(개발용 Catalog)으로 넘어간다
        waitText("쇼핑 (아바타 · 우리 집)")
        assertEquals(listOf("FEMALE"), TestAvatarCalls.genderRequests.toList())
        assertEquals("FEMALE", TestAvatarCalls.gender)
    }

    @Test fun defaultChoiceIsBoy() {
        waitTag("characterStart")
        compose.onNodeWithTag("characterStart").performClick()

        waitText("쇼핑 (아바타 · 우리 집)")
        assertEquals(listOf("MALE"), TestAvatarCalls.genderRequests.toList())
    }

    @Test fun failureShowsMessageAndStaysToRetry() {
        waitTag("characterStart")
        TestAvatarCalls.genderFailure = java.io.IOException("offline")
        compose.onNodeWithTag("characterStart").performClick()

        waitTag("characterError")
        compose.onNodeWithTag("characterStart").assertIsEnabled()
        // 다시 누르면 성공해서 넘어간다
        compose.onNodeWithTag("characterStart").performClick()
        waitText("쇼핑 (아바타 · 우리 집)")
        assertEquals(2, TestAvatarCalls.genderRequests.size)
    }
}
