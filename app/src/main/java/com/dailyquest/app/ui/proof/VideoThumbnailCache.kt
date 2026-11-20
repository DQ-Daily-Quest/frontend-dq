package com.dailyquest.app.ui.proof

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** 추출한 영상 프레임(JPEG 파일)과 실제 재생시간. */
data class VideoThumb(val file: File, val durationMs: Long?)

/** 영상에서 첫 프레임을 JPEG 파일로 뽑는 방법. Android 구현은 MediaMetadataRetriever를 쓰며 테스트에서는 대체한다. */
fun interface FrameExtractor {
    /** [source]는 http(s) URL 또는 로컬 파일 경로. 실패하면 null. 인증 헤더는 절대 보내지 않는다. */
    suspend fun extract(source: String, into: File): Long?
}

/**
 * 영상 썸네일 캐시. 같은 영상은 디스크에 한 번만 저장하고(재생시간은 파일명에 함께 기록), 동시에 같은 영상을 요청하면 한 번만 추출한다.
 * 실패는 [failureTtlMs] 동안 기억해 화면을 오갈 때 불필요하게 다시 내려받지 않는다.
 */
class VideoThumbnailCache(
    private val dir: File,
    private val extractor: FrameExtractor,
    private val now: () -> Long = System::currentTimeMillis,
    private val failureTtlMs: Long = 60_000,
    private val maxFiles: Int = 60,
) {
    private val lock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<VideoThumb?>>()
    private val failedAt = HashMap<String, Long>()

    /** [cacheSeed]는 캐시 키 재료(기본은 [source]). 로컬 임시 파일처럼 경로가 재사용될 수 있으면 크기·수정시각을 섞어 넘긴다. */
    suspend fun get(source: String, cacheSeed: String = source): VideoThumb? {
        val key = keyOf(cacheSeed)
        var waitFor: CompletableDeferred<VideoThumb?>? = null
        var mine: CompletableDeferred<VideoThumb?>? = null
        lock.withLock {
            cached(key)?.let { return it }
            failedAt[key]?.let { if (now() - it < failureTtlMs) return null }
            waitFor = inFlight[key]
            if (waitFor == null) mine = CompletableDeferred<VideoThumb?>().also { inFlight[key] = it }
        }
        waitFor?.let { return it.await() }
        val result = try { extractAndStore(source, key) } catch (e: Exception) { null }
        lock.withLock {
            inFlight.remove(key)
            if (result == null) failedAt[key] = now() else failedAt.remove(key)
        }
        mine?.complete(result)
        return result
    }

    private fun cached(key: String): VideoThumb? {
        val f = dir.listFiles { x -> x.name.startsWith("$key-") && x.name.endsWith(".jpg") }?.firstOrNull() ?: return null
        if (f.length() == 0L) { f.delete(); return null }
        val dur = f.name.removePrefix("$key-").removeSuffix(".jpg").toLongOrNull()?.takeIf { it >= 0 }
        return VideoThumb(f, dur)
    }

    private suspend fun extractAndStore(source: String, key: String): VideoThumb? {
        dir.mkdirs()
        val tmp = File(dir, "$key.tmp")
        try {
            val dur = extractor.extract(source, tmp)
            if (!tmp.exists() || tmp.length() == 0L) return null
            val out = File(dir, "$key-${dur ?: -1}.jpg")
            if (!tmp.renameTo(out)) return null
            prune()
            return VideoThumb(out, dur?.takeIf { it >= 0 })
        } finally {
            tmp.delete()
        }
    }

    private fun prune() {
        val files = dir.listFiles { x -> x.name.endsWith(".jpg") } ?: return
        if (files.size > maxFiles) files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }

    companion object {
        fun keyOf(source: String): String =
            MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

        /** 영상 재생시간 표시: 5000 → "0:05", 65_000 → "1:05". 올림해서 0초로 보이지 않게 한다. */
        fun formatDuration(ms: Long): String {
            val total = ((ms.coerceAtLeast(0) + 999) / 1000)
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}
