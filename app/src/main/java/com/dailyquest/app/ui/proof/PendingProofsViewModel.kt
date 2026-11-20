package com.dailyquest.app.ui.proof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.proof.PendingProof
import com.dailyquest.app.data.proof.ProofErrorMessages
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingProofsUiState(
    val roomId: Long,
    val userId: Long? = null,
    val items: List<PendingProof> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val gone: Boolean = false,
)

/**
 * 방의 승인 대기 인증(GET /rooms/{id}/proofs/pending). 서버가 "내가 아직 투표하지 않았고 내가 승인할 수 있는" 것만 내려준다.
 * 방마다 하나씩 만들어 방 사이에 목록이 섞이지 않으며, 계정이 바뀌거나 403/404면 이전 목록을 버린다.
 */
class PendingProofsViewModel(
    private val roomId: Long,
    private val repo: ProofRepository,
    session: StateFlow<SessionState>,
    refreshSignal: Flow<Int> = emptyFlow(),
) : ViewModel() {

    private val _ui = MutableStateFlow(PendingProofsUiState(roomId))
    val ui: StateFlow<PendingProofsUiState> = _ui.asStateFlow()

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
                    _ui.value = PendingProofsUiState(roomId, userId = userId)
                    if (userId != null) load(page = 0, keep = false)
                }
        }
    }

    fun retry() = load(page = 0, keep = false)
    fun refresh() = load(page = 0, keep = true)

    fun loadMore() {
        val s = _ui.value
        if (!s.hasNext || s.loading || s.loadingMore) return
        load(page = s.page + 1, keep = true)
    }

    private fun load(page: Int, keep: Boolean) {
        if (_ui.value.userId == null) return
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { it.copy(loading = page == 0, loadingMore = page > 0, error = null, gone = false, items = if (keep) it.items else emptyList()) }
        job = viewModelScope.launch {
            val r = repo.pending(roomId, page)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(
                        items = if (page == 0) r.value.items else (it.items + r.value.items).distinctBy { p -> p.proofId },
                        page = r.value.page, hasNext = r.value.hasNext, loaded = true, loading = false, loadingMore = false,
                    )
                    is ApiResult.Failure -> {
                        val e = r.error
                        val gone = e is ApiException.Http && (e.status == 403 || e.status == 404)
                        it.copy(
                            items = if (gone) emptyList() else it.items, hasNext = if (gone) false else it.hasNext,
                            loading = false, loadingMore = false, gone = gone, error = ProofErrorMessages.forApi(e),
                        )
                    }
                }
            }
        }
    }

    class Factory(private val roomId: Long, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PendingProofsViewModel(roomId, container.proofRepository, container.session.state, container.proofSync.version) as T
    }
}
