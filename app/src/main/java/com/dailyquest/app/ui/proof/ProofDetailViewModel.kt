package com.dailyquest.app.ui.proof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.mission.MissionType
import com.dailyquest.app.data.proof.ProofDetail
import com.dailyquest.app.data.proof.ProofErrorMessages
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.proof.ProofStatus
import com.dailyquest.app.data.proof.ReactionEmojis
import com.dailyquest.app.data.proof.VoteChoice
import com.dailyquest.app.data.proof.VoteResult
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

const val REACTION_OUTCOME_UNKNOWN_MESSAGE =
    "리액션이 처리됐는지 확인하지 못했어요. 최신 상태를 불러왔어요."
const val DELETE_OUTCOME_UNKNOWN_MESSAGE =
    "삭제가 처리됐는지 확인하지 못했어요. 자동으로 다시 보내지 않고 최신 상태를 불러왔어요."

const val VOTE_OUTCOME_UNKNOWN_MESSAGE =
    "투표가 처리됐는지 확인하지 못했어요. 자동으로 다시 보내지 않고 최신 상태를 불러왔어요. 내 투표 상태를 확인해 주세요."

data class ProofDetailUiState(
    val proofId: Long,
    val userId: Long? = null,
    val detail: ProofDetail? = null,
    /** MEMBER 미션의 생성자. 확인하지 못하면 null이며 그때도 최종 판단은 서버가 한다. */
    val missionCreatorId: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 403/404면 이전 정보를 비우고 안내만 보여준다. */
    val gone: Boolean = false,
    val voting: Boolean = false,
    val voteError: String? = null,
    /** 투표 직후 한 번 보여주는 서버 확정 결과 안내. */
    val voteNotice: String? = null,
    val reacting: Boolean = false,
    val reactionError: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
    /** 서버가 삭제를 확정했다. 화면은 이전 목록으로 돌아간다. */
    val deleted: Boolean = false,
) {
    /** 서버 규칙: 작성자 본인의 승인 대기 인증만 삭제할 수 있다. */
    val canDelete: Boolean get() = detail?.let { it.isMine(userId) && it.proof.status == ProofStatus.PENDING } == true && !deleting && !deleted
    val canVote: Boolean get() = detail?.canVote(userId, missionCreatorId) == true && !voting
}

/**
 * 인증 상세(GET /proofs/{id})와 승인·거절(POST /proofs/{id}/approve|reject).
 * 보상은 서버가 최종 승인 시 제출자에게 지급하므로 앱은 코인을 계산하지 않고, 응답의 rewardGranted만 안내에 쓴다.
 * 투표 응답을 받지 못하면 결과를 추측하지 않고 상세를 다시 조회한다.
 */
class ProofDetailViewModel(
    private val proofId: Long,
    private val proofs: ProofRepository,
    private val missions: MissionRepository,
    session: StateFlow<SessionState>,
    refreshSignal: Flow<Int> = emptyFlow(),
    private val onChanged: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(ProofDetailUiState(proofId))
    val ui: StateFlow<ProofDetailUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var voteJob: Job? = null
    private var mutationJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch { refreshSignal.drop(1).collect { if (_ui.value.userId != null && !_ui.value.voting) load() } }
        viewModelScope.launch {
            session
                .filter { it is SessionState.Authenticated || it is SessionState.Unauthenticated }
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    generation++
                    job?.cancel()
                    voteJob?.cancel()
                    mutationJob?.cancel()
                    _ui.value = ProofDetailUiState(proofId, userId = userId)
                    if (userId != null) load()
                }
        }
    }

    fun retry() = load()

    fun consumeVoteNotice() = _ui.update { it.copy(voteNotice = null) }

    private fun load() {
        generation++
        val gen = generation
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, gone = false) }
        job = viewModelScope.launch {
            val r = proofs.detail(proofId)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    val d = r.value
                    val me = _ui.value.userId
                    val creator = if (d.missionType == MissionType.MEMBER && d.proof.status == ProofStatus.PENDING && !d.isMine(me)) {
                        (missions.getMission(d.proof.missionId, d.roomId) as? ApiResult.Success)?.value?.mission?.createdBy?.userId
                    } else null
                    if (gen != generation) return@launch
                    _ui.update { it.copy(detail = d, missionCreatorId = creator, loading = false, error = null) }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    val gone = e is ApiException.Http && (e.status == 403 || e.status == 404)
                    _ui.update {
                        it.copy(
                            detail = if (gone) null else it.detail, missionCreatorId = if (gone) null else it.missionCreatorId,
                            loading = false, gone = gone, error = ProofErrorMessages.forApi(e),
                        )
                    }
                }
            }
        }
    }

    /** 내가 이미 누른 이모지면 취소, 아니면 등록한다. 한 번에 하나의 요청만 보내며 화면 값은 서버가 돌려준 목록으로만 바꾼다. */
    fun toggleReaction(emoji: String) {
        val s = _ui.value
        val d = s.detail ?: return
        if (s.reacting || s.deleting || emoji !in ReactionEmojis.ALLOWED) return
        val mine = d.reactions.firstOrNull { it.emoji == emoji }?.reacted == true
        val gen = generation
        _ui.update { it.copy(reacting = true, reactionError = null) }
        mutationJob = viewModelScope.launch {
            val r = if (mine) proofs.removeReaction(proofId, emoji) else proofs.addReaction(proofId, emoji)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> _ui.update { cur ->
                    cur.copy(reacting = false, detail = cur.detail?.copy(reactions = r.value))
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    val http = e is ApiException.Http
                    _ui.update { it.copy(reacting = false, reactionError = if (http) ProofErrorMessages.forApi(e) else REACTION_OUTCOME_UNKNOWN_MESSAGE) }
                    // 응답을 못 받았으면 결과를 추측하지 않고 서버 상태를 다시 읽는다. 자동 재전송은 하지 않는다.
                    if (!http || (e as ApiException.Http).status in setOf(403, 404)) load()
                }
            }
        }
    }

    fun requestDelete() {
        val s = _ui.value
        if (!s.canDelete || s.reacting) return
        val gen = generation
        _ui.update { it.copy(deleting = true, deleteError = null) }
        mutationJob = viewModelScope.launch {
            val r = proofs.delete(proofId)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(deleting = false, deleted = true) }
                    runCatching { onChanged() }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    val http = e is ApiException.Http
                    _ui.update { it.copy(deleting = false, deleteError = if (http) ProofErrorMessages.forApi(e) else DELETE_OUTCOME_UNKNOWN_MESSAGE) }
                    load()
                }
            }
        }
    }

    fun approve() = vote(VoteChoice.APPROVE)
    fun reject() = vote(VoteChoice.REJECT)

    private fun vote(choice: VoteChoice) {
        val s = _ui.value
        if (s.voting || !s.canVote) return
        val gen = generation
        _ui.update { it.copy(voting = true, voteError = null, voteNotice = null) }
        voteJob = viewModelScope.launch {
            val r = proofs.vote(proofId, choice)
            if (gen != generation) return@launch
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(voting = false, voteNotice = noticeFor(r.value, s.detail?.proof?.user?.nickname)) }
                }
                is ApiResult.Failure -> {
                    val e = r.error
                    _ui.update {
                        it.copy(
                            voting = false,
                            voteError = if (e is ApiException.Http) ProofErrorMessages.forApi(e) else VOTE_OUTCOME_UNKNOWN_MESSAGE,
                        )
                    }
                }
            }
            load()
            runCatching { onChanged() }
        }
    }

    private fun noticeFor(r: VoteResult, submitter: String?): String = when (r.status) {
        ProofStatus.APPROVED -> {
            val who = submitter?.let { "${it}님에게 " }.orEmpty()
            val coins = r.rewardCoins?.let { " 보상 +${it}코인${if (r.streakBonus > 0) "(연속 보너스 +${r.streakBonus})" else ""}이 지급됐어요." }
            "승인이 확정됐어요.${if (coins != null) " ${who}${coins.trim()}" else ""}"
        }
        ProofStatus.REJECTED -> "거절이 확정됐어요. 제출자는 다시 인증할 수 있어요."
        else -> "투표했어요. (승인 ${r.approval.approved} · 거절 ${r.approval.rejected} / 필요 ${r.approval.required})"
    }

    class Factory(private val proofId: Long, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProofDetailViewModel(
            proofId, container.proofRepository, container.missionRepository, container.session.state,
            container.proofSync.version, onChanged = { container.proofSync.notifyChanged() },
        ) as T
    }
}
