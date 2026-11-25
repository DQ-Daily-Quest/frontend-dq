package com.dailyquest.app.ui.shop

import com.dailyquest.app.data.remote.ApiException
import com.dailyquest.app.data.remote.ApiResult
import com.dailyquest.app.data.shop.ItemCategory
import com.dailyquest.app.data.shop.ItemType
import com.dailyquest.app.data.shop.ShopItem
import com.dailyquest.app.data.shop.ShopPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** 상점 이미지 속도 3단계: 상품 이미지를 기기 저장소에 미리 받는다. */
class ShopImagePrefetcherTest {
    private val fetched = Collections.synchronizedList(mutableListOf<String>())

    @Test fun `모든 상품 이미지를 한 번씩 받고 빈 주소와 겹치는 주소는 건너뛴다`() = runBlocking {
        val p = ShopImagePrefetcher(listImageUrls = { listOf("a", "b", "", "a", "c") }, fetch = { fetched += it; true })
        assertEquals(3, p.run())
        assertEquals(listOf("a", "b", "c"), fetched.sorted())
        assertTrue(p.finished)
    }

    @Test fun `동시에 받는 수는 정한 수를 넘지 않는다`() = runBlocking {
        val now = AtomicInteger(); val peak = AtomicInteger()
        val p = ShopImagePrefetcher(
            listImageUrls = { (1..20).map { "u$it" } },
            fetch = { now.incrementAndGet().also { n -> peak.accumulateAndGet(n, ::maxOf) }; delay(20); now.decrementAndGet(); true },
            parallelism = 4,
        )
        assertEquals(20, p.run())
        assertTrue("최대 ${peak.get()}", peak.get() in 2..4)
    }

    @Test fun `한 번 끝나면 다시 하지 않는다`() = runBlocking {
        var lists = 0
        val p = ShopImagePrefetcher(listImageUrls = { lists++; listOf("a") }, fetch = { fetched += it; true })
        p.run(); p.run()
        assertEquals(1, lists)
        assertEquals(listOf("a"), fetched.toList())
    }

    @Test fun `목록을 못 받으면 끝난 것으로 치지 않아 다음에 다시 한다`() = runBlocking {
        var fail = true
        val p = ShopImagePrefetcher(listImageUrls = { if (fail) null else listOf("a") }, fetch = { fetched += it; true })
        assertNull(p.run())
        assertTrue(!p.finished)
        fail = false
        assertEquals(1, p.run())
        assertEquals(listOf("a"), fetched.toList())
    }

    @Test fun `이미지 하나가 실패해도 나머지는 계속 받는다`() = runBlocking {
        val p = ShopImagePrefetcher(
            listImageUrls = { listOf("ok1", "bad", "boom", "ok2") },
            fetch = { url -> fetched += url; if (url == "boom") throw java.io.IOException("x"); url != "bad" },
        )
        assertEquals(2, p.run())
        assertEquals(4, fetched.size)
    }

    // --- 주소 모으기

    private fun item(id: Long, type: ItemType, thumb: String?, image: String? = null) = ShopItem(
        id, "i$id", type, if (type == ItemType.AVATAR) ItemCategory.HAIR else ItemCategory.DESK, 0, image, null, false, false, thumb,
    )

    @Test fun `아바타 다음 가구 순서로 모든 쪽의 카드 이미지를 모은다`() = runBlocking {
        val calls = mutableListOf<String>()
        val urls = shopImageUrls(listPage = { type, page, size ->
            calls += "$type/$page/$size"
            ApiResult.Success(
                when (type) {
                    ItemType.AVATAR -> if (page == 0) ShopPage(listOf(item(1, type, "a1"), item(2, type, null, "a2-image")), 0, 2, 3)
                    else ShopPage(listOf(item(3, type, null, null)), 1, 2, 3)
                    else -> ShopPage(listOf(item(10, type, "f1")), 0, 1, 1)
                },
            )
        }, pageSize = 2)
        assertEquals("썸네일이 없으면 원래 그림, 둘 다 없으면 뺀다", listOf("a1", "a2-image", "f1"), urls)
        assertEquals(listOf("AVATAR/0/2", "AVATAR/1/2", "FURNITURE/0/2"), calls)
    }

    @Test fun `목록 한 쪽이라도 못 받으면 null`() = runBlocking {
        val urls = shopImageUrls(listPage = { type, _, _ ->
            if (type == ItemType.FURNITURE) ApiResult.Failure(ApiException.Network(java.io.IOException("off")))
            else ApiResult.Success(ShopPage(listOf(item(1, type, "a1")), 0, 1, 1))
        })
        assertNull(urls)
    }

    @Test fun `쪽 수가 아주 많아도 정한 쪽까지만 받는다`() = runBlocking {
        var calls = 0
        val urls = shopImageUrls(listPage = { type, page, _ ->
            calls++
            ApiResult.Success(ShopPage(listOf(item(page.toLong(), type, "$type$page")), page, 1000, 1000))
        }, maxPages = 3)
        assertEquals(6, calls)
        assertEquals(6, urls!!.size)
    }
}
