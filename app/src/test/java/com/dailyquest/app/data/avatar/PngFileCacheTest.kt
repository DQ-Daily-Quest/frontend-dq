package com.dailyquest.app.data.avatar

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * STEP 10-4: 방 꾸미기 그림(PNG)도 STEP 9의 다운로드·디스크 캐시를 쓴다. 파일 검사만 PNG로 바꾼다.
 * 기본값(GLB)으로 쓰는 아바타 쪽 동작은 기존 테스트(CachingAvatarModelFetcherTest, HttpAvatarModelFetcherTest)가 그대로 확인한다.
 */
class PngFileCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.shutdown()

    private fun png(tag: Int) = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, tag.toByte())
    private val glb = byteArrayOf(0x67, 0x6C, 0x54, 0x46, 2, 0, 0, 0, 12, 0, 0, 0)

    @Test fun `PNG 헤더 판별`() {
        assertTrue(PngFormat.isPng(png(1)))
        assertFalse(PngFormat.isPng(glb))
        assertFalse(PngFormat.isPng(byteArrayOf(0x89.toByte(), 0x50, 0x4E)))
        assertFalse("GLB 검사로는 PNG가 통과하지 않는다", GlbFormat.isGlb(png(1)))
    }

    @Test fun `PNG 검사로 만든 다운로더는 PNG를 받고 GLB를 거절한다`() = runBlocking {
        val fetcher = HttpAvatarModelFetcher(OkHttpClient(), PngFormat::isPng, "PNG")
        server.enqueue(MockResponse().setBody(Buffer().write(png(7))))
        assertArrayEquals(png(7), fetcher.fetch(server.url("/desk_oak.png").toString()).getOrThrow())

        server.enqueue(MockResponse().setBody(Buffer().write(glb)))
        val e = fetcher.fetch(server.url("/x.png").toString()).exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("PNG 파일이 아닙니다"))
    }

    @Test fun `PNG 캐시는 한 번 받은 그림을 다시 받지 않고, 깨진 파일은 다시 받는다`() = runBlocking {
        var calls = 0
        val network = AvatarModelFetcher { calls++; Result.success(png(calls)) }
        val dir = temp.newFolder("decor_images")
        val cache = CachingAvatarModelFetcher(network, dir, isValid = PngFormat::isPng)
        val url = "https://s3/items/furniture/desk_oak/desk_oak.png"

        val first = cache.fetch(url).getOrThrow()
        assertArrayEquals(first, cache.fetch(url).getOrThrow())
        assertEquals(1, calls)

        // 저장된 파일이 PNG가 아니게 되면(깨짐) 지우고 다시 받는다
        cache.fileFor(url).writeBytes(glb)
        cache.fetch(url).getOrThrow()
        assertEquals(2, calls)
    }
}
