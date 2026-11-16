package com.dailyquest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dailyquest.app.data.customization.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShopFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    @Test fun purchasePreviewSaveCancelAndSharedBalance() {
        click("쇼핑 (아바타 · 우리 집)")
        click("민트 상의")
        click("장착 저장")
        compose.onNodeWithText("보유하지 않은 아이템은 저장할 수 없습니다.").assertExists()
        click("120 코인으로 구매하기")
        compose.onNodeWithText("200").assertExists()
        compose.runOnIdle {
            assertTrue("top_premium" in CustomizationData.store.state.owned)
            assertEquals("top_default", CustomizationData.store.state.equipped[AvatarSlot.TOP])
        }
        click("취소")
        compose.runOnIdle {
            assertEquals(200, CustomizationData.store.state.coins)
            assertTrue("top_premium" in CustomizationData.store.state.owned)
        }
        click("민트 상의")
        click("장착 미리보기")
        compose.onNodeWithText("미리보기에 적용했습니다. 장착 저장을 눌러 확정해 주세요.").assertExists()
        compose.onNodeWithText("저장된 장착: 기본 상의").assertExists()
        click("장착 저장")
        compose.onNodeWithText("장착이 완료되었습니다.").assertExists()
        compose.runOnIdle { assertEquals("top_premium", CustomizationData.store.state.equipped[AvatarSlot.TOP]) }
        click("기본 상의")
        click("취소")
        compose.onNodeWithText("상의: 민트 상의").assertExists()
        compose.onNodeWithText("마지막으로 저장한 장착 상태로 복원했습니다. 구매 내역과 코인은 유지됩니다.").assertExists()
        compose.runOnIdle { assertEquals("top_premium", CustomizationData.store.state.equipped[AvatarSlot.TOP]) }
        click("남 전용 상의")
        click("240 코인으로 구매하기")
        compose.onNodeWithText("코인이 부족합니다.").assertExists()
        compose.onNodeWithText("홈").performClick()
        compose.onNodeWithText("200").assertExists()
        compose.onNodeWithText("쇼핑").performClick()
        compose.onNodeWithText("200").assertExists()
        compose.onNodeWithText("상의: 민트 상의").assertExists()
        compose.onNodeWithText("저장된 장착: 민트 상의").assertExists()
        compose.runOnIdle {
            assertEquals("top_premium", CustomizationData.store.state.equipped[AvatarSlot.TOP])
            assertEquals(200, CustomizationData.store.state.coins)
        }
        click("보유 아이템")
        compose.onNode(hasText("민트 상의") and hasClickAction()).assertExists()
        compose.onNode(hasText("남 전용 상의") and hasClickAction()).assertDoesNotExist()
    }
}
