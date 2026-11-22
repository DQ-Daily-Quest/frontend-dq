package com.dailyquest.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.avatar.AvatarDefaults
import com.dailyquest.app.data.avatar.AvatarErrorMessages
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.MyAvatar
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterCreateUiState(
    val gender: AvatarGender = AvatarGender.MALE,
    val skin: AvatarDefaults.SkinTone = AvatarDefaults.SkinTone.IVORY,
    val saving: Boolean = false,
    val error: String? = null,
    /** 캐릭터를 만들었다. 화면이 다음으로 넘어간다. */
    val done: Boolean = false,
) {
    /** 고르는 동안 보여 줄 미리보기(앱에 둔 기본 한 벌). 실제 지급은 서버가 한다. */
    val previewParts: Map<AvatarPart, String> get() = AvatarDefaults.previewParts(gender, skin)
}

/**
 * 캐릭터 만들기 (STEP 9-9). 성별과 피부를 고르고 "시작하기"를 누르면 서버에 확정한다.
 * 성별은 **한 번 정하면 바꿀 수 없다**(서버 규칙).
 *
 * @param create 앱에서는 [com.dailyquest.app.data.avatar.MyAvatarRepository.createCharacter]
 */
class CharacterCreateViewModel(
    private val create: suspend (AvatarGender, AvatarDefaults.SkinTone) -> ApiResult<MyAvatar>,
) : ViewModel() {
    private val _ui = MutableStateFlow(CharacterCreateUiState())
    val ui: StateFlow<CharacterCreateUiState> = _ui.asStateFlow()

    fun selectGender(gender: AvatarGender) = _ui.update { if (it.saving) it else it.copy(gender = gender, error = null) }
    fun selectSkin(skin: AvatarDefaults.SkinTone) = _ui.update { if (it.saving) it else it.copy(skin = skin, error = null) }

    /** 저장 중이거나 이미 만들었으면 무시한다(연속 클릭으로 두 번 요청하지 않게). */
    fun submit() {
        val state = _ui.value
        if (state.saving || state.done) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = create(state.gender, state.skin)) {
                is ApiResult.Success -> _ui.update { it.copy(saving = false, done = true) }
                is ApiResult.Failure -> _ui.update { it.copy(saving = false, error = AvatarErrorMessages.forApi(result.error)) }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CharacterCreateViewModel(container.myAvatarRepository::createCharacter) as T
    }
}
