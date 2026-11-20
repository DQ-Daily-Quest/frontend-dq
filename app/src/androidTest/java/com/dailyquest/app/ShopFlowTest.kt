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
import java.io.IOException

/** FakeApi 기반 STEP 7 상점·인벤토리·코인 내역 흐름. 실제 서버에는 어떤 구매 요청도 보내지 않는다. */
class ShopFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            TestRoomCalls.reset()
            TestShopCalls.seedDefaults(); TestShopCalls.coins = 320
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset() }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(text: String) = compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun openShop() {
        compose.onNode(hasText("쇼핑 (아바타 · 우리 집)") and hasClickAction()).performScrollTo().performClick()
        waitTag("shopGrid")
    }
    private fun openItem(id: Long) { compose.onNodeWithTag("shopItem_$id").performClick(); waitTag("shopDetail") }
    private fun state(id: Long) = compose.onNodeWithTag("shopItemState_$id", useUnmergedTree = true)

    @Test fun catalogShowsServerItemsWithOwnedStateAndNoSampleBanner() {
        openShop()
        compose.onNodeWithText("민트 상의").assertExists()
        state(1).assertTextEquals("기본 제공")
        state(2).assertTextEquals("120 코인")
        // 서버 연동 화면이라 샘플 안내가 없고 가구는 종류 탭으로만 보인다.
        compose.onNodeWithText("샘플 데이터", substring = true).assertDoesNotExist()
        compose.onNodeWithText("나무 책상").assertDoesNotExist()
        compose.onNodeWithTag("shopType_FURNITURE").performClick()
        waitText("나무 책상")
        compose.onNodeWithText("민트 상의").assertDoesNotExist()
        compose.onNodeWithTag("shopType_AVATAR").performClick()
        compose.onNodeWithTag("shopCategory_TOP").performClick()
        waitText("민트 상의")
        compose.onNodeWithText("비싼 모자").assertDoesNotExist()
    }

    @Test fun purchaseUsesServerBalanceAndUpdatesCatalogAndInventory() {
        openShop()
        compose.onNodeWithTag("shopCoin").assertTextContains("320", substring = true)
        openItem(2)
        compose.onNodeWithTag("shopBuy").performClick()
        compose.onNodeWithText("보유 코인 320 → 구매 후 200", substring = true).assertExists()
        compose.onNodeWithTag("shopConfirm").performClick()
        compose.waitUntil(5_000) { TestShopCalls.purchaseCalls.size == 1 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("shopCoin").fetchSemanticsNodes().isNotEmpty() && runCatching { state(2).assertTextEquals("보유 중") }.isSuccess }
        compose.onNodeWithText("200", substring = true).assertExists()
        assertEquals(200, TestShopCalls.coins)
        // 실제 구매는 Mock 커스터마이징 저장소의 코인·소유권을 바꾸지 않는다.
        compose.runOnIdle {
            assertEquals(320, com.dailyquest.app.data.customization.CustomizationData.store.state.coins)
            assertTrue("top_premium" !in com.dailyquest.app.data.customization.CustomizationData.store.state.owned)
        }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("shopTabOwned").performClick()
        waitTag("inventoryGrid")
        compose.onNodeWithText("민트 상의").assertExists()
    }

    @Test fun insufficientCoinsDisablesPurchaseWithoutRequest() {
        openShop()
        openItem(3)
        compose.onNodeWithTag("shopBuy").assertIsNotEnabled()
        compose.onNodeWithText("코인이 부족해요.", substring = true).assertExists()
        assertTrue(TestShopCalls.purchaseCalls.isEmpty())
    }

    @Test fun lostPurchaseResponseIsVerifiedAndNeverRetried() {
        TestShopCalls.purchaseFailure = IOException("lost"); TestShopCalls.applyBeforeFailure = true
        openShop()
        openItem(2)
        compose.onNodeWithTag("shopBuy").performClick()
        compose.onNodeWithTag("shopConfirm").performClick()
        compose.waitUntil(5_000) { runCatching { state(2).assertTextEquals("보유 중") }.isSuccess }
        assertEquals(listOf(2L), TestShopCalls.purchaseCalls)
        assertEquals(200, TestShopCalls.coins)
    }

    @Test fun coinHistoryOpensFromShopAndBackReturnsToShop() {
        TestShopCalls.txs.add(com.dailyquest.app.data.remote.dto.CoinTransactionDto(1, 50, 320, "MISSION_REWARD", 5, "2025-04-17T01:02:03Z"))
        openShop()
        compose.onNodeWithTag("shopCoin").performClick()
        waitTag("coinTx_1")
        compose.onNodeWithText("미션 보상").assertExists()
        compose.onNodeWithText("+50").assertExists()
        compose.onNodeWithText("샘플 데이터", substring = true).assertDoesNotExist()
        Espresso.pressBack()
        waitTag("shopGrid")
        Espresso.pressBack()
        // 일반 사용자 뒤로가기는 개발용 Catalog가 아니라 홈 계열로 간다.
        compose.waitForIdle()
        compose.onNodeWithTag("shopGrid").assertDoesNotExist()
    }

    @Test fun listFailureShowsRetryAndRecovers() {
        openShop()
        TestShopCalls.listFailure = IOException("down")
        compose.onNodeWithTag("shopType_FURNITURE").performClick()
        waitTag("shopError")
        TestShopCalls.listFailure = null
        compose.onNodeWithText("다시 시도").performClick()
        waitTag("shopGrid")
        compose.onNodeWithText("나무 책상").assertExists()
    }
}
