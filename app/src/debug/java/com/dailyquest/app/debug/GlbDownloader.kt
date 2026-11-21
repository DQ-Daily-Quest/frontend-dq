package com.dailyquest.app.debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 3D 테스트 화면(디버그 전용)에서 GLB 파일을 통째로 내려받는다.
 * 디스크 캐시는 하지 않는다. 실제 앱용 다운로드·캐시 저장소는 STEP 9-5에서 따로 만든다.
 */
class GlbDownloader(private val client: OkHttpClient) {

    suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("응답 본문이 없습니다")
                if (!isGlb(bytes)) throw IOException("GLB 파일이 아닙니다 (${bytes.size}바이트)")
                Result.success(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** GLB 헤더: 앞 4바이트가 ASCII "glTF", 그다음 4바이트(리틀엔디언)가 버전 2. */
        fun isGlb(bytes: ByteArray): Boolean =
            bytes.size >= 12 &&
                bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
                bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[4].toInt() == 2 && bytes[5].toInt() == 0 && bytes[6].toInt() == 0 && bytes[7].toInt() == 0
    }
}
