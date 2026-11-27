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
    /** 착용 저장 중 (STEP 9-8). 저장 중에는 다른 저장을 받지 않는다. */
    val saving: Boolean = false,
    /** 한 번 보여 줄 안내(토스트). 보여 준 뒤 [MyAvatarViewModel.consumeMessage]로 지운다. */
    val message: String? = null,
    /** [message]가 실패 안내인지(빨강) 성공 안내인지(초록). QA 디자인 5단계. */
    val messageIsError: Boolean = false,
) {
    /** [AvatarView]에 넘길 모습: 내가 입은 옷 위에 입어 보는 옷을 덮어쓴다(3D 모델이 있는 것만). */
    val previewParts: Map<AvatarPart, String>
        get() = (avatar?.modelParts ?: emptyMap()) + tryOn.mapNotNull { (part, item) -> item.modelUrl?.let { part to it } }

    val isTryingOn: Boolean get() = tryOn.isNotEmpty()
    fun isTryingOn(itemId: Long): Boolean = tryOn.values.any { it.itemId == itemId }

    /** 지금 실제로 입고 있는(서버에 저장된) 아이템인지 */
    fun isEquipped(item: TryOnItem): Boolean = avatar?.itemId(item.part) == item.itemId
}

/**
 * 상점·꾸미기 화면의 내 아바타와 입어보기 상태 (STEP 9-7).
 * 로그인 계정이 바뀌면 이전 계정의 모습을 지우고 다시 불러온다([com.dailyquest.app.ui.shop.InventoryViewModel]과 같은 방식).
 *
 * 화면을 다시 열면 마지막으로 받은 아바타([cached])로 **바로** 그리고, 서버에서는 뒤에서 새로 받아 바뀐 것만 반영한다(아바타 속도 1단계).
 * 새로 받기가 실패하면 마지막 모습을 그대로 둔다(미리보기는 아바타가 있으면 오류 화면 대신 그 모습을 보여준다). 기억한 것이 없을 때만 오류 화면이 뜬다.
 *
 * @param load 내 아바타 불러오기. 앱에서는 [com.dailyquest.app.data.avatar.MyAvatarRepository.load]
 * @param sessionState 로그인 상태. 계정 전환을 감지한다.
 * @param cached 계정의 마지막 아바타. 앱에서는 [com.dailyquest.app.data.avatar.MyAvatarRepository.cached]
 */
class MyAvatarViewModel(
    private val load: suspend () -> ApiResult<MyAvatar>,
    sessionState: StateFlow<SessionState>,
    private val saveEquip: suspend (Map<AvatarPart, Long?>) -> ApiResult<MyAvatar> = { error("착용 저장이 연결되지 않았습니다") },
    private val cached: (Long) -> MyAvatar? = { null },
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
                    _ui.value = MyAvatarUiState(userId = userId, avatar = userId?.let(cached))
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

    /**
     * 보유한 아이템을 실제로 착용한다 (STEP 9-8). 미리보기는 이미 입어 본 모습이므로 그대로 두고 서버에 저장한다.
     * - 성공: 서버가 돌려준 착용으로 바꾸고, 그 슬롯의 입어보기는 끝낸다(이제 진짜 입은 옷이다).
     * - 실패: 그 슬롯의 입어보기를 벗겨 원래 입던 모습으로 되돌리고 이유를 알린다.
     */
    fun equip(item: TryOnItem) = save(mapOf(item.part to item.itemId), item.part, "착용했어요")

    /**
     * 슬롯을 비운다. **모자만** 벗을 수 있다. 서버는 모든 슬롯 해제를 받아 주지만, 피부·눈·머리·옷이 없는 캐릭터가
     * 되지 않도록 앱에서 막는다(백엔드 v0.4 문서 "남은 확인 사항 1").
     */
    fun takeOff(part: AvatarPart) {
        if (part !in REMOVABLE_PARTS) {
            _ui.update { it.copy(message = "모자만 벗을 수 있어요.", messageIsError = true) }
            return
        }
        save(mapOf(part to null), part, "벗었어요")
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun save(changes: Map<AvatarPart, Long?>, part: AvatarPart, successMessage: String) {
        val state = _ui.value
        val userId = state.userId ?: return
        if (state.saving || state.avatar == null) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = saveEquip(changes)
            if (_ui.value.userId != userId) return@launch
            _ui.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, avatar = result.value, tryOn = it.tryOn - part, message = successMessage, messageIsError = false)
                    is ApiResult.Failure -> it.copy(saving = false, tryOn = it.tryOn - part, message = AvatarErrorMessages.forApi(result.error), messageIsError = true)
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MyAvatarViewModel(
                container.myAvatarRepository::load, container.session.state, container.myAvatarRepository::equip,
                container.myAvatarRepository::cached,
            ) as T
    }
}

/** 비울 수 있는 슬롯. 나머지 6개는 항상 무언가를 입고 있어야 한다. */
val REMOVABLE_PARTS: Set<AvatarPart> = setOf(AvatarPart.HAT)

/** 같은 아이템이면 벗고, 아니면 그 슬롯을 이 아이템으로 바꾼다. 다른 슬롯은 그대로 둔다. */
internal fun toggleTryOn(current: Map<AvatarPart, TryOnItem>, item: TryOnItem): Map<AvatarPart, TryOnItem> =
    if (current[item.part]?.itemId == item.itemId) current - item.part else current + (item.part to item)
