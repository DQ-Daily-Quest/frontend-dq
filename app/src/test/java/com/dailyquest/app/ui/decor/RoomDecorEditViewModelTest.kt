package com.dailyquest.app.ui.decor

import com.dailyquest.app.data.decor.AvailableFurniture
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.DecorChoices
import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.GridPlacement
import com.dailyquest.app.data.decor.KeptPlacement
import com.dailyquest.app.data.decor.PlacedFurniture
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.RoomDecor
import com.dailyquest.app.data.decor.RoomLayout
import com.dailyquest.app.data.decor.Rotation
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/** STEP 10-6 편집 상태: 불러오기, 가구 넣기(빈자리·개수 제한), 선택·옮기기·돌리기·빼기, 겹침 표시, 바뀜 여부. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDecorEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val catalog = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())
    private val room = DecorItem(138, "기본 방", "FLOOR", "room_16x16", "https://s3/room.png")
    private fun item(id: Long, code: String) = DecorItem(id, code, "DESK", code, "https://s3/$code.png")
    private fun avail(id: Long, code: String?, owners: Int) =
        AvailableFurniture(item(id, code ?: "unknown"), code?.let(catalog::furniture), owners, 0, emptyList())

    private val desk = avail(142, "desk_oak", owners = 2)
    private val chair = avail(143, "chair_sage", owners = 1)
    private val unknown = avail(900, null, owners = 1)

    private var decor: ApiResult<RoomDecor> = ApiResult.Success(RoomDecor(10, null, emptyList()))
    private var choices: ApiResult<DecorChoices> = ApiResult.Success(DecorChoices(room, listOf(room), listOf(desk, chair, unknown)))

    private fun newVm() = RoomDecorEditViewModel(10, catalog, { decor }, { choices })

    @Test fun loadsSavedFurnitureAndChoices() = runTest(dispatcher) {
        decor = ApiResult.Success(RoomDecor(10, room, listOf(PlacedFurniture(desk.item, GridPlacement(desk.spec!!, 1, 1, Rotation.R90)))))
        val vm = newVm()
        advanceUntilIdle()
        val s = vm.ui.value
        assertFalse(s.loading)
        assertEquals(1, s.items.size)
        assertEquals(room, s.background)
        assertEquals(3, s.choices?.furniture?.size)
        assertFalse("불러온 직후에는 바뀐 것이 없다", s.dirty)
        assertEquals(1, s.remaining(desk))
    }

    @Test fun loadFailureShowsErrorAndRetryWorks() = runTest(dispatcher) {
        choices = ApiResult.Failure(ApiException.Network(IOException("offline")))
        val vm = newVm()
        advanceUntilIdle()
        assertNotNull(vm.ui.value.loadError)
        choices = ApiResult.Success(DecorChoices(room, listOf(room), listOf(desk)))
        vm.load()
        advanceUntilIdle()
        assertNull(vm.ui.value.loadError)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun addPlacesNearMiddleSelectsItAndCountsDown() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        val s = vm.ui.value
        assertEquals(1, s.items.size)
        assertEquals(s.items[0].key, s.selectedKey)
        assertTrue(s.items[0].placement.gridX in 6..8)
        assertEquals(1, s.remaining(desk))
        assertTrue(s.dirty)
        assertEquals(emptySet<Long>(), s.problemKeys)
    }

    @Test fun cannotAddMoreThanOwnersHave() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(chair)
        vm.add(chair)
        assertEquals(1, vm.ui.value.items.size)
        assertEquals("의자는 멤버 1명만 가져서 1개까지", true, vm.ui.value.message?.contains("1개까지만"))
        assertEquals(0, vm.ui.value.remaining(chair))
    }

    @Test fun keptPlacementsCountTowardTheLimit() = runTest(dispatcher) {
        // 앱이 그릴 수 없는 위치(32칸 시절 값)에 의자가 이미 하나 있다 → 더 놓을 수 없다
        decor = ApiResult.Success(RoomDecor(10, room, emptyList(), kept = listOf(KeptPlacement(chair.item, 0.03125, 0.0, 0, 0, "칸에 맞지 않음"))))
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(0, vm.ui.value.remaining(chair))
        vm.add(chair)
        assertTrue(vm.ui.value.items.isEmpty())
    }

    @Test fun unknownFurnitureCannotBeAdded() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(unknown)
        assertTrue(vm.ui.value.items.isEmpty())
        assertTrue(vm.ui.value.message!!.contains("업데이트"))
    }

    @Test fun movingOntoAnotherFurnitureMarksBothThenMovingAwayClears() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.add(chair)
        val (d, c) = vm.ui.value.items
        vm.moveTo(c.key, Cell(d.placement.gridX, d.placement.gridY))
        assertEquals(setOf(d.key, c.key), vm.ui.value.problemKeys)
        vm.moveTo(c.key, Cell(0, 0))
        assertEquals(emptySet<Long>(), vm.ui.value.problemKeys)
        vm.moveTo(c.key, Cell(16, 3)) // 방 밖
        assertEquals(setOf(c.key), vm.ui.value.problemKeys)
    }

    @Test fun rotateTurnsAndPushesBackIntoRoom() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        val key = vm.ui.value.selectedKey!!
        vm.moveTo(key, Cell(14, 15)) // 2×1이 오른쪽 아래 끝에 딱 맞음
        vm.rotateSelected() // 90° → 1×2: 아래로 넘치므로 한 칸 위로
        val p = vm.ui.value.selected!!.placement
        assertEquals(Rotation.R90, p.rotation)
        assertEquals(14, p.gridX)
        assertEquals(14, p.gridY)
        assertTrue(RoomLayout.fitsInRoom(catalog.room, p))
        repeat(3) { vm.rotateSelected() }
        assertEquals(Rotation.R0, vm.ui.value.selected!!.placement.rotation)
    }

    @Test fun removeDeletesSelectedAndFreesCount() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(chair)
        assertEquals(0, vm.ui.value.remaining(chair))
        vm.removeSelected()
        assertTrue(vm.ui.value.items.isEmpty())
        assertNull(vm.ui.value.selectedKey)
        assertEquals(1, vm.ui.value.remaining(chair))
        assertFalse("넣었다 빼면 처음과 같다", vm.ui.value.dirty)
    }

    @Test fun selectAndDeselect() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.add(chair)
        val first = vm.ui.value.items[0].key
        vm.select(first)
        assertEquals(first, vm.ui.value.selectedKey)
        vm.select(null)
        assertNull(vm.ui.value.selected)
    }

    @Test fun movingBackToOriginalPlaceIsNotDirty() = runTest(dispatcher) {
        decor = ApiResult.Success(RoomDecor(10, room, listOf(PlacedFurniture(desk.item, GridPlacement(desk.spec!!, 3, 3, Rotation.R0)))))
        val vm = newVm()
        advanceUntilIdle()
        val key = vm.ui.value.items[0].key
        vm.moveTo(key, Cell(5, 5))
        assertTrue(vm.ui.value.dirty)
        vm.moveTo(key, Cell(3, 3))
        assertFalse(vm.ui.value.dirty)
    }
}
