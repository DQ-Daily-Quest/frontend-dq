package com.dailyquest.app.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.shop.CoinTransaction
import com.dailyquest.app.data.shop.InventoryRepository
import com.dailyquest.app.data.shop.ShopErrorMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoinHistoryUiState(
    val userId: Long? = null,
    val items: List<CoinTransaction> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
)

/** 코인 거래 내역(GET /users/me/coins/transactions, 최신순). 서버가 내려준 내역만 보여 주며 계정이 바뀌면 비운다. */
class CoinHistoryViewModel(
    private val repo: InventoryRepository,
    session: StateFlow<SessionState>,
) : ViewModel() {
    private val _ui = MutableStateFlow(CoinHistoryUiState())
    val ui: StateFlow<CoinHistoryUiState> = _ui.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _ui.value = CoinHistoryUiState(userId = userId)
                }
        }
    }

    /** 화면에 들어올 때마다 첫 페이지를 다시 조회한다. 기존 목록은 응답이 올 때까지 유지한다. */
    fun refresh() = load(reset = true)

    fun loadMore() {
        val s = _ui.value
        if (s.hasNext && !s.loading && !s.loadingMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val s = _ui.value
        if (s.userId == null) return
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { if (reset) it.copy(loading = !it.loaded, error = null, loadMoreError = null, loadingMore = false) else it.copy(loadingMore = true, loadMoreError = null) }
        val page = if (reset) 0 else s.page + 1
        job = viewModelScope.launch {
            val r = repo.listCoinTransactions(page)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        items = if (reset) r.value.items else (it.items + r.value.items).distinctBy { t -> t.transactionId },
                        page = r.value.page, hasNext = r.value.hasNext, loading = false, loadingMore = false, loaded = true, error = null,
                    )
                    is ApiResult.Failure -> if (reset) it.copy(loading = false, error = if (it.loaded) null else ShopErrorMessages.forApi(r.error))
                    else it.copy(loadingMore = false, loadMoreError = ShopErrorMessages.forApi(r.error))
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CoinHistoryViewModel(container.inventoryRepository, container.session.state) as T
    }
}
