package com.dailyquest.app.ui.decor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.decor.DecorErrorMessages
import com.dailyquest.app.data.decor.DecorRepository
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.RoomDecor
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 방 화면의 "꾸민 방" 상태 (STEP 10-5).
 * @property decor 마지막으로 받은 꾸미기. 다시 받다가 실패해도 이전 모습을 계속 보여준다.
 */
data class RoomDecorUiState(
    val isMember: Boolean = false,
    val loading: Boolean = false,
    val decor: RoomDecor? = null,
    val error: String? = null,
) {
    /** 그릴 방 그림. 한 번도 저장하지 않았거나 비멤버면 기본 방. */
    val backgroundUrl: String get() = decor?.background?.imageUrl ?: DecorRepository.DEFAULT_ROOM_IMAGE_URL
}

class RoomDecorViewModel(
    private val roomId: Long,
    private val load: suspend (roomId: Long) -> ApiResult<RoomDecor>,
) : ViewModel() {
    private val _ui = MutableStateFlow(RoomDecorUiState())
    val ui: StateFlow<RoomDecorUiState> = _ui.asStateFlow()
    private var job: Job? = null

    /**
     * 멤버 여부가 정해지거나 바뀔 때 부른다(방에 참여한 직후 등).
     * 꾸미기 API는 멤버 전용이라(비멤버는 403) 비멤버면 부르지 않고 기본 방만 보여준다.
     */
    fun setMember(isMember: Boolean) {
        val wasMember = _ui.value.isMember
        _ui.update { it.copy(isMember = isMember) }
        if (!isMember) {
            job?.cancel()
            _ui.value = RoomDecorUiState(isMember = false)
        } else if (!wasMember || _ui.value.decor == null) {
            refresh()
        }
    }

    fun refresh() {
        if (!_ui.value.isMember) return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            when (val r = load(roomId)) {
                is ApiResult.Success -> _ui.update { it.copy(loading = false, decor = r.value, error = null) }
                is ApiResult.Failure -> _ui.update { it.copy(loading = false, error = DecorErrorMessages.forApi(r.error)) }
            }
        }
    }

    class Factory(
        private val container: AppContainer,
        private val roomId: Long,
        private val catalog: RoomCatalog,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomDecorViewModel(roomId) { container.decorRepository.load(it, catalog) } as T
    }
}
