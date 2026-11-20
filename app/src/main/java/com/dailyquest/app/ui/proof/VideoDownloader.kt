package com.dailyquest.app.ui.proof

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface VideoFetch {
    data class Ready(val file: File) : VideoFetch
    data class Failed(val reason: Reason) : VideoFetch
    enum class Reason { NETWORK, HTTP, TOO_LARGE }
}

/**
 * 원격 영상을 앱 캐시로 내려받아 로컬 파일로 재생하게 한다. MediaPlayer의 원격 HTTP 스트리밍은 서버·네트워크 조합에 따라
 * 읽기가 멈추거나 실패하므로(Range/지연), 시간 제한이 명확한 OkHttp로 받는다. 공개 URL이라 인증 헤더는 보내지 않는다.
 * 같은 URL의 동시 요청은 한 번만 받고, 받은 파일은 재사용하며 오래된 파일은 정리한다.
 */
class VideoDownloader(
    private val dir: File,
    private val client: OkHttpClient = defaultClient(),
    private val maxBytes: Long = 30L * 1024 * 1024,
    private val maxFiles: Int = 6,
) {
    private val lock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<VideoFetch>>()

    fun cachedFile(url: String): File? = fileFor(url).takeIf { it.isFile && it.length() > 0 }

    suspend fun fetch(url: String): VideoFetch {
        cachedFile(url)?.let { it.setLastModified(System.currentTimeMillis()); return VideoFetch.Ready(it) }
        val mine = CompletableDeferred<VideoFetch>()
        val existing = lock.withLock { inFlight[url] ?: run { inFlight[url] = mine; null } }
        if (existing != null) return existing.await()
        try {
            val r = withContext(Dispatchers.IO) { download(url) }
            mine.complete(r)
            return r
        } catch (t: Throwable) {
            mine.complete(VideoFetch.Failed(VideoFetch.Reason.NETWORK))
            throw t
        } finally {
            lock.withLock { inFlight.remove(url) }
        }
    }

    private fun fileFor(url: String) = File(dir, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".mp4")

    private fun download(url: String): VideoFetch {
        dir.mkdirs()
        val target = fileFor(url)
        val part = File(dir, target.name + ".part")
        try {
            val req = try { Request.Builder().url(url).get().build() } catch (_: IllegalArgumentException) { return VideoFetch.Failed(VideoFetch.Reason.HTTP) }
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return VideoFetch.Failed(VideoFetch.Reason.HTTP)
                val body = res.body ?: return VideoFetch.Failed(VideoFetch.Reason.HTTP)
                if (body.contentLength() > maxBytes) return VideoFetch.Failed(VideoFetch.Reason.TOO_LARGE)
                var total = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) return VideoFetch.Failed(VideoFetch.Reason.TOO_LARGE)
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (total == 0L || (body.contentLength() in 1..Long.MAX_VALUE && total < body.contentLength())) return VideoFetch.Failed(VideoFetch.Reason.NETWORK)
            }
            if (!part.renameTo(target)) return VideoFetch.Failed(VideoFetch.Reason.NETWORK)
            prune(target)
            return VideoFetch.Ready(target)
        } catch (_: IOException) {
            return VideoFetch.Failed(VideoFetch.Reason.NETWORK)
        } finally {
            part.delete()
        }
    }

    private fun prune(keep: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(maxFiles).filter { it != keep }.forEach { it.delete() }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
    }
}
