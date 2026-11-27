package com.dailyquest.app.ui.proof

import com.dailyquest.app.data.proof.ProofMediaType
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
private const val PREPARE_TIMEOUT_MS = 15_000L

private val extractScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * MediaMetadataRetriever로 영상의 첫 프레임과 재생시간을 얻는다. 서버 영상 URL은 공개 주소라 인증 헤더 없이 접근하며,
 * 따라서 JWT가 외부 URL로 나가지 않는다(빈 헤더 맵을 명시). 응답이 없는 서버에 묶이지 않도록 시간 제한을 둔다.
 */
object RetrieverFrameExtractor : FrameExtractor {
    @Volatile var localCopy: (String) -> File? = { null }

    override suspend fun extract(source: String, into: File): Long? {
        val src = localCopy(source)?.path ?: source
        val job = extractScope.async { runCatching { extractBlocking(src, into) }.getOrNull() }
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

object VideoDownloads {
    @Volatile private var downloader: VideoDownloader? = null

    /** 계측 테스트에서 네트워크 대신 가짜 응답을 쓰기 위한 교체 지점. */
    fun setForTest(d: VideoDownloader?) { downloader = d }

    fun get(context: Context): VideoDownloader = downloader ?: synchronized(this) {
        downloader ?: VideoDownloader(File(context.applicationContext.cacheDir, "video_play")).also { downloader = it }
    }
}

object VideoThumbnails {
    @Volatile private var cache: VideoThumbnailCache? = null

    fun cache(context: Context): VideoThumbnailCache = cache ?: synchronized(this) {
        RetrieverFrameExtractor.localCopy = { url -> VideoDownloads.get(context).cachedFile(url) }
        cache ?: VideoThumbnailCache(File(context.applicationContext.cacheDir, "video_thumbs"), RetrieverFrameExtractor).also { cache = it }
    }
}

private enum class PlayerState { DOWNLOADING, LOADING, PLAYING, PAUSED, ENDED, ERROR }

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
    val context = LocalContext.current
    val remote = source.startsWith("http://") || source.startsWith("https://")
    var state by remember(source) { mutableStateOf(if (remote) PlayerState.DOWNLOADING else PlayerState.LOADING) }
    var attempt by remember(source) { mutableIntStateOf(0) }
    var localPath by remember(source) { mutableStateOf<String?>(if (remote) null else source) }
    var errorText by remember(source) { mutableStateOf(PLAY_FAILED) }
    var view by remember(source) { mutableStateOf<ExoPlayer?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // 화면을 벗어나면 일시정지한다. 플레이어 해제는 AndroidView.onRelease에서 처리한다.
    val latest by androidx.compose.runtime.rememberUpdatedState(view)
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) latest?.let { if (it.isPlaying) { it.pause(); state = PlayerState.PAUSED } }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // 내려받기와 준비 단계 모두 시간 제한을 둬 무한 로딩을 막는다. 내려받기는 OkHttp 제한(연결 10초·읽기 15초·전체 60초)을 따른다.
    LaunchedEffect(source, attempt) {
        if (remote && localPath == null) {
            state = PlayerState.DOWNLOADING
            when (val r = VideoDownloads.get(context).fetch(source)) {
                is VideoFetch.Ready -> { localPath = r.file.path; state = PlayerState.LOADING }
                is VideoFetch.Failed -> { errorText = fetchMessage(r.reason); state = PlayerState.ERROR }
            }
        }
        if (localPath != null) {
            state = PlayerState.LOADING
            delay(PREPARE_TIMEOUT_MS)
            if (state == PlayerState.LOADING) { errorText = PLAY_FAILED; state = PlayerState.ERROR }
        }
    }

    Box(Modifier.fillMaxSize().testTag("${tag}Player")) {
        val path = localPath
        if (path != null) key(attempt, path) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val player = ExoPlayer.Builder(ctx).build().apply {
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(st: Int) {
                                if (st == Player.STATE_READY && state == PlayerState.LOADING) { state = PlayerState.PLAYING; play() }
                                if (st == Player.STATE_ENDED) state = PlayerState.ENDED
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                android.util.Log.w("DQVideo", "playback error code=${error.errorCodeName}")
                                errorText = PLAY_FAILED; state = PlayerState.ERROR
                            }
                        })
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                        prepare()
                    }
                    view = player
                    PlayerView(ctx).apply { useController = false; this.player = player }
                },
                onRelease = { v -> v.player?.let { runCatching { it.release() } }; v.player = null },
            )
        }
        when (state) {
            PlayerState.DOWNLOADING, PlayerState.LOADING -> Column(
                Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.size(36.dp).testTag("${tag}Loading"), color = Color.White)
                if (state == PlayerState.DOWNLOADING) Txt("영상을 불러오는 중이에요", size = 12f, color = Color.White, modifier = Modifier.padding(top = 10.dp))
            }
            PlayerState.ERROR -> Column(
                Modifier.fillMaxSize().background(Color(0xCC000000)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Txt(errorText, size = 13f, weight = Bold, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp).testTag("${tag}Error"))
                ControlPill("다시 시도", Modifier.padding(top = 10.dp).testTag("${tag}Retry")) {
                    // 받아 둔 파일이 손상됐을 수 있어 재시도 때는 캐시를 지우고 새로 받는다.
                    if (remote) { VideoDownloads.get(context).cachedFile(source)?.delete(); localPath = null }
                    view = null
                    state = if (remote) PlayerState.DOWNLOADING else PlayerState.LOADING
                    attempt++
                }
            }
            else -> {
                val label = when (state) { PlayerState.PLAYING -> "일시정지"; PlayerState.ENDED -> "다시 재생"; else -> "재생" }
                ControlPill(label, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("${tag}Control")) {
                    val v = view ?: return@ControlPill
                    when (state) {
                        PlayerState.PLAYING -> { v.pause(); state = PlayerState.PAUSED }
                        PlayerState.ENDED -> { v.seekTo(0); v.play(); state = PlayerState.PLAYING }
                        else -> { v.play(); state = PlayerState.PLAYING }
                    }
                }
            }
        }
    }
}

private const val PLAY_FAILED = "영상을 재생할 수 없어요"
private fun fetchMessage(r: VideoFetch.Reason) = when (r) {
    VideoFetch.Reason.NETWORK -> "네트워크 문제로 영상을 불러오지 못했어요"
    VideoFetch.Reason.HTTP -> "영상 파일을 찾을 수 없어요"
    VideoFetch.Reason.TOO_LARGE -> "영상 용량이 너무 커서 재생할 수 없어요"
}

@Composable
private fun ControlPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.background(Color(0xB3000000), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Txt(text, size = 13f, weight = Bold, color = Color.White)
    }
}

/**
 * 인증 팝업(승인·거절·삭제) 카드의 작은 그림 (QA 디자인).
 * 사진은 썸네일(없으면 원본), 영상은 서버 썸네일이 없으면 **첫 장면**(인증 상세와 같은 기기 저장 캐시라 한 번 뽑으면 바로 보인다)
 * 위에 작은 ▶, 글 인증은 "글".
 */
@Composable
fun ProofMediaThumb(mediaType: ProofMediaType, mediaUrl: String?, thumbnailUrl: String?, modifier: Modifier = Modifier) {
    when (mediaType) {
        ProofMediaType.IMAGE -> RemoteImage(thumbnailUrl ?: mediaUrl, modifier, placeholder = "사진")
        ProofMediaType.TEXT -> Box(modifier.background(DQColors.White), contentAlignment = Alignment.Center) { Txt("글", size = 14f, weight = Bold) }
        ProofMediaType.VIDEO -> Box(modifier.background(DQColors.Soft), contentAlignment = Alignment.Center) {
            val context = LocalContext.current
            val bitmap by produceState<Bitmap?>(null, mediaUrl, thumbnailUrl) {
                if (thumbnailUrl != null || mediaUrl.isNullOrBlank()) return@produceState
                val thumb = VideoThumbnails.cache(context).get(mediaUrl, cacheKeyOf(mediaUrl))
                value = thumb?.let { t -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(t.file.path) }.getOrNull() } }
            }
            val b = bitmap
            when {
                thumbnailUrl != null -> RemoteImage(thumbnailUrl, Modifier.matchParentSize(), placeholder = "영상")
                b != null -> Image(b.asImageBitmap(), "영상 첫 장면", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                else -> Txt("영상", size = 12f, color = DQColors.Muted)
            }
            Box(Modifier.size(22.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(9.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close()
                    }
                    drawPath(path, Color.White)
                }
            }
        }
    }
}
