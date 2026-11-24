package com.dailyquest.app.data.avatar

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 받은 GLB를 기기에 저장해 두고, 같은 주소는 다시 받지 않는 다운로더 (STEP 9-5).
 *
 * - **유효기간 없음:** 백엔드 v0.4 문서(7.1)대로 파일이 바뀌면 주소도 바뀌므로(`Cache-Control: immutable`) 서버에 다시 묻지 않는다.
 * - **용량 상한:** 저장한 파일 합계가 [maxBytes]를 넘으면 가장 오래 안 쓴 파일부터 지운다.
 * - **동시 요청 합치기:** 같은 주소를 동시에 여러 번 요청하면 한 번만 받는다(예: 여러 화면이 같은 몸체를 그릴 때).
 * - **깨진 파일:** 저장된 파일이 GLB가 아니면 지우고 다시 받는다. 저장은 임시 파일에 쓴 뒤 이름을 바꿔서, 쓰는 도중 앱이 꺼져도 반쯤 쓴 파일이 남지 않는다.
 * - 캐시는 공개 에셋이라 사용자와 관계없다. 로그아웃해도 지울 필요가 없다.
 * - 저장에 실패해도(저장 공간 부족 등) 받은 내용은 그대로 돌려준다. 캐시는 있으면 좋은 것이지 필수가 아니다.
 *
 * @param network 실제로 받는 방법 (보통 [HttpAvatarModelFetcher])
 * @param directory 저장 폴더. 앱에서는 [AvatarModels.cacheDirectory]
 * @param clock 마지막 사용 시각 기록용. 테스트에서 시간 순서를 정하려고 바꿀 수 있다.
 * @param isValid 저장된 파일이 온전한지 검사. 기본은 GLB. 방 꾸미기 이미지(PNG) 캐시도 이 클래스를 쓴다(STEP 10-4).
 */
class CachingAvatarModelFetcher(
    private val network: AvatarModelFetcher,
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isValid: (ByteArray) -> Boolean = GlbFormat::isGlb,
) : AvatarModelFetcher {

    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Result<ByteArray>>>()
    // 받는 작업은 요청한 화면이 사라져도 끝까지 받아 저장한다(같은 파일을 기다리는 다른 화면이 있을 수 있다).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun fetch(url: String): Result<ByteArray> {
        readCached(url)?.let { return Result.success(it) }
        val download = lock.withLock {
            inFlight[url] ?: scope.async {
                try {
                    // 기다리는 사이 다른 요청이 이미 저장했을 수 있다.
                    readCached(url)?.let { Result.success(it) } ?: downloadAndStore(url)
                } finally {
                    lock.withLock { inFlight.remove(url) }
                }
            }.also { inFlight[url] = it }
        }
        return download.await()
    }

    /** 저장된 파일 수와 합계 크기 (디버그 화면 표시용) */
    suspend fun stats(): CacheStats = withContext(Dispatchers.IO) {
        val files = cachedFiles()
        CacheStats(files.size, files.sumOf { it.length() })
    }

    /** 저장된 파일을 모두 지운다 (디버그·테스트용) */
    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    private suspend fun downloadAndStore(url: String): Result<ByteArray> =
        network.fetch(url).onSuccess { bytes -> store(url, bytes) }

    private suspend fun readCached(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (!file.isFile) return@withContext null
        try {
            val bytes = file.readBytes()
            if (isValid(bytes)) {
                file.setLastModified(clock())
                bytes
            } else {
                file.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun store(url: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            directory.mkdirs()
            val target = fileFor(url)
            val temp = File(directory, target.name + TEMP_SUFFIX)
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) temp.delete()
            }
            target.setLastModified(clock())
            evictIfNeeded()
        } catch (e: Exception) {
            // 저장하지 못해도 받은 내용은 쓸 수 있다.
        }
    }

    private fun evictIfNeeded() {
        val files = cachedFiles()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun cachedFiles(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(CACHE_SUFFIX) }?.toList().orEmpty()

    /** 주소를 SHA-256으로 바꾼 파일 이름. 주소에 쿼리(`?v=2`)나 특수문자가 있어도 안전하다. */
    internal fun fileFor(url: String): File = File(directory, sha256(url) + CACHE_SUFFIX)

    data class CacheStats(val fileCount: Int, val totalBytes: Long)

    companion object {
        /** 남자 파츠 57개 합계가 약 44MB. 한 체형 전체가 들어가고도 남는 크기. */
        const val DEFAULT_MAX_BYTES = 100L * 1024 * 1024
        private const val CACHE_SUFFIX = ".glb"
        private const val TEMP_SUFFIX = ".tmp"

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** 앱 전체에서 같은 캐시를 쓰도록 하나만 만든다. */
object AvatarModels {
    @Volatile private var instance: CachingAvatarModelFetcher? = null

    /** 시스템이 저장 공간이 부족할 때 지울 수 있는 캐시 폴더. 지워져도 다시 받으면 된다. */
    fun cacheDirectory(context: Context): File = File(context.applicationContext.cacheDir, "avatar_models")

    fun fetcher(context: Context): CachingAvatarModelFetcher =
        instance ?: synchronized(this) {
            instance ?: CachingAvatarModelFetcher(HttpAvatarModelFetcher.shared, cacheDirectory(context)).also { instance = it }
        }
}
