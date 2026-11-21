package com.dailyquest.app.data.avatar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 아바타 파츠 GLB 파일의 내용을 가져온다. 실패는 예외 대신 [Result.failure]로 돌려준다.
 * 화면(AvatarView)은 이 인터페이스만 알고, 다운로드·캐시 방식은 구현이 정한다 (STEP 9-5에서 캐시 구현 추가).
 */
fun interface AvatarModelFetcher {
    suspend fun fetch(url: String): Result<ByteArray>
}

/**
 * 캐시 없이 매번 HTTP로 받는 기본 구현.
 * GLB 주소는 S3 공개 주소라 앱 API용 클라이언트(인증 헤더 포함)가 아닌 별도 클라이언트를 쓴다.
 */
class HttpAvatarModelFetcher(private val client: OkHttpClient) : AvatarModelFetcher {

    override suspend fun fetch(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("응답 본문이 없습니다")
                if (!GlbFormat.isGlb(bytes)) throw IOException("GLB 파일이 아닙니다 (${bytes.size}바이트)")
                Result.success(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** 앱 전체에서 하나만 쓴다. OkHttp 클라이언트는 연결·스레드를 공유하도록 재사용하는 것이 권장된다. */
        val shared: HttpAvatarModelFetcher by lazy { HttpAvatarModelFetcher(OkHttpClient()) }
    }
}

object GlbFormat {
    /** GLB 헤더: 앞 4바이트가 ASCII "glTF", 그다음 4바이트(리틀엔디언)가 버전 2. */
    fun isGlb(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[4].toInt() == 2 && bytes[5].toInt() == 0 && bytes[6].toInt() == 0 && bytes[7].toInt() == 0
}
