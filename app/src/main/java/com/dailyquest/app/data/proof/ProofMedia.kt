package com.dailyquest.app.data.proof

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** 업로드 직전의 검증을 마친 로컬 임시 파일. 앱 캐시에만 있으며 제출·취소·화면 종료 시 삭제한다. */
data class PreparedMedia(
    val file: File,
    val mime: String,
    val type: ProofMediaType,
    val sizeBytes: Long,
    val durationMs: Long?,
)

sealed interface PrepareResult {
    data class Ready(val media: PreparedMedia) : PrepareResult
    data class Rejected(val message: String) : PrepareResult
}

/** 사진 선택기·카메라가 돌려준 Content URI를 검증된 임시 파일로 바꾼다. 화면·ViewModel이 Android 파일 API에 묶이지 않게 분리했다. */
interface ProofMediaPreparer {
    suspend fun prepare(source: String): PrepareResult
    fun discard(media: PreparedMedia)
}

/**
 * Content URI를 캐시 파일로 스트리밍 복사하며 크기를 센다(전체를 메모리에 올리지 않고, 한도를 넘으면 즉시 중단).
 * 실제 형식은 선언된 MIME이 아니라 파일 시그니처로 판별하고, 영상은 길이를 읽어 5초 한도를 확인한다. 저장소 권한은 필요 없다.
 */
class AndroidProofMediaPreparer(context: Context) : ProofMediaPreparer {
    private val app = context.applicationContext
    private val dir = File(app.cacheDir, DIR).apply { mkdirs() }

    init {
        purgeStale()
    }

    override suspend fun prepare(source: String): PrepareResult = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${UUID.randomUUID()}.tmp")
        try {
            val total = copy(Uri.parse(source), tmp) ?: return@withContext PrepareResult.Rejected("파일을 열 수 없어요. 다시 선택해 주세요.")
            val mime = tmp.inputStream().use { ins -> ByteArray(16).let { b -> b.copyOf(ins.read(b).coerceAtLeast(0)) } }
                .let(ProofValidation::sniffMime)
            val type = ProofValidation.mediaTypeOf(mime)
            val duration = if (type == ProofMediaType.VIDEO) readDurationMs(tmp) else null
            ProofValidation.validateMedia(mime, total, duration)?.let {
                tmp.delete()
                return@withContext PrepareResult.Rejected(it)
            }
            val final = File(dir, "${UUID.randomUUID()}.${ProofValidation.extensionOf(mime!!)}")
            if (!tmp.renameTo(final)) {
                tmp.delete()
                return@withContext PrepareResult.Rejected("파일을 준비하지 못했어요. 다시 선택해 주세요.")
            }
            PrepareResult.Ready(PreparedMedia(final, mime, type!!, total, duration))
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: TooLarge) {
            tmp.delete()
            PrepareResult.Rejected("파일 용량이 너무 커요. 사진·영상은 10MB 이하만 올릴 수 있어요.")
        } catch (e: IOException) {
            tmp.delete()
            PrepareResult.Rejected("파일을 읽지 못했어요. 다시 선택해 주세요.")
        } catch (e: SecurityException) {
            tmp.delete()
            PrepareResult.Rejected("파일에 접근할 수 없어요. 다시 선택해 주세요.")
        }
    }

    override fun discard(media: PreparedMedia) {
        media.file.delete()
    }

    private class TooLarge : IOException()

    private fun copy(uri: Uri, dest: File): Long? {
        val input = app.contentResolver.openInputStream(uri) ?: return null
        var total = 0L
        input.use { ins ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_COPY_BYTES) throw TooLarge()
                    out.write(buf, 0, n)
                }
            }
        }
        return total
    }

    private fun readDurationMs(file: File): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** 이전 실행에서 남은 임시 파일(1시간 이상)을 지운다. */
    private fun purgeStale() {
        val cutoff = System.currentTimeMillis() - 3_600_000L
        listOf(dir, File(app.cacheDir, CAPTURE_DIR)).forEach { d ->
            d.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    companion object {
        const val DIR = "proof_media"

        /** 카메라 앱이 결과를 쓰는 FileProvider 경로(res/xml/file_paths.xml). */
        const val CAPTURE_DIR = "proof_capture"
        private const val MAX_COPY_BYTES = ProofValidation.IMAGE_MAX_BYTES + 1
    }
}
