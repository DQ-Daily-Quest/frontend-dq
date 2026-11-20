package com.dailyquest.app.ui.room

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
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
 * 방 하나의 상세·멤버 조회(GET만). Navigation 백스택 항목마다 하나씩 만들어 다른 방의 데이터가 섞이지 않는다.
 * 멤버 API는 서버가 멤버에게만 허용하므로 myRole이 OWNER/MEMBER일 때만 호출한다.
 */
class RoomDetailViewModel(
    private val roomId: Long,
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
    /** 공개방 미리보기처럼 멤버 목록이 필요 없는 화면에서는 false. */
    private val withMembers: Boolean = true,
) : ViewModel() {

    private val _ui = MutableStateFlow(RoomDetailUiState(roomId))
    val ui: StateFlow<RoomDetailUiState> = _ui.asStateFlow()

    private var detailJob: Job? = null
    private var membersJob: Job? = null

    /** 재조회·사용자 변경마다 증가하며 이전 세대의 늦은 응답은 버린다. */
    private var generation = 0

    init {
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    detailJob?.cancel(); membersJob?.cancel()
                    _ui.value = RoomDetailUiState(roomId)
                    if (userId != null) loadDetail(clear = true)
                }
        }
    }

    /** 상세 조회 실패 후 재시도. */
    fun retry() = loadDetail(clear = true)

    /** 기존 화면을 유지한 채 다시 조회한다. */
    fun refresh() = loadDetail(clear = false)

    /** 멤버 조회만 실패했을 때 상세는 그대로 두고 멤버만 다시 불러온다. */
    fun retryMembers() {
        if (withMembers && _ui.value.isMember) loadMembers(generation)
    }

    private fun loadDetail(clear: Boolean) {
        generation++
        val gen = generation
        detailJob?.cancel(); membersJob?.cancel()
        _ui.update {
            if (clear) RoomDetailUiState(roomId) else it.copy(refreshing = true, failure = null, error = null)
        }
        detailJob = viewModelScope.launch {
            val r = repo.getRoom(roomId)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update {
                        val member = r.value.myRole == ROLE_OWNER || r.value.myRole == ROLE_MEMBER
                        it.copy(
                            detail = r.value, loading = false, refreshing = false, failure = null, error = null,
                            members = if (member) it.members else emptyList(),
                            membersLoaded = member && it.membersLoaded,
                            membersError = null, membersLoading = false,
                        )
                    }
                    if (withMembers && _ui.value.isMember) loadMembers(gen)
                }
                is ApiResult.Failure -> _ui.update {
                    val failure = classify(r.error)
                    val message = RoomErrorMessages.forApi(r.error)
                    if (failure == RoomDetailFailure.OTHER) {
                        // 일시적 오류는 이미 받은 상세를 유지하고 메시지만 보여 준다.
                        it.copy(loading = false, refreshing = false, failure = failure, error = message)
                    } else {
                        // 삭제·권한 상실이면 이전 상세·초대코드·멤버를 남기지 않는다.
                        RoomDetailUiState(roomId, loading = false, failure = failure, error = message)
                    }
                }
            }
        }
    }

    private fun loadMembers(gen: Int) {
        membersJob?.cancel()
        _ui.update { it.copy(membersLoading = true, membersError = null) }
        membersJob = viewModelScope.launch {
            val r = repo.listMembers(roomId)
            if (gen != generation) return@launch
            _ui.update {
                when (r) {
                    is ApiResult.Success -> it.copy(members = r.value, membersLoading = false, membersLoaded = true, membersError = null)
                    is ApiResult.Failure -> it.copy(membersLoading = false, membersError = RoomErrorMessages.forApi(r.error))
                }
            }
        }
    }

    private fun classify(e: ApiException): RoomDetailFailure {
        if (e !is ApiException.Http) return RoomDetailFailure.OTHER
        return when {
            e.status == 404 || e.code == ApiErrorCode.ROOM_NOT_FOUND -> RoomDetailFailure.NOT_FOUND
            e.status == 403 || e.code == ApiErrorCode.FORBIDDEN -> RoomDetailFailure.FORBIDDEN
            else -> RoomDetailFailure.OTHER
        }
    }

    class Factory(private val roomId: Long, private val container: AppContainer, private val withMembers: Boolean = true) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomDetailViewModel(roomId, container.roomRepository, container.session.state, withMembers) as T
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
