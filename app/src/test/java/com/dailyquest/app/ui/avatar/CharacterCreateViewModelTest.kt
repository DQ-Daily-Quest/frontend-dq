package com.dailyquest.app.ui.avatar

import com.dailyquest.app.data.avatar.AvatarDefaults
import com.dailyquest.app.data.avatar.AvatarDefaults.SkinTone
import com.dailyquest.app.data.avatar.AvatarGender
import com.dailyquest.app.data.avatar.AvatarPart
import com.dailyquest.app.data.avatar.MyAvatar
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
import java.io.IOException

/** STEP 9-9 캐릭터 만들기 화면 상태와 미리보기 기본 한 벌. */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterCreateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val made = MyAvatar(AvatarGender.MALE, emptyMap())

    @Test fun `처음에는 남자아이 아이보리이고 미리보기는 기본 한 벌 6개다`() {
        val vm = CharacterCreateViewModel { _, _ -> ApiResult.Success(made) }

        assertEquals(AvatarGender.MALE, vm.ui.value.gender)
        assertEquals(SkinTone.IVORY, vm.ui.value.skin)
        assertEquals(6, vm.ui.value.previewParts.size)
        assertTrue(vm.ui.value.previewParts.getValue(AvatarPart.SKIN).endsWith("/boy/DQ_Boy_01_Body.glb"))
    }

    @Test fun `여자아이 브라운을 고르면 미리보기가 여자 기본 한 벌과 브라운 피부로 바뀐다`() {
        val vm = CharacterCreateViewModel { _, _ -> ApiResult.Success(made) }

        vm.selectGender(AvatarGender.FEMALE)
        vm.selectSkin(SkinTone.BROWN)

        val parts = vm.ui.value.previewParts
        assertTrue(parts.getValue(AvatarPart.SKIN).endsWith("/girl/DQ_Body_DeepBrown.glb"))
        assertTrue(parts.getValue(AvatarPart.HAIR).endsWith("/girl/DQ_Hair.glb"))
        assertTrue(parts.values.all { "/girl/" in it })
        assertNull(parts[AvatarPart.HAT])
    }

    @Test fun `시작하기를 누르면 고른 성별과 피부로 만들고 done이 된다`() = runTest(dispatcher) {
        val sent = mutableListOf<Pair<AvatarGender, SkinTone>>()
        val vm = CharacterCreateViewModel { g, s -> sent += g to s; ApiResult.Success(made) }
        vm.selectGender(AvatarGender.FEMALE)
        vm.selectSkin(SkinTone.PEACH)

        vm.submit()
        assertTrue(vm.ui.value.saving)
        advanceUntilIdle()

        assertEquals(listOf(AvatarGender.FEMALE to SkinTone.PEACH), sent)
        assertTrue(vm.ui.value.done)
        assertFalse(vm.ui.value.saving)
    }

    @Test fun `실패하면 안내를 보여 주고 다시 누를 수 있다`() = runTest(dispatcher) {
        var fail = true
        val vm = CharacterCreateViewModel { _, _ ->
            if (fail) ApiResult.Failure(ApiException.Network(IOException("offline"))) else ApiResult.Success(made)
        }

        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.ui.value.error != null)
        assertFalse(vm.ui.value.done)

        fail = false
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.ui.value.done)
        assertNull(vm.ui.value.error)
    }

    @Test fun `저장 중에는 다시 누르거나 선택을 바꿔도 무시한다`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<MyAvatar>>()
        var calls = 0
        val vm = CharacterCreateViewModel { _, _ -> calls++; gate.await() }

        vm.submit()
        advanceUntilIdle()
        vm.submit()
        vm.selectGender(AvatarGender.FEMALE)
        vm.selectSkin(SkinTone.BROWN)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(AvatarGender.MALE, vm.ui.value.gender)
        assertEquals(SkinTone.IVORY, vm.ui.value.skin)
        gate.complete(ApiResult.Success(made))
        advanceUntilIdle()
    }

    @Test fun `만든 뒤에는 다시 보내지 않는다`() = runTest(dispatcher) {
        var calls = 0
        val vm = CharacterCreateViewModel { _, _ -> calls++; ApiResult.Success(made) }

        vm.submit(); advanceUntilIdle()
        vm.submit(); advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test fun `피부 톤 코드는 체형마다 서버 시드와 같다`() {
        assertEquals("DQ_Boy_01_Body", SkinTone.IVORY.code(AvatarGender.MALE))
        assertEquals("DQ_Body_BoySkin", SkinTone.IVORY.code(AvatarGender.FEMALE))
        assertEquals("DQ_Boy_01_Body_GirlSkin", SkinTone.PEACH.code(AvatarGender.MALE))
        assertEquals("DQ_Body", SkinTone.PEACH.code(AvatarGender.FEMALE))
        assertEquals("DQ_Boy_01_Body_DeepBrown", SkinTone.BROWN.code(AvatarGender.MALE))
        assertEquals("DQ_Body_DeepBrown", SkinTone.BROWN.code(AvatarGender.FEMALE))
        // 서버가 처음 입히는 피부(체형별 첫 톤)
        assertEquals(SkinTone.IVORY, AvatarDefaults.serverDefaultSkin(AvatarGender.MALE))
        assertEquals(SkinTone.PEACH, AvatarDefaults.serverDefaultSkin(AvatarGender.FEMALE))
    }
}
