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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    /** 내 인증이 거절돼 다시 인증해야 하는 미션 id. 서버의 myProof에는 거절이 오지 않아 미션 상세의 인증 목록으로 확인한다. */
    val rejectedIds: Set<Long> = emptySet(),
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

    fun stateOf(m: Mission, nowMs: Long): MissionState = m.state(userId, nowMs, rejected = m.missionId in rejectedIds)

    fun byState(state: MissionState, nowMs: Long): List<RoomMission> =
        flat().filter { stateOf(it.mission, nowMs) == state }

    fun filtered(filter: MissionFilter, nowMs: Long): List<RoomMission> = flat().filter { rm ->
        when (filter) {
            MissionFilter.ALL -> true
            MissionFilter.TODO -> stateOf(rm.mission, nowMs).let { it == MissionState.DOABLE || it == MissionState.RESUBMIT_NEEDED }
            MissionFilter.PENDING -> stateOf(rm.mission, nowMs) == MissionState.PENDING_REVIEW
            MissionFilter.DONE -> stateOf(rm.mission, nowMs) == MissionState.DONE
            MissionFilter.REJECTED -> stateOf(rm.mission, nowMs) == MissionState.RESUBMIT_NEEDED
            MissionFilter.SENT -> rm.mission.isCreatedBy(userId)
        }
    }

    fun count(filter: MissionFilter, nowMs: Long): Int = filtered(filter, nowMs).size
}

/** 미션 탭 상단 필터. 분류는 서버 데이터와 현재 사용자 기준으로만 계산한다. 거절은 재인증이 필요한 미션이다. */
enum class MissionFilter(val label: String) {
    ALL("전체"), TODO("미인증"), PENDING("승인 대기"), DONE("완료"), REJECTED("거절"), SENT("보낸 미션")
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
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _ui = MutableStateFlow(MyMissionsUiState())
    val ui: StateFlow<MyMissionsUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var rejectJob: Job? = null
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
                    rejectJob?.cancel()
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
            if (r is ApiResult.Success) startRejectedCheck(gen, userId, r.value.distinctBy { g -> g.roomId })
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

    /**
     * 내 인증이 없고 마감 전이며 다른 인증이 제출된 미션만 상세를 조회해 거절 이력을 확인한다(조회 전용, 최대 3개 동시).
     * 일부 조회가 실패해도 목록은 그대로 두고 해당 미션은 확인된 값만 반영한다.
     */
    private fun startRejectedCheck(gen: Int, userId: Long, groups: List<RoomMissions>) {
        rejectJob?.cancel()
        val now = clock()
        val candidates = groups.flatMap { g -> g.missions.filter { it.mayHaveRejectedProof(userId, now) }.map { g.roomId to it.missionId } }
        if (candidates.isEmpty()) { _ui.update { it.copy(rejectedIds = emptySet()) }; return }
        rejectJob = viewModelScope.launch {
            val gate = Semaphore(3)
            val found = candidates.map { (roomId, missionId) ->
                async { gate.withPermit { missionId to (repo.getMission(missionId, roomId) as? ApiResult.Success)?.value?.hasMyRejectedProof(userId) } }
            }.awaitAll()
            if (gen != generation) return@launch
            val checked = found.map { it.first }.toSet()
            _ui.update { s ->
                s.copy(rejectedIds = (s.rejectedIds.filter { it !in checked }.filter { id -> candidates.any { c -> c.second == id } } + found.filter { it.second == true }.map { it.first }).toSet())
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
