package com.dailyquest.app.data.avatar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 프로필 얼굴 사진 하나를 가리키는 이름표 (프로필 얼굴 2단계).
 * 같은 착용 조합(슬롯별 GLB 주소)이면 같은 [id]가 나온다. 옷을 바꾸면 주소가 달라져 다른 [id]가 된다.
 * 얼굴 카메라 값이 바뀌면 [VERSION]을 올려 예전 사진을 쓰지 않게 한다.
 */
data class FaceKey(val parts: Map<AvatarPart, String>) {
    val id: String by lazy {
        val text = VERSION + "\n" + parts.entries.sortedBy { it.key.ordinal }.joinToString("\n") { "${it.key.name}=${it.value}" }
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
    }

    /** 그릴 파츠가 하나도 없으면 찍을 수 없다. */
    val isEmpty: Boolean get() = parts.isEmpty()

    companion object {
        /** 얼굴 카메라(AvatarCamera.FACE)나 사진 크기를 바꾸면 올린다. */
        const val VERSION = "face-v1"
    }
}

/**
 * 얼굴 사진(PNG)을 기기에 저장한다.
 * - 파일은 바뀌지 않는다(같은 [FaceKey]면 같은 모습). 유효기간 없이 쓴다.
 * - 합계가 [maxBytes]를 넘으면 가장 오래 안 쓴 사진부터 지운다.
 * - 저장은 임시 파일에 쓴 뒤 이름을 바꿔, 쓰는 도중 앱이 꺼져도 반쯤 쓴 파일이 남지 않는다.
 * - 공개된 모습이라 로그아웃해도 지울 필요가 없다.
 */
class FaceStore(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun has(key: FaceKey): Boolean = fileFor(key).isFile

    suspend fun get(key: FaceKey): ByteArray? = withContext(Dispatchers.IO) {
        val f = fileFor(key)
        if (!f.isFile) return@withContext null
        runCatching { f.readBytes().also { f.setLastModified(clock()) } }.getOrNull()
    }

    suspend fun put(key: FaceKey, png: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val target = fileFor(key)
            val temp = File(directory, target.name + ".tmp")
            temp.writeBytes(png)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) temp.delete()
            }
            target.setLastModified(clock())
            evict()
        }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** 저장된 사진 수와 합계 크기 (디버그 화면 표시용). */
    fun stats(): Pair<Int, Long> {
        val files = files()
        return files.size to files.sumOf { it.length() }
    }

    private fun evict() {
        val files = files()
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    private fun files(): List<File> = directory.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }?.toList().orEmpty()

    internal fun fileFor(key: FaceKey): File = File(directory, key.id + SUFFIX)

    companion object {
        /** 사진 한 장 약 30~60KB(256px). 수백 명 분량. */
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
        private const val SUFFIX = ".png"
    }
}
