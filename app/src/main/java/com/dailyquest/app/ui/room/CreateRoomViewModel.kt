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

const val OUTCOME_UNKNOWN_MESSAGE =
    "방이 만들어졌는지 확인하지 못했어요. 중복 생성을 막기 위해 자동으로 다시 시도하지 않아요. " +
        "우리만 탭의 내 방 목록에서 먼저 확인한 뒤, 없을 때만 다시 만들어 주세요."

/**
 * 방 생성(POST /rooms). 서버에 멱등성 키가 없어 이 ViewModel은 요청을 자동 재시도하지 않고,
 * 진행 중이거나 이미 성공했다면 submit을 무시한다. 응답을 못 받은 경우에는 사용자가 내 방 목록을 먼저 확인하게 한다.
 *
 * @param onCreated 생성 성공 후 목록 갱신(내 방·공개방). 실패해도 생성 결과에는 영향이 없다.
 * @param onOutcomeUnknown 생성 여부가 불확실할 때 내 방 목록만 다시 조회한다.
 */
class CreateRoomViewModel(
    initialIsPublic: Boolean,
    private val repo: RoomRepository,
    session: StateFlow<SessionState>,
    private val onCreated: () -> Unit = {},
    private val onOutcomeUnknown: () -> Unit = {},
) : ViewModel() {

    private val initial = CreateRoomUiState(isPublic = initialIsPublic)
    private val _ui = MutableStateFlow(initial)
    val ui: StateFlow<CreateRoomUiState> = _ui.asStateFlow()

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
                    // 계정이 바뀌면 이전 계정의 입력·진행 중 요청·결과를 모두 버린다.
                    generation++
                    job?.cancel()
                    _ui.value = initial
                }
        }
    }

    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = null, serverError = null) }
    fun onDescriptionChange(v: String) = _ui.update { it.copy(description = v, descriptionError = null, serverError = null) }
    fun onMaxMembersChange(v: String) = _ui.update { it.copy(maxMembers = v, maxMembersError = null, serverError = null) }
    fun onPublicChange(v: Boolean) = _ui.update { it.copy(isPublic = v, serverError = null) }

    fun consumeCreated() = _ui.update { it.copy(created = null) }

    fun submit() {
        val s = _ui.value
        if (s.submitting || s.created != null) return
        val nameError = RoomValidation.nameError(s.name)
        val descriptionError = RoomValidation.descriptionError(s.description)
        val membersError = RoomValidation.maxMembersError(s.maxMembers)
        if (nameError != null || descriptionError != null || membersError != null) {
            _ui.update { it.copy(nameError = nameError, descriptionError = descriptionError, maxMembersError = membersError) }
            return
        }
        val gen = generation
        _ui.update { it.copy(submitting = true, serverError = null, outcomeUnknown = false) }
        job = viewModelScope.launch {
            val r = repo.createRoom(s.name, s.description, s.isPublic, s.maxMembers.trim().toInt())
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update {
                        it.copy(
                            submitting = false,
                            created = CreatedRoom(r.value.roomId, r.value.name, r.value.isPublic, r.value.inviteCode),
                        )
                    }
                    runCatching { onCreated() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    if (e is ApiException.Http) {
                        _ui.update { it.copy(submitting = false, serverError = RoomErrorMessages.forApi(e)) }
                    } else {
                        // 연결 끊김·타임아웃은 서버가 이미 처리했을 수 있다.
                        _ui.update { it.copy(submitting = false, outcomeUnknown = true) }
                        runCatching { onOutcomeUnknown() }
                    }
                }
            }
        }
    }

    class Factory(
        private val initialIsPublic: Boolean,
        private val container: AppContainer,
        private val onCreated: () -> Unit,
        private val onOutcomeUnknown: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CreateRoomViewModel(initialIsPublic, container.roomRepository, container.session.state, onCreated, onOutcomeUnknown) as T
    }
}
