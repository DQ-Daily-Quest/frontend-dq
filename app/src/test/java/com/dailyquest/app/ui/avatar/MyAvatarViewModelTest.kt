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

    // ── STEP 9-8 착용 저장 ──────────────────────────────────────────────────────────────

    private fun vmWithSave(save: suspend (Map<AvatarPart, Long?>) -> ApiResult<MyAvatar>) =
        MyAvatarViewModel({ ApiResult.Success(boy) }, MutableStateFlow(SessionState.Authenticated(profile(1))), save)

    @Test fun `착용하면 바꾼 슬롯만 저장하고 서버 응답으로 바꾸며 입어보기는 끝난다`() = runTest(dispatcher) {
        val sent = mutableListOf<Map<AvatarPart, Long?>>()
        val afterSave = avatarOf(AvatarPart.SKIN to "u/body", AvatarPart.TOP to "u/hoodie", AvatarPart.HAIR to "u/hair")
        val vm = vmWithSave { sent += it; ApiResult.Success(afterSave) }
        advanceUntilIdle()
        vm.toggleTryOn(hoodie)

        vm.equip(hoodie)
        assertTrue(vm.ui.value.saving)
        advanceUntilIdle()

        assertEquals(listOf(mapOf(AvatarPart.TOP to 21L)), sent)
        assertFalse(vm.ui.value.saving)
        assertEquals(afterSave, vm.ui.value.avatar)
        assertFalse(vm.ui.value.isTryingOn)
        assertEquals("u/hoodie", vm.ui.value.previewParts[AvatarPart.TOP])
        assertEquals("착용했어요", vm.ui.value.message)
    }

    @Test fun `착용 저장이 실패하면 입어보기를 벗겨 원래 모습으로 되돌리고 이유를 알린다`() = runTest(dispatcher) {
        val vm = vmWithSave {
            ApiResult.Failure(com.dailyquest.app.data.remote.ApiException.Http(409, "ITEM_GENDER_MISMATCH", null, ""))
        }
        advanceUntilIdle()
        vm.toggleTryOn(hoodie)

        vm.equip(hoodie)
        advanceUntilIdle()

        assertEquals(boy, vm.ui.value.avatar)
        assertEquals(boy.modelParts, vm.ui.value.previewParts)
        assertFalse(vm.ui.value.saving)
        assertEquals("내 캐릭터가 입을 수 없는 아이템이에요.", vm.ui.value.message)
    }

    @Test fun `실패해도 다른 슬롯의 입어보기는 그대로다`() = runTest(dispatcher) {
        val vm = vmWithSave { ApiResult.Failure(ApiException.Network(IOException("offline"))) }
        advanceUntilIdle()
        vm.toggleTryOn(hoodie)
        vm.toggleTryOn(beret)

        vm.equip(hoodie)
        advanceUntilIdle()

        assertFalse(vm.ui.value.isTryingOn(21))
        assertTrue(vm.ui.value.isTryingOn(50))
    }

    @Test fun `모자는 null로 벗는다`() = runTest(dispatcher) {
        val sent = mutableListOf<Map<AvatarPart, Long?>>()
        val vm = vmWithSave { sent += it; ApiResult.Success(boy) }
        advanceUntilIdle()

        vm.takeOff(AvatarPart.HAT)
        advanceUntilIdle()

        assertEquals(listOf(mapOf<AvatarPart, Long?>(AvatarPart.HAT to null)), sent)
        assertEquals("벗었어요", vm.ui.value.message)
    }

    @Test fun `모자 외 슬롯은 벗을 수 없고 서버에 요청하지 않는다`() = runTest(dispatcher) {
        var calls = 0
        val vm = vmWithSave { calls++; ApiResult.Success(boy) }
        advanceUntilIdle()

        AvatarPart.entries.filter { it != AvatarPart.HAT }.forEach { vm.takeOff(it) }
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals("모자만 벗을 수 있어요.", vm.ui.value.message)
    }

    @Test fun `저장 중에는 다른 저장을 받지 않는다`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<MyAvatar>>()
        var calls = 0
        val vm = vmWithSave { calls++; gate.await() }
        advanceUntilIdle()

        vm.equip(hoodie)
        vm.equip(beret)
        advanceUntilIdle()
        assertEquals(1, calls)

        gate.complete(ApiResult.Success(boy))
        advanceUntilIdle()
        assertFalse(vm.ui.value.saving)
    }

    @Test fun `아바타를 불러오기 전에는 저장하지 않는다`() = runTest(dispatcher) {
        var calls = 0
        val vm = MyAvatarViewModel(
            { ApiResult.Failure(ApiException.Network(IOException("offline"))) },
            MutableStateFlow(SessionState.Authenticated(profile(1))),
            saveEquip = { calls++; ApiResult.Success(boy) },
        )
        advanceUntilIdle()

        vm.equip(hoodie)
        advanceUntilIdle()

        assertEquals(0, calls)
    }

    @Test fun `isEquipped는 서버에 저장된 착용과 비교한다`() = runTest(dispatcher) {
        val vm = vmWithSave { ApiResult.Success(boy) }
        advanceUntilIdle()

        // boy의 TOP은 itemId 1 (avatarOf가 순서대로 0,1,2를 준다)
        assertTrue(vm.ui.value.isEquipped(TryOnItem(1, "기본 상의", AvatarPart.TOP, "u/top")))
        assertFalse(vm.ui.value.isEquipped(hoodie))
    }

    @Test fun `안내를 보여 준 뒤에는 지운다`() = runTest(dispatcher) {
        val vm = vmWithSave { ApiResult.Success(boy) }
        advanceUntilIdle()
        vm.takeOff(AvatarPart.TOP)

        vm.consumeMessage()

        assertNull(vm.ui.value.message)
    }

    // ---- 아바타 속도 1단계: 마지막 아바타로 바로 시작 ----

    @Test fun `기억해 둔 아바타가 있으면 서버 응답 전에 바로 보이고, 응답이 오면 바꾼다`() = runTest(dispatcher) {
        val withHat = avatarOf(AvatarPart.SKIN to "u/body", AvatarPart.TOP to "u/top", AvatarPart.HAIR to "u/hair", AvatarPart.HAT to "u/hat")
        val reply = CompletableDeferred<ApiResult<MyAvatar>>()
        val vm = MyAvatarViewModel({ reply.await() }, MutableStateFlow(SessionState.Authenticated(profile(1))), cached = { id -> if (id == 1L) boy else null })

        dispatcher.scheduler.runCurrent()
        assertEquals("서버를 기다리지 않고 바로", boy, vm.ui.value.avatar)
        assertTrue("뒤에서는 새로 받는 중", vm.ui.value.loading)

        reply.complete(ApiResult.Success(withHat))
        advanceUntilIdle()
        assertEquals(withHat, vm.ui.value.avatar)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun `새로 받기가 실패해도 기억해 둔 모습은 그대로 둔다`() = runTest(dispatcher) {
        val vm = MyAvatarViewModel({ ApiResult.Failure(ApiException.Network(IOException("offline"))) }, MutableStateFlow(SessionState.Authenticated(profile(1))), cached = { boy })
        advanceUntilIdle()
        assertEquals(boy, vm.ui.value.avatar)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun `다른 계정이 기억한 아바타는 쓰지 않는다`() = runTest(dispatcher) {
        val reply = CompletableDeferred<ApiResult<MyAvatar>>()
        val vm = MyAvatarViewModel({ reply.await() }, MutableStateFlow(SessionState.Authenticated(profile(2))), cached = { id -> if (id == 1L) boy else null })
        dispatcher.scheduler.runCurrent()
        assertNull("2번 계정은 기억한 것이 없어 비어 있다", vm.ui.value.avatar)
        reply.complete(ApiResult.Success(boy))
        advanceUntilIdle()
    }
}

