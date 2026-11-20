package com.dailyquest.app.data.shop

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall

/**
 * 상점 API 호출 계층. 전역 사용자 상태를 보관하지 않는다.
 * 구매(POST)는 응답을 받지 못해도 서버에서는 성공했을 수 있으므로 자동 재시도하지 않고, 결과 확인은 호출 쪽이 [getItem]으로 한다.
 */
class ShopRepository(private val api: DqApi) {

    suspend fun listItems(type: ItemType? = null, category: ItemCategory? = null, page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): ApiResult<ShopPage> = apiCall {
        val r = api.listShopItems(type?.name, category?.name, page, size)
        ShopPage(r.content.map { it.toShopItem() }, r.page, r.totalPages, r.totalElements)
    }

    suspend fun getItem(itemId: Long): ApiResult<ShopItem> = apiCall { api.getShopItem(itemId).toShopItem() }

    suspend fun purchase(itemId: Long): ApiResult<PurchaseResult> = apiCall {
        api.purchaseItem(itemId).let { PurchaseResult(it.itemId, it.remainingCoins) }
    }

    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}
