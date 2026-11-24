package com.dailyquest.app.ui.decor

import com.dailyquest.app.data.decor.DecorItem
import com.dailyquest.app.data.decor.DecorRepository
import com.dailyquest.app.data.decor.DecorUser
import com.dailyquest.app.data.decor.RoomDecor
import com.dailyquest.app.data.remote.ApiErrorCode
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** STEP 10-5 방 화면의 "꾸민 방" 상태: 멤버만 조회, 실패해도 이전 모습 유지, 다시 시도. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDecorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val requests = mutableListOf<Long>()
    private var next: ApiResult<RoomDecor> = ApiResult.Success(RoomDecor(10, null, emptyList()))
    private fun vm() = RoomDecorViewModel(10) { requests += it; next }

    private val wall = DecorItem(200, "다른 방", "WALL", "wall_x", "https://s3/wall_x.png")
    private fun http(code: String, status: Int = 403) = ApiException.Http(status, code, null, "")

    @Test fun nonMemberDoesNotCallServerAndShowsDefaultRoom() = runTest(dispatcher) {
        val vm = vm()
        vm.setMember(false)
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), requests)
        assertEquals(DecorRepository.DEFAULT_ROOM_IMAGE_URL, vm.ui.value.backgroundUrl)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun memberLoadsDecorAndUsesSavedBackground() = runTest(dispatcher) {
        next = ApiResult.Success(RoomDecor(10, wall, emptyList(), updatedBy = DecorUser(1, "지원")))
        val vm = vm()
        vm.setMember(true)
        advanceUntilIdle()
        assertEquals(listOf(10L), requests)
        assertEquals("https://s3/wall_x.png", vm.ui.value.backgroundUrl)
        assertEquals("지원", vm.ui.value.decor?.updatedBy?.nickname)
    }

    @Test fun neverSavedRoomUsesDefaultRoomImage() = runTest(dispatcher) {
        val vm = vm()
        vm.setMember(true)
        advanceUntilIdle()
        assertNull(vm.ui.value.decor?.background)
        assertEquals(DecorRepository.DEFAULT_ROOM_IMAGE_URL, vm.ui.value.backgroundUrl)
    }

    @Test fun failureKeepsPreviousDecorAndRetryLoadsAgain() = runTest(dispatcher) {
        next = ApiResult.Success(RoomDecor(10, wall, emptyList()))
        val vm = vm()
        vm.setMember(true)
        advanceUntilIdle()

        next = ApiResult.Failure(ApiException.Network(IOException("offline")))
        vm.refresh()
        advanceUntilIdle()
        assertEquals("이전 모습을 계속 보여준다", wall, vm.ui.value.decor?.background)
        assertFalse(vm.ui.value.error.isNullOrBlank())

        next = ApiResult.Success(RoomDecor(10, null, emptyList()))
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.ui.value.error)
        assertEquals(3, requests.size)
    }

    @Test fun forbiddenShowsMemberOnlyMessage() = runTest(dispatcher) {
        next = ApiResult.Failure(http(ApiErrorCode.FORBIDDEN))
        val vm = vm()
        vm.setMember(true)
        advanceUntilIdle()
        assertEquals("이 방의 멤버만 꾸밀 수 있어요.", vm.ui.value.error)
    }

    @Test fun becomingMemberLoadsAndLeavingClears() = runTest(dispatcher) {
        val vm = vm()
        vm.setMember(false)
        advanceUntilIdle()
        vm.setMember(true) // 방에 참여한 직후
        advanceUntilIdle()
        assertEquals(1, requests.size)
        vm.setMember(true) // 같은 상태로 다시 불려도 또 부르지 않는다
        advanceUntilIdle()
        assertEquals(1, requests.size)
        vm.setMember(false) // 나갔거나 강퇴
        advanceUntilIdle()
        assertNull(vm.ui.value.decor)
    }
}
