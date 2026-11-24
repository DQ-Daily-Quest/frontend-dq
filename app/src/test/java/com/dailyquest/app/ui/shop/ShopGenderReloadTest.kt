package com.dailyquest.app.ui.shop

import com.dailyquest.app.data.auth.SessionState
import com.dailyquest.app.data.auth.UserProfile
import com.dailyquest.app.data.remote.DqApi
import com.dailyquest.app.data.remote.dto.ItemDto
import com.dailyquest.app.data.remote.dto.PageDto
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopRepository
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
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * STEP 10-8 (STEP 9에서 찾은 상점 버그): 캐릭터를 만든 직후 상점 아바타 탭이 비어 있던 문제.
 * 서버는 내 성별의 아바타 아이템만 준다(성별이 없으면 0개). 같은 계정에서 성별이 정해지면 목록을 다시 받아야 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShopGenderReloadTest {

    /** 서버처럼 성별에 따라 아바타 아이템을 거른다. */
    private class GenderFilteringApi(delegate: DqApi, private val gender: () -> String?) : DqApi by delegate {
        val calls = mutableListOf<String?>()
        private val stock = listOf(
            ItemDto(1, "남자 머리", "AVATAR", "HAIR", 0, isDefault = true),
            ItemDto(2, "여자 머리", "AVATAR", "HAIR", 0, isDefault = true),
            ItemDto(3, "나무 책상", "FURNITURE", "DESK", 80),
        )
        /** 아이템 ID → 착용 가능한 성별 (가구는 없음) */
        private val genderOf = mapOf(1L to "MALE", 2L to "FEMALE")

        override suspend fun listShopItems(type: String?, category: String?, page: Int, size: Int): PageDto<ItemDto> {
            calls += type
            val g = gender()
            val all = stock.filter { (type == null || it.type == type) && (it.type != "AVATAR" || (g != null && genderOf[it.itemId] == g)) }
            return PageDto(all, page, size, all.size.toLong(), 1)
        }
    }

    private val unused: DqApi = Proxy.newProxyInstance(DqApi::class.java.classLoader, arrayOf(DqApi::class.java)) { _, _, _ -> error("not used") } as DqApi
    private lateinit var session: MutableStateFlow<SessionState>
    private lateinit var api: GenderFilteringApi

    private fun signedIn(id: Long, gender: String?, coins: Int = 0) =
        SessionState.Authenticated(UserProfile(id, null, "n", coins, 0, 0, 0, 0, gender = gender))

    private fun gender() = (session.value as? SessionState.Authenticated)?.profile?.gender

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        session = MutableStateFlow(signedIn(1, gender = null))
        api = GenderFilteringApi(unused, ::gender)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ShopViewModel(ShopRepository(api), session, applyCoins = { _, _ -> }, refreshProfile = {})

    @Test fun creatingCharacterReloadsTheEmptyAvatarList() = runTest {
        val vm = vm(); advanceUntilIdle()
        assertEquals("로그인 직후(성별 없음)에는 서버가 아바타를 주지 않는다", emptyList<String>(), vm.ui.value.items.map { it.name })

        session.value = signedIn(1, gender = "MALE") // 캐릭터 만들기 완료
        advanceUntilIdle()
        assertEquals(listOf("남자 머리"), vm.ui.value.items.map { it.name })
        assertEquals(2, api.calls.size)
    }

    @Test fun genderChangeKeepsTheTabTheUserIsOn() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.selectType(ItemType.FURNITURE); advanceUntilIdle()

        session.value = signedIn(1, gender = "FEMALE")
        advanceUntilIdle()
        assertEquals("보던 탭(가구)을 그대로 다시 받는다", ItemType.FURNITURE, vm.ui.value.type)
        assertEquals(listOf("나무 책상"), vm.ui.value.items.map { it.name })
        assertEquals("FURNITURE", api.calls.last())
        assertEquals("처음(아바타) → 가구 → 성별이 정해져 가구 다시", listOf("AVATAR", "FURNITURE", "FURNITURE"), api.calls)
    }

    @Test fun coinChangeAloneDoesNotReload() = runTest {
        session.value = signedIn(1, gender = "MALE")
        val vm = vm(); advanceUntilIdle()
        val before = api.calls.size
        session.value = signedIn(1, gender = "MALE", coins = 500)
        advanceUntilIdle()
        assertEquals(before, api.calls.size)
        assertEquals(listOf("남자 머리"), vm.ui.value.items.map { it.name })
    }

    @Test fun switchingAccountStillResetsEverything() = runTest {
        session.value = signedIn(1, gender = "MALE")
        val vm = vm(); advanceUntilIdle()
        vm.selectType(ItemType.FURNITURE); advanceUntilIdle()

        session.value = signedIn(2, gender = "FEMALE")
        advanceUntilIdle()
        assertEquals("다른 계정이면 처음 상태(아바타 탭)로", ItemType.AVATAR, vm.ui.value.type)
        assertEquals(2L, vm.ui.value.userId)
        assertEquals(listOf("여자 머리"), vm.ui.value.items.map { it.name })
    }
}
