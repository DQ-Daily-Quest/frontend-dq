package com.dailyquest.app.ui.proof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.mission.Mission
import com.dailyquest.app.data.mission.MissionRepository
import com.dailyquest.app.data.proof.AndroidProofMediaPreparer
import com.dailyquest.app.data.proof.PrepareResult
import com.dailyquest.app.data.proof.PreparedMedia
import com.dailyquest.app.data.proof.ProofErrorMessages
import com.dailyquest.app.data.proof.ProofRepository
import com.dailyquest.app.data.proof.ProofMediaPreparer
import com.dailyquest.app.data.proof.ProofValidation
import com.dailyquest.app.data.proof.UploadRepository
import com.dailyquest.app.data.proof.UploadedMedia
import com.dailyquest.app.data.remote.ApiErrorCode
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

const val PROOF_OUTCOME_UNKNOWN_MESSAGE =
    "인증이 제출됐는지 확인하지 못했어요. 중복 제출을 막기 위해 자동으로 다시 보내지 않아요. " +
        "네트워크를 확인한 뒤 다시 눌러 주세요. 먼저 서버에서 제출 여부를 확인해요."

enum class SubmitPhase { IDLE, UPLOADING, SUBMITTING, CHECKING }

/** 제출 완료 이벤트. [proofId]는 서버 응답(또는 재조회한 내 인증)의 값이며 모르면 null이다. */
data class ProofSubmitted(val proofId: Long?, val recovered: Boolean)

data class ProofSubmitUiState(
    val missionId: Long,
    val myUserId: Long? = null,
    val media: PreparedMedia? = null,
    val preparing: Boolean = false,
    val mediaError: String? = null,
    val text: String = "",
    val textError: String? = null,
    val phase: SubmitPhase = SubmitPhase.IDLE,
    val error: String? = null,
    val outcomeUnknown: Boolean = false,
    /** 업로드는 끝났고 제출만 남은 상태. 이때 다시 눌러도 파일을 다시 올리지 않는다. */
    val uploaded: Boolean = false,
    val done: ProofSubmitted? = null,
) {
    val busy: Boolean get() = preparing || phase != SubmitPhase.IDLE
    val canSubmit: Boolean get() = media != null && !busy && done == null && ProofValidation.validateText(text) == null
}

/**
 * 인증 제출: 미디어 선택 → 업로드(POST /uploads) → 제출(POST /missions/{id}/proofs).
 * - 업로드 URL을 보관해 제출만 실패하면 파일을 다시 올리지 않는다(미디어를 바꾸면 폐기).
 * - 제출 응답을 받지 못하면 성공 여부를 추측하지 않고 미션을 다시 조회해 내 인증이 있는지 확인한다. 확인이 안 되면 자동 재전송하지 않는다.
 * - 진행 중·성공 후 submit은 무시한다. 계정이 바뀌면 이전 계정의 선택 미디어·결과를 버린다.
 */
class ProofSubmitViewModel(
    private val missionId: Long,
    private val proofs: ProofRepository,
    private val uploads: UploadRepository,
    private val missions: MissionRepository,
    private val preparer: ProofMediaPreparer,
    session: StateFlow<SessionState>,
    private val myUserId: () -> Long? = { null },
    private val onChanged: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(ProofSubmitUiState(missionId, myUserId = myUserId()))
    val ui: StateFlow<ProofSubmitUiState> = _ui.asStateFlow()

    private var job: Job? = null
    private var generation = 0
    private var uploaded: UploadedMedia? = null

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
                    _ui.value.media?.let(preparer::discard)
                    uploaded = null
                    _ui.value = ProofSubmitUiState(missionId, myUserId = userId)
                }
        }
    }

    fun onTextChange(v: String) = _ui.update { it.copy(text = v, textError = ProofValidation.validateText(v), error = null) }

    /** 사진 선택기·카메라가 돌려준 URI. 진행 중에는 무시한다. */
    fun onMediaSelected(source: String) {
        val s = _ui.value
        if (s.busy || s.done != null) return
        val gen = generation
        _ui.update { it.copy(preparing = true, mediaError = null, error = null) }
        job = viewModelScope.launch {
            val r = preparer.prepare(source)
            if (gen != generation) {
                (r as? PrepareResult.Ready)?.let { preparer.discard(it.media) }
                return@launch
            }
            when (r) {
                is PrepareResult.Ready -> {
                    _ui.value.media?.let(preparer::discard)
                    uploaded = null
                    _ui.update { it.copy(media = r.media, preparing = false, uploaded = false, outcomeUnknown = false) }
                }
                is PrepareResult.Rejected -> _ui.update { it.copy(preparing = false, mediaError = r.message) }
            }
        }
    }

    /** 선택 취소. 서버로 보내는 중에는 막는다. */
    fun clearMedia() {
        val s = _ui.value
        if (s.phase != SubmitPhase.IDLE) return
        s.media?.let(preparer::discard)
        uploaded = null
        _ui.update { it.copy(media = null, uploaded = false, mediaError = null, error = null) }
    }

    /** 업로드 중에만 취소할 수 있다. 제출 요청은 결과가 모호해지므로 취소하지 않는다. */
    fun cancelUpload() {
        if (_ui.value.phase != SubmitPhase.UPLOADING) return
        generation++
        job?.cancel()
        _ui.update { it.copy(phase = SubmitPhase.IDLE, error = "업로드를 취소했어요.") }
    }

    /** 카메라 앱이 없는 등 선택 단계의 안내. */
    fun onMediaError(message: String) = _ui.update { it.copy(mediaError = message) }

    fun consumeDone() = _ui.update { it.copy(done = null) }

    fun submit() {
        val s = _ui.value
        if (s.busy || s.done != null) return
        val media = s.media
        if (media == null) {
            _ui.update { it.copy(mediaError = "사진이나 영상을 선택해 주세요.") }
            return
        }
        ProofValidation.validateText(s.text)?.let { e ->
            _ui.update { it.copy(textError = e) }
            return
        }
        val gen = generation
        // 코루틴이 시작되기 전에 busy로 바꿔 연속 탭이 두 번째 요청을 만들지 못하게 한다.
        _ui.update { it.copy(error = null, mediaError = null, phase = if (s.outcomeUnknown) SubmitPhase.CHECKING else SubmitPhase.UPLOADING) }
        job = viewModelScope.launch {
            if (s.outcomeUnknown) {
                when (val found = existingProof()) {
                    is Existing.Found -> return@launch finish(gen, found.proofId, recovered = true)
                    Existing.Unknown -> return@launch fail(gen, unknown = true)
                    Existing.None -> Unit
                }
            }
            val up = uploaded ?: run {
                _ui.update { it.copy(phase = SubmitPhase.UPLOADING) }
                when (val r = uploads.uploadProofMedia(media.file, media.mime)) {
                    is ApiResult.Success -> r.value.also { if (gen == generation) { uploaded = it; _ui.update { s2 -> s2.copy(uploaded = true) } } }
                    is ApiResult.Failure -> return@launch fail(gen, message = ProofErrorMessages.forApi(r.error))
                }
            }
            if (gen != generation) return@launch
            _ui.update { it.copy(phase = SubmitPhase.SUBMITTING) }
            when (val r = proofs.submit(missionId, up, s.text)) {
                is ApiResult.Success -> finish(gen, r.value.proofId, recovered = false)
                is ApiResult.Failure -> {
                    val e = r.error
                    if (e is ApiException.Http) {
                        if (e.code == ApiErrorCode.PROOF_ALREADY_SUBMITTED || e.code == ApiErrorCode.MISSION_EXPIRED) runCatching { onChanged() }
                        fail(gen, message = ProofErrorMessages.forApi(e))
                    } else {
                        _ui.update { it.copy(phase = SubmitPhase.CHECKING) }
                        when (val found = existingProof()) {
                            is Existing.Found -> finish(gen, found.proofId, recovered = true)
                            Existing.None -> fail(gen, message = "인증이 전송되지 않았어요. 네트워크를 확인하고 다시 눌러 주세요.")
                            Existing.Unknown -> fail(gen, unknown = true)
                        }
                    }
                }
            }
        }
    }

    private sealed interface Existing {
        data class Found(val proofId: Long?) : Existing
        data object None : Existing
        data object Unknown : Existing
    }

    /** 서버의 내 인증(대기·승인)을 확인한다. 확인 자체가 실패하면 Unknown. */
    private suspend fun existingProof(): Existing = when (val r = missions.getMission(missionId)) {
        is ApiResult.Success -> r.value.mission.let { m ->
            if (m.myProofStatus == Mission.PROOF_PENDING || m.myProofStatus == Mission.PROOF_APPROVED) Existing.Found(m.myProofId) else Existing.None
        }
        is ApiResult.Failure -> Existing.Unknown
    }

    private fun fail(gen: Int, message: String? = null, unknown: Boolean = false) {
        if (gen != generation) return
        _ui.update {
            it.copy(
                phase = SubmitPhase.IDLE,
                error = if (unknown) PROOF_OUTCOME_UNKNOWN_MESSAGE else message,
                outcomeUnknown = unknown,
            )
        }
    }

    private fun finish(gen: Int, proofId: Long?, recovered: Boolean) {
        if (gen != generation) return
        _ui.value.media?.let(preparer::discard)
        uploaded = null
        _ui.update { it.copy(phase = SubmitPhase.IDLE, media = null, uploaded = false, outcomeUnknown = false, done = ProofSubmitted(proofId, recovered)) }
        runCatching { onChanged() }
    }

    override fun onCleared() {
        _ui.value.media?.let(preparer::discard)
    }

    class Factory(
        private val missionId: Long,
        private val container: AppContainer,
        private val preparer: ProofMediaPreparer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProofSubmitViewModel(
            missionId, container.proofRepository, container.uploadRepository, container.missionRepository, preparer,
            container.session.state,
            myUserId = { (container.session.state.value as? SessionState.Authenticated)?.profile?.userId },
            onChanged = { container.proofSync.notifyChanged() },
        ) as T
    }
}
