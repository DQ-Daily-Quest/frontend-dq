package com.dailyquest.app.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.MissionDetail
import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
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
)

/** 미션 상세(GET /missions/{id}). 계정이 바뀌면 이전 계정의 상세를 버린다. */
class MissionDetailViewModel(
    private val missionId: Long,
    private val roomId: Long,
    private val repo: MissionRepository,
    session: StateFlow<SessionState>,
) : ViewModel() {

    private val _ui = MutableStateFlow(MissionDetailUiState(missionId))
    val ui: StateFlow<MissionDetailUiState> = _ui.asStateFlow()

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
                    _ui.value = MissionDetailUiState(missionId, userId = userId)
                    if (userId != null) load()
                }
        }
    }

    fun retry() = load()

    private fun load() {
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, gone = false) }
        job = viewModelScope.launch {
            val r = repo.getMission(missionId, roomId)
            if (gen != generation) return@launch
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
            MissionDetailViewModel(missionId, roomId, container.missionRepository, container.session.state) as T
    }
}
