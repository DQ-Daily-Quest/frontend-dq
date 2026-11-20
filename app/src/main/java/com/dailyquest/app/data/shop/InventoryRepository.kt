package com.dailyquest.app.data.shop

import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.apiCall

/** 내 보유 아이템과 코인 내역 조회(모두 GET). */
class InventoryRepository(private val api: DqApi) {

    suspend fun listMine(type: ItemType? = null, category: ItemCategory? = null): ApiResult<List<InventoryItem>> = apiCall {
        api.listMyItems(type?.name, category?.name).map { it.toInventoryItem() }
    }

    suspend fun listCoinTransactions(page: Int = 0, size: Int = ShopRepository.DEFAULT_PAGE_SIZE): ApiResult<CoinTransactionPage> = apiCall {
        val r = api.listCoinTransactions(page, size)
        CoinTransactionPage(r.content.map { it.toTransaction() }, r.page, r.totalPages)
    }
}
