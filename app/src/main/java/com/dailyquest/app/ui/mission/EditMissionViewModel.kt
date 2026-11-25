package com.dailyquest.app.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.Mission
import com.dailyquest.app.data.mission.MissionErrorMessages
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionValidation
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MISSION_EDIT_OUTCOME_UNKNOWN_MESSAGE =
    "수정이 반영됐는지 확인하지 못했어요. 자동으로 다시 시도하지 않아요. 미션 상세에서 내용을 확인한 뒤 필요하면 다시 수정해 주세요."

data class EditMissionUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val original: Mission? = null,
    val content: String = "",
    /** null이면 마감을 바꾸지 않는다. */
    val due: MissionDueOption? = null,
    val contentError: String? = null,
    val serverError: String? = null,
    val submitting: Boolean = false,
    val outcomeUnknown: Boolean = false,
    val saved: Boolean = false,
)

/**
 * 멤버 미션 수정(PATCH /missions/{id}). 서버가 받는 필드는 content·dueAt뿐이며, 바뀐 필드만 보낸다.
 * 권한(작성자)과 인증 상태(MISSION_HAS_PROOFS)는 서버가 최종 판단한다. 자동 재시도하지 않고 진행 중·저장 후 submit은 무시한다.
 */
class EditMissionViewModel(
    private val missionId: Long,
    private val roomId: Long,
    private val repo: MissionRepository,
    private val myUserId: () -> Long?,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChanged: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(EditMissionUiState())
    val ui: StateFlow<EditMissionUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.getMission(missionId, roomId)) {
                is ApiResult.Success -> {
                    val m = r.value.mission
                    if (!m.canEdit(myUserId())) {
                        _ui.update { it.copy(loading = false, loadError = "미션을 만든 사람만 수정할 수 있어요.") }
                    } else {
                        _ui.update { it.copy(loading = false, original = m, content = m.content) }
                    }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    val msg = if (e is ApiException.Http) MissionErrorMessages.forManage(e, deleting = false) else MissionErrorMessages.forApi(e)
                    _ui.update { it.copy(loading = false, loadError = msg) }
                }
            }
        }
    }

    fun onContentChange(v: String) = _ui.update { it.copy(content = v, contentError = null, serverError = null) }
    fun onDueChange(v: MissionDueOption?) = _ui.update { it.copy(due = v, serverError = null) }
    fun consumeSaved() = _ui.update { it.copy(saved = false) }

    fun submit() {
        val s = _ui.value
        val original = s.original ?: return
        if (s.submitting || s.saved) return
        val contentError = MissionValidation.contentError(s.content)
        if (contentError != null) {
            _ui.update { it.copy(contentError = contentError) }
            return
        }
        val newContent = s.content.trim().takeIf { it != original.content.trim() }
        val newDue = s.due?.let { now() + it.hours * 3_600_000L }
        if (newContent == null && newDue == null) {
            _ui.update { it.copy(serverError = "변경된 내용이 없어요.") }
            return
        }
        _ui.update { it.copy(submitting = true, serverError = null, outcomeUnknown = false) }
        viewModelScope.launch {
            when (val r = repo.updateMission(missionId, roomId, newContent, newDue)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(submitting = false, saved = true) }
                    runCatching { onChanged() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    if (e is ApiException.Http) {
                        _ui.update { it.copy(submitting = false, serverError = MissionErrorMessages.forManage(e, deleting = false)) }
                    } else {
                        _ui.update { it.copy(submitting = false, outcomeUnknown = true) }
                        runCatching { onChanged() }
                    }
                }
            }
        }
    }

    class Factory(private val missionId: Long, private val roomId: Long, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditMissionViewModel(
            missionId, roomId, container.missionRepository,
            myUserId = { (container.session.state.value as? SessionState.Authenticated)?.profile?.userId },
            onChanged = { container.proofSync.notifyChanged(refreshCoins = false) },
        ) as T
    }
}
