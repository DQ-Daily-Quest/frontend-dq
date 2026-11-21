package com.dailyquest.app.debug

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** STEP 9-2 테스트 화면의 GLB 다운로드: 성공, HTTP 오류, GLB가 아닌 응답, 연결 끊김을 MockWebServer로 검증한다. */
class GlbDownloaderTest {
    private lateinit var server: MockWebServer
    private val downloader = GlbDownloader(OkHttpClient())

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    /** 최소 GLB 헤더: "glTF" + 버전 2 + 전체 길이 */
    private fun glbBytes(extra: Int = 20): ByteArray =
        byteArrayOf('g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte(), 2, 0, 0, 0, 32, 0, 0, 0) +
            ByteArray(extra) { it.toByte() }

    private fun url(path: String = "/part.glb") = server.url(path).toString()

    @Test fun `GLB 응답이면 바이트를 그대로 돌려준다`() = runBlocking {
        val body = glbBytes()
        server.enqueue(MockResponse().setBody(Buffer().write(body)))

        val result = downloader.download(url("/items/avatar/boy/DQ_Boy_01_Body.glb"))

        assertTrue(result.isSuccess)
        assertArrayEquals(body, result.getOrThrow())
        assertEquals("/items/avatar/boy/DQ_Boy_01_Body.glb", server.takeRequest().path)
    }

    @Test fun `404면 실패하고 상태 코드를 알려준다`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = downloader.download(url())

        assertTrue(result.isFailure)
        assertEquals("HTTP 404", result.exceptionOrNull()?.message)
    }

    @Test fun `GLB가 아닌 응답이면 실패한다`() = runBlocking {
        server.enqueue(MockResponse().setBody("<Error>AccessDenied</Error>"))

        val result = downloader.download(url())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().startsWith("GLB 파일이 아닙니다"))
    }

    @Test fun `연결이 끊기면 예외를 던지지 않고 실패로 돌려준다`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = downloader.download(url())

        assertTrue(result.isFailure)
    }

    @Test fun `GLB 헤더 판별`() {
        assertTrue(GlbDownloader.isGlb(glbBytes(0)))
        assertFalse(GlbDownloader.isGlb(ByteArray(0)))
        assertFalse(GlbDownloader.isGlb("glTF".toByteArray()))
        // 버전 1 GLB는 지원하지 않는다
        assertFalse(GlbDownloader.isGlb(glbBytes(0).also { it[4] = 1 }))
        // JSON glTF(.gltf)는 GLB가 아니다
        assertFalse(GlbDownloader.isGlb("{\"asset\":{\"version\":\"2.0\"}}".toByteArray()))
    }
}
