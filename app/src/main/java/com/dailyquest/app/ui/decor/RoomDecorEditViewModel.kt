package com.dailyquest.app.ui.decor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailyquest.app.data.auth.AppContainer
import com.dailyquest.app.data.decor.AvailableFurniture
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.DecorChoices
import com.dailyquest.app.data.decor.DecorErrorMessages
import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.GridPlacement
import com.dailyquest.app.data.decor.KeptPlacement
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.RoomDecor
import com.dailyquest.app.data.decor.RoomLayout
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 편집 중인 가구 하나. [key]는 화면 안에서만 쓰는 번호다(같은 가구를 여러 개 놓아도 구분). */
data class EditItem(val key: Long, val item: DecorItem, val placement: GridPlacement)

/** 꾸미기 편집 화면 상태 (STEP 10-6). */
data class DecorEditUiState(
    val loading: Boolean = true,
    /** 처음 불러오기 실패. 화면 전체에 다시 시도를 보여준다. */
    val loadError: String? = null,
    val roomId: Long = 0,
    val background: DecorItem? = null,
    val items: List<EditItem> = emptyList(),
    /** 앱이 그릴 수 없어 그대로 두는 배치(3단계). 개수 제한 계산에 포함한다. */
    val kept: List<KeptPlacement> = emptyList(),
    val choices: DecorChoices? = null,
    val selectedKey: Long? = null,
    /** 놓을 수 없는 가구(겹침·방 밖)의 [EditItem.key]. */
    val problemKeys: Set<Long> = emptySet(),
    /** 불러온 뒤 바뀐 것이 있는지. 저장 버튼·나가기 확인에 쓴다(7단계). */
    val dirty: Boolean = false,
    /** 한 번 보여 줄 안내. 보여 준 뒤 [RoomDecorEditViewModel.consumeMessage]로 지운다. */
    val message: String? = null,
) {
    val selected: EditItem? get() = items.firstOrNull { it.key == selectedKey }

    /** [RoomView]에 넘길 가구 목록 (순서 = [items] 순서). */
    val furniture: List<PlacedFurniture> get() = items.map { PlacedFurniture(it.item, it.placement) }

    /** 같은 가구를 지금 몇 개 놓았는지(그릴 수 없는 보관 배치 포함). */
    fun placedCount(itemId: Long): Int = items.count { it.item.itemId == itemId } + kept.count { it.item.itemId == itemId }

    /** 더 놓을 수 있는 개수. 멤버 중 가진 사람 수까지 놓을 수 있다(서버 규칙). */
    fun remaining(f: AvailableFurniture): Int = (f.ownerCount - placedCount(f.item.itemId)).coerceAtLeast(0)
}

class RoomDecorEditViewModel(
    private val roomId: Long,
    private val catalog: RoomCatalog,
    private val loadDecor: suspend (Long) -> ApiResult<RoomDecor>,
    private val loadChoices: suspend (Long) -> ApiResult<DecorChoices>,
) : ViewModel() {
    private val _ui = MutableStateFlow(DecorEditUiState(roomId = roomId))
    val ui: StateFlow<DecorEditUiState> = _ui.asStateFlow()
    private var nextKey = 1L
    /** 불러온 직후 배치. 바뀐 것이 있는지 비교할 때 쓴다. */
    private var original: List<Snapshot> = emptyList()

    private data class Snapshot(val itemId: Long, val x: Int, val y: Int, val rotation: Int)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, loadError = null) }
            val (decor, choices) = coroutineScope {
                val d = async { loadDecor(roomId) }
                val c = async { loadChoices(roomId) }
                d.await() to c.await()
            }
            when {
                decor is ApiResult.Failure -> _ui.update { it.copy(loading = false, loadError = DecorErrorMessages.forApi(decor.error)) }
                choices is ApiResult.Failure -> _ui.update { it.copy(loading = false, loadError = DecorErrorMessages.forApi(choices.error)) }
                decor is ApiResult.Success && choices is ApiResult.Success -> {
                    val items = decor.value.furniture.map { EditItem(nextKey++, it.item, it.placement) }
                    original = items.snapshot()
                    _ui.value = DecorEditUiState(
                        loading = false,
                        roomId = roomId,
                        background = decor.value.background,
                        items = items,
                        kept = decor.value.kept,
                        choices = choices.value,
                    ).withChecks()
                }
            }
        }
    }

    /** 가구 목록에서 하나를 골라 방 가운데 가까운 빈자리에 놓고 선택한다. */
    fun add(f: AvailableFurniture) {
        val s = _ui.value
        val spec = f.spec
        when {
            spec == null -> message("앱을 업데이트해야 놓을 수 있는 가구예요.")
            s.remaining(f) <= 0 -> message("${f.item.name}은(는) 방 멤버가 가진 ${f.ownerCount}개까지만 놓을 수 있어요.")
            else -> {
                val spot = RoomLayout.findFreeSpot(catalog.room, s.items.map { it.placement }, spec)
                if (spot == null) {
                    message("빈 자리가 없어요. 다른 가구를 빼거나 옮겨 주세요.")
                } else {
                    val item = EditItem(nextKey++, f.item, spot)
                    update { it.copy(items = it.items + item, selectedKey = item.key) }
                }
            }
        }
    }

    /** 가구를 누르면 선택, 빈 곳을 누르면 선택 해제(null). */
    fun select(key: Long?) {
        if (_ui.value.selectedKey != key) _ui.update { it.copy(selectedKey = key) }
    }

    /** 끄는 중: 시작 칸을 바꾼다. 방 밖·겹침이어도 일단 옮기고 빨간색으로 보여준다. */
    fun moveTo(key: Long, start: Cell) {
        val target = _ui.value.items.firstOrNull { it.key == key } ?: return
        if (target.placement.gridX == start.x && target.placement.gridY == start.y) return
        update { s -> s.copy(items = s.items.map { if (it.key == key) it.copy(placement = it.placement.copy(gridX = start.x, gridY = start.y)) else it }) }
    }

    /** 선택한 가구를 90도 돌린다. 돌려서 방 밖으로 나가면 방 안으로 밀어 넣는다. */
    fun rotateSelected() {
        val sel = _ui.value.selected ?: return
        val turned = sel.placement.copy(rotation = sel.placement.rotation.next())
        val room = catalog.room
        val fitted = turned.copy(
            gridX = turned.gridX.coerceIn(0, (room.columns - turned.width).coerceAtLeast(0)),
            gridY = turned.gridY.coerceIn(0, (room.rows - turned.depth).coerceAtLeast(0)),
        )
        update { s -> s.copy(items = s.items.map { if (it.key == sel.key) it.copy(placement = fitted) else it }) }
    }

    fun removeSelected() {
        val key = _ui.value.selectedKey ?: return
        update { s -> s.copy(items = s.items.filterNot { it.key == key }, selectedKey = null) }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun message(text: String) = _ui.update { it.copy(message = text) }

    private fun update(change: (DecorEditUiState) -> DecorEditUiState) = _ui.update { change(it).withChecks() }

    /** 겹침·방 밖 검사와 바뀜 여부를 다시 계산한다. */
    private fun DecorEditUiState.withChecks(): DecorEditUiState {
        val bad = RoomLayout.problems(catalog.room, items.map { it.placement })
        return copy(
            problemKeys = bad.mapTo(HashSet()) { items[it].key },
            dirty = items.snapshot().sortedWith(SNAPSHOT_ORDER) != original.sortedWith(SNAPSHOT_ORDER),
        )
    }

    private fun List<EditItem>.snapshot() = map { Snapshot(it.item.itemId, it.placement.gridX, it.placement.gridY, it.placement.rotation.degrees) }

    class Factory(
        private val container: AppContainer,
        private val roomId: Long,
        private val catalog: RoomCatalog,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RoomDecorEditViewModel(
            roomId, catalog,
            loadDecor = { container.decorRepository.load(it, catalog) },
            loadChoices = { container.decorRepository.available(it, catalog) },
        ) as T
    }

    private companion object {
        val SNAPSHOT_ORDER = compareBy<Snapshot>({ it.itemId }, { it.x }, { it.y }, { it.rotation })
    }
}
