package com.dailyquest.app.ui.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.room.RoomErrorMessages
import com.dailyquest.app.data.room.RoomPage
import com.dailyquest.app.data.room.RoomRepository
import com.dailyquest.app.data.room.RoomSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 공개방 목록(검색·정렬·서버 페이지네이션). Activity 범위로 하나만 만들어 홈 탭 전환에도 목록을 유지한다.
 * 조회(GET)만 수행하며 로그인 사용자가 바뀌면 이전 사용자의 목록과 진행 중인 요청을 모두 버린다.
 */
class PublicRoomsViewModel(
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
    private val debounceMs: Long = SEARCH_DEBOUNCE_MS,
    private val pageSize: Int = RoomRepository.DEFAULT_PAGE_SIZE,
) : ViewModel() {

    private val _ui = MutableStateFlow(PublicRoomsUiState())
    val ui: StateFlow<PublicRoomsUiState> = _ui.asStateFlow()

    private var searchJob: Job? = null
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null

    /** 조건이 바뀌거나 사용자가 바뀔 때 증가한다. 이전 세대의 응답은 버린다. */
    private var generation = 0
    private var appliedKeyword = ""

    init {
        viewModelScope.launch {
            // Loading·BootstrapFailed는 사용자 변경이 아니므로 무시한다.
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    reset()
                    if (userId != null) loadFirstPage(clearList = true)
                }
        }
    }

    fun setKeyword(text: String) {
        if (text == _ui.value.keyword) return
        _ui.update { it.copy(keyword = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            if (text.trim() != appliedKeyword) loadFirstPage(clearList = true)
        }
    }

    fun setSort(sort: RoomSort) {
        if (sort == _ui.value.sort) return
        _ui.update { it.copy(sort = sort) }
        searchJob?.cancel()
        loadFirstPage(clearList = true)
    }

    /** 첫 페이지 조회 실패 시 재시도. */
    fun retry() {
        searchJob?.cancel()
        loadFirstPage(clearList = true)
    }

    /** 기존 목록을 유지한 채 첫 페이지부터 다시 불러온다. */
    fun refresh() {
        searchJob?.cancel()
        loadFirstPage(clearList = false)
    }

    /** 다음 서버 페이지를 추가한다. 진행 중이거나 마지막 페이지면 무시한다. */
    fun loadMore() {
        val s = _ui.value
        if (!s.hasNext || s.initialLoading || s.refreshing || s.loadingMore || nextPageJob?.isActive == true) return
        val gen = generation
        val target = s.page + 1
        val keyword = appliedKeyword
        _ui.update { it.copy(loadingMore = true, loadMoreError = null) }
        nextPageJob = viewModelScope.launch {
            val r = repo.listPublicRooms(keyword.ifEmpty { null }, s.sort, target, pageSize)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> _ui.update {
                    val known = it.rooms.mapTo(HashSet()) { room -> room.roomId }
                    it.copy(
                        rooms = it.rooms + r.value.rooms.filter { room -> known.add(room.roomId) },
                        page = r.value.page, totalPages = r.value.totalPages, totalElements = r.value.totalElements,
                        loadingMore = false,
                    )
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loadingMore = false, loadMoreError = RoomErrorMessages.forApi(r.error))
                }
            }
        }
    }

    private fun loadFirstPage(clearList: Boolean) {
        generation++
        val gen = generation
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        val s = _ui.value
        appliedKeyword = s.keyword.trim()
        _ui.update {
            if (clearList) {
                it.copy(rooms = emptyList(), page = 0, totalPages = 0, totalElements = 0, loaded = false,
                    initialLoading = true, refreshing = false, loadingMore = false, error = null, loadMoreError = null)
            } else {
                it.copy(refreshing = true, loadingMore = false, error = null, loadMoreError = null)
            }
        }
        val keyword = appliedKeyword
        firstPageJob = viewModelScope.launch {
            val r = repo.listPublicRooms(keyword.ifEmpty { null }, s.sort, 0, pageSize)
            if (gen != generation) return@launch
            _ui.update { applyFirstPage(it, r) }
        }
    }

    private fun applyFirstPage(state: PublicRoomsUiState, r: ApiResult<RoomPage>): PublicRoomsUiState = when (r) {
        is ApiResult.Success -> state.copy(
            rooms = r.value.rooms.distinctBy { it.roomId },
            page = r.value.page, totalPages = r.value.totalPages, totalElements = r.value.totalElements,
            initialLoading = false, refreshing = false, loaded = true, error = null,
        )
        // 갱신 실패 시에는 기존 목록을 그대로 두고 메시지만 표시한다.
        is ApiResult.Failure -> state.copy(
            initialLoading = false, refreshing = false, error = RoomErrorMessages.forApi(r.error),
        )
    }

    private fun reset() {
        generation++
        searchJob?.cancel(); firstPageJob?.cancel(); nextPageJob?.cancel()
        appliedKeyword = ""
        _ui.value = PublicRoomsUiState()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PublicRoomsViewModel(container.roomRepository, container.session.state) as T
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

val LocalPublicRooms = staticCompositionLocalOf<PublicRoomsViewModel> { error("PublicRoomsViewModel not provided") }
