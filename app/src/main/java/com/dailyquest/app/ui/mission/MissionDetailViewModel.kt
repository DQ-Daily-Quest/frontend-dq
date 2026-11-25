package com.dailyquest.app.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.MissionDetail
import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
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

data class MissionDetailUiState(
    val missionId: Long,
    val userId: Long? = null,
    val detail: MissionDetail? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 403/404면 이전 정보를 비우고 재시도 대신 안내만 보여준다. */
    val gone: Boolean = false,
    /** GET /rooms/{id}의 myRole이 OWNER일 때만 true. 확인하지 못하면 false(방장 권한을 주지 않는다). */
    val isRoomOwner: Boolean = false,
    val deleting: Boolean = false,
    val actionError: String? = null,
    /** 삭제 성공 이벤트. 화면은 이동 후 [MissionDetailViewModel.consumeDeleted]로 비운다. */
    val deleted: Boolean = false,
) {
    val canEdit: Boolean get() = detail?.mission?.canEdit(userId) == true
    val canDelete: Boolean get() = detail?.mission?.canDelete(userId, isRoomOwner) == true
    val showMenu: Boolean get() = canEdit || canDelete
}

/** 미션 상세(GET /missions/{id}). 계정이 바뀌면 이전 계정의 상세를 버린다. */
class MissionDetailViewModel(
    private val missionId: Long,
    private val roomId: Long,
    private val repo: MissionRepository,
    session: StateFlow<SessionState>,
    refreshSignal: Flow<Int> = emptyFlow(),
    private val onChanged: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(MissionDetailUiState(missionId))
    val ui: StateFlow<MissionDetailUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch { refreshSignal.drop(1).collect { if (_ui.value.userId != null && !_ui.value.deleted) load() } }
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _ui.value = MissionDetailUiState(missionId, userId = userId)
                    if (userId != null) load()
                }
        }
    }

    fun retry() = load()

    fun consumeDeleted() = _ui.update { it.copy(deleted = false) }
    fun dismissActionError() = _ui.update { it.copy(actionError = null) }

    /**
     * 삭제(DELETE). 서버가 최종 판단하며(작성자·방장·인증 상태) 자동 재시도하지 않는다. 진행 중·삭제 후 호출은 무시한다.
     * 응답을 받지 못하면 삭제됐는지 미션을 다시 조회해 확인한다(404면 삭제된 것).
     */
    fun delete() {
        val s = _ui.value
        val mission = s.detail?.mission ?: return
        if (s.deleting || s.deleted || !s.canDelete) return
        val gen = generation
        _ui.update { it.copy(deleting = true, actionError = null) }
        viewModelScope.launch {
            val r = repo.deleteMission(mission.missionId)
            if (gen != generation) return@launch
            val success = when (r) {
                is ApiResult.Success -> true
                is ApiResult.Failure -> {
                    val e = r.error
                    when {
                        e is ApiException.Http -> e.code == ApiErrorCode.MISSION_NOT_FOUND
                        else -> (repo.getMission(missionId, roomId) as? ApiResult.Failure)
                            ?.error.let { it is ApiException.Http && it.status == 404 }
                    }
                }
            }
            if (gen != generation) return@launch
            if (success) {
                _ui.update { it.copy(deleting = false, deleted = true) }
                runCatching { onChanged() }
            } else {
                val e = (r as ApiResult.Failure).error
                val msg = if (e is ApiException.Http) MissionErrorMessages.forManage(e, deleting = true)
                else "삭제 결과를 확인하지 못했어요. 미션이 아직 남아 있다면 다시 삭제해 주세요."
                _ui.update { it.copy(deleting = false, actionError = msg) }
                if (e is ApiException.Http && e.status == 409) load()
            }
        }
    }

    private fun load() {
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, gone = false) }
        job = viewModelScope.launch {
            val r = repo.getMission(missionId, roomId)
            if (gen != generation) return@launch
            if (r is ApiResult.Success && r.value.mission.type == MissionType.MEMBER) {
                val owner = repo.isRoomOwner(r.value.mission.roomId.takeIf { it > 0 } ?: roomId)
                if (gen != generation) return@launch
                _ui.update { it.copy(isRoomOwner = owner == true) }
            }
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(detail = r.value, loading = false, error = null)
                    is ApiResult.Failure -> {
                        val e = r.error
                        val gone = e is ApiException.Http && (e.status == 403 || e.status == 404)
                        it.copy(
                            detail = if (gone) null else it.detail,
                            loading = false, gone = gone, error = MissionErrorMessages.forApi(e),
                        )
                    }
                }
            }
        }
    }

    class Factory(private val missionId: Long, private val roomId: Long, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MissionDetailViewModel(missionId, roomId, container.missionRepository, container.session.state, container.proofSync.version,
                onChanged = { container.proofSync.notifyChanged(refreshCoins = false) },
            ) as T
    }
}
