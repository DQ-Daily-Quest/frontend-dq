package com.dailyquest.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.avatar.AvatarErrorMessages
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.MyAvatar
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 입어 보는 아이템 하나. [modelUrl]이 없으면 3D로 입혀 볼 수 없다. */
data class TryOnItem(val itemId: Long, val name: String, val part: AvatarPart, val modelUrl: String?)

data class MyAvatarUiState(
    val userId: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val avatar: MyAvatar? = null,
    /** 슬롯별로 입어 보는 아이템. 서버에는 저장하지 않는다. */
    val tryOn: Map<AvatarPart, TryOnItem> = emptyMap(),
) {
    /** [AvatarView]에 넘길 모습: 내가 입은 옷 위에 입어 보는 옷을 덮어쓴다(3D 모델이 있는 것만). */
    val previewParts: Map<AvatarPart, String>
        get() = (avatar?.modelParts ?: emptyMap()) + tryOn.mapNotNull { (part, item) -> item.modelUrl?.let { part to it } }

    val isTryingOn: Boolean get() = tryOn.isNotEmpty()
    fun isTryingOn(itemId: Long): Boolean = tryOn.values.any { it.itemId == itemId }
}

/**
 * 상점·꾸미기 화면의 내 아바타와 입어보기 상태 (STEP 9-7).
 * 로그인 계정이 바뀌면 이전 계정의 모습을 지우고 다시 불러온다([com.dailyquest.app.ui.shop.InventoryViewModel]과 같은 방식).
 *
 * @param load 내 아바타 불러오기. 앱에서는 [com.dailyquest.app.data.avatar.MyAvatarRepository.load]
 * @param sessionState 로그인 상태. 계정 전환을 감지한다.
 */
class MyAvatarViewModel(
    private val load: suspend () -> ApiResult<MyAvatar>,
    sessionState: StateFlow<SessionState>,
) : ViewModel() {
    private val _ui = MutableStateFlow(MyAvatarUiState())
    val ui: StateFlow<MyAvatarUiState> = _ui.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionState
                .map { (it as? SessionState.Authenticated)?.profile?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    loadJob?.cancel()
                    _ui.value = MyAvatarUiState(userId = userId)
                    if (userId != null) refresh()
                }
        }
    }

    fun refresh() {
        val userId = _ui.value.userId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val result = load()
            // 기다리는 사이 계정이 바뀌었으면 버린다.
            if (_ui.value.userId != userId) return@launch
            _ui.update {
                when (result) {
                    is ApiResult.Success -> it.copy(loading = false, avatar = result.value)
                    is ApiResult.Failure -> it.copy(loading = false, error = AvatarErrorMessages.forApi(result.error))
                }
            }
        }
    }

    /** 아이템을 입어 본다. 이미 그 아이템을 입어 보는 중이면 벗는다(같은 카드를 다시 누른 경우). */
    fun toggleTryOn(item: TryOnItem) = _ui.update { it.copy(tryOn = toggleTryOn(it.tryOn, item)) }

    /** 입어 보던 것을 모두 벗고 내가 입은 모습으로 돌아간다. */
    fun clearTryOn() = _ui.update { it.copy(tryOn = emptyMap()) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MyAvatarViewModel(container.myAvatarRepository::load, container.session.state) as T
    }
}

/** 같은 아이템이면 벗고, 아니면 그 슬롯을 이 아이템으로 바꾼다. 다른 슬롯은 그대로 둔다. */
internal fun toggleTryOn(current: Map<AvatarPart, TryOnItem>, item: TryOnItem): Map<AvatarPart, TryOnItem> =
    if (current[item.part]?.itemId == item.itemId) current - item.part else current + (item.part to item)
