package com.dailyquest.app.ui.decor

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dailyquest.app.data.avatar.AvatarModelFetcher
import com.dailyquest.app.data.avatar.CachingAvatarModelFetcher
import com.dailyquest.app.data.avatar.HttpAvatarModelFetcher
import com.dailyquest.app.data.avatar.PngFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

/**
 * 방·가구 PNG를 그릴 수 있는 그림으로 가져온다 (STEP 10-4). 실패는 [Result.failure].
 * 화면([RoomView])은 이 인터페이스만 알고, 테스트는 가짜 그림을 넣는다.
 */
fun interface DecorImageSource {
    suspend fun load(url: String): Result<ImageBitmap>
}

/**
 * 기기 저장(디스크) + 메모리 두 단계 캐시.
 * - 디스크: STEP 9의 GLB 캐시와 같은 방식([CachingAvatarModelFetcher], PNG 검사). S3 가구 파일은 바뀌지 않는다(연동 규칙 11-2, 1년 immutable).
 * - 메모리: 디코딩한 그림을 [MEMORY_BYTES]까지. 방 그림(1080×810)이 약 3.5MB, 가구는 하나에 수십 KB다.
 */
class CachedDecorImages(private val files: AvatarModelFetcher) : DecorImageSource {
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }

    override suspend fun load(url: String): Result<ImageBitmap> {
        memory.get(url)?.let { return Result.success(it) }
        return files.fetch(url).mapCatching { bytes ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?: throw IOException("그림을 읽을 수 없습니다: $url")
            }
        }.onSuccess { memory.put(url, it) }
    }

    /** 메모리 캐시만 비운다 (디버그 화면의 "캐시 비우기"에서 디스크 캐시와 함께 비운다). */
    fun clearMemory() = memory.evictAll()

    companion object {
        const val MEMORY_BYTES = 24 * 1024 * 1024
    }
}

/** 앱 전체에서 같은 캐시를 쓰도록 하나만 만든다. */
object DecorImages {
    @Volatile private var files: CachingAvatarModelFetcher? = null
    @Volatile private var source: CachedDecorImages? = null

    /** 저장 공간이 부족하면 시스템이 지울 수 있는 캐시 폴더. 지워져도 다시 받으면 된다. */
    fun cacheDirectory(context: Context): File = File(context.applicationContext.cacheDir, "decor_images")

    /** 디스크 캐시 (디버그 화면의 "캐시 비우기"·개수 표시용). */
    fun files(context: Context): CachingAvatarModelFetcher = files ?: synchronized(this) {
        files ?: CachingAvatarModelFetcher(
            network = HttpAvatarModelFetcher(OkHttpClient(), PngFormat::isPng, "PNG"),
            directory = cacheDirectory(context),
            // 방 1장 + 가구 50종 × 4방향 = 약 3MB. 새 가구가 늘어도 넉넉하다.
            maxBytes = 30L * 1024 * 1024,
            isValid = PngFormat::isPng,
        ).also { files = it }
    }

    fun source(context: Context): CachedDecorImages = source ?: synchronized(this) {
        source ?: CachedDecorImages(files(context)).also { source = it }
    }
}
