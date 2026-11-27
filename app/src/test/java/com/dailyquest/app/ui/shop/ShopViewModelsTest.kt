package com.dailyquest.app.ui.shop

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.CoinTransactionDto
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.MyItemDto
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.remote.dto.PurchaseDto
import com.dailyquest.app.data.shop.InventoryRepository
import com.dailyquest.app.data.shop.ItemCategory
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

private fun http(status: Int, code: String) = HttpException(Response.error<Any>(status, """{"code":"$code","message":"m"}""".toResponseBody("application/json".toMediaType())))

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelsTest {

    /** 서버처럼 소유·잔액을 직접 관리하는 가짜 API. 앱 코드는 이 값을 응답으로만 받는다. */
    private class FakeApi(delegate: DqApi) : DqApi by delegate {
        var coins = 320
        val owned = mutableSetOf<Long>()
        val stock = listOf(
            ItemDto(1, "기본 머리", "AVATAR", "HAIR", 0, isDefault = true),
            ItemDto(2, "민트 상의", "AVATAR", "TOP", 120),
            ItemDto(3, "비싼 모자", "AVATAR", "HAT", 900),
            ItemDto(4, "나무 책상", "FURNITURE", "DESK", 80),
        )
        var purchaseBehavior: suspend (Long) -> PurchaseDto = { id -> realPurchase(id) }
        var listGate: (suspend (String?, String?) -> Unit)? = null
        var itemFailure: Throwable? = null
        val purchaseCalls = mutableListOf<Long>()
        val listCalls = mutableListOf<Triple<String?, String?, Int>>()
        var itemCalls = 0
        var myItemsCalls = 0

        fun realPurchase(id: Long): PurchaseDto {
            val s = stock.first { it.itemId == id }
            if (id in owned) throw http(409, "ITEM_ALREADY_OWNED")
            if (coins < s.price) throw http(409, "INSUFFICIENT_COINS")
            coins -= s.price; owned += id
            return PurchaseDto(id, coins)
        }

        override suspend fun listShopItems(type: String?, category: String?, page: Int, size: Int): PageDto<ItemDto> {
            listCalls.add(Triple(type, category, page))
            listGate?.invoke(type, category)
            val all = stock.filter { (type == null || it.type == type) && (category == null || it.category == category) }
            return PageDto(all.map { it.copy(owned = it.itemId in owned) }, page, size, all.size.toLong(), 1)
        }
        override suspend fun getShopItem(itemId: Long): ItemDto {
            itemCalls++; itemFailure?.let { throw it }
            return stock.first { it.itemId == itemId }.let { it.copy(owned = it.itemId in owned) }
        }
        override suspend fun purchaseItem(itemId: Long): PurchaseDto { purchaseCalls.add(itemId); return purchaseBehavior(itemId) }
        override suspend fun listMyItems(type: String?, category: String?): List<MyItemDto> {
            myItemsCalls++
            return stock.filter { it.itemId in owned && (type == null || it.type == type) }.map { MyItemDto(it.itemId, it.name, it.type, it.category, it.price) }
        }
        override suspend fun listCoinTransactions(page: Int, size: Int): PageDto<CoinTransactionDto> =
            PageDto(listOf(CoinTransactionDto(1, -120, 200, "PURCHASE", 2, "2025-04-17T00:00:00Z")), page, size, 1, 1)
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private fun profile(id: Long, coins: Int) = SessionState.Authenticated(UserProfile(id, null, "n", coins, 0, 0, 0, 0))

    private lateinit var api: FakeApi
    private lateinit var session: MutableStateFlow<SessionState>
    private var profileRefreshes = 0
    private var profileRefreshFails = false

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        api = FakeApi(unused); session = MutableStateFlow(profile(1, 320)); profileRefreshes = 0; profileRefreshFails = false
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun shopVm() = ShopViewModel(
        ShopRepository(api), session,
        applyCoins = { uid, c -> (session.value as? SessionState.Authenticated)?.takeIf { it.profile.userId == uid }?.let { session.value = SessionState.Authenticated(it.profile.copy(coins = c)) } },
        refreshProfile = {
            profileRefreshes++
            if (profileRefreshFails) throw IOException("profile")
            (session.value as? SessionState.Authenticated)?.let { session.value = SessionState.Authenticated(it.profile.copy(coins = api.coins)) }
        },
    )
    private fun coins() = (session.value as SessionState.Authenticated).profile.coins

    // ---------- 카탈로그 ----------

    @Test fun loadsAvatarCatalogOnStartAndSwitchesTypeAndCategory() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        assertEquals(listOf("기본 머리", "민트 상의", "비싼 모자"), vm.ui.value.items.map { it.name })
        vm.selectType(ItemType.FURNITURE); advanceUntilIdle()
        assertEquals(listOf("나무 책상"), vm.ui.value.items.map { it.name })
        vm.selectType(ItemType.AVATAR); vm.selectCategory(ItemCategory.TOP); advanceUntilIdle()
        assertEquals(listOf("민트 상의"), vm.ui.value.items.map { it.name })
        assertEquals(Triple<String?, String?, Int>("AVATAR", "TOP", 0), api.listCalls.last())
    }

    @Test fun staleCategoryResponseIsIgnored() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        api.listGate = { _, c -> if (c == "TOP") gate.await() }
        vm.selectCategory(ItemCategory.TOP); runCurrent()
        api.listGate = null
        vm.selectCategory(ItemCategory.HAT); advanceUntilIdle()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("비싼 모자"), vm.ui.value.items.map { it.name })
    }

    @Test fun errorThenRetryRecovers() = runTest {
        api.listGate = { _, _ -> throw IOException("x") }
        val vm = shopVm(); advanceUntilIdle()
        assertNotNull(vm.ui.value.error); assertTrue(vm.ui.value.items.isEmpty())
        api.listGate = null; vm.retry(); advanceUntilIdle()
        assertNull(vm.ui.value.error); assertEquals(3, vm.ui.value.items.size)
    }

    @Test fun detailFetchesFromServerAndIgnoresClosedResponse() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        val item = vm.ui.value.items[1]
        vm.openDetail(item); advanceUntilIdle()
        assertEquals(2L, vm.ui.value.detail!!.itemId); assertEquals(1, api.itemCalls)
        vm.closeDetail(); assertNull(vm.ui.value.detail)
        api.itemFailure = IOException("x"); vm.openDetail(item); advanceUntilIdle()
        assertNotNull(vm.ui.value.detailError); assertEquals(2L, vm.ui.value.detail!!.itemId)
    }

    // ---------- 구매 ----------

    @Test fun purchaseSuccessUsesServerBalanceAndMarksOwned() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        val item = vm.ui.value.items[1]
        vm.purchase(item); advanceUntilIdle()
        assertEquals(listOf(2L), api.purchaseCalls)
        assertEquals(200, coins())
        assertTrue(vm.ui.value.isOwned(vm.ui.value.items.first { it.itemId == 2L }))
        assertEquals(1, vm.purchaseVersion.value); assertEquals(1, profileRefreshes)
        assertEquals("'민트 상의' 구매가 완료됐어요.", vm.ui.value.message)
        assertFalse("구매 완료는 초록 알림", vm.ui.value.messageIsError)
        assertNull(vm.ui.value.purchasingId)
    }

    @Test fun purchaseMessageIsShownOnceAndClearedAfterConsume() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals("'민트 상의' 구매가 완료됐어요.", vm.ui.value.message)
        vm.consumeMessage()
        assertNull(vm.ui.value.message)
        // 탭을 벗어났다 돌아오거나(재구독) 목록을 새로 불러와도 지난 메시지는 다시 생기지 않는다.
        vm.refresh(); advanceUntilIdle()
        assertNull(vm.ui.value.message)
        assertEquals(1, vm.purchaseVersion.value)
    }

    @Test fun purchaseStillSucceedsWhenFollowUpRefreshFails() = runTest {
        profileRefreshFails = true
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(200, coins()) // 구매 응답의 서버 잔액은 반영된다
        assertTrue(2L in vm.ui.value.ownedIds); assertEquals(1, vm.purchaseVersion.value)
        assertTrue(vm.ui.value.message!!.contains("완료"))
    }

    @Test fun duplicateTapsSendSingleRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.purchaseBehavior = { id -> gate.await(); api.realPurchase(id) }
        val vm = shopVm(); advanceUntilIdle()
        val item = vm.ui.value.items[1]
        vm.purchase(item); vm.purchase(item); runCurrent(); vm.purchase(item)
        assertEquals(1, api.purchaseCalls.size)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(1, api.purchaseCalls.size); assertEquals(200, api.coins)
    }

    @Test fun insufficientCoinsNeverCallsServer() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[2]); advanceUntilIdle()
        assertTrue(api.purchaseCalls.isEmpty()); assertEquals("코인이 부족해요.", vm.ui.value.message)
        assertTrue("코인 부족은 빨간 알림", vm.ui.value.messageIsError)
        assertEquals(320, coins())
    }

    @Test fun staleBalanceInsufficientFromServerShowsMessageAndRefreshesProfile() = runTest {
        api.coins = 10 // 앱이 아는 잔액(320)보다 서버가 적다
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(1, api.purchaseCalls.size); assertEquals("코인이 부족해요.", vm.ui.value.message)
        assertTrue(vm.ui.value.messageIsError)
        assertEquals(10, coins()); assertTrue(vm.ui.value.ownedIds.isEmpty())
    }

    @Test fun alreadyOwnedFromServerMarksOwnedWithoutDeduction() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        api.owned += 2 // 다른 기기에서 이미 구매
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals("이미 보유한 아이템이에요.", vm.ui.value.message)
        assertTrue("이번 구매는 이뤄지지 않았으므로 빨간 알림", vm.ui.value.messageIsError)
        assertTrue(2L in vm.ui.value.ownedIds); assertEquals(320, api.coins)
    }

    @Test fun ownedItemIsNotPurchasedAgain() = runTest {
        api.owned += 2
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertTrue(api.purchaseCalls.isEmpty())
    }

    @Test fun lostResponseAfterServerAppliedIsVerifiedNotRetried() = runTest {
        api.purchaseBehavior = { id -> api.realPurchase(id); throw IOException("response lost") }
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(listOf(2L), api.purchaseCalls)
        assertTrue(2L in vm.ui.value.ownedIds); assertEquals(200, coins()) // 프로필 재조회로 실제 잔액 확인
        assertTrue(vm.ui.value.unconfirmedIds.isEmpty())
        assertEquals(1, api.itemCalls)
    }

    @Test fun lostRequestNotAppliedIsReportedAndRepurchaseIsPossible() = runTest {
        api.purchaseBehavior = { throw IOException("never reached") }
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals("구매가 처리되지 않았어요. 다시 시도해 주세요.", vm.ui.value.message)
        assertTrue(vm.ui.value.messageIsError)
        assertTrue(vm.ui.value.ownedIds.isEmpty()); assertEquals(320, coins())
        api.purchaseBehavior = { id -> api.realPurchase(id) }
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(200, coins())
    }

    @Test fun unverifiableLossKeepsUnconfirmedAndChecksOwnershipBeforeRepurchase() = runTest {
        api.purchaseBehavior = { id -> api.realPurchase(id); throw IOException("response lost") }
        api.itemFailure = IOException("also down")
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertTrue(2L in vm.ui.value.unconfirmedIds); assertTrue(vm.ui.value.ownedIds.isEmpty())
        assertTrue(vm.ui.value.message!!.contains("확인하지 못했어요"))
        // 확인이 가능해지면 재구매 대신 소유 여부만 확인하고 POST는 다시 보내지 않는다.
        api.itemFailure = null
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(listOf(2L), api.purchaseCalls)
        assertTrue(2L in vm.ui.value.ownedIds); assertEquals(200, api.coins)
    }

    @Test fun serverErrorIsTreatedAsUnknownOutcome() = runTest {
        api.purchaseBehavior = { throw http(500, "INTERNAL") }
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertEquals(1, api.itemCalls); assertEquals(1, api.purchaseCalls.size)
    }

    // ---------- 계정 전환 ----------

    @Test fun accountSwitchClearsCatalogOwnershipAndIgnoresLateResponse() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); advanceUntilIdle()
        assertTrue(vm.ui.value.ownedIds.isNotEmpty())
        val gate = CompletableDeferred<Unit>()
        api.listGate = { _, _ -> gate.await() }
        api.owned.clear(); api.coins = 50
        session.value = profile(2, 50); runCurrent()
        assertTrue(vm.ui.value.items.isEmpty()); assertTrue(vm.ui.value.ownedIds.isEmpty()); assertEquals(2L, vm.ui.value.userId)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(vm.ui.value.items.any { it.owned })
    }

    @Test fun logoutClearsState() = runTest {
        val vm = shopVm(); advanceUntilIdle()
        session.value = SessionState.Unauthenticated(); advanceUntilIdle()
        assertNull(vm.ui.value.userId); assertTrue(vm.ui.value.items.isEmpty())
    }

    @Test fun lateCoinResponseForPreviousUserDoesNotChangeNewUserBalance() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.purchaseBehavior = { id -> gate.await(); api.realPurchase(id) }
        val vm = shopVm(); advanceUntilIdle()
        vm.purchase(vm.ui.value.items[1]); runCurrent()
        session.value = profile(2, 500); advanceUntilIdle()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(500, coins()); assertTrue(vm.ui.value.ownedIds.isEmpty())
    }

    // ---------- 인벤토리·내역 ----------

    @Test fun inventoryLoadsOwnedByTypeAndRefreshesAfterPurchase() = runTest {
        api.owned += 1
        val inv = InventoryViewModel(InventoryRepository(api), session); advanceUntilIdle()
        assertEquals(listOf("기본 머리"), inv.ui.value.items.map { it.name })
        api.owned += 2; inv.refresh(); advanceUntilIdle()
        assertEquals(2, inv.ui.value.items.size)
        inv.selectType(ItemType.FURNITURE); advanceUntilIdle()
        assertTrue(inv.ui.value.items.isEmpty()); assertTrue(inv.ui.value.loaded)
    }

    @Test fun inventoryClearedOnAccountSwitch() = runTest {
        api.owned += 1
        val inv = InventoryViewModel(InventoryRepository(api), session); advanceUntilIdle()
        api.owned.clear(); session.value = profile(2, 0); advanceUntilIdle()
        assertTrue(inv.ui.value.items.isEmpty()); assertEquals(2L, inv.ui.value.userId)
    }

    @Test fun coinHistoryLoadsAndClearsOnAccountSwitch() = runTest {
        val vm = CoinHistoryViewModel(InventoryRepository(api), session); advanceUntilIdle()
        assertTrue(vm.ui.value.items.isEmpty()) // 화면 진입 전에는 조회하지 않는다
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, vm.ui.value.items.size); assertEquals(-120, vm.ui.value.items[0].amount)
        session.value = profile(2, 0); advanceUntilIdle()
        assertTrue(vm.ui.value.items.isEmpty())
    }
}
