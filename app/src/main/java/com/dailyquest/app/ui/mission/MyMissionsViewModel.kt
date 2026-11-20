package com.dailyquest.app.ui.mission

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.Mission
import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionState
import com.dailyquest.app.data.mission.RoomMissions
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

/** 방 이름이 붙은 미션. 여러 방의 미션이 섞이지 않도록 항상 방과 함께 다룬다. */
data class RoomMission(val roomId: Long, val roomName: String, val mission: Mission)

data class MyMissionsUiState(
    val userId: Long? = null,
    val groups: List<RoomMissions> = emptyList(),
    val loaded: Boolean = false,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    /** 참여한 방이 없거나 방에 미션이 하나도 없는 상태. */
    val isEmpty: Boolean get() = loaded && groups.all { it.missions.isEmpty() }

    private fun flat(): List<RoomMission> =
        groups.flatMap { g -> g.missions.map { RoomMission(g.roomId, g.roomName, it) } }

    /** 오늘의 APP 미션(방마다 하나). */
    val appMissions: List<RoomMission> get() = flat().filter { it.mission.type == com.dailyquest.app.data.mission.MissionType.APP }

    /** 다른 멤버가 보낸 미션. 내가 보낸 미션은 포함하지 않는다. */
    val friendMissions: List<RoomMission> get() = flat().filter { it.mission.isFromFriend(userId) }

    /** 내가 보낸 미션(작성자가 나). */
    val sentMissions: List<RoomMission> get() = flat().filter { it.mission.isCreatedBy(userId) }

    fun byState(state: MissionState, nowMs: Long): List<RoomMission> =
        flat().filter { it.mission.state(userId, nowMs) == state }
}

/**
 * 홈과 미션 탭이 공유하는 내 미션(GET /missions/mine, 방별 묶음). 조회 전용이며 서버가 오늘의 APP 미션을 지연 생성한다.
 * 로그인 사용자가 바뀌면 목록과 진행 중인 요청을 모두 버린다.
 */
class MyMissionsViewModel(
    private val repo: MissionRepository,
    session: StateFlow<SessionState>,
    /** 인증 제출·승인 등으로 서버 상태가 바뀌면 값이 바뀐다. 바뀔 때마다 목록을 다시 조회한다(첫 값은 무시). */
    refreshSignal: Flow<Int> = emptyFlow(),
) : ViewModel() {

    private val _ui = MutableStateFlow(MyMissionsUiState())
    val ui: StateFlow<MyMissionsUiState> = _ui.asStateFlow()

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
                    _ui.value = MyMissionsUiState(userId = userId)
                    if (userId != null) load(clear = true)
                }
        }
    }

    fun retry() = load(clear = true)

    /** 기존 목록을 유지한 채 다시 조회한다. 미션 생성 후 등 다른 기능에서 호출한다. */
    fun refresh() = load(clear = false)

    private fun load(clear: Boolean) {
        val userId = _ui.value.userId ?: return
        generation++
        val gen = generation
        job?.cancel()
        _ui.update {
            if (clear) MyMissionsUiState(userId = userId, initialLoading = true)
            else it.copy(refreshing = true, error = null)
        }
        job = viewModelScope.launch {
            val r = repo.listMine()
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        groups = r.value.distinctBy { g -> g.roomId },
                        loaded = true, initialLoading = false, refreshing = false, error = null,
                    )
                    is ApiResult.Failure -> it.copy(
                        initialLoading = false, refreshing = false, error = MissionErrorMessages.forApi(r.error),
                    )
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MyMissionsViewModel(container.missionRepository, container.session.state, container.proofSync.version) as T
    }
}

val LocalMyMissions = staticCompositionLocalOf<MyMissionsViewModel> { error("MyMissionsViewModel not provided") }
