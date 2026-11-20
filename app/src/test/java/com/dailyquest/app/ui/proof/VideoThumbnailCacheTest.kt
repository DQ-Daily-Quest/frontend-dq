package com.dailyquest.app.ui.proof

import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VideoThumbnailCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Extractor(var result: Long? = 4_200, val gate: CompletableDeferred<Unit>? = null) : FrameExtractor {
        var calls = 0
        override suspend fun extract(source: String, into: java.io.File): Long? {
            calls++
            gate?.await()
            if (result != null) into.writeBytes(byteArrayOf(1, 2, 3))
            return result
        }
    }

    @Test fun formatsDurationRoundedUpToSeconds() {
        assertEquals("0:05", VideoThumbnailCache.formatDuration(4_200))
        assertEquals("1:05", VideoThumbnailCache.formatDuration(65_000))
        assertEquals("0:01", VideoThumbnailCache.formatDuration(1))
    }

    @Test fun secondRequestHitsDiskCacheWithoutExtracting() = runTest {
        val ex = Extractor()
        val cache = VideoThumbnailCache(tmp.newFolder(), ex)
        val a = cache.get("https://x/v.mp4")
        val b = cache.get("https://x/v.mp4")
        assertNotNull(a); assertEquals(4_200L, a!!.durationMs)
        assertEquals(a.file.path, b!!.file.path)
        assertEquals(1, ex.calls)
        // 새 인스턴스(프로세스 재시작)에서도 디스크 캐시를 재사용한다.
        val again = VideoThumbnailCache(a.file.parentFile!!, Extractor()).get("https://x/v.mp4")
        assertEquals(4_200L, again!!.durationMs)
    }

    @Test fun concurrentRequestsShareOneExtraction() = runTest {
        val gate = CompletableDeferred<Unit>()
        val ex = Extractor(gate = gate)
        val cache = VideoThumbnailCache(tmp.newFolder(), ex)
        val r = (1..3).map { async { cache.get("https://x/v.mp4") } }
        advanceUntilIdle()
        gate.complete(Unit)
        r.forEach { assertNotNull(it.await()) }
        assertEquals(1, ex.calls)
    }

    @Test fun failureIsNegativeCachedUntilTtlThenRetried() = runTest {
        var t = 0L
        val ex = Extractor(result = null)
        val cache = VideoThumbnailCache(tmp.newFolder(), ex, now = { t }, failureTtlMs = 1_000)
        assertNull(cache.get("u")); assertNull(cache.get("u"))
        assertEquals(1, ex.calls)
        t = 2_000
        ex.result = 3_000
        assertNotNull(cache.get("u"))
        assertEquals(2, ex.calls)
    }

    @Test fun prunesOldFilesBeyondLimit() = runTest {
        val dir = tmp.newFolder()
        val cache = VideoThumbnailCache(dir, Extractor(), maxFiles = 2)
        (1..5).forEach { cache.get("u$it") }
        assertTrue((dir.listFiles()?.size ?: 0) <= 3)
    }
}
