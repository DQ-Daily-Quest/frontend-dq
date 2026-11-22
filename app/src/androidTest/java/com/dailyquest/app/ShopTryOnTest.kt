package com.dailyquest.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * STEP 9-7 상점 아바타 탭의 입어보기 흐름 (FakeApi 기반).
 * 테스트 앱은 3D를 끄므로(AppContainer.avatar3dEnabled = false) 미리보기 자리에는 빈 상자가 있고,
 * 입어보기 상태는 미리보기 아래 "입어보는 중" 표시와 구매 버튼으로 확인한다. 3D 모습은 debug.Avatar3dTestScreenTest가 확인한다.
 */
class ShopTryOnTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestShopCalls.seedDefaults(); TestShopCalls.coins = 320
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun openShop() {
        compose.onNode(hasText("쇼핑 (아바타 · 우리 집)") and hasClickAction()).performScrollTo().performClick()
        waitTag("shopGrid")
    }

    @Test fun avatarTabShowsMyAvatarPreview() {
        openShop()
        compose.onNodeWithTag("myAvatarPreview").assertExists()
        // 테스트 계정은 서버 gender가 없어 "캐릭터를 먼저 만들어 주세요"가 보인다(성별 선택은 STEP 9-9).
        waitTag("myAvatarNeedsGender")
    }

    @Test fun tappingItemTriesItOnAndShowsBuyButton() {
        openShop()
        compose.onNodeWithTag("shopItem_2").performClick()

        compose.onNodeWithTag("myAvatarTryOnLabel").assertTextContains("민트 상의", substring = true)
        compose.onNodeWithTag("shopTryOnAction").assertTextContains("120 코인으로 구매").assertIsEnabled()
        // 카드를 누르는 것만으로는 상세 창이 뜨지 않는다
        assertTrue(!exists("shopDetail"))
    }

    @Test fun buyButtonOpensExistingPurchaseDialog() {
        openShop()
        compose.onNodeWithTag("shopItem_2").performClick()
        compose.onNodeWithTag("shopTryOnAction").performClick()

        waitTag("shopDetail")
        compose.onNodeWithTag("shopBuy").assertIsEnabled()
        assertTrue(TestShopCalls.purchaseCalls.isEmpty())
    }

    @Test fun tappingSameItemAgainTakesItOff() {
        openShop()
        compose.onNodeWithTag("shopItem_2").performClick()
        compose.onNodeWithTag("shopItem_2").performClick()

        assertTrue(!exists("myAvatarTryOnLabel"))
        assertTrue(!exists("shopTryOnAction"))
    }

    @Test fun tryingOnTwoSlotsThenResetRestoresMyAvatar() {
        openShop()
        compose.onNodeWithTag("shopItem_2").performClick()   // 상의
        compose.onNodeWithTag("shopItem_3").performClick()   // 모자
        compose.onNodeWithTag("myAvatarTryOnLabel").assertTextContains("민트 상의", substring = true)
        compose.onNodeWithTag("myAvatarTryOnLabel").assertTextContains("비싼 모자", substring = true)

        compose.onNodeWithTag("myAvatarTryOnClear").performClick()

        assertTrue(!exists("myAvatarTryOnLabel"))
        assertTrue(!exists("shopTryOnAction"))
    }

    @Test fun ownedItemShowsOwnedInsteadOfBuy() {
        openShop()
        compose.onNodeWithTag("shopItem_1").performClick()   // 기본 머리(보유)

        compose.onNodeWithTag("shopTryOnAction").assertTextContains("보유 중인 아이템이에요").assertIsNotEnabled()
    }

    @Test fun furnitureTabStillOpensDetailDirectlyWithoutPreview() {
        openShop()
        compose.onNodeWithTag("shopType_FURNITURE").performClick()
        waitTag("shopItem_4")
        assertTrue(!exists("myAvatarPreview"))

        compose.onNodeWithTag("shopItem_4").performClick()

        waitTag("shopDetail")
    }
}
