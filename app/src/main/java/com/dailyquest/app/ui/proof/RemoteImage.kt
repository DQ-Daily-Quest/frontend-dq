package com.dailyquest.app.ui.proof

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private val imageClient: OkHttpClient by lazy { OkHttpClient() }

private const val MAX_EDGE = 1280

/** 인증 사진/썸네일 표시. 이미지 라이브러리를 추가하지 않고 인증 헤더 없이 공개 URL을 받아 축소 디코딩한다. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, placeholder: String = "미리보기를 불러올 수 없어요") {
    val cache = LocalContext.current.cacheDir
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) { runCatching { download(url, cache) }.getOrNull() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
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
