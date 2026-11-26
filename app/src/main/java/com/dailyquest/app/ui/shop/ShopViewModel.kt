package com.dailyquest.app.ui.shop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.shop.ItemCategory
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopErrorMessages
import com.dailyquest.app.data.shop.ShopItem
import com.dailyquest.app.data.shop.ShopRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    val userId: Long? = null,
    val type: ItemType = ItemType.AVATAR,
    /** null이면 해당 종류의 전체 카테고리. */
    val category: ItemCategory? = null,
    val items: List<ShopItem> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    /** 서버 응답으로 소유가 확인된 상품. 목록 재조회가 끝나기 전에도 보유 표시를 유지한다. */
    val ownedIds: Set<Long> = emptySet(),
    /** 구매 결과를 알 수 없어 소유 여부를 먼저 확인해야 하는 상품. 이 상품은 자동 재구매하지 않는다. */
    val unconfirmedIds: Set<Long> = emptySet(),
    val purchasingId: Long? = null,
    /** 상세를 연 상품. 목록 값으로 먼저 보여 주고 GET /shop/items/{id} 응답으로 갱신한다. */
    val detail: ShopItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    /** 1회성 안내(성공·오류). 화면이 표시한 뒤 [ShopViewModel.consumeMessage]로 비운다. */
    val message: String? = null,
    /** [message]가 실패 안내인지(빨강) 성공 안내인지(초록). QA 디자인 5단계. */
    val messageIsError: Boolean = false,
) {
    fun isOwned(item: ShopItem) = item.owned || item.itemId in ownedIds
}

/**
 * 상점 카탈로그(GET /shop/items)와 구매(POST)를 다룬다. 보유 여부와 코인은 항상 서버 값만 쓰고 앱에서 계산하지 않는다.
 * 사용자가 바뀌면 목록·구매 상태를 모두 버리고, 늦게 도착한 응답은 generation으로 무시한다.
 */
class ShopViewModel(
    private val repo: ShopRepository,
    private val session: StateFlow<SessionState>,
    /** 서버가 알려 준 잔액(구매 응답)을 세션에 반영한다. */
    private val applyCoins: (userId: Long, coins: Int) -> Unit,
    /** GET /users/me 로 잔액을 다시 맞춘다. 실패해도 구매 결과에는 영향이 없다. */
    private val refreshProfile: suspend () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShopUiState())
    val ui: StateFlow<ShopUiState> = _ui.asStateFlow()

    /** 구매가 확정될 때마다 증가한다. 인벤토리 등 다른 화면이 다시 조회하는 신호다. */
    private val _purchaseVersion = MutableStateFlow(0)
    val purchaseVersion: StateFlow<Int> = _purchaseVersion.asStateFlow()

    private var listJob: Job? = null
    private var generation = 0

    /** 계정이 바뀔 때만 증가한다. 목록 재조회로 바뀌는 [generation]과 달리 구매·상세 응답이 이전 계정 것인지 가르는 데 쓴다. */
    private var accountGen = 0
    private var purchaseInFlight = false

    init {
        viewModelScope.launch {
            // 계정과 함께 성별도 본다. 서버는 내 성별의 아바타 아이템만 내려 주므로(backend v0.4),
            // 로그인 직후(성별 없음 → 아바타 0개) 캐릭터를 만들면 같은 계정이어도 목록을 다시 받아야 한다.
            var currentUser: Long? = null
            var first = true
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { s -> (s as? SessionState.Authenticated)?.profile.let { it?.userId to it?.gender } }
                .distinctUntilChanged()
                .collect { (userId, _) ->
                    if (first || userId != currentUser) {
                        // 계정이 바뀜: 목록·구매 상태를 모두 버린다.
                        first = false
                        currentUser = userId
                        accountGen++
                        generation++
                        listJob?.cancel()
                        detailJob?.cancel()
                        purchaseInFlight = false
                        _ui.value = ShopUiState(userId = userId)
                        if (userId != null) load(reset = true)
                    } else if (userId != null) {
                        // 같은 계정에서 성별만 바뀜(캐릭터 만들기): 보던 탭·카테고리는 그대로 두고 목록만 다시 받는다.
                        load(reset = true)
                    }
                }
        }
    }

    private fun coins(): Int? = (session.value as? SessionState.Authenticated)?.profile?.coins

    fun selectType(type: ItemType) {
        if (_ui.value.type == type) return
        _ui.update { it.copy(type = type, category = null) }
        load(reset = true)
    }

    fun selectCategory(category: ItemCategory?) {
        if (_ui.value.category == category) return
        _ui.update { it.copy(category = category) }
        load(reset = true)
    }

    fun retry() = load(reset = true)

    /** 가격순 목록의 다음 페이지. */
    fun loadMore() {
        val s = _ui.value
        if (!s.hasNext || s.loading || s.loadingMore) return
        load(reset = false)
    }

    /** 현재 필터의 첫 페이지부터 조용히 다시 조회한다(구매 후 등). 화면 목록은 응답이 올 때까지 유지한다. */
    fun refresh() = load(reset = true, silent = true)

    private fun load(reset: Boolean, silent: Boolean = false) {
        val s = _ui.value
        if (s.userId == null) return
        generation++
        val gen = generation
        listJob?.cancel()
        val nextPage = if (reset) 0 else s.page + 1
        _ui.update {
            when {
                reset && silent -> it.copy(error = null)
                reset -> it.copy(items = emptyList(), page = 0, hasNext = false, loading = true, loaded = false, error = null, loadMoreError = null)
                else -> it.copy(loadingMore = true, loadMoreError = null)
            }
        }
        listJob = viewModelScope.launch {
            val r = repo.listItems(s.type, s.category, nextPage)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        items = if (reset) r.value.items else (it.items + r.value.items).distinctBy { i -> i.itemId },
                        page = r.value.page, hasNext = r.value.hasNext, loading = false, loadingMore = false, loaded = true, error = null,
                        // 서버가 보유로 내려 준 상품은 오버레이에서 정리하지 않아도 되지만 해제되는 일은 없어 그대로 둔다.
                    )
                    is ApiResult.Failure -> if (reset && !silent) it.copy(loading = false, error = ShopErrorMessages.forApi(r.error))
                    else if (reset) it.copy(loading = false)
                    else it.copy(loadingMore = false, loadMoreError = ShopErrorMessages.forApi(r.error))
                }
            }
        }
    }

    private var detailJob: Job? = null

    /** 상품 상세 조회. 다른 상품을 열거나 닫으면 이전 응답은 무시한다. */
    fun openDetail(item: ShopItem) {
        detailJob?.cancel()
        val gen = accountGen
        _ui.update { it.copy(detail = item, detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            val r = repo.getItem(item.itemId)
            if (gen != accountGen) return@launch
            _ui.update { s ->
                if (s.detail?.itemId != item.itemId) s
                else when (r) {
                    is ApiResult.Success -> s.copy(detail = r.value, detailLoading = false)
                    is ApiResult.Failure -> s.copy(detailLoading = false, detailError = ShopErrorMessages.forApi(r.error))
                }
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _ui.update { it.copy(detail = null, detailLoading = false, detailError = null) }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    /**
     * 구매. 중복 탭은 동기 플래그로 막고, 이미 보유했거나 코인이 부족하면 요청 자체를 보내지 않는다.
     * 응답을 잃은 경우에는 재전송하지 않고 상품 상세·잔액을 조회해 실제 결과를 확인한다.
     */
    fun purchase(item: ShopItem) {
        val userId = _ui.value.userId ?: return
        if (purchaseInFlight) return
        val s = _ui.value
        if (s.isOwned(item)) { _ui.update { it.copy(message = "이미 보유한 아이템이에요.", messageIsError = true) }; return }
        val balance = coins()
        if (balance != null && balance < item.price) { _ui.update { it.copy(message = ShopErrorMessages.forApi(insufficient()), messageIsError = true) }; return }
        purchaseInFlight = true
        val gen = accountGen
        _ui.update { it.copy(purchasingId = item.itemId) }
        viewModelScope.launch {
            try {
                if (item.itemId in s.unconfirmedIds) {
                    // 이전 요청이 서버에서 처리됐을 수 있으므로 소유 여부를 먼저 확인한다.
                    when (val verdict = verifyOwnership(item.itemId)) {
                        true -> { finishOwned(gen, item, "이미 구매가 완료된 아이템이에요."); return@launch }
                        null -> { post(gen, "구매 결과를 확인하지 못했어요. 잠시 후 다시 시도해 주세요."); return@launch }
                        false -> _ui.update { it.copy(unconfirmedIds = it.unconfirmedIds - item.itemId) }
                    }
                }
                when (val r = repo.purchase(item.itemId)) {
                    is ApiResult.Success -> {
                        if (gen != accountGen) return@launch
                        applyCoins(userId, r.value.remainingCoins)
                        finishOwned(gen, item, "'${item.name}' 구매가 완료됐어요.", refreshBalance = true)
                    }
                    is ApiResult.Failure -> handleFailure(gen, item, r.error)
                }
            } finally {
                if (gen == accountGen) purchaseInFlight = false
                if (gen == accountGen) _ui.update { it.copy(purchasingId = null) }
            }
        }
    }

    private fun insufficient() = ApiException.Http(409, com.dailyquest.app.data.remote.ApiErrorCode.INSUFFICIENT_COINS, null, "")

    private fun post(gen: Int, msg: String) {
        if (gen == accountGen) _ui.update { it.copy(message = msg, messageIsError = true) }
    }

    private suspend fun handleFailure(gen: Int, item: ShopItem, e: ApiException) {
        if (gen != accountGen) return
        val http = e as? ApiException.Http
        when {
            // 이미 가진 아이템: 보유로 표시하되 이번 구매는 이뤄지지 않았으므로 실패(빨강) 안내.
            http?.code == com.dailyquest.app.data.remote.ApiErrorCode.ITEM_ALREADY_OWNED -> finishOwned(gen, item, ShopErrorMessages.forApi(e), refreshBalance = true, isError = true)
            http != null && http.status in 400..499 -> {
                post(gen, ShopErrorMessages.forApi(e))
                // 코인 부족 등은 앱이 가진 잔액이 낡았다는 뜻일 수 있어 서버 값으로 다시 맞춘다.
                if (http.code == com.dailyquest.app.data.remote.ApiErrorCode.INSUFFICIENT_COINS) runCatching { refreshProfile() }
            }
            else -> {
                // 네트워크 오류·타임아웃·5xx: 서버에서 처리됐는지 알 수 없다. 재전송하지 않고 실제 소유 여부를 조회한다.
                _ui.update { it.copy(unconfirmedIds = it.unconfirmedIds + item.itemId) }
                when (verifyOwnership(item.itemId)) {
                    true -> finishOwned(gen, item, "'${item.name}' 구매가 완료됐어요.", refreshBalance = true)
                    false -> {
                        _ui.update { it.copy(unconfirmedIds = it.unconfirmedIds - item.itemId) }
                        post(gen, ShopErrorMessages.PURCHASE_NOT_APPLIED)
                    }
                    null -> post(gen, ShopErrorMessages.PURCHASE_UNCONFIRMED)
                }
            }
        }
    }

    /** 서버의 상품 상세로 소유 여부를 확인한다. 조회 자체가 실패하면 null. */
    private suspend fun verifyOwnership(itemId: Long): Boolean? =
        (repo.getItem(itemId) as? ApiResult.Success)?.value?.owned

    private suspend fun finishOwned(gen: Int, item: ShopItem, msg: String, refreshBalance: Boolean = true, isError: Boolean = false) {
        if (gen != accountGen) return
        _ui.update { it.copy(ownedIds = it.ownedIds + item.itemId, unconfirmedIds = it.unconfirmedIds - item.itemId, message = msg, messageIsError = isError) }
        _purchaseVersion.update { it + 1 }
        // 후속 조회는 실패해도 구매 결과(성공)를 바꾸지 않는다.
        if (refreshBalance) runCatching { refreshProfile() }
        if (gen == accountGen) refresh()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShopViewModel(
            container.shopRepository, container.session.state,
            { uid, coins -> container.session.applyServerCoins(uid, coins) },
            { container.authRepository.refreshProfile() },
        ) as T
    }
}

val LocalShop = staticCompositionLocalOf<ShopViewModel> { error("ShopViewModel not provided") }
