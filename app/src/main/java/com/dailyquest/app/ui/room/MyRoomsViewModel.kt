package com.dailyquest.app.ui.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.room.RoomErrorMessages
import com.dailyquest.app.data.room.RoomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 우리만 탭의 내가 참여한 방(GET /rooms/mine). 조회 전용이며 로그인 사용자가 바뀌면 목록·요청을 모두 버린다.
 * 방 생성·참여·탈퇴·삭제 이후에는 [refresh]를 호출해 목록을 갱신한다.
 */
class MyRoomsViewModel(
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
) : ViewModel() {

    private val _ui = MutableStateFlow(MyRoomsUiState())
    val ui: StateFlow<MyRoomsUiState> = _ui.asStateFlow()

    private var job: Job? = null

    /** 사용자 변경·재조회마다 증가하며, 이전 세대의 늦은 응답은 버린다. */
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
                    _ui.value = MyRoomsUiState()
                    if (userId != null) load(clearList = true)
                }
        }
    }

    /** 첫 조회 실패 후 재시도. */
    fun retry() = load(clearList = true)

    /** 기존 목록을 유지한 채 다시 불러온다. 다른 기능에서 목록 갱신용으로 사용한다. */
    fun refresh() = load(clearList = false)

    fun nextPage() = _ui.update { if (it.hasNext) it.copy(page = it.currentPage + 1) else it }

    fun prevPage() = _ui.update { if (it.hasPrev) it.copy(page = it.currentPage - 1) else it }

    private fun load(clearList: Boolean) {
        generation++
        val gen = generation
        job?.cancel()
        _ui.update {
            if (clearList) MyRoomsUiState(page = 0, initialLoading = true)
            else it.copy(refreshing = true, error = null)
        }
        job = viewModelScope.launch {
            val r = repo.listMyRooms()
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> {
                        val rooms = r.value.distinctBy { room -> room.roomId }
                        // 목록 길이가 줄어 현재 페이지가 범위를 벗어나면 보정한다.
                        it.copy(rooms = rooms, loaded = true, initialLoading = false, refreshing = false, error = null)
                            .let { n -> n.copy(page = n.currentPage) }
                    }
                    is ApiResult.Failure -> it.copy(
                        initialLoading = false, refreshing = false, error = RoomErrorMessages.forApi(r.error),
                    )
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MyRoomsViewModel(container.roomRepository, container.session.state) as T
    }
}

val LocalMyRooms = staticCompositionLocalOf<MyRoomsViewModel> { error("MyRoomsViewModel not provided") }
