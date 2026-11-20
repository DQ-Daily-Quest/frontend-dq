package com.dailyquest.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
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

enum class RoomAction { UPDATE, DELETE, LEAVE, KICK, TRANSFER, REGENERATE_CODE }

/** 화면이 한 번만 처리하는 결과. 처리 후 [RoomManageViewModel.consumeEvent]로 비운다. */
sealed interface RoomManageEvent {
    /** 서버 상태가 바뀌었으므로 상세·멤버를 다시 조회해야 한다. */
    data class Changed(val action: RoomAction) : RoomManageEvent
    /** 방을 삭제했거나 나갔다. 이 방 화면을 떠난다. */
    data class Gone(val action: RoomAction) : RoomManageEvent
    /** 응답을 받지 못해 결과를 알 수 없다. 상세를 다시 조회해 실제 상태를 확인한다. */
    data class Unknown(val action: RoomAction) : RoomManageEvent
}

const val MANAGE_UNKNOWN_MESSAGE =
    "요청 결과를 확인하지 못했어요. 자동으로 다시 시도하지 않고 최신 방 정보를 다시 불러와요."

data class RoomManageUiState(
    val busy: RoomAction? = null,
    val error: String? = null,
    val notice: String? = null,
    val event: RoomManageEvent? = null,
    val myUserId: Long? = null,
)

/**
 * 방 관리(수정·삭제·탈퇴·추방·방장 위임·초대코드 재발급). 권한 판단은 서버 응답(myRole)으로 하며
 * 이 ViewModel은 어떤 요청도 자동 재시도하지 않는다. 진행 중에는 다른 관리 요청도 받지 않는다.
 *
 * @param onListsChanged 성공 후 내 방·공개방 목록 재조회. 실패해도 요청 결과에는 영향이 없다.
 */
class RoomManageViewModel(
    private val roomId: Long,
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
    private val onListsChanged: () -> Unit = {},
) : ViewModel() {
    private val _ui = MutableStateFlow(RoomManageUiState())
    val ui: StateFlow<RoomManageUiState> = _ui.asStateFlow()

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
                    _ui.value = RoomManageUiState(myUserId = userId)
                }
        }
    }

    fun consumeEvent() = _ui.update { it.copy(event = null) }
    fun dismissMessages() = _ui.update { it.copy(error = null, notice = null) }

    fun update(name: String?, description: String?, isPublic: Boolean?, maxMembers: Int?) =
        run(RoomAction.UPDATE) { repo.updateRoom(roomId, name, description, isPublic, maxMembers).map { } }

    fun delete() = run(RoomAction.DELETE, gone = true) { repo.deleteRoom(roomId) }

    fun leave() = run(RoomAction.LEAVE, gone = true) { repo.leaveRoom(roomId) }

    fun kick(userId: Long) {
        if (userId == _ui.value.myUserId) return
        run(RoomAction.KICK) { repo.kickMember(roomId, userId) }
    }

    fun transferOwner(userId: Long) {
        if (userId == _ui.value.myUserId) return
        run(RoomAction.TRANSFER) { repo.transferOwner(roomId, userId).map { } }
    }

    fun regenerateInviteCode() = run(
        RoomAction.REGENERATE_CODE,
        notice = "새 초대코드가 발급됐어요. 이전 코드는 더 이상 사용할 수 없어요.",
    ) { repo.regenerateInviteCode(roomId).map { } }

    private fun <T> ApiResult<T>.map(f: (T) -> Unit): ApiResult<Unit> = when (this) {
        is ApiResult.Success -> ApiResult.Success(f(value))
        is ApiResult.Failure -> this
    }

    private fun run(
        action: RoomAction,
        gone: Boolean = false,
        notice: String? = null,
        call: suspend () -> ApiResult<Unit>,
    ) {
        if (_ui.value.busy != null) return
        val gen = generation
        _ui.update { it.copy(busy = action, error = null, notice = null) }
        job = viewModelScope.launch {
            val r = call()
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update {
                        it.copy(
                            busy = null, notice = notice,
                            event = if (gone) RoomManageEvent.Gone(action) else RoomManageEvent.Changed(action),
                        )
                    }
                    runCatching { onListsChanged() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    if (e is ApiException.Http) {
                        // 403/404도 같은 경로로 알리며, 최신 상세 조회가 이전 정보를 정리한다.
                        _ui.update { it.copy(busy = null, error = RoomErrorMessages.forApi(e), event = RoomManageEvent.Unknown(action).takeIf { _ -> e.status == 403 || e.status == 404 }) }
                    } else {
                        _ui.update { it.copy(busy = null, error = MANAGE_UNKNOWN_MESSAGE, event = RoomManageEvent.Unknown(action)) }
                        runCatching { onListsChanged() }
                    }
                }
            }
        }
    }

    class Factory(
        private val roomId: Long,
        private val container: AppContainer,
        private val onListsChanged: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomManageViewModel(roomId, container.roomRepository, container.session.state, onListsChanged) as T
    }
}
