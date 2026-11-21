package com.dailyquest.app.data.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** STEP 9-5 GLB 디스크 캐시: 다시 받지 않기, 앱 재시작 후에도 유지, 깨진 파일, 실패, 동시 요청, 용량 상한. */
class CachingAvatarModelFetcherTest {
    @get:Rule val temp = TemporaryFolder()

    /** 주소마다 정해진 GLB 내용을 돌려주고 호출 횟수를 센다. [failing] 주소는 실패. */
    private class FakeNetwork : AvatarModelFetcher {
        val calls = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun fetch(url: String): Result<ByteArray> {
            synchronized(calls) { calls += url }
            gate?.await()
            return if (url in failing) Result.failure(IOException("offline")) else Result.success(glb(url))
        }
    }

    private val network = FakeNetwork()
    private val dir by lazy { temp.newFolder("avatar_models") }
    private val time = AtomicInteger(1_000)
    private fun cache(maxBytes: Long = CachingAvatarModelFetcher.DEFAULT_MAX_BYTES) =
        CachingAvatarModelFetcher(network, dir, maxBytes, clock = { time.addAndGet(1_000).toLong() })

    @Test fun `처음에는 네트워크에서 받고, 두 번째부터는 저장된 파일을 쓴다`() = runBlocking {
        val cache = cache()

        val first = cache.fetch("https://s3/boy/DQ_Boy_Hat_Cafe.glb").getOrThrow()
        val second = cache.fetch("https://s3/boy/DQ_Boy_Hat_Cafe.glb").getOrThrow()

        assertArrayEquals(first, second)
        assertEquals(1, network.calls.size)
        assertEquals(CachingAvatarModelFetcher.CacheStats(1, first.size.toLong()), cache.stats())
    }

    @Test fun `앱을 다시 켜도(새 인스턴스) 저장된 파일을 쓴다`() = runBlocking {
        cache().fetch("https://s3/a.glb")

        val afterRestart = cache()
        val bytes = afterRestart.fetch("https://s3/a.glb").getOrThrow()

        assertArrayEquals(glb("https://s3/a.glb"), bytes)
        assertEquals(1, network.calls.size)
    }

    @Test fun `주소가 다르면 따로 받는다 - 파일을 고쳐 주소가 바뀐 경우 포함`() = runBlocking {
        val cache = cache()

        cache.fetch("https://s3/hat.glb")
        cache.fetch("https://s3/hat_v2.glb")
        cache.fetch("https://s3/hat.glb?v=3")

        assertEquals(3, network.calls.size)
        assertEquals(3, cache.stats().fileCount)
    }

    @Test fun `저장된 파일이 깨졌으면 지우고 다시 받는다`() = runBlocking {
        val cache = cache()
        cache.fetch("https://s3/a.glb")
        cache.fileFor("https://s3/a.glb").writeText("broken")

        val bytes = cache.fetch("https://s3/a.glb").getOrThrow()

        assertArrayEquals(glb("https://s3/a.glb"), bytes)
        assertEquals(2, network.calls.size)
        assertTrue(GlbFormat.isGlb(cache.fileFor("https://s3/a.glb").readBytes()))
    }

    @Test fun `받지 못하면 실패를 돌려주고 아무것도 저장하지 않는다`() = runBlocking {
        val cache = cache()
        network.failing += "https://s3/a.glb"

        val result = cache.fetch("https://s3/a.glb")

        assertTrue(result.isFailure)
        assertEquals(0, cache.stats().fileCount)
        // 다음에 다시 시도하면 다시 받으러 간다
        network.failing.clear()
        assertTrue(cache.fetch("https://s3/a.glb").isSuccess)
        assertEquals(2, network.calls.size)
    }

    @Test fun `같은 주소를 동시에 요청하면 한 번만 받는다`() = runBlocking {
        val cache = cache()
        network.gate = CompletableDeferred()

        val requests = (1..5).map { async { cache.fetch("https://s3/body.glb") } }
        // 모두 대기하도록 잠깐 기다린 뒤 응답을 풀어 준다
        while (network.calls.isEmpty()) kotlinx.coroutines.delay(5)
        kotlinx.coroutines.delay(50)
        network.gate!!.complete(Unit)
        val results = requests.awaitAll()

        assertEquals(1, network.calls.size)
        assertTrue(results.all { it.isSuccess })
    }

    @Test fun `용량을 넘으면 가장 오래 안 쓴 파일부터 지운다`() = runBlocking {
        val size = glb("https://s3/a.glb").size.toLong()
        val cache = cache(maxBytes = size * 2)   // 파일 2개까지

        cache.fetch("https://s3/a.glb")
        cache.fetch("https://s3/b.glb")
        cache.fetch("https://s3/a.glb")          // a를 다시 써서 b가 가장 오래 안 쓴 파일이 된다
        cache.fetch("https://s3/c.glb")          // 3개째 → b를 지운다

        assertTrue(cache.fileFor("https://s3/a.glb").exists())
        assertFalse(cache.fileFor("https://s3/b.glb").exists())
        assertTrue(cache.fileFor("https://s3/c.glb").exists())
        assertEquals(2, cache.stats().fileCount)
    }

    @Test fun `저장 후 임시 파일이 남지 않는다`() = runBlocking {
        cache().fetch("https://s3/a.glb")

        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `저장 폴더를 만들 수 없어도 받은 내용은 돌려준다`() = runBlocking {
        val notADirectory = temp.newFile("blocked")   // 폴더 자리에 파일이 있어서 저장할 수 없다
        val cache = CachingAvatarModelFetcher(network, java.io.File(notADirectory, "avatar_models"))

        val result = cache.fetch("https://s3/a.glb")

        assertArrayEquals(glb("https://s3/a.glb"), result.getOrThrow())
    }

    @Test fun `clear하면 모두 지워지고 다음에 다시 받는다`() = runBlocking {
        val cache = cache()
        cache.fetch("https://s3/a.glb")

        cache.clear()

        assertEquals(0, cache.stats().fileCount)
        cache.fetch("https://s3/a.glb")
        assertEquals(2, network.calls.size)
    }

    private companion object {
        /** 주소마다 내용이 다른 최소 GLB (헤더 12바이트 + 주소) */
        fun glb(url: String): ByteArray =
            byteArrayOf('g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte(), 2, 0, 0, 0, 64, 0, 0, 0) +
                url.padEnd(48, '_').toByteArray()
    }
}
