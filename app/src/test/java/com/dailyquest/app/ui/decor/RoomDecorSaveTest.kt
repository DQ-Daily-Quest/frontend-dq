package com.dailyquest.app.ui.decor

import com.dailyquest.app.data.decor.AvailableFurniture
import com.dailyquest.app.data.decor.Cell
import com.dailyquest.app.data.decor.DecorChoices
import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.DecorUser
import com.dailyquest.app.data.decor.KeptPlacement
import com.dailyquest.app.data.decor.RoomCatalog
import com.dailyquest.app.data.decor.RoomDecor
import com.dailyquest.app.data.remote.ApiErrorCode
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** STEP 10-7 저장: 보내는 내용, 겹침이면 막기, 실패해도 편집 유지, 중복 저장 막기, 방 화면 다시 받기. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDecorSaveTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val catalog = RoomCatalog.parse(File("src/main/assets/" + RoomCatalog.ASSET_PATH).readText())
    private val defaultRoom = DecorItem(138, "기본 방", "FLOOR", "room_16x16", "https://s3/room.png")
    private fun item(id: Long, code: String) = DecorItem(id, code, "DESK", code, "https://s3/$code.png")
    private val desk = AvailableFurniture(item(142, "desk_oak"), catalog.furniture("desk_oak"), 2, 0, emptyList())
    private val chair = AvailableFurniture(item(143, "chair_sage"), catalog.furniture("chair_sage"), 1, 0, emptyList())
    private val kept = KeptPlacement(item(900, "new_2027"), 0.5, 0.5, 3, 0, "앱이 모르는 가구")

    private val saves = mutableListOf<Pair<RoomDecor, DecorItem?>>()
    private var gate: CompletableDeferred<Unit>? = null
    private var result: (RoomDecor) -> ApiResult<RoomDecor> = { sent ->
        ApiResult.Success(sent.copy(background = sent.background ?: defaultRoom, updatedBy = DecorUser(1, "나")))
    }

    private fun newVm() = RoomDecorEditViewModel(
        10, catalog,
        loadDecor = { ApiResult.Success(RoomDecor(10, null, emptyList(), kept = listOf(kept))) },
        loadChoices = { ApiResult.Success(DecorChoices(defaultRoom, listOf(defaultRoom), listOf(desk, chair))) },
        saveDecor = { decor, bg -> saves += decor to bg; gate?.await(); result(decor) },
    )

    @Test fun nothingChangedMeansSaveIsOff() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        assertFalse(vm.ui.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertTrue(saves.isEmpty())
    }

    @Test fun saveSendsLayoutKeptPlacementsAndDefaultBackground() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.add(chair)
        assertTrue(vm.ui.value.canSave)
        vm.save()
        advanceUntilIdle()

        val (sent, bg) = saves.single()
        assertEquals(10L, sent.roomId)
        assertNull("한 번도 저장 안 한 방", sent.background)
        assertEquals("배경이 없으면 기본 방을 보낸다", defaultRoom, bg)
        assertEquals(listOf(142L, 143L), sent.furniture.map { it.item.itemId })
        assertEquals("앱이 그릴 수 없는 배치도 그대로 보낸다", listOf(kept), sent.kept)

        val s = vm.ui.value
        assertTrue(s.saved)
        assertFalse("저장한 뒤에는 바뀐 것이 없다", s.dirty)
        assertFalse(s.saving)
        assertEquals(defaultRoom, s.background)
        assertEquals("방을 저장했어요.", s.message)
        assertNull(s.selectedKey)
    }

    @Test fun overlappingFurnitureBlocksSaveAndSelectsTheProblem() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.add(chair)
        val (d, c) = vm.ui.value.items
        vm.moveTo(c.key, Cell(d.placement.gridX, d.placement.gridY))
        vm.select(null)
        vm.save()
        advanceUntilIdle()
        assertTrue("서버로 보내지 않는다", saves.isEmpty())
        assertEquals("첫 번째 문제 가구를 선택해 보여 준다", d.key, vm.ui.value.selectedKey)
        assertTrue(vm.ui.value.message!!.contains("2개를 먼저 옮겨"))
    }

    @Test fun failureKeepsEditsAndShowsServerMessage() = runTest(dispatcher) {
        val msg = "오크 서랍 책상은(는) 멤버 보유 수(1)를 넘겨 배치할 수 없습니다."
        result = { ApiResult.Failure(ApiException.Http(403, ApiErrorCode.ITEM_NOT_OWNED_IN_ROOM, msg, "")) }
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.save()
        advanceUntilIdle()
        val s = vm.ui.value
        assertEquals(msg, s.message)
        assertFalse(s.saved)
        assertFalse(s.saving)
        assertTrue("편집한 내용은 그대로", s.dirty)
        assertEquals(1, s.items.size)
        assertTrue("다시 저장할 수 있다", s.canSave)
    }

    @Test fun cannotSaveTwiceWhileSaving() = runTest(dispatcher) {
        gate = CompletableDeferred()
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.ui.value.saving)
        assertFalse(vm.ui.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, saves.size)
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.ui.value.saved)
    }

    @Test fun editingAfterSaveIsDirtyAgain() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        vm.add(desk)
        vm.save()
        advanceUntilIdle()
        vm.consumeSaved()
        assertFalse(vm.ui.value.dirty)
        vm.add(chair)
        assertTrue(vm.ui.value.dirty)
    }

    @Test fun roomCardReloadsWhenShownAgainOnlyAfterFirstLoad() = runTest(dispatcher) {
        var loads = 0
        val card = RoomDecorViewModel(10) { loads++; ApiResult.Success(RoomDecor(10, null, emptyList())) }
        card.onResume() // 멤버 여부를 모를 때는 받지 않는다
        advanceUntilIdle()
        assertEquals(0, loads)
        card.setMember(true)
        advanceUntilIdle()
        assertEquals(1, loads)
        card.onResume() // 편집 화면에서 돌아옴
        advanceUntilIdle()
        assertEquals(2, loads)
    }
}
