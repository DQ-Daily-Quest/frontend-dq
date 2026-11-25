package com.dailyquest.app.ui.proof

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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val imageClient: OkHttpClient by lazy {
    OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
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
 * [contentScale] 기본은 칸을 꽉 채우고 넘치는 부분을 자른다(Crop). 상점 가구 그림처럼 잘리면 안 되는 그림은 Fit을 넘긴다(STEP 10-8).
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "미리보기를 불러올 수 없어요",
    contentScale: ContentScale = ContentScale.Crop,
) {
    val cache = LocalContext.current.cacheDir
    var attempt by remember(url) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(url?.let { bitmapCache.get(it) }, url, attempt) {
        if (url.isNullOrBlank()) return@produceState
        bitmapCache.get(url)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) { loadWithRetry { download(url, cache) } }?.also { bitmapCache.put(url, it) }
    }
    Box(modifier.let { if (bitmap == null && !url.isNullOrBlank()) it.clickable { attempt++ } else it }, contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = contentScale)
        else Txt(placeholder, size = 12f, color = DQColors.Muted)
    }
}

private fun download(url: String, cacheDir: File): Bitmap? {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    val tmp = File.createTempFile("img", ".bin", cacheDir)
    try {
        imageClient.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.byteStream()?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } } ?: return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
        return BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } finally {
        tmp.delete()
    }
}
