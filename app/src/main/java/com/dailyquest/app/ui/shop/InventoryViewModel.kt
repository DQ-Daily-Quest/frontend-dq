package com.dailyquest.app.ui.shop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.shop.InventoryItem
import com.dailyquest.app.data.shop.InventoryRepository
import com.dailyquest.app.data.shop.ItemCategory
import com.dailyquest.app.data.shop.ItemType
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

data class InventoryUiState(
    val userId: Long? = null,
    val type: ItemType = ItemType.AVATAR,
    val category: ItemCategory? = null,
    val items: List<InventoryItem> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/** 내 보유 아이템(GET /users/me/items, 페이지 없음). 사용자 변경·종류 변경 시 이전 응답은 무시한다. */
class InventoryViewModel(
    private val repo: InventoryRepository,
    session: StateFlow<SessionState>,
) : ViewModel() {
    private val _ui = MutableStateFlow(InventoryUiState())
    val ui: StateFlow<InventoryUiState> = _ui.asStateFlow()
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
                    _ui.value = InventoryUiState(userId = userId)
                    if (userId != null) load(clear = true)
                }
        }
    }

    fun selectType(type: ItemType) {
        if (_ui.value.type == type) return
        _ui.update { it.copy(type = type, category = null) }
        load(clear = true)
    }

    fun selectCategory(category: ItemCategory?) {
        if (_ui.value.category == category) return
        _ui.update { it.copy(category = category) }
        load(clear = true)
    }

    fun retry() = load(clear = true)

    /** 화면 목록을 유지한 채 다시 조회한다. */
    fun refresh() = load(clear = false)

    private fun load(clear: Boolean) {
        val s = _ui.value
        if (s.userId == null) return
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { if (clear) it.copy(items = emptyList(), loading = true, loaded = false, error = null) else it.copy(error = null) }
        job = viewModelScope.launch {
            val r = repo.listMine(s.type, s.category)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(items = r.value, loading = false, loaded = true, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, error = if (clear || !it.loaded) ShopErrorMessages.forApi(r.error) else null)
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InventoryViewModel(container.inventoryRepository, container.session.state) as T
    }
}

val LocalInventory = staticCompositionLocalOf<InventoryViewModel> { error("InventoryViewModel not provided") }
