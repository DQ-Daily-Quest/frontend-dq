package com.dailyquest.app.ui.proof

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class VideoDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Server(val code: Int = 200, val bytes: ByteArray = ByteArray(2048) { 1 }, val declared: Long? = null, val fail: Boolean = false) : Interceptor {
        var calls = 0
        var sawAuth = false
        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            sawAuth = chain.request().header("Authorization") != null
            if (fail) throw IOException("boom")
            Thread.sleep(50)
            val body = object : okhttp3.ResponseBody() {
                override fun contentType() = "video/mp4".toMediaType()
                override fun contentLength() = declared ?: bytes.size.toLong()
                override fun source() = okio.Buffer().write(bytes)
            }
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("m").body(body).build()
        }
    }

    private fun dl(s: Server, max: Long = 1_000_000) =
        VideoDownloader(tmp.newFolder(), OkHttpClient.Builder().addInterceptor(s).build(), maxBytes = max)

    @Test fun downloadsOnceAndReusesCacheWithoutAuthHeader() = runBlocking {
        val s = Server(); val d = dl(s)
        val a = d.fetch("https://h/a.mp4") as VideoFetch.Ready
        val b = d.fetch("https://h/a.mp4") as VideoFetch.Ready
        assertEquals(a.file.path, b.file.path); assertEquals(2048L, a.file.length())
        assertEquals(1, s.calls); assertTrue(!s.sawAuth)
        assertTrue(d.cachedFile("https://h/a.mp4") != null)
    }

    @Test fun concurrentFetchesShareOneRequest() = runBlocking {
        val s = Server(); val d = dl(s)
        val r = (1..3).map { async { d.fetch("https://h/a.mp4") } }.map { it.await() }
        assertTrue(r.all { it is VideoFetch.Ready }); assertEquals(1, s.calls)
    }

    @Test fun httpErrorAndNetworkErrorAreReportedAndNothingCached() = runBlocking {
        val d1 = dl(Server(code = 404))
        assertEquals(VideoFetch.Failed(VideoFetch.Reason.HTTP), d1.fetch("https://h/a.mp4"))
        assertNull(d1.cachedFile("https://h/a.mp4"))
        val d2 = dl(Server(fail = true))
        assertEquals(VideoFetch.Failed(VideoFetch.Reason.NETWORK), d2.fetch("https://h/a.mp4"))
        assertNull(d2.cachedFile("https://h/a.mp4"))
    }

    @Test fun oversizedFileIsRejectedWithoutKeepingPartialData() = runBlocking {
        val d = dl(Server(bytes = ByteArray(5000)), max = 1000)
        assertEquals(VideoFetch.Failed(VideoFetch.Reason.TOO_LARGE), d.fetch("https://h/a.mp4"))
        assertNull(d.cachedFile("https://h/a.mp4"))
    }

    @Test fun emptyBodyIsFailure() = runBlocking {
        val d = dl(Server(bytes = ByteArray(0)))
        assertTrue(d.fetch("https://h/a.mp4") is VideoFetch.Failed)
    }

    @Test fun failedFetchCanBeRetriedSuccessfully() = runBlocking {
        val dir = tmp.newFolder(); var fail = true
        val client = OkHttpClient.Builder().addInterceptor { c ->
            if (fail) throw IOException("x") else Server().intercept(c)
        }.build()
        val d = VideoDownloader(dir, client)
        assertTrue(d.fetch("https://h/a.mp4") is VideoFetch.Failed)
        fail = false
        assertTrue(d.fetch("https://h/a.mp4") is VideoFetch.Ready)
    }

    @Test fun prunesOldestBeyondLimit() = runBlocking {
        val dir = tmp.newFolder()
        val d = VideoDownloader(dir, OkHttpClient.Builder().addInterceptor(Server()).build(), maxFiles = 2)
        (1..4).forEach { d.fetch("https://h/$it.mp4") }
        assertEquals(2, dir.listFiles()!!.count { it.name.endsWith(".mp4") })
    }
}
