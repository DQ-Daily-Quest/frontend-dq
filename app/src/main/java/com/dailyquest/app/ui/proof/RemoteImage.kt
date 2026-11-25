package com.dailyquest.app.ui.proof

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import android.util.LruCache
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 이미지 받기용 인터넷 연결. [cacheDir]를 주면 받은 이미지를 기기에 저장해 다시 받지 않는다(상점 이미지 속도 1단계).
 * OkHttp의 HTTP 캐시라 **서버가 저장해도 된다고 한 것만** 저장한다: 상점·가구 썸네일(S3)은 `max-age=1년, immutable`이라
 * 앱을 다시 켜도 인터넷 없이 바로 쓴다. 저장 금지·유효기간이 없는 응답(인증 사진 등)은 전과 같이 매번 받는다.
 */
internal fun imageClient(cacheDir: File?, maxBytes: Long = RemoteImages.DISK_CACHE_BYTES): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .apply { if (cacheDir != null) cache(Cache(cacheDir, maxBytes)) }
        .build()

/** 앱 전체가 함께 쓰는 이미지 연결과 기기 저장소. */
object RemoteImages {
    /** 상점 썸네일 108개가 약 10MB. 상품이 늘어도 넉넉하게. 넘치면 오래 안 쓴 것부터 지운다. */
    const val DISK_CACHE_BYTES = 50L * 1024 * 1024

    @Volatile private var client: OkHttpClient? = null

    /** 저장 공간이 부족하면 시스템이 지울 수 있는 캐시 폴더. 지워져도 다시 받으면 된다. */
    fun cacheDirectory(context: Context): File = File(context.applicationContext.cacheDir, "remote_images")

    fun client(context: Context): OkHttpClient = client ?: synchronized(this) {
        client ?: imageClient(cacheDirectory(context)).also { client = it }
    }
}

/** 성공한 비트맵만 보관한다. 실패는 저장하지 않으므로 화면에 다시 들어오면 다시 받는다. */
private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** 일시적인 실패(연결 끊김·타임아웃·5xx)에 대비해 짧게 몇 번 더 시도한다. 성공하면 즉시 끝낸다. */
internal suspend fun <T : Any> loadWithRetry(
    attempts: Int = 3,
    delayMs: Long = 600,
    load: suspend () -> T?,
): T? {
    repeat(attempts) { i ->
        val r = runCatching { load() }.getOrNull()
        if (r != null) return r
        if (i < attempts - 1) delay(delayMs * (i + 1))
    }
    return null
}

private const val MAX_EDGE = 1280

/**
 * 인증 사진/썸네일 표시. 이미지 라이브러리를 추가하지 않고 인증 헤더 없이 공개 URL을 받아 축소 디코딩한다.
 * 받은 파일은 서버가 허용하면 기기에 저장해 다시 받지 않는다([RemoteImages]).
 * [contentScale] 기본은 칸을 꽉 채우고 넘치는 부분을 자른다(Crop). 상점 가구 그림처럼 잘리면 안 되는 그림은 Fit을 넘긴다(STEP 10-8).
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "미리보기를 불러올 수 없어요",
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(url?.let { bitmapCache.get(it) }, url, attempt) {
        if (url.isNullOrBlank()) return@produceState
        bitmapCache.get(url)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            val client = RemoteImages.client(context)
            loadWithRetry { download(client, url, context.cacheDir) }
        }?.also { bitmapCache.put(url, it) }
    }
    Box(modifier.let { if (bitmap == null && !url.isNullOrBlank()) it.clickable { attempt++ } else it }, contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = contentScale)
        else Txt(placeholder, size = 12f, color = DQColors.Muted)
    }
}

/** [url]을 [file]에 받는다. 기기에 저장된 것이 있으면 인터넷 없이 거기서 읽는다. 실패하면 false. */
internal fun fetchToFile(client: OkHttpClient, url: String, file: File): Boolean {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return false
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return false
        r.body?.byteStream()?.use { ins -> file.outputStream().use { ins.copyTo(it) } } ?: return false
    }
    return true
}

private fun download(client: OkHttpClient, url: String, tmpDir: File): Bitmap? {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val tmp = File.createTempFile("img", ".bin", tmpDir)
    try {
        if (!fetchToFile(client, url, tmp)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
        return BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } finally {
        tmp.delete()
    }
}
