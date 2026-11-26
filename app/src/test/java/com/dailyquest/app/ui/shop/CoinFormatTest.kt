package com.dailyquest.app.ui.shop

import org.junit.Assert.assertEquals
import org.junit.Test

/** QA 디자인 5-2단계: 구매 확인 창의 금액 표시. */
class CoinFormatTest {
    @Test fun `천 단위에 쉼표를 넣는다`() {
        assertEquals("0", coinText(0))
        assertEquals("180", coinText(180))
        assertEquals("98,890", coinText(98_890))
        assertEquals("1,234,567", coinText(1_234_567))
    }

    @Test fun `보유 코인, 사용 코인(음수), 구매 후 잔액`() {
        assertEquals(PurchaseSummary("98,890", "-180", "98,710"), purchaseSummary(98_890, 180))
        assertEquals(PurchaseSummary("120", "-120", "0"), purchaseSummary(120, 120))
    }
}
