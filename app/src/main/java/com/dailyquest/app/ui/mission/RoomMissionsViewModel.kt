package com.dailyquest.app.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.Mission
import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionScope
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomMissionsUiState(
    val roomId: Long,
    val userId: Long? = null,
    val scope: MissionScope = MissionScope.ACTIVE,
    val missions: List<Mission> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * 방 하나의 미션 목록(GET /rooms/{id}/missions). Navigation 항목마다 하나씩 만들어 방 사이에 데이터가 섞이지 않는다.
 * ACTIVE 조회는 서버가 오늘의 APP 미션을 지연 생성하며, 마감된 미션은 EXPIRED 범위로만 볼 수 있다.
 */
class RoomMissionsViewModel(
    private val roomId: Long,
    private val repo: MissionRepository,
    session: StateFlow<SessionState>,
    refreshSignal: Flow<Int> = emptyFlow(),
) : ViewModel() {

    private val _ui = MutableStateFlow(RoomMissionsUiState(roomId))
    val ui: StateFlow<RoomMissionsUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch { refreshSignal.drop(1).collect { if (_ui.value.userId != null) refresh() } }
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _ui.value = RoomMissionsUiState(roomId, userId = userId)
                    if (userId != null) load(MissionScope.ACTIVE, page = 0, keep = false)
                }
        }
    }

    fun retry() = load(_ui.value.scope, page = 0, keep = false)

    /** 목록을 유지한 채 첫 페이지부터 다시 조회한다. */
    fun refresh() = load(_ui.value.scope, page = 0, keep = true)

    fun selectScope(scope: MissionScope) {
        if (_ui.value.userId == null || scope == _ui.value.scope && _ui.value.loaded) return
        load(scope, page = 0, keep = false)
    }

    fun loadMore() {
        val s = _ui.value
        if (!s.hasNext || s.loading || s.loadingMore) return
        load(s.scope, s.page + 1, keep = true)
    }

    private fun load(scope: MissionScope, page: Int, keep: Boolean) {
        generation++
        val gen = generation
        job?.cancel()
        val append = page > 0
        _ui.update {
            when {
                append -> it.copy(loadingMore = true, error = null)
                keep && it.scope == scope -> it.copy(loading = true, error = null)
                else -> RoomMissionsUiState(roomId, userId = it.userId, scope = scope, loading = true)
            }
        }
        job = viewModelScope.launch {
            val r = repo.listRoomMissions(roomId, scope, page)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        missions = if (append) (it.missions + r.value.missions).distinctBy { m -> m.missionId } else r.value.missions,
                        page = r.value.page, hasNext = r.value.hasNext,
                        loaded = true, loading = false, loadingMore = false, error = null,
                    )
                    is ApiResult.Failure -> it.copy(
                        loading = false, loadingMore = false, error = MissionErrorMessages.forApi(r.error),
                    )
                }
            }
        }
    }

    class Factory(private val roomId: Long, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomMissionsViewModel(roomId, container.missionRepository, container.session.state, container.proofSync.version) as T
    }
}
