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

const val MISSION_OUTCOME_UNKNOWN_MESSAGE =
    "미션이 보내졌는지 확인하지 못했어요. 중복 생성을 막기 위해 자동으로 다시 시도하지 않아요. " +
        "방의 미션 목록에서 먼저 확인한 뒤, 없을 때만 다시 보내 주세요."

/** 마감 선택지. 서버 dueAt은 임의의 미래 시각이며 기본값은 7일 뒤다. */
enum class MissionDueOption(val label: String, val hours: Long) {
    HOURS_24("24시간", 24), THREE_DAYS("3일", 72), WEEK("일주일", 168);
}

data class CreateMissionUiState(
    val myUserId: Long? = null,
    val content: String = "",
    /** 사용자가 입력 중인 보상 문자열. 입력 중에는 자유롭게 수정할 수 있다. */
    val rewardText: String = MissionValidation.DEFAULT_REWARD.toString(),
    val due: MissionDueOption = MissionDueOption.HOURS_24,
    /** null이면 방 전체 멤버가 대상이다(생성자는 서버가 제외). */
    val assigneeUserId: Long? = null,
    val contentError: String? = null,
    val assigneeError: String? = null,
    val serverError: String? = null,
    val submitting: Boolean = false,
    val outcomeUnknown: Boolean = false,
    /** 성공 이벤트. 화면은 이동 후 [CreateMissionViewModel.consumeCreated]로 비운다. */
    val created: Mission? = null,
) {
    /** 숫자로 해석되는 보상. 비었거나 숫자가 아니면 null. */
    val rewardCoins: Int? get() = rewardText.toIntOrNull()
    val rewardError: String? get() = MissionValidation.rewardError(rewardCoins ?: -1)
}

/**
 * 미션 보내기(POST /rooms/{id}/missions). 서버에 멱등성 키가 없어 자동 재시도하지 않고, 진행 중·성공 후 submit은 무시한다.
 * 서버가 코인을 차감하지 않으므로 잔액 확인이나 사용자 정보 갱신은 하지 않는다(보상은 인증 승인 시 서버가 지급).
 *
 * @param onCreated 성공 후 미션 목록 갱신. 실패해도 생성 결과에는 영향이 없다.
 * @param onOutcomeUnknown 생성 여부가 불확실할 때 목록만 다시 조회한다.
 */
class CreateMissionViewModel(
    private val roomId: Long,
    private val repo: MissionRepository,
    session: StateFlow<SessionState>,
    private val myUserId: () -> Long?,
    private val now: () -> Long = System::currentTimeMillis,
    private val onCreated: () -> Unit = {},
    private val onOutcomeUnknown: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(CreateMissionUiState(myUserId = myUserId()))
    val ui: StateFlow<CreateMissionUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .drop(1)
                .collect { userId ->
                    generation++
                    job?.cancel()
                    _ui.value = CreateMissionUiState(myUserId = userId)
                }
        }
    }

    fun onContentChange(v: String) = _ui.update { it.copy(content = v, contentError = null, serverError = null) }
    fun onDueChange(v: MissionDueOption) = _ui.update { it.copy(due = v, serverError = null) }
    fun onAssigneeChange(userId: Long?) = _ui.update { it.copy(assigneeUserId = userId, assigneeError = null, serverError = null) }
    fun onRewardTextChange(v: String) {
        val digits = v.filter { it.isDigit() }.take(4)
        _ui.update { it.copy(rewardText = digits, serverError = null) }
    }

    /** 입력값이 10 단위가 아니면 증가는 다음 10 단위로, 감소는 이전 10 단위로 맞춘다. 비어 있으면 기본값에서 시작한다. */
    fun stepReward(delta: Int) = _ui.update {
        val step = MissionValidation.REWARD_STEP
        val cur = it.rewardCoins ?: MissionValidation.DEFAULT_REWARD
        val aligned = when {
            cur % step == 0 -> MissionValidation.stepReward(cur, delta)
            delta > 0 -> MissionValidation.stepReward(cur - cur % step, step)
            else -> MissionValidation.stepReward(cur - cur % step + step, -step)
        }
        it.copy(rewardText = aligned.toString(), serverError = null)
    }

    fun consumeCreated() = _ui.update { it.copy(created = null) }

    fun submit() {
        val s = _ui.value
        if (s.submitting || s.created != null) return
        val contentError = MissionValidation.contentError(s.content)
        val assigneeError = MissionValidation.assigneeError(s.assigneeUserId, myUserId())
        val rewardError = s.rewardError
        val reward = s.rewardCoins
        if (contentError != null || assigneeError != null || rewardError != null || reward == null) {
            _ui.update { it.copy(contentError = contentError, assigneeError = assigneeError) }
            return
        }
        val dueAt = now() + s.due.hours * 3_600_000L
        val gen = generation
        _ui.update { it.copy(submitting = true, serverError = null, outcomeUnknown = false) }
        job = viewModelScope.launch {
            val r = repo.createMission(roomId, s.content, reward, dueAt, s.assigneeUserId)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(submitting = false, created = r.value) }
                    runCatching { onCreated() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    if (e is ApiException.Http) {
                        _ui.update { it.copy(submitting = false, serverError = MissionErrorMessages.forApi(e)) }
                    } else {
                        _ui.update { it.copy(submitting = false, outcomeUnknown = true) }
                        runCatching { onOutcomeUnknown() }
                    }
                }
            }
        }
    }

    class Factory(
        private val roomId: Long,
        private val container: AppContainer,
        private val onCreated: () -> Unit,
        private val onOutcomeUnknown: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CreateMissionViewModel(
            roomId, container.missionRepository, container.session.state,
            myUserId = { (container.session.state.value as? SessionState.Authenticated)?.profile?.userId },
            onCreated = onCreated, onOutcomeUnknown = onOutcomeUnknown,
        ) as T
    }
}
