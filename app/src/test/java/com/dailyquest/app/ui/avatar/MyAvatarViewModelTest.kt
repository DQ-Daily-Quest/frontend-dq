package com.dailyquest.app.ui.avatar

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.EquippedPart
import com.dailyquest.app.data.avatar.MyAvatar
import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException

/** STEP 9-7 상점 미리보기의 내 아바타·입어보기 상태. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyAvatarViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun profile(id: Long) = UserProfile(id, null, "u$id", 0, 0, 0, 0, 0)
    private fun avatarOf(vararg parts: Pair<AvatarPart, String>) = MyAvatar(
        AvatarGender.MALE,
        parts.mapIndexed { i, (part, url) -> part to EquippedPart(i.toLong(), part.name, url, null) }.toMap(),
    )

    private val boy = avatarOf(AvatarPart.SKIN to "u/body", AvatarPart.TOP to "u/top", AvatarPart.HAIR to "u/hair")
    private val hoodie = TryOnItem(21, "후드 티", AvatarPart.TOP, "u/hoodie")
    private val beret = TryOnItem(50, "카페 모자", AvatarPart.HAT, "u/beret")

    @Test fun `로그인하면 내 아바타를 불러온다`() = runTest(dispatcher) {
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(1)))
        var calls = 0
        val vm = MyAvatarViewModel({ calls++; ApiResult.Success(boy) }, session)

        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(boy, vm.ui.value.avatar)
        assertFalse(vm.ui.value.loading)
        assertEquals(boy.modelParts, vm.ui.value.previewParts)
    }

    @Test fun `로그인 전에는 불러오지 않는다`() = runTest(dispatcher) {
        var calls = 0
        val vm = MyAvatarViewModel({ calls++; ApiResult.Success(boy) }, MutableStateFlow(SessionState.Unauthenticated()))

        advanceUntilIdle()

        assertEquals(0, calls)
        assertNull(vm.ui.value.avatar)
    }

    @Test fun `불러오기 실패는 안내 문구로 바뀌고 다시 시도할 수 있다`() = runTest(dispatcher) {
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(1)))
        var fail = true
        val vm = MyAvatarViewModel(
            { if (fail) ApiResult.Failure(ApiException.Network(IOException("offline"))) else ApiResult.Success(boy) },
            session,
        )
        advanceUntilIdle()
        assertTrue(vm.ui.value.error != null)
        assertNull(vm.ui.value.avatar)

        fail = false
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.ui.value.error)
        assertEquals(boy, vm.ui.value.avatar)
    }

    @Test fun `입어 보면 그 슬롯만 덮어쓰고 나머지는 내 옷 그대로다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))))
        advanceUntilIdle()

        vm.toggleTryOn(hoodie)

        val parts = vm.ui.value.previewParts
        assertEquals("u/hoodie", parts[AvatarPart.TOP])
        assertEquals("u/body", parts[AvatarPart.SKIN])
        assertEquals("u/hair", parts[AvatarPart.HAIR])
        assertTrue(vm.ui.value.isTryingOn(21))
    }

    @Test fun `없던 슬롯(모자)을 입어 보면 슬롯이 늘어난다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))))
        advanceUntilIdle()

        vm.toggleTryOn(beret)

        assertEquals(4, vm.ui.value.previewParts.size)
        assertEquals("u/beret", vm.ui.value.previewParts[AvatarPart.HAT])
    }

    @Test fun `같은 아이템을 다시 누르면 벗는다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))))
        advanceUntilIdle()

        vm.toggleTryOn(hoodie)
        vm.toggleTryOn(hoodie)

        assertFalse(vm.ui.value.isTryingOn)
        assertEquals(boy.modelParts, vm.ui.value.previewParts)
    }

    @Test fun `같은 슬롯의 다른 아이템을 누르면 바꿔 입는다`() {
        val other = TryOnItem(22, "셔츠", AvatarPart.TOP, "u/shirt")

        val result = toggleTryOn(toggleTryOn(emptyMap(), hoodie), other)

        assertEquals(mapOf(AvatarPart.TOP to other), result)
    }

    @Test fun `원래대로를 누르면 입어 보던 것을 모두 벗는다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))))
        advanceUntilIdle()
        vm.toggleTryOn(hoodie)
        vm.toggleTryOn(beret)

        vm.clearTryOn()

        assertFalse(vm.ui.value.isTryingOn)
        assertEquals(boy.modelParts, vm.ui.value.previewParts)
    }

    @Test fun `3D 모델이 없는 아이템은 이름은 보이지만 모습은 바뀌지 않는다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))))
        advanceUntilIdle()

        vm.toggleTryOn(TryOnItem(99, "2D 전용 모자", AvatarPart.HAT, null))

        assertTrue(vm.ui.value.isTryingOn(99))
        assertEquals(boy.modelParts, vm.ui.value.previewParts)
    }

    @Test fun `다른 계정으로 바뀌면 이전 계정의 모습과 입어보기를 지우고 다시 불러온다`() = runTest(dispatcher) {
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(1)))
        val girl = avatarOf(AvatarPart.SKIN to "u/girl-body")
        val vm = MyAvatarViewModel({ ApiResult.Success(if ((session.value as SessionState.Authenticated).profile.userId == 1L) boy else girl) }, session)
        advanceUntilIdle()
        vm.toggleTryOn(hoodie)

        session.value = SessionState.Authenticated(profile(2))
        advanceUntilIdle()

        assertEquals(girl, vm.ui.value.avatar)
        assertFalse(vm.ui.value.isTryingOn)
    }

    @Test fun `불러오는 사이 계정이 바뀌면 이전 계정의 응답은 버린다`() = runTest(dispatcher) {
        val session = MutableStateFlow<SessionState>(SessionState.Authenticated(profile(1)))
        val firstResponse = CompletableDeferred<ApiResult<MyAvatar>>()
        val girl = avatarOf(AvatarPart.SKIN to "u/girl-body")
        val vm = MyAvatarViewModel(
            { if ((session.value as? SessionState.Authenticated)?.profile?.userId == 1L) firstResponse.await() else ApiResult.Success(girl) },
            session,
        )
        advanceUntilIdle()

        session.value = SessionState.Authenticated(profile(2))
        advanceUntilIdle()
        firstResponse.complete(ApiResult.Success(boy))
        advanceUntilIdle()

        assertEquals(girl, vm.ui.value.avatar)
    }
}
