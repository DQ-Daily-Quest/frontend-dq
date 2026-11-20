package com.dailyquest.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.remote.dto.RoomDetailDto
import com.dailyquest.app.data.room.RoomErrorMessages
import com.dailyquest.app.data.room.RoomRepository
import com.dailyquest.app.data.room.RoomValidation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val JOIN_UNKNOWN_MESSAGE =
    "참여 여부를 확인하지 못했어요. 중복 요청을 막기 위해 자동으로 다시 시도하지 않아요. 우리만 탭의 내 방 목록에서 먼저 확인해 주세요."
const val ALREADY_JOINED_CODE_MESSAGE =
    "이미 참여 중인 방이에요. 우리만 탭에서 확인할 수 있어요."

data class RoomJoinUiState(
    val code: String = "",
    val codeError: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val outcomeUnknown: Boolean = false,
    /** 이미 참여한 방(초대코드). 서버가 방 ID를 주지 않으므로 이동하지 않고 안내만 한다. */
    val alreadyJoinedNotice: Boolean = false,
    /** 입장할 방 ID 이벤트. 이동 후 [RoomJoinViewModel.consumeJoined]로 비운다. */
    val joinedRoomId: Long? = null,
)

/**
 * 공개방 참여(POST /rooms/{id}/join)와 초대코드 참여(POST /rooms/join).
 * 서버에 멱등성 키가 없어 자동 재시도하지 않으며, 진행 중·성공 직후의 중복 요청은 무시한다.
 */
class RoomJoinViewModel(
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
    private val onChanged: () -> Unit = {},
    private val onOutcomeUnknown: () -> Unit = {},
) : ViewModel() {
    private val _ui = MutableStateFlow(RoomJoinUiState())
    val ui: StateFlow<RoomJoinUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    generation++
                    job?.cancel()
                    _ui.value = RoomJoinUiState()
                }
        }
    }

    fun onCodeChange(v: String) = _ui.update {
        it.copy(code = v, codeError = null, error = null, alreadyJoinedNotice = false)
    }

    fun consumeJoined() = _ui.update { it.copy(joinedRoomId = null) }

    fun joinPublic(roomId: Long) {
        if (!canStart()) return
        run(knownRoomId = roomId) { repo.joinPublicRoom(roomId) }
    }

    fun joinByCode() {
        if (!canStart()) return
        val err = RoomValidation.inviteCodeError(_ui.value.code)
        if (err != null) {
            _ui.update { it.copy(codeError = err) }
            return
        }
        val code = RoomValidation.normalizeInviteCode(_ui.value.code)
        run(knownRoomId = null) { repo.joinByInviteCode(code) }
    }

    private fun canStart(): Boolean = !_ui.value.submitting && _ui.value.joinedRoomId == null

    private fun run(knownRoomId: Long?, call: suspend () -> ApiResult<RoomDetailDto>) {
        val gen = generation
        _ui.update { it.copy(submitting = true, error = null, outcomeUnknown = false, alreadyJoinedNotice = false) }
        job = viewModelScope.launch {
            val r = call()
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(submitting = false, joinedRoomId = r.value.roomId) }
                    runCatching { onChanged() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    when {
                        e is ApiException.Http && e.code == ApiErrorCode.ALREADY_JOINED -> {
                            runCatching { onChanged() }
                            if (knownRoomId != null) {
                                // 요청한 방 ID가 확실하므로 입장하되 실제 권한은 RoomScreen이 서버로 다시 확인한다.
                                _ui.update { it.copy(submitting = false, joinedRoomId = knownRoomId) }
                            } else {
                                _ui.update { it.copy(submitting = false, alreadyJoinedNotice = true, error = ALREADY_JOINED_CODE_MESSAGE) }
                            }
                        }
                        e is ApiException.Http ->
                            _ui.update { it.copy(submitting = false, error = RoomErrorMessages.forApi(e)) }
                        else -> {
                            _ui.update { it.copy(submitting = false, outcomeUnknown = true) }
                            runCatching { onOutcomeUnknown() }
                        }
                    }
                }
            }
        }
    }

    class Factory(
        private val container: AppContainer,
        private val onChanged: () -> Unit,
        private val onOutcomeUnknown: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomJoinViewModel(container.roomRepository, container.session.state, onChanged, onOutcomeUnknown) as T
    }
}
