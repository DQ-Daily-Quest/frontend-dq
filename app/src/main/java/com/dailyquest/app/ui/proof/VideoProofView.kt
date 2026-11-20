package com.dailyquest.app.ui.proof

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dailyquest.app.ui.components.Bold
import com.dailyquest.app.ui.components.Txt
import com.dailyquest.app.ui.theme.DQColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val THUMB_MAX_EDGE = 720
private const val EXTRACT_TIMEOUT_MS = 20_000L
private const val LOAD_TIMEOUT_MS = 25_000L

private val extractScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * MediaMetadataRetriever로 영상의 첫 프레임과 재생시간을 얻는다. 서버 영상 URL은 공개 주소라 인증 헤더 없이 접근하며,
 * 따라서 JWT가 외부 URL로 나가지 않는다(빈 헤더 맵을 명시). 응답이 없는 서버에 묶이지 않도록 시간 제한을 둔다.
 */
object RetrieverFrameExtractor : FrameExtractor {
    override suspend fun extract(source: String, into: File): Long? {
        val job = extractScope.async { runCatching { extractBlocking(source, into) }.getOrNull() }
        val r = withTimeoutOrNull(EXTRACT_TIMEOUT_MS) { job.await() }
        if (r == null) job.cancel()
        return r
    }

    private fun extractBlocking(source: String, into: File): Long? {
        val r = MediaMetadataRetriever()
        try {
            if (source.startsWith("http://") || source.startsWith("https://")) r.setDataSource(source, HashMap<String, String>())
            else r.setDataSource(source)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scaled = scaleDown(frame)
            into.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            return dur
        } finally {
            runCatching { r.release() }
        }
    }

    private fun scaleDown(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= THUMB_MAX_EDGE) return b
        val f = THUMB_MAX_EDGE.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }
}

object VideoThumbnails {
    @Volatile private var cache: VideoThumbnailCache? = null

    fun cache(context: Context): VideoThumbnailCache = cache ?: synchronized(this) {
        cache ?: VideoThumbnailCache(File(context.applicationContext.cacheDir, "video_thumbs"), RetrieverFrameExtractor).also { cache = it }
    }
}

private enum class PlayerState { LOADING, PLAYING, PAUSED, ENDED, ERROR }

/** 로컬 임시 파일은 경로가 재사용돼도 다른 영상의 썸네일이 보이지 않도록 크기·수정시각을 키에 넣는다. */
private fun cacheKeyOf(source: String): String {
    if (source.startsWith("http://") || source.startsWith("https://")) return source
    val f = File(source)
    return "$source#${f.length()}#${f.lastModified()}"
}

/**
 * 영상 인증 표시: 실제 첫 프레임 썸네일 + 가운데 재생 아이콘 + 우측 하단 재생시간. 누르면 같은 자리에서 앱 안에서 재생한다.
 * [source]는 서버 영상 URL 또는 로컬 파일 경로. 썸네일 추출에 실패하면 [fallbackThumbUrl]의 이미지, 그것도 없으면 안내 문구로 대체하며 재생은 가능하다.
 */
@Composable
fun VideoProofView(
    source: String?,
    modifier: Modifier = Modifier,
    knownDurationMs: Long? = null,
    fallbackThumbUrl: String? = null,
    tag: String = "proofVideo",
) {
    var playing by remember(source) { mutableStateOf(false) }
    Box(modifier.background(Color.Black).testTag(tag)) {
        when {
            source.isNullOrBlank() -> Box(Modifier.fillMaxSize().background(DQColors.Soft), contentAlignment = Alignment.Center) {
                Txt("영상을 불러올 수 없어요", size = 12f, color = DQColors.Muted, modifier = Modifier.testTag("${tag}Unavailable"))
            }
            playing -> VideoPlayer(source, tag)
            else -> VideoThumbnail(source, knownDurationMs, fallbackThumbUrl, tag) { playing = true }
        }
    }
}

@Composable
private fun VideoThumbnail(source: String, knownDurationMs: Long?, fallbackThumbUrl: String?, tag: String, onPlay: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember(source) { mutableStateOf(false) }
    val thumb by produceState<VideoThumb?>(null, source) {
        value = VideoThumbnails.cache(context).get(source, cacheKeyOf(source))
        loaded.value = true
    }
    val bitmap by produceState<Bitmap?>(null, thumb) {
        value = thumb?.let { t -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(t.file.path) }.getOrNull() } }
    }
    val durationMs = knownDurationMs ?: thumb?.durationMs
    Box(Modifier.fillMaxSize().clickable(onClick = onPlay).testTag("${tag}Thumb")) {
        val b = bitmap
        when {
            b != null -> Image(b.asImageBitmap(), "영상 썸네일", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            loaded.value && !fallbackThumbUrl.isNullOrBlank() -> RemoteImage(fallbackThumbUrl, Modifier.fillMaxSize(), placeholder = "영상 인증이에요")
            loaded.value -> Box(Modifier.fillMaxSize().background(DQColors.Soft), contentAlignment = Alignment.Center) {
                Txt("영상 인증이에요", size = 12f, color = DQColors.Muted, modifier = Modifier.padding(bottom = 56.dp).testTag("${tag}ThumbFallback"))
            }
            else -> Box(Modifier.fillMaxSize().background(DQColors.Soft))
        }
        PlayBadge(Modifier.align(Alignment.Center))
        if (durationMs != null && durationMs >= 0) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Txt(VideoThumbnailCache.formatDuration(durationMs), size = 11.5f, weight = Bold, color = Color.White, modifier = Modifier.testTag("${tag}Duration"))
            }
        }
    }
}

@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close()
            }
            drawPath(p, Color.White)
        }
    }
}

@Composable
private fun VideoPlayer(source: String, tag: String) {
    var state by remember(source) { mutableStateOf(PlayerState.LOADING) }
    var attempt by remember(source) { mutableIntStateOf(0) }
    var view by remember(source) { mutableStateOf<VideoView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // 화면을 벗어나면 일시정지하고, 컴포지션에서 제거되면 플레이어를 완전히 해제한다.
    DisposableEffect(lifecycle, view) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) view?.let { if (it.isPlaying) { it.pause(); state = PlayerState.PAUSED } }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); view?.let { runCatching { it.stopPlayback() } } }
    }
    LaunchedEffect(source, attempt) {
        if (state == PlayerState.LOADING) { delay(LOAD_TIMEOUT_MS); if (state == PlayerState.LOADING) state = PlayerState.ERROR }
    }

    Box(Modifier.fillMaxSize().testTag("${tag}Player")) {
        key(attempt) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnPreparedListener { mp -> mp.isLooping = false; state = PlayerState.PLAYING; start() }
                        setOnCompletionListener { state = PlayerState.ENDED }
                        setOnErrorListener { _, _, _ -> state = PlayerState.ERROR; true }
                        if (source.startsWith("http://") || source.startsWith("https://")) setVideoURI(Uri.parse(source)) else setVideoPath(source)
                        view = this
                    }
                },
                onRelease = { runCatching { it.stopPlayback() } },
            )
        }
        when (state) {
            PlayerState.LOADING -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp).testTag("${tag}Loading"), color = Color.White)
            PlayerState.ERROR -> Column(
                Modifier.fillMaxSize().background(Color(0xCC000000)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Txt("영상을 재생할 수 없어요", size = 13f, weight = Bold, color = Color.White, modifier = Modifier.testTag("${tag}Error"))
                ControlPill("다시 시도", Modifier.padding(top = 10.dp).testTag("${tag}Retry")) { state = PlayerState.LOADING; attempt++ }
            }
            else -> {
                val label = when (state) { PlayerState.PLAYING -> "일시정지"; PlayerState.ENDED -> "다시 재생"; else -> "재생" }
                ControlPill(label, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("${tag}Control")) {
                    val v = view ?: return@ControlPill
                    when (state) {
                        PlayerState.PLAYING -> { v.pause(); state = PlayerState.PAUSED }
                        PlayerState.ENDED -> { v.seekTo(0); v.start(); state = PlayerState.PLAYING }
                        else -> { v.start(); state = PlayerState.PLAYING }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.background(Color(0xB3000000), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Txt(text, size = 13f, weight = Bold, color = Color.White)
    }
}
