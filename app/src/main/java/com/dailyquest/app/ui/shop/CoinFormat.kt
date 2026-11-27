package com.dailyquest.app.ui.shop

import java.util.Locale

/** 코인 수를 천 단위 쉼표로 쓴다(98890 → "98,890"). 기기 언어와 관계없이 쉼표를 쓴다(QA 디자인 5-2단계). */
fun coinText(n: Int): String = String.format(Locale.US, "%,d", n)

/** 구매 확인 창의 금액: 보유 코인, 사용 코인(음수로 표시), 구매 후 잔액. */
data class PurchaseSummary(val balance: String, val cost: String, val after: String)

fun purchaseSummary(coins: Int, price: Int): PurchaseSummary =
    PurchaseSummary(balance = coinText(coins), cost = "-" + coinText(price), after = coinText(coins - price))
