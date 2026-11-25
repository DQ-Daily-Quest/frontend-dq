package com.dailyquest.app.ui.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 아바타 속도 3단계: 파츠 모델 보관함. 모델 대신 문자열 상자로 확인한다. */
class AvatarModelCacheTest {
    private class M(val url: String)

    private var now = 10_000L
    private val destroyed = mutableListOf<String>()
    private val created = mutableListOf<String>()
    private fun cache(max: Int = 2) = AvatarModelCache<M>(destroy = { destroyed += it.url }, maxUnused = max, graceMs = 1_000, clock = { now })
    private suspend fun AvatarModelCache<M>.get(url: String) = acquire(url) { created += url; M(url) }

    @Test fun `같은 파츠는 한 번만 만들고 함께 쓴다`() = runBlocking {
        val c = cache()
        val a = c.get("hair")!!
        val b = c.get("hair")!!
        assertSame(a, b)
        assertEquals(listOf("hair"), created)
        assertEquals(1, c.inUse)
        c.release(a); c.release(b)
        assertEquals("다 써도 지우지 않고 보관", 1, c.size)
        assertEquals(0, c.inUse)
        assertTrue(c.has("hair"))
        assertSame("다시 쓰면 보관된 것", a, c.get("hair"))
        assertEquals(1, created.size)
    }

    @Test fun `동시에 같은 파츠를 요청하면 한 번만 만든다`() = runBlocking {
        val c = cache()
        val gate = CompletableDeferred<Unit>()
        val first = async { c.acquire("top") { created += "top"; gate.await(); M("top") } }
        yield()
        val second = async { c.acquire("top") { created += "top-again"; M("top") } }
        yield()
        gate.complete(Unit)
        assertSame(first.await(), second.await())
        assertEquals(listOf("top"), created)
        assertEquals(1, c.inUse)
    }

    @Test fun `안 쓰는 모델이 상한을 넘으면 오래 안 쓴 것부터 지우되 방금 돌려받은 것은 남긴다`() = runBlocking {
        val c = cache(max = 2)
        val ms = listOf("a", "b", "c", "d").map { c.get(it)!! }
        ms.forEachIndexed { i, m -> now += 10; c.release(m) } // a가 가장 먼저 돌려받음
        assertTrue("방금 돌려받아 아직 하나도 지우지 않음", destroyed.isEmpty())
        assertEquals(4, c.size)

        now += 2_000
        c.evictIfNeeded()
        assertEquals("오래 안 쓴 a, b부터", listOf("a", "b"), destroyed)
        assertEquals(2, c.size)
        assertFalse(c.has("a"))
        assertTrue(c.has("d"))
    }

    @Test fun `쓰는 중인 모델은 상한을 넘어도 지우지 않는다`() = runBlocking {
        val c = cache(max = 0)
        val inUse = c.get("skin")!!
        now += 5_000
        c.evictIfNeeded()
        assertTrue(destroyed.isEmpty())
        c.release(inUse)
        now += 5_000
        c.evictIfNeeded()
        assertEquals(listOf("skin"), destroyed)
    }

    @Test fun `만들기에 실패하면 보관하지 않고 다음에 다시 만든다`() = runBlocking {
        val c = cache()
        assertNull(c.acquire("eyes") { null })
        assertNull(c.acquire("eyes") { throw IllegalStateException("broken glb") })
        assertFalse(c.has("eyes"))
        assertEquals(0, c.size)
        assertSame("eyes", c.get("eyes")!!.url)
    }

    @Test fun `모르는 모델이나 두 번 돌려주기는 무시한다`() = runBlocking {
        val c = cache()
        val m = c.get("hat")!!
        c.release(M("other"))
        c.release(m); c.release(m)
        assertEquals(0, c.inUse)
        val again = c.get("hat")!!
        assertEquals("두 번 돌려줘도 다시 빌리면 1", 1, c.inUse)
        c.release(again)
        assertEquals(0, c.inUse)
    }
}
