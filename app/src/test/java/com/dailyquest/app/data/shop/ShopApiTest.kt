package com.dailyquest.app.data.shop

import com.dailyquest.app.data.auth.InMemoryTokenStore
import com.dailyquest.app.data.auth.LogoutReason
import com.dailyquest.app.data.auth.SessionManager
import com.dailyquest.app.data.auth.SessionTokens
import com.dailyquest.app.data.auth.TokenPair
import com.dailyquest.app.data.remote.ApiConfig
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 상점·인벤토리·코인 내역 DTO 파싱, 요청 형태, 구매 재전송 금지, 오류 메시지를 MockWebServer로 검증한다. */
class ShopApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DqApi
    private lateinit var shop: ShopRepository
    private lateinit var inventory: InventoryRepository

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val tokens = SessionTokens(InMemoryTokenStore(TokenPair("A", "R")))
        val session = SessionManager(tokens, emptyList())
        api = NetworkModule.createApi(tokens, { session.expire(LogoutReason.EXPIRED) }, server.url("/api/v1/").newBuilder().host("127.0.0.1").build().toString())
        shop = ShopRepository(api); inventory = InventoryRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val page = """{"content":[
        {"itemId":1,"name":"기본 머리","type":"AVATAR","category":"HAIR","price":0,"imageUrl":"/files/items/hair_default.png","modelUrl":"/files/items/hair.glb","isDefault":true,"owned":true},
        {"itemId":2,"name":"민트 상의","type":"AVATAR","category":"TOP","price":120,"imageUrl":null,"modelUrl":null,"default":false,"owned":false},
        {"itemId":3,"name":"신상","type":"HOLOGRAM","category":"WINGS","price":10,"imageUrl":"https://cdn.example/a.png","isDefault":false,"owned":false,"extra":1}
      ],"page":0,"size":20,"totalElements":41,"totalPages":3}"""

    @Test fun listParsesItemsPagingAndResolvesImageUrls() = runBlocking {
        server.enqueue(json(page))
        val r = shop.listItems(ItemType.AVATAR, ItemCategory.TOP, page = 0) as ApiResult.Success
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.startsWith("/api/v1/shop/items?"))
        assertTrue(req.path!!.contains("type=AVATAR") && req.path!!.contains("category=TOP") && req.path!!.contains("page=0"))
        val items = r.value.items
        assertTrue(r.value.hasNext); assertEquals(41L, r.value.totalElements)
        assertTrue(items[0].isDefault && items[0].owned)
        assertTrue(items[0].imageUrl!!.endsWith("/files/items/hair_default.png") && items[0].imageUrl!!.startsWith("https://"))
        assertEquals("/files/items/hair.glb", items[0].modelUrl)
        assertNull(items[1].imageUrl); assertFalse(items[1].isDefault) // "default" 표기도 읽는다
        assertEquals("https://cdn.example/a.png", items[2].imageUrl)
        assertEquals(ItemType.UNKNOWN, items[2].type); assertEquals(ItemCategory.UNKNOWN, items[2].category)
    }

    @Test fun listWithoutFiltersOmitsTypeAndCategory() = runBlocking {
        server.enqueue(json("""{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}"""))
        val r = shop.listItems() as ApiResult.Success
        assertTrue(r.value.items.isEmpty()); assertFalse(r.value.hasNext)
        val path = server.takeRequest().path!!
        assertFalse(path.contains("type=")); assertFalse(path.contains("category="))
    }

    @Test fun detailGetsItemById() = runBlocking {
        server.enqueue(json("""{"itemId":2,"name":"민트 상의","type":"AVATAR","category":"TOP","price":120,"owned":true}"""))
        val r = shop.getItem(2) as ApiResult.Success
        assertTrue(r.value.owned); assertNull(r.value.imageUrl)
        assertTrue(server.takeRequest().path!!.endsWith("/shop/items/2"))
    }

    @Test fun purchasePostsOnceAndReturnsServerBalance() = runBlocking {
        server.enqueue(json("""{"itemId":2,"remainingCoins":200}""", 200))
        val r = shop.purchase(2) as ApiResult.Success
        assertEquals(PurchaseResult(2, 200), r.value)
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertTrue(req.path!!.endsWith("/shop/items/2/purchase"))
    }

    @Test fun purchaseIsNotResentAfterConnectionDrop() = runBlocking {
        server.enqueue(json("[]")); inventory.listMine(); server.takeRequest() // 연결 워밍업
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json("""{"itemId":2,"remainingCoins":200}"""))
        val r = shop.purchase(2)
        assertTrue(r is ApiResult.Failure && r.error is ApiException.Network)
        assertEquals(2, server.requestCount)
    }

    @Test fun purchaseErrorsMapToKoreanMessages() = runBlocking {
        fun err(status: Int, code: String) = json("""{"code":"$code","message":"x"}""", status)
        server.enqueue(err(409, "INSUFFICIENT_COINS")); server.enqueue(err(409, "ITEM_ALREADY_OWNED")); server.enqueue(err(404, "ITEM_NOT_FOUND"))
        val msgs = (1..3).map { ((shop.purchase(2)) as ApiResult.Failure).error }.map { ShopErrorMessages.forApi(it) }
        assertEquals(listOf("코인이 부족해요.", "이미 보유한 아이템이에요.", "판매가 종료되었거나 존재하지 않는 아이템이에요."), msgs)
        assertEquals(ApiErrorCode.ITEM_ALREADY_OWNED, "ITEM_ALREADY_OWNED")
    }

    @Test fun inventoryIsPlainListWithFiltersAndSafeEnums() = runBlocking {
        server.enqueue(json("""[
          {"itemId":1,"name":"기본 머리","type":"AVATAR","category":"HAIR","price":0,"imageUrl":"/files/a.png","modelUrl":"/files/a.glb","isDefault":true,"owned":true,"acquiredAt":"2025-01-01T00:00:00Z","acquiredBy":"DEFAULT","equipped":true},
          {"itemId":2,"name":"민트 상의","type":"AVATAR","category":"TOP","price":120,"isDefault":false,"owned":true,"acquiredBy":"GIFT","equipped":false}]"""))
        val r = inventory.listMine(ItemType.AVATAR, null) as ApiResult.Success
        val path = server.takeRequest().path!!
        assertTrue(path.contains("/users/me/items") && path.contains("type=AVATAR") && !path.contains("category="))
        assertEquals(AcquiredBy.DEFAULT, r.value[0].acquiredBy); assertTrue(r.value[0].equipped)
        assertEquals(AcquiredBy.UNKNOWN, r.value[1].acquiredBy)
        assertEquals("/files/a.glb", r.value[0].modelUrl)
    }

    @Test fun coinTransactionsParseReasonsAndPaging() = runBlocking {
        server.enqueue(json("""{"content":[
          {"transactionId":3,"amount":-120,"balanceAfter":200,"reason":"PURCHASE","refId":2,"createdAt":"2025-04-17T00:00:00Z"},
          {"transactionId":2,"amount":10,"balanceAfter":320,"reason":"STREAK_BONUS","refId":null,"createdAt":"2025-04-16T00:00:00Z"},
          {"transactionId":1,"amount":50,"balanceAfter":310,"reason":"MISSION_REWARD","createdAt":null},
          {"transactionId":0,"amount":1,"balanceAfter":1,"reason":"SOMETHING_NEW"}],"page":0,"size":20,"totalElements":4,"totalPages":2}"""))
        val r = inventory.listCoinTransactions(0) as ApiResult.Success
        assertTrue(server.takeRequest().path!!.contains("/users/me/coins/transactions"))
        assertEquals(listOf(CoinReason.PURCHASE, CoinReason.STREAK_BONUS, CoinReason.MISSION_REWARD, CoinReason.UNKNOWN), r.value.items.map { it.reason })
        assertEquals(-120, r.value.items[0].amount); assertEquals(2L, r.value.items[0].refId)
        assertTrue(r.value.hasNext)
    }

    @Test fun resolveFileUrlHandlesRelativeAbsoluteAndBlank() {
        assertTrue(ApiConfig.resolveFileUrl("/files/x.png")!!.endsWith("/files/x.png"))
        assertFalse(ApiConfig.resolveFileUrl("/files/x.png")!!.contains("/api/"))
        assertEquals("http://a/b.png", ApiConfig.resolveFileUrl("http://a/b.png"))
        assertNull(ApiConfig.resolveFileUrl(null)); assertNull(ApiConfig.resolveFileUrl("  ")); assertNull(ApiConfig.resolveFileUrl("javascript:x"))
    }

    @Test fun categoriesCoverBothTypes() {
        assertEquals(7, ItemCategory.of(ItemType.AVATAR).size)
        assertEquals(7, ItemCategory.of(ItemType.FURNITURE).size)
        assertEquals(ItemType.UNKNOWN, ItemType.parse(null))
    }
}
