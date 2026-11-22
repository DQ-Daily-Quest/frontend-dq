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
import org.junit.Assert.assertEquals
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
            TestAvatarCalls.reset()
        }
    }).around(compose)

    @After fun resetFakeServer() { TestRoomCalls.reset(); TestAvatarCalls.reset() }

    /** 성별이 정해진 계정으로 시작한다. [equipped]는 슬롯 이름(소문자) → 아이템 ID. */
    private fun startAsBoy(vararg equipped: Pair<String, Long>) {
        TestAvatarCalls.gender = "MALE"
        TestAvatarCalls.equipped.putAll(equipped)
    }

    private fun waitTag(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun openShop() {
        compose.onNode(hasText("쇼핑 (아바타 · 우리 집)") and hasClickAction()).performScrollTo().performClick()
        waitTag("shopGrid")
    }

    /**
     * 상점 목록은 앱이 켜질 때 이미 받아 둔다. 테스트에서 보유 아이템을 바꾼 뒤에는
     * "우리 집 → 아바타"로 탭을 오가 목록을 새로 받게 하고, [ownedId]가 보유로 보일 때까지 기다린다.
     */
    private fun reloadCatalog(ownedId: Long) {
        compose.onNodeWithTag("shopType_FURNITURE").performClick()
        waitTag("shopItem_4")
        compose.onNodeWithTag("shopType_AVATAR").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("shopItemState_$ownedId", useUnmergedTree = true).assertTextContains("보유 중") }.isSuccess
        }
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

    /** 9-8: 성별을 정하지 않은 계정은 보유 아이템도 착용할 수 없다(캐릭터가 없다). */
    @Test fun ownedItemCannotBeEquippedBeforeCharacterExists() {
        openShop()
        compose.onNodeWithTag("shopItem_1").performClick()   // 기본 머리(보유)

        compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용하기").assertIsNotEnabled()
    }

    /** 9-8: 보유한 아이템을 착용하면 그 슬롯만 저장하고 버튼이 "착용 중"이 된다. */
    @Test fun equippingOwnedItemSavesAndShowsEquipped() {
        startAsBoy("hair" to 1)
        TestShopCalls.owned += 2
        openShop()
        reloadCatalog(2)
        waitTag("myAvatarPlaceholder")
        compose.onNodeWithTag("shopItem_2").performClick()   // 민트 상의(보유, 안 입음)
        compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용하기").assertIsEnabled().performClick()

        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용 중") }.isSuccess }
        compose.onNodeWithTag("shopTryOnAction").assertIsNotEnabled()
        assertEquals(listOf(mapOf<String, Long?>("top" to 2L)), TestAvatarCalls.requests)
        // 저장된 옷은 더 이상 "입어보는 중"이 아니다
        assertTrue(!exists("myAvatarTryOnLabel"))
    }

    /** 9-8: 이미 입고 있는 모자 외 슬롯은 "착용 중"이고 벗을 수 없다. */
    @Test fun equippedNonHatShowsEquippedAndCannotBeRemoved() {
        startAsBoy("hair" to 1)
        openShop()
        waitTag("myAvatarPlaceholder")
        compose.onNodeWithTag("shopItem_1").performClick()   // 기본 머리(입고 있음)

        compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용 중").assertIsNotEnabled()
        assertTrue(TestAvatarCalls.requests.isEmpty())
    }

    /** 9-8: 입고 있는 모자는 벗을 수 있다(null로 저장). */
    @Test fun equippedHatCanBeTakenOff() {
        startAsBoy("hair" to 1, "hat" to 3)
        TestShopCalls.owned += 3
        openShop()
        reloadCatalog(3)
        waitTag("myAvatarPlaceholder")
        compose.onNodeWithTag("shopItem_3").performClick()   // 비싼 모자(입고 있음)
        compose.onNodeWithTag("shopTryOnAction").assertTextContains("모자 벗기").performClick()

        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용하기") }.isSuccess }
        assertEquals(listOf(mapOf<String, Long?>("hat" to null)), TestAvatarCalls.requests)
    }

    /** 9-8: 저장이 실패하면 입어 보던 것을 벗기고(원래 모습) 다시 착용할 수 있게 둔다. */
    @Test fun failedEquipRevertsTryOn() {
        startAsBoy("hair" to 1)
        TestShopCalls.owned += 2
        TestAvatarCalls.failure = java.io.IOException("lost")
        openShop()
        reloadCatalog(2)
        waitTag("myAvatarPlaceholder")
        compose.onNodeWithTag("shopItem_2").performClick()
        compose.onNodeWithTag("myAvatarTryOnLabel").assertExists()
        compose.onNodeWithTag("shopTryOnAction").performClick()

        compose.waitUntil(5_000) { !exists("myAvatarTryOnLabel") }
        compose.onNodeWithTag("shopTryOnAction").assertTextContains("착용하기").assertIsEnabled()
        assertEquals(1, TestAvatarCalls.requests.size)
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
