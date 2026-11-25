package com.dailyquest.app.ui.proof

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

/** 상점 이미지 속도 1단계: 받은 이미지를 기기에 저장하고, 서버가 허용한 것만 다시 받지 않는다. */
class RemoteImageDiskCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun image(cacheControl: String?) = MockResponse().setBody(Buffer().write(png)).apply {
        setHeader("Content-Type", "image/png")
        if (cacheControl != null) setHeader("Cache-Control", cacheControl)
    }

    private fun fetch(client: okhttp3.OkHttpClient, path: String): ByteArray? {
        val f = tmp.newFile()
        return if (fetchToFile(client, server.url(path).toString(), f)) f.readBytes() else null
    }

    @Test fun `서버가 오래 저장해도 된다고 한 이미지는 두 번째부터 서버에 묻지 않는다`() {
        server.enqueue(image("public, max-age=31536000, immutable"))
        val client = imageClient(tmp.newFolder("cache"))
        assertArrayEquals(png, fetch(client, "/thumb/a.png"))
        assertArrayEquals(png, fetch(client, "/thumb/a.png"))
        assertArrayEquals(png, fetch(client, "/thumb/a.png"))
        assertEquals("서버 요청은 처음 한 번", 1, server.requestCount)
    }

    @Test fun `앱을 다시 켜도 기기에 저장한 이미지를 쓴다`() {
        server.enqueue(image("public, max-age=31536000, immutable"))
        val dir = tmp.newFolder("cache")
        assertArrayEquals(png, fetch(imageClient(dir), "/thumb/a.png"))
        // 새 연결(앱 재시작)이어도 같은 폴더면 저장된 것을 쓴다.
        assertArrayEquals(png, fetch(imageClient(dir), "/thumb/a.png"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `저장 금지나 유효기간이 없는 이미지는 매번 받는다`() {
        server.enqueue(image("no-store"))
        server.enqueue(image("no-store"))
        server.enqueue(image(null))
        server.enqueue(image(null))
        val client = imageClient(tmp.newFolder("cache"))
        fetch(client, "/proof/p.png"); fetch(client, "/proof/p.png")
        fetch(client, "/proof/q.png"); fetch(client, "/proof/q.png")
        assertEquals(4, server.requestCount)
    }

    @Test fun `실패한 응답은 저장하지 않아 다음에 다시 받는다`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Cache-Control", "max-age=31536000"))
        server.enqueue(image("public, max-age=31536000, immutable"))
        val client = imageClient(tmp.newFolder("cache"))
        assertEquals(null, fetch(client, "/thumb/b.png"))
        assertArrayEquals(png, fetch(client, "/thumb/b.png"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `http(s)가 아닌 주소는 받지 않는다`() {
        val f = tmp.newFile()
        assertFalse(fetchToFile(imageClient(null), "content://x", f))
        assertTrue(f.length() == 0L)
    }
}
